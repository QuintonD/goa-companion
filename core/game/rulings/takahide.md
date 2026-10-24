# Rulings: Takahide the Warlord

Readings of the engine for Takahide's cards (format and sources as [RULINGS.md](../RULINGS.md); card
text beats the rulebook, p.3). Takahide has no FAQ entries (goa2-faq `cards/takahide.json`, hero JSON
`faq: []`). Code: `heroes/TakahideHero.kt`; tests: `heroes/TakahideTest.kt`.

## The three gold cards

1. **Setup.** Float like a Butterfly starts in hand; Sting like a Bee and Strike like a Tiger start in
   the deck (their printed reminders, dealt by `StartingCards`); the silver Handicap Shameful Display
   replaces Bushido (p.20: the basic card of its colour). Test `Setup - …`.
2. **"Swap this card with a different gold card in your deck."** p.10 "Swap a Card": all card states
   are swapped, so the deck gold takes this card's place (played and then resolved on the Turn slot,
   faceup once the cards are revealed) and this card goes to the deck. Takahide chooses which of the two
   deck golds comes in. The log names both (the incoming card is now open information).
3. **Float like a Butterfly: the swap comes before the movement.** p.11 "Unless it says otherwise, the
   card text comes into effect before movement." The movement is still this card's action (Movement 5
   plus items) although the card is now in the deck. Test `Float like a Butterfly - …`. Disagreement:
   goa2-backend swaps after the movement (no difference on the board).
4. **After Ready for War no gold is left in the deck.** The swap is a mandatory step that cannot be done
   (p.10): Sting/Strike's "After the attack" swap stops harmlessly; Float's first instruction cannot be
   done, so its Primary action is not offered (FAQ `faq-common-fizzle`) — Fast Travel or its Defense
   remain. goa2-backend agrees ("these swap texts permanently fizzle"). Test `Float like a Butterfly -
   with no gold left in the deck…`.
5. **Sting like a Bee** *"Target a unit at maximum range"*: exactly at Range (3 + Range items), as the
   designer reads "maximum range" for Gydion's Sunburst (BGG 3589662). Test `Sting like a Bee - …`.
6. **"After the attack"** follows a defense or a defeat of the target, not an attack that failed to
   connect (RULINGS 18). Test `Strike like a Tiger - …`.

## Bushido and Shameful Display

7. **Bushido** *"Swap your gold card with a different gold card in your deck; if you swap a resolved or
   discarded card this way, place the new card facedown."* "Your gold card" is the gold card out of the
   deck (hand, resolved or discarded). A facedown card "loses its type, color and actions" (p.10), so a
   gold placed facedown by an earlier Bushido is not a gold card until it is back in hand: with no other
   gold out of the deck, Bushido's Primary action cannot start. Tests `Bushido - …` (three).
8. **Bushido and hidden cards.** Swapping the gold in hand with a deck gold names neither card in the log
   (hand and deck are hidden, p.4); a new card placed facedown is not named either and other seats see a
   facedown card (`Views`). The determinizer treats the facedown gold as hidden, like the deck.
9. **Shameful Display** is a Block with no condition: the defender is always defeated (RULINGS 20). Test
   `Shameful Display - …`.

## Ready for War (Ultimate)

10. *"Return your silver card to your deck and take two gold cards from your deck into your hand."* Done
    once, when it is unlocked at level 8 (`UltimateUnlock`, during the Level Up step after Retrieve, so
    every card is in hand): the silver card goes to the deck and both deck golds to hand (6 cards). [APP]
    With the Handicap, "your silver card" is Shameful Display (the silver card that replaced Bushido;
    Bushido stays in the box). Test `Ready for War - …`.

## Red: Set an Example family, Spinning Blade, Blade Helix

11. **"A friendly hero in radius may swap their unresolved card with a card in their hand (, or in their
    discard)."** Takahide chooses the friendly hero (never himself, p.17), or nobody; that hero chooses the
    card or not to swap (p.10 "Making Choices": the card text's "may" is theirs). Only friendly heroes
    with an unresolved card are offered. The new card is their unresolved card, faceup (cards were revealed),
    and acts at its own Initiative; the old one goes to hand (Hold my Saké: or to the discard). Tests
    `Set an Example - …`, `Hold my Sake - …`.
12. **[APP] Facedown flags in that swap.** A card coming into play or into hand is faceup; the old
    unresolved card goes faceup into the discard (facedown is a property of resolved and discarded cards,
    p.10, not one of the swapped card states). Hold my Saké may take any card of the discard, faceup or
    facedown.
13. **[APP] A swapped-in attack card counts as "played an attack card"** (Sabina, Brogan): it is now a
    played card (p.10 "all their card states are swapped"); same as RULINGS 36 for a card entering play
    after the reveal.
14. **Spinning Blade / Blade Helix** *"This turn: Empty spaces adjacent to you (in radius) count as
    obstacles for enemy units."* Empty = a space of the map with nothing on it; measured from Takahide's
    current space; for every move, push or placement of an enemy unit (hero or minion) during this turn,
    whoever performs it; not for tokens; immune units are not affected (p.18 "and their active effects").
    Blade Helix's radius is 1 plus Radius items. Tests `Spinning Blade - …`, `Blade Helix - …`.

## Green: Come to Aid family

15. **"A friendly hero in range may discard a card."** Takahide must choose a friendly hero in range when
    there is one (the step has no "may" for him; with none the Primary action cannot start); that hero
    may discard (Calculated Risk: an attack card, a card whose primary action is Attack, p.9).
16. **"If that hero has a card in the discard"** counts the cards already there, facedown ones too (a
    presence check), so the reward follows even when the friend declines. goa2-backend agrees. Tests
    `Come to Aid - …`.
17. **Rewards.** "you may move up to N spaces (ignoring obstacles)" is movement in the card text (p.11),
    Takahide's choice. Pledge of Allegiance / Loyal Retainer: both gain the coins; "you may retrieve a
    discarded card" is Takahide's own discard (p.10 "You can never retrieve another hero's cards").
    Calculated Risk / Tactical Gambit: "that hero may move" — that hero chooses the move.

## Blue: Proven Warrior family

18. **"Choose a card in the discard of a friendly hero in radius."** A faceup card (a facedown card has no
    colour, p.10), never Takahide's own; with none the action cannot start. Its printed colour (gold and
    silver are colours) is "the same color".
19. **"An enemy hero in radius discards a card of the same color, if able."** Takahide chooses the enemy
    hero (mandatory when one is in radius; immune heroes cannot be chosen); that hero discards a card of
    that colour if they have one. The Right Hand: up to two different enemy heroes, each discards if able (heroes, not figures:
    two of Razzle's figures are one hero, so she is never picked twice).
    Tests `Proven Warrior - …`, `Chosen Champion - …`, `The Right Hand - …`.

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`docs/superpowers/plans/2026-07-11-takahide-*.md`) | Engine |
|-------|------------------------------------------------------------------|--------|
| Float like a Butterfly's swap | After the movement | Before the movement (p.11) |
| Swap-unresolved family, Takahide's pick | Optional | Optional (same) |
| Facedown discards in the swap / Proven Warrior | Masked; excluded from colour choice | Faceup cards only for the colour choice (same); Hold my Saké may take them |
| Ready for War with the Handicap | Not covered | Shameful Display returns to the deck (ruling 10) |
