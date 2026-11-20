// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // Compose compiler plugin — required for Kotlin 2.0+ regardless of built-in Kotlin.
    // Version must match AGP 9.2.1's bundled Kotlin (2.2.10).
    alias(libs.plugins.compose.compiler) apply false
    // Hilt DI — declared here so subprojects can apply without a version.
    alias(libs.plugins.hilt.android) apply false
    // KSP — annotation processor used by Hilt (version matches Kotlin 2.2.10 built-in).
    alias(libs.plugins.ksp) apply false
}
