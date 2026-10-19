package dev.quinton.goa.hub.feature

/**
 * Every area of the web app, one line each. To add yours, replace its `ComingSoon` line with your
 * feature object (keep the comment lines between entries: they let parallel branches merge cleanly).
 * See server/hub/README.md.
 */
val HubFeatures: List<HubFeature> = listOf(
    // Heroes: catalog, hero page, cards, FAQ
    dev.quinton.goa.hub.heroes.HeroesFeature,
    // Card trivia
    dev.quinton.goa.hub.trivia.TriviaFeature,
    // Rules reference
    dev.quinton.goa.hub.rules.RulesFeature,
    // Timer
    dev.quinton.goa.hub.timer.TimerFeature,
    // Draft
    dev.quinton.goa.hub.draft.DraftFeature(),
    // Players: leaderboard, player page, match log, editor, meta
    ComingSoon.Players,
    // Group: who am I, invite, devices
    dev.quinton.goa.hub.group.GroupFeature,
    // Settings
    dev.quinton.goa.hub.settings.SettingsFeature,
    // Core pages: Get the app (APK)
    AppDownloadFeature,
)
