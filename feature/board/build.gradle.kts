plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // BackHandler: Back leaves a tool (Add, Measure) before it leaves the board.
    implementation(libs.androidx.activity.compose)
    // Play against the computer (ADR-035): the engine's public contract.
    implementation(projects.core.game)
    // What the screens show, worked out in shared code (the web app uses the same, ADR-043).
    implementation(projects.core.play)
    // Online rooms (ADR-056): the invite as a QR drawn with ZXing, read with Google's code scanner, which
    // needs no CAMERA permission (as group invites, feature/sync).
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.zxing.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// The UI states moved to core:play (shared with the web app) lost their @Immutable: Compose is told
// here that they are immutable, as before (compose-stability.conf).
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}
