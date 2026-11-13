plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.kotlin.test)
    // Reads the cross-language fixtures in fixtures/ (also checked by packages/sms-core).
    testImplementation(libs.kotlinx.serialization.json)
}
