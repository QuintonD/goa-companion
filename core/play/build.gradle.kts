plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// The board, play and puzzle screens' pure presentation logic (ADR-043 "Addendum (play, W4)"): what
// the table, the turn track, the sandbox and the puzzles show, worked out from the engine's views and
// the board rules. Shared by the Android feature:board (jvm) and the web app (js); the package stays
// dev.quinton.goa.feature.board so the move kept the phone's code unchanged. No android.*, no java.*.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.domain)
            api(projects.core.game)
            api(projects.core.dataContracts)
            api(libs.kotlinx.serialization.json)
        }
    }
}
