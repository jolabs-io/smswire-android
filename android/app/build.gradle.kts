import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sentry)
}

@Suppress("UNCHECKED_CAST")
val brand = JsonSlurper().parse(rootProject.file("packages/brand/brand.json")) as Map<String, Any?>

// Each build is numbered by its commit, so the dashboard ("app 1.0.83") shows which one a phone runs.
val buildNumber = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// Crash reports go to Sentry when a DSN is given (-Prelay.sentryDsn; CI: ANDROID_SENTRY_DSN), never otherwise.
val sentryDsn: String = providers.gradleProperty("relay.sentryDsn").getOrElse("")
// With a Sentry auth token (CI secret), the R8 mapping is uploaded so stack traces are readable.
val sentryAuthToken: String? = providers.environmentVariable("SENTRY_AUTH_TOKEN").orNull?.takeIf { it.isNotBlank() }

// Release signing comes from the environment (CI secrets, see deploy/README.md); without it
// assembleRelease still builds, unsigned.
val releaseKeystore: String? = providers.environmentVariable("SMSWIRE_KEYSTORE_FILE").orNull

android {
    namespace = "app.relay.android"

    lint {
        // An SMS gateway needs a SIM: no point offering it to ChromeOS devices without telephony.
        disable += "PermissionImpliesUnsupportedChromeOsHardware"
    }
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.smswire.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = buildNumber
        // A release names its version (-Prelay.versionName=1.2.0, from the android-v1.2.0 tag);
        // other builds are 1.0.<build number>.
        versionName = providers.gradleProperty("relay.versionName").getOrElse("1.0.$buildNumber")
        // The product name lives in packages/brand/brand.json, shared with the web apps.
        resValue("string", "app_name", brand["name"] as String)
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    // Two builds of the same app (same id and key, so either installs over the other):
    // - twoWay: sends and receives (replies to the inbox, STOP opt-outs, default SMS app).
    // - sendOnly: no RECEIVE_SMS and no receivers (they're in src/twoWay/AndroidManifest.xml only),
    //   so Play Protect's fraud protection doesn't block it when installed from a browser, which it
    //   does in some countries for any app that can read incoming SMS.
    // Never put the build in versionName: a "-" there marks a pre-release (appVersion.ts).
    flavorDimensions += "sms"
    productFlavors {
        create("twoWay") {
            dimension = "sms"
            isDefault = true
            buildConfigField("boolean", "RECEIVES_SMS", "true")
        }
        create("sendOnly") {
            dimension = "sms"
            buildConfigField("boolean", "RECEIVES_SMS", "false")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SMSWIRE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SMSWIRE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SMSWIRE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // By default the API on the computer, through `adb reverse tcp:8080 tcp:8080`;
            // -Prelay.serverUrl=https://… points a test build at a deployed server instead.
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"${providers.gradleProperty("relay.serverUrl").getOrElse("http://localhost:8080")}\"")
        }
        release {
            // ./gradlew :android:app:assembleRelease -Prelay.serverUrl=https://your-api.example
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"${providers.gradleProperty("relay.serverUrl").getOrElse("")}\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

sentry {
    // Only the SDK added below: no auto-installed integrations and no tracing instrumentation, so
    // nothing about messages, numbers or network calls is collected.
    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
    includeSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    includeProguardMapping.set(sentryAuthToken != null)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    authToken.set(sentryAuthToken ?: "")
    org.set(providers.environmentVariable("SENTRY_ORG").orElse(""))
    projectName.set(providers.environmentVariable("SENTRY_PROJECT").orElse(""))
}

dependencies {
    implementation(projects.gateway.engine)
    implementation(projects.android.core.designsystem)
    implementation(projects.android.core.data)
    implementation(projects.android.core.telephony)
    implementation(projects.android.core.network)
    implementation(projects.android.feature.gateway)
    implementation(projects.android.feature.messages)
    implementation(projects.android.feature.bulk)
    implementation(projects.android.feature.projects)
    implementation(projects.android.feature.settings)
    implementation(projects.android.feature.pairing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.fragment)
    implementation(libs.sentry.android.core)

    testImplementation(libs.junit)
}
