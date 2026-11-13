import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// AGP and KSP must share a classloader with the Kotlin plugin, so they live on the root
// classpath — but only when Android modules are part of the build (see settings.gradle.kts).
buildscript {
    if (providers.gradleProperty("relay.android").map(String::toBoolean).getOrElse(true)) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradle.plugin)
            classpath(libs.ksp.gradle.plugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Shared JVM conventions: Java 17 bytecode (Android-compatible) built by whatever JDK runs Gradle.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
