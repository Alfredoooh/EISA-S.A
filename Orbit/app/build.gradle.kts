// app/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orbit.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.orbit.app"
        minSdk = 26
        targetSdk = 37
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
    // Compose 1.12.0-beta01 family (pulled transitively via material3 1.5.0-alpha26)
    // now matches compileSdk 37 + AGP 9.4.0.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")

    // Retains Material 3 Expressive APIs (MaterialExpressiveTheme,
    // MotionScheme.expressive and ShortNavigationBar).
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}