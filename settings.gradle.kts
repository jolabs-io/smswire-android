pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                // The QR code scanner (Play services) pulls ML Kit and Firebase transport artifacts.
                includeGroupByRegex("com\\.google\\.mlkit.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "relay"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Pure Kotlin modules: build anywhere a JDK is available.
include(
    ":shared:protocol",
    ":shared:sms-core",
    ":gateway:engine",
    ":android:core:network",
)

// Android modules need the Android SDK. Disable with -Prelay.android=false (e.g. server-only CI).
val includeAndroid = providers.gradleProperty("relay.android").map(String::toBoolean).getOrElse(true)
if (includeAndroid) {
    include(
        ":android:app",
        ":android:core:designsystem",
        ":android:core:data",
        ":android:core:telephony",
        ":android:feature:gateway",
        ":android:feature:messages",
        ":android:feature:bulk",
        ":android:feature:projects",
        ":android:feature:settings",
        ":android:feature:pairing",
    )
}
