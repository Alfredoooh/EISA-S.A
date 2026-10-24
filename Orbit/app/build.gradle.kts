import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orbit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orbit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose 1.11.4 is compatible with compileSdk 36 and AGP 8.13.2.
    // Do not use the 2026.09 BOM here: it selects Compose 1.12.1,
    // which requires compileSdk 37 and AGP 9.1.0 or later.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")

    // Retains Material 3 Expressive APIs (MaterialExpressiveTheme,
    // MotionScheme.expressive and ShortNavigationBar) without moving
    // this project to the Compose 1.12 / API 37 toolchain.
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}
