plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // Settings sections shared with the hub web app (ADR-030 §1).
    implementation(projects.core.presentation)
}
