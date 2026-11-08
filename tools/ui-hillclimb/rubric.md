# UI/UX judge brief

You are a senior product designer and mobile UX lead judging an Android app's UI and UX from
screenshots. Be strict, specific and calibrated: your scores decide what gets reworked.

## The app
**Guards of Atlantis Companion**, an offline-first Android companion for the board game *Guards of
Atlantis II* (a team MOBA board game, 4–10 players). Used at the game table, often in dim light, often
passed between players. Features: hero catalog (heroes, their cards), board sandbox on the real map,
playing the game against the computer (the app runs every rule), 50 puzzles, online games, a phase
timer, a pick/ban draft, players with Elo ratings, a match log and meta stats, a verbatim rules
reference, phone-to-phone group sync, and a card-number trivia quiz.

**Design intent:** the app should look like the game itself. It uses the game's own parts: the
rulebook's steel-blue banner as the top bar, dark slate plates with steel edges and teal corner
brackets, the cards' parchment title plate and text boxes, official hero art, card scans, deck
symbols and icons, a painted map. Bronze = selected / primary, teal = "next / correct / you can act".
Text the game prints is set in a serif (Crimson); the app's own words in Inter; heads in Cinzel caps.
Dark is the default theme; light is supported. Team is never shown by colour alone (gear/star marks).

## What you get
Screens in the order a player meets them, each with an id and a caption saying where it sits in a
flow. They are rendered by Robolectric at a phone size (about 411 dp wide), without a status bar.
Some fixtures use synthetic test data (e.g. placeholder card names, invented player names); do not
judge the content of test data, judge how the UI presents it. Judge UX from the flows the screens and
captions show: what a player must understand and do on each screen, and how screens connect.

## Aspects (score each 1–10)
| key | aspect |
|-----|--------|
| `hierarchy` | Layout and visual hierarchy: the eye lands on what matters; spacing, alignment, grouping, balance |
| `aesthetic` | Aesthetic quality and cohesion with the game's look: does it feel like a premium, crafted product of this game's world |
| `art_assets` | Art and asset quality: sharpness, resolution, style consistency of images, frames, textures, icons, illustrations, empty states |
| `typography` | Typography and readability: type scale, weights, line length, legibility at size, font roles used well |
| `color_contrast` | Colour, contrast and theming: palette discipline, contrast, dark and light themes both good |
| `information` | Information design and clarity: right content, right density, clear labels and wording, no clutter or redundancy |
| `affordance` | Affordances and interaction clarity: what is tappable, the primary action is obvious, sensible touch targets and controls |
| `navigation` | Navigation, wayfinding and flow efficiency: where am I, how do I get back, few steps to common goals |
| `feedback` | Feedback and states: empty, error, progress, selection, success and turn states communicated well |
| `accessibility` | Accessibility and adaptivity: large text, landscape, narrow phones, never colour alone, nothing clipped |
| `table_fitness` | Fitness for use at a game table: glanceable, legible at arm's length and in dim light, fast, pass-and-play friendly |
| `consistency` | Consistency across screens and features: same patterns, components and language everywhere |

**Scale.** 10 = flawless, reference quality, nothing to improve. 9 = excellent, on par with the best
polished apps on the Play Store; only small nitpicks remain and **no medium- or high-severity issue
in that aspect**. 8 = very good, a few noticeable issues. 7 = good but clear issues. 5–6 = mediocre,
dated or inconsistent. ≤4 = poor. Do not inflate; do not give a 9 out of politeness. Do not compare
with an earlier version; judge only what you see.

## Output
Return **only** a JSON object, no prose around it:

```json
{
  "aspects": { "<key>": { "score": 8, "rationale": "one or two sentences" } },
  "features": { "catalog": 8, "board": 8, "game": 8, "puzzles": 8, "online": 8, "timer": 8,
                "draft": 8, "players": 8, "rules": 8, "sync": 8, "trivia": 8, "settings": 8, "shell": 8 },
  "issues": [
    { "screens": ["game-action"], "aspect": "hierarchy", "severity": "high|medium|low",
      "problem": "what is wrong, concretely, as a player would hit it",
      "fix": "a concrete design change (what to change, to what), implementable in Jetpack Compose" }
  ],
  "strengths": ["what must be kept"],
  "overall": 8.0
}
```

List up to 30 issues, most impactful first. Every aspect scored below 9 must have at least one issue
that names what would bring it to 9. Prefer issues that recur across screens. Where art is missing,
soft or off-style, say so: the team can use the game's official assets, redraw them, or generate new
painted art in the game's style.
