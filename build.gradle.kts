// Root build configuration for Nmap-for-android-REMAKE.
// Module structure: core (domain model, pure JVM) -> engine (scan mechanics, pure JVM) -> app (Compose UI).
// See docs/ARCHITECTURE.md for the module map and dependency rules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
