package dev.quinton.goa.hub.feature

/**
 * Every area of the web app, one line each. To add yours, replace its `ComingSoon` line with your
 * feature object (keep the comment lines between entries: they let parallel branches merge cleanly).
 * See server/hub/README.md.
 */
val HubFeatures: List<HubFeature> = listOf(
    // Heroes: catalog, hero page, cards, FAQ
    ComingSoon.Heroes,
    // Card trivia
    ComingSoon.Trivia,
    // Rules reference
    ComingSoon.Rules,
    // Timer
    dev.quinton.goa.hub.timer.TimerFeature,
    // Draft
    ComingSoon.Draft,
    // Players: leaderboard, player page, match log, editor, meta
    ComingSoon.Players,
    // Group: who am I, invite, devices
    ComingSoon.Group,
    // Settings
    ComingSoon.Settings,
    // Core pages: Get the app (APK)
    AppDownloadFeature,
)
