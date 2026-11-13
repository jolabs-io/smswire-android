package app.relay.android.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The product's name, from packages/brand/brand.json via the app's `app_name` resource.
 * Feature screens read it here so a rename touches one file.
 */
val LocalAppName = staticCompositionLocalOf { "" }
