# Tacklebox for Android

[![Android CI](https://github.com/Stumyatt76/tacklebox-android/actions/workflows/android.yml/badge.svg)](https://github.com/Stumyatt76/tacklebox-android/actions/workflows/android.yml)
[![Release](https://github.com/Stumyatt76/tacklebox-android/actions/workflows/release.yml/badge.svg)](https://github.com/Stumyatt76/tacklebox-android/actions/workflows/release.yml)

Tacklebox is a private, dark-only fishing journal for Android. It stores the angler's waters, sessions, catches, conditions, gear and presets locally with Room; there are no accounts, advertisements, analytics, subscriptions or in-app purchases.

## Requirements

- Android Studio Ladybug or newer
- JDK 21
- Android SDK 35
- Internet access for the first dependency download and optional live conditions

The project pins Android Gradle Plugin 8.7.3, Gradle 8.11.1, Kotlin 2.0.21 and the Compose 2024.12 BOM. Minimum Android version is API 26 and compile/target SDK is 35.

## Run

1. Open this repository root in Android Studio and allow Gradle sync to finish.
2. Select an API 35 emulator or an Android 8+ device.
3. Run the `app` configuration.

From a terminal with `ANDROID_HOME` configured:

```sh
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Module structure

The project deliberately uses one Android application module while keeping responsibilities separated by package:

```text
:app
├── MainActivity.kt           Compose shell, navigation and feature screens
├── MainViewModel.kt          MVVM state and coroutine actions
├── TackleboxApp.kt           Application-scoped repository
├── data/
│   ├── Models.kt             Room entities, relations, converters, DAO and database
│   └── Repository.kt         Offline-first operations and UK seed data
├── services/
│   └── LiveServices.kt       Open-Meteo, EA river API and local astronomy
└── ui/
    └── Theme.kt              Dark heritage Material 3 palette and typography
```

Navigation covers onboarding, Vault, catch logging/details, species records, Waters/passports, Sessions, Insights, Year on the Water, My Tacklebox, local solunar windows, tides/sea, river conditions and Settings. The persistent bottom bar uses Vault, Waters, Sessions and Insights around a centre catch-log FAB.

## Privacy and optional services

All journal features work offline. Open-Meteo marine/weather and Environment Agency gauge requests fail into retryable UI states. Solunar calculations are local. The species-identification affordance remains disabled until the user supplies a token. The Google Drive backup preference is off by default; a production distributor must configure its own OAuth client before connecting the app-data implementation. User location is requested only for live conditions and is not persisted.

Photos are selected through Android's document picker and stored as local content references. Missing images use the app's fish-glyph treatment.

## License

Proprietary — Copyright (c) 2026 Stuart Myatt. All rights reserved. The source is public for reference only and is not open-source; see [LICENSE](LICENSE). No use, building, or redistribution is permitted without written permission.

## Development

Enable the shared git hooks once per clone so commits are checked for the copyright header locally (mirrors CI):

```bash
git config core.hooksPath .githooks
```
