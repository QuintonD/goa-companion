plugins {
    alias(libs.plugins.goa.kmp.library)
}

// What the Players and Group sync screens show, built from the data contracts (ADR-043 "Addendum
// (Group, W2)"): leaderboard, player page, match log rows, meta tables, the match form and its
// validation, the sync server card's words. Moved out of feature:players / feature:sync so the
// phone and the web app build the same rows with the same words. Packages are the features' own
// (dev.quinton.goa.feature.players.*, dev.quinton.goa.feature.sync.*), so Android imports didn't
// change. Dates are formatted by the platform ([dev.quinton.goa.feature.players.common.DateLabels]).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.dataContracts)
        }
        commonTest.dependencies {
            implementation(projects.core.dataTesting)
        }
    }
}
