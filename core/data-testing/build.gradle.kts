plugins {
    alias(libs.plugins.goa.kmp.library)
}

// In-memory fakes of the data contracts and a synthetic catalog (ADR-043 "Data contracts, F3"),
// for the phone's tests (through core:testing) and the web app's tests alike. Package
// dev.quinton.goa.core.testing, as when they lived in core:testing.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.dataContracts)
        }
    }
}
