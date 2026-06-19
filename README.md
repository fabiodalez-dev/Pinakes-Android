# Pinakes Android

A modern native **Android** client for a [Pinakes](https://github.com/) library instance's
Mobile API (`/api/v1`). Search the catalog, view book details, manage loans & reservations,
keep a wishlist, edit your profile, read your notification feed and message the library.

## Tech stack

- **Kotlin + Jetpack Compose + Material 3** (light & dark), single-module app.
- **Navigation-Compose** for routing, **ViewModel + StateFlow** for state.
- **Manual DI** — a `ServiceLocator` exposed through a `LocalServices` composition local (no DI framework).
- **Retrofit + OkHttp + kotlinx.serialization** for the `{data, meta, error}` API envelope, with a
  bearer-token interceptor and per-instance base URL.
- **Coil** for cover images, **EncryptedSharedPreferences** for the token + instance URL.
- Brand: magenta `#D70161` / indigo `#6366F1`, **Inter** typography (bundled).

Versions are pinned in `gradle/libs.versions.toml` (AGP 8.7.x, Gradle 8.10.x, Kotlin 2.0.x,
Compose BOM 2024.10.01, compileSdk/targetSdk 35, minSdk 26, JDK 21 toolchain).

## Build

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk (also copied to ./pinakes-debug.apk)
./gradlew lintDebug          # static analysis
```

Always build with the Gradle **wrapper** (`./gradlew`).

## Install & connect

```bash
adb install -r pinakes-debug.apk
```

Launch → enter your instance URL on the onboarding screen → sign in. The library must have
**mobile app access enabled**. For local dev the API is on `http://<lan-ip>:8081` (emulator:
`http://10.0.2.2:8081`). HTTPS is required for non-loopback hosts.

## Project layout

```
app/src/main/java/com/pinakes/app/
├── data/            # models, network (Retrofit API + envelope handling), repositories, secure session store
├── di/              # ServiceLocator (manual DI)
└── ui/
    ├── theme/       # Material 3 colour schemes, Inter typography, shapes, spacing
    ├── components/  # design system: BookCard, AvailabilityChip, buttons, text fields, states, rows…
    ├── common/      # UiState, date formatting, status mapping, LocalServices
    ├── navigation/  # NavHost, bottom-nav scaffold, route keys
    └── screens/     # onboarding, login, search, detail, library, wishlist, profile, notifications, contact
```

See `STATUS.md` for what's implemented vs. partial/TODO (notably: UnifiedPush is stubbed).
