plugins {
    alias(libs.plugins.goa.jvm.library)
}

// The in-memory sync transport for tests of the Nearby protocol (core:domain, core:data,
// feature:sync). It was core:domain's `java-test-fixtures`, which a multiplatform module can't
// have (ADR-043); the sources stay in core/domain/src/testFixtures so history follows them.
sourceSets {
    named("main") { kotlin.srcDir("../domain/src/testFixtures/kotlin") }
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.core)
}
