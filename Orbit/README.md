# Orbit — Android

A clean, native Android starter app named **Orbit**. It uses Jetpack Compose and the Material 3 Expressive APIs, with a single expressive bottom navigation bar and a blue Material color palette. It is independent of any previous project.

## Main features

- One `ShortNavigationBar` using Material 3 Expressive components.
- Four working destinations: Início, Explorar, Guardados and Perfil.
- Expressive spring-based Material motion via `MaterialExpressiveTheme`.
- Explicit blue light/dark themes; wallpaper-based dynamic colors are disabled so the brand stays blue.
- Native Android status/navigation system bars and an app launcher vector.
- No WebView, no custom navigation framework, and no third-party icon pack.

## Build

Open the `Orbit` folder in Android Studio or run:

```bash
./gradlew assembleDebug
```

The first build downloads the pinned Gradle distribution and Android/Compose dependencies. A compatible Android SDK (API 36) and JDK 17 or newer are required. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The project pins Gradle 8.14.6 with its published SHA-256 checksum, Android Gradle Plugin 8.13.2, Kotlin 2.4.21, Compose BOM 2026.09.00 and Material 3 1.5.0-beta02, the current Material 3 Expressive line as of October 2026.
