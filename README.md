# LibreHU Dialer

Automotive phone interface for LibreHU head units, inspired by the interaction patterns of Android Auto while using original LibreHU styling and assets.

## Status

Initial project scaffold. The first milestone is the dialer UX shell: Favorites, Recents, Contacts, keypad and an in-call screen. Telephony integration with the UJC201 Bluetooth hands-free stack must be validated separately; this project must not pretend that a demo call is a real call.

## Target hardware

- Autochips AC8257 / Jancar UJC201
- Android 9 (API 28), landscape head-unit display
- Kotlin, Jetpack Compose, Material 3

## Build

Requires JDK 17, Android SDK (API 37), and Gradle 9.7.1. Run `gradle assembleDebug` from the repository root until the Gradle wrapper is added.
