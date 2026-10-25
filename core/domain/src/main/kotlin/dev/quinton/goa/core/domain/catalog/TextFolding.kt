package dev.quinton.goa.core.domain.catalog

/**
 * Unicode canonical decomposition (NFD), so accents become separate combining marks that search
 * can drop ("Ñ" → "N" + "◌̃"): `java.text.Normalizer` on the JVM, `String.prototype.normalize` in
 * JavaScript. Both follow the Unicode standard, so the phone and the browser fold the same way.
 */
internal expect fun decomposeCanonical(text: String): String
