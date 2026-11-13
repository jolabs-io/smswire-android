plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(projects.shared.protocol)
    api(projects.shared.smsCore)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
