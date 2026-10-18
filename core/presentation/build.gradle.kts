plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Screen-state builders shared by the Android ViewModels and the hub (ADR-030 §1). Pure JVM.
dependencies {
    api(projects.core.domain)
}
