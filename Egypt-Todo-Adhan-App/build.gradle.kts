// Root project build file
// Destination: build.gradle.kts
// Plugins are declared here with `apply false` and applied in the modules that need them.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
