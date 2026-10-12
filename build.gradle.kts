buildscript {
    dependencies {
        // AGP 8.9.1's bundled R8 can't read Kotlin 2.3 metadata ("error parsing kotlin metadata").
        // Pin a newer R8 until AGP is upgraded; remove this when AGP ships a matching R8.
        classpath("com.android.tools:r8:9.4.28")
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}
