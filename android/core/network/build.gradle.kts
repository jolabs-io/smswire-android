// Plain JVM: OkHttp needs no Android APIs, so this adapter is tested on the JVM (see :e2e).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(projects.gateway.engine)
    implementation(libs.okhttp)
}
