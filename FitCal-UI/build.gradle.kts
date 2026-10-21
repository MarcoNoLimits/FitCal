import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sentryAndroid)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            
            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.material.icons.core)
            
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.supabase.compose.auth)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sentry.android.core)
            
            // CameraX
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // ML Kit barcode scanning (bundled model, on-device, free)
            implementation(libs.mlkit.barcode.scanning)
            
            // Google Mobile Ads (AdMob) + User Messaging Platform (UMP Consent)
            implementation(libs.play.services.ads)
            implementation("com.google.android.ump:user-messaging-platform:3.1.0")
            
            
            // Guava for CameraX ListenableFuture resolution with Play Services
            implementation("com.google.guava:guava:33.3.1-android")
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.fitcal.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    val properties = Properties()
    // 1. Try loading from .env
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use { properties.load(it) }
    }
    // 2. Fallback to local.properties if keys are missing
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val localProps = Properties()
        localPropertiesFile.inputStream().use { localProps.load(it) }
        localProps.forEach { key, value ->
            properties.putIfAbsent(key, value)
        }
    }

    defaultConfig {
        applicationId = "com.fitcal.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        
        val gatewayUrl = properties.getProperty("GATEWAY_URL") ?: ""
        val supabaseUrl = properties.getProperty("SUPABASE_URL") ?: ""
        val supabaseAnonKey = properties.getProperty("SUPABASE_ANON_KEY") ?: ""

        // Phase 10: Fail build if required configs are missing or contain placeholder values
        val forbiddenPlaceholders = listOf(
            "placeholder",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.anon",
            "your_"
        )

        val requiredConfigs = listOf(
            "GATEWAY_URL" to gatewayUrl,
            "SUPABASE_URL" to supabaseUrl,
            "SUPABASE_ANON_KEY" to supabaseAnonKey
        )

        for ((name, value) in requiredConfigs) {
            if (value.isBlank()) {
                throw GradleException("Build config '$name' is required but missing or blank. Provide it via local.properties or environment variable.")
            }
            if (forbiddenPlaceholders.any { value.contains(it, ignoreCase = true) }) {
                throw GradleException("Build config '$name' contains forbidden placeholder pattern: '$value'")
            }
        }

        // ZERO paid VLM keys in the APK — all inference routes through the Supabase Edge Function gateway
        buildConfigField("String", "GATEWAY_URL", "\"$gatewayUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Optional: Google sign-in is hidden until the OAuth web client ID is configured.
        val googleWebClientId = properties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        // Optional: crash reporting stays off until a Sentry DSN is configured.
        val sentryDsn = properties.getProperty("SENTRY_DSN") ?: ""
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    
    buildFeatures {
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = properties.getProperty("RELEASE_KEYSTORE_PATH")
            val keystoreFile = if (!keystorePath.isNullOrBlank()) file(keystorePath) else null
            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = properties.getProperty("RELEASE_KEYSTORE_PASSWORD") ?: ""
                keyAlias = properties.getProperty("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
            } else {
                // Pre-release/CI verification fallback
                initWith(getByName("debug"))
            }
        }
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Sentry: upload the R8 mapping on release builds so crash stack traces are readable.
// SENTRY_AUTH_TOKEN (org token, CI scope) comes from local.properties and is never committed.
// Without it the upload is skipped and the build still succeeds.
val sentryProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val sentryAuthToken: String? = sentryProps.getProperty("SENTRY_AUTH_TOKEN")?.takeIf { it.isNotBlank() }

sentry {
    org.set(sentryProps.getProperty("SENTRY_ORG") ?: "tecaa")
    projectName.set(sentryProps.getProperty("SENTRY_PROJECT") ?: "fitcal")
    sentryAuthToken?.let { authToken.set(it) }

    includeProguardMapping.set(sentryAuthToken != null)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    ignoredBuildTypes.set(setOf("debug"))

    // The SDK version is pinned in libs.versions.toml; don't let the plugin add or upgrade it.
    autoInstallation { enabled.set(false) }
    // No automatic performance instrumentation (privacy: crash/ANR reporting only).
    tracingInstrumentation { enabled.set(false) }
    includeSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
}
