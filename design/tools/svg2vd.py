"""Convert the hand-authored SVG masters in design/ to Android VectorDrawables.

Usage:
    python -P -E design/tools/svg2vd.py            # convert everything (from repo root)
    python -P -E design/tools/svg2vd.py --check    # fail if any generated XML is stale

Deliberately small: the masters only use <path>, <line>, <polyline>, <polygon>, <circle>,
<ellipse>, <rect> (no rx), untransformed <g> groups, and <linearGradient>/<radialGradient>
fills referenced as url(#id). Presentation attributes are inherited from <svg>/<g>.
Anything else raises, so a master can never silently convert wrong.

Mapping (master -> generated):
    design/sigils/<id>.svg            -> core/designsystem/src/main/res/drawable/sigil_<id>.xml
    design/avatars/crest_NN.svg       -> core/designsystem/src/main/res/drawable/crest_NN.xml
    design/icons/<name>.svg           -> core/designsystem/src/main/res/drawable/ic_<name>.xml
    design/brand/ic_launcher_*.svg    -> app/src/main/res/drawable/ic_launcher_*.xml
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DS_DRAWABLE = os.path.join(ROOT, "core", "designsystem", "src", "main", "res", "drawable")
APP_DRAWABLE = os.path.join(ROOT, "app", "src", "main", "res", "drawable")

INHERITED = (
    "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule",
    "opacity", "fill-opacity", "stroke-opacity", "stroke-miterlimit",
)


def num(v):
    f = float(v)
    s = f"{f:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def circle_path(cx, cy, rx, ry):
    # Two half-arcs; VectorDrawable has no circle primitive.
    return (
        f"M{num(cx - rx)},{num(cy)}"
        f"A{num(rx)},{num(ry)} 0 1,1 {num(cx + rx)},{num(cy)}"
        f"A{num(rx)},{num(ry)} 0 1,1 {num(cx - rx)},{num(cy)}Z"
    )


def points_path(points, close):
    vals = [float(p) for p in re.split(r"[\s,]+", points.strip()) if p]
    pairs = [(vals[i], vals[i + 1]) for i in range(0, len(vals), 2)]
    d = "M" + " L".join(f"{num(x)},{num(y)}" for x, y in pairs)
    return d + ("Z" if close else "")


def color(value, opacity):
    """SVG colour -> #AARRGGBB."""
    v = value.strip()
    if v.startswith("#") and len(v) == 4:
        v = "#" + "".join(c * 2 for c in v[1:])
    named = {"white": "#FFFFFF", "black": "#000000"}
    v = named.get(v, v)
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", v):
        raise ValueError(f"unsupported colour {value!r}")
    a = round(max(0.0, min(1.0, opacity)) * 255)
    return f"#{a:02X}{v[1:].upper()}"


def style_attrs(el):
    attrs = dict(el.attrib)
    style = attrs.pop("style", None)
    if style:
        for decl in style.split(";"):
            if ":" in decl:
                k, v = decl.split(":", 1)
                attrs[k.strip()] = v.strip()
    return attrs


class Converter:
    def __init__(self, path):
        self.path = path
        self.tree = ET.parse(path)
        self.root = self.tree.getroot()
        self.gradients = {}
        self.out = []

    def gradient_xml(self, gid, indent):
        g = self.gradients.get(gid)
        if g is None:
            raise ValueError(f"{self.path}: unknown gradient #{gid}")
        tag = g.tag.replace(SVG_NS, "")
        a = g.attrib
        if a.get("gradientUnits") != "userSpaceOnUse":
            raise ValueError(f"{self.path}: gradient #{gid} must use gradientUnits=userSpaceOnUse")
        if tag == "linearGradient":
            head = (
                f'<gradient android:type="linear" android:startX="{num(a["x1"])}" '
                f'android:startY="{num(a["y1"])}" android:endX="{num(a["x2"])}" android:endY="{num(a["y2"])}">'
            )
        else:
            head = (
                f'<gradient android:type="radial" android:centerX="{num(a["cx"])}" '
                f'android:centerY="{num(a["cy"])}" android:gradientRadius="{num(a["r"])}">'
            )
        lines = [f'{indent}<aapt:attr name="android:fillColor">', f"{indent}    {head}"]
        for stop in g.iter(SVG_NS + "stop"):
            sa = style_attrs(stop)
            off = sa.get("offset", "0")
            off = float(off[:-1]) / 100 if off.endswith("%") else float(off)
            c = color(sa.get("stop-color", "#000000"), float(sa.get("stop-opacity", "1")))
            lines.append(f'{indent}        <item android:offset="{num(off)}" android:color="{c}" />')
        lines += [f"{indent}    </gradient>", f"{indent}</aapt:attr>"]
        return lines

    def shape_d(self, tag, a):
        f = lambda k, d=0.0: float(a.get(k, d))  # noqa: E731
        if tag == "path":
            return " ".join(a["d"].split())
        if tag == "line":
            return f"M{num(f('x1'))},{num(f('y1'))} L{num(f('x2'))},{num(f('y2'))}"
        if tag == "polyline":
            return points_path(a["points"], close=False)
        if tag == "polygon":
            return points_path(a["points"], close=True)
        if tag == "circle":
            return circle_path(f("cx"), f("cy"), f("r"), f("r"))
        if tag == "ellipse":
            return circle_path(f("cx"), f("cy"), f("rx"), f("ry"))
        if tag == "rect":
            if "rx" in a or "ry" in a:
                raise ValueError(f"{self.path}: rounded rect not supported, use a path")
            x, y, w, h = f("x"), f("y"), f("width"), f("height")
            return f"M{num(x)},{num(y)} h{num(w)} v{num(h)} h{num(-w)} Z"
        raise ValueError(f"{self.path}: unsupported element <{tag}>")

    def emit_shape(self, tag, a, inherited):
        st = {**inherited, **{k: v for k, v in a.items() if k in INHERITED}}
        opacity = float(st.get("opacity", "1"))
        d = self.shape_d(tag, a)
        lines = ["    <path", f'        android:pathData="{d}"']
        fill = st.get("fill", "#000000")
        grad = None
        if fill.startswith("url(#"):
            grad = fill[5:-1]
        elif fill != "none":
            lines.append(f'        android:fillColor="{color(fill, opacity * float(st.get("fill-opacity", "1")))}"')
        if st.get("fill-rule") == "evenodd":
            lines.append('        android:fillType="evenOdd"')
        stroke = st.get("stroke", "none")
        if stroke != "none":
            lines.append(
                f'        android:strokeColor="{color(stroke, opacity * float(st.get("stroke-opacity", "1")))}"'
            )
            lines.append(f'        android:strokeWidth="{num(st.get("stroke-width", "1"))}"')
            cap = st.get("stroke-linecap", "butt")
            join = st.get("stroke-linejoin", "miter")
            lines.append(f'        android:strokeLineCap="{cap}"')
            lines.append(f'        android:strokeLineJoin="{join}"')
            if "stroke-miterlimit" in st:
                lines.append(f'        android:strokeMiterLimit="{num(st["stroke-miterlimit"])}"')
        if grad:
            lines[-1] += ">"
            lines += self.gradient_xml(grad, "        ")
            lines.append("    </path>")
        else:
            lines[-1] += " />"
        self.out += lines

    def walk(self, el, inherited):
        for child in el:
            tag = child.tag.replace(SVG_NS, "")
            a = style_attrs(child)
            if tag in ("title", "desc", "metadata"):
                continue
            if tag == "defs":
                for g in child:
                    gt = g.tag.replace(SVG_NS, "")
                    if gt not in ("linearGradient", "radialGradient"):
                        raise ValueError(f"{self.path}: unsupported <{gt}> in defs")
                    self.gradients[g.attrib["id"]] = g
                continue
            if "transform" in a:
                raise ValueError(f"{self.path}: transforms not supported; bake coordinates")
            if tag == "g":
                self.walk(child, {**inherited, **{k: v for k, v in a.items() if k in INHERITED}})
            else:
                self.emit_shape(tag, a, inherited)

    def convert(self, width_dp, height_dp):
        vb = [float(v) for v in self.root.attrib["viewBox"].split()]
        if vb[0] != 0 or vb[1] != 0:
            raise ValueError(f"{self.path}: viewBox must start at 0 0")
        root_attrs = {k: v for k, v in style_attrs(self.root).items() if k in INHERITED}
        # Pre-scan defs anywhere at top level.
        for child in self.root:
            if child.tag == SVG_NS + "defs":
                for g in child:
                    self.gradients[g.attrib["id"]] = g
        self.walk(self.root, root_attrs)
        uses_aapt = bool(self.gradients)
        rel = os.path.relpath(self.path, ROOT).replace(os.sep, "/")
        head = [
            '<?xml version="1.0" encoding="utf-8"?>',
            f"<!-- Generated from {rel} by design/tools/svg2vd.py. Do not edit; edit the SVG. -->",
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"'
            + ('\n    xmlns:aapt="http://schemas.android.com/aapt"' if uses_aapt else ""),
            f'    android:width="{num(width_dp)}dp"',
            f'    android:height="{num(height_dp)}dp"',
            f'    android:viewportWidth="{num(vb[2])}"',
            f'    android:viewportHeight="{num(vb[3])}">',
        ]
        return "\n".join(head + self.out + ["</vector>", ""])


def jobs():
    def listdir(sub):
        d = os.path.join(ROOT, "design", sub)
        return sorted(f for f in os.listdir(d) if f.endswith(".svg")) if os.path.isdir(d) else []

    for f in listdir("sigils"):
        yield os.path.join("sigils", f), os.path.join(DS_DRAWABLE, "sigil_" + f[:-4] + ".xml"), 24
    for f in listdir("avatars"):
        yield os.path.join("avatars", f), os.path.join(DS_DRAWABLE, f[:-4] + ".xml"), 24
    for f in listdir("icons"):
        yield os.path.join("icons", f), os.path.join(DS_DRAWABLE, "ic_" + f[:-4] + ".xml"), 24
    for f in listdir("brand"):
        if f.startswith("ic_launcher_"):
            yield os.path.join("brand", f), os.path.join(APP_DRAWABLE, f[:-4] + ".xml"), 108


def main():
    check = "--check" in sys.argv
    stale = []
    for src, dst, size in jobs():
        xml = Converter(os.path.join(ROOT, "design", src)).convert(size, size)
        old = open(dst, encoding="utf-8").read() if os.path.exists(dst) else None
        if old != xml:
            if check:
                stale.append(dst)
            else:
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "w", encoding="utf-8", newline="\n") as fh:
                    fh.write(xml)
                print("wrote", os.path.relpath(dst, ROOT))
    if stale:
        print("stale:", *stale, sep="\n  ")
        sys.exit(1)


if __name__ == "__main__":
    main()
