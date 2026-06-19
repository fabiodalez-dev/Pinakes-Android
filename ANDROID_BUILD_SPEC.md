# Pinakes Android — autonomous build contract

> Build a modern, well-styled native **Android** app that connects to a Pinakes
> instance's Mobile API (`/api/v1`) and lets a library user search the catalog,
> view book details, manage loans/reservations, wishlist, profile, and see their
> notification feed. **Goal: a debug APK that installs and runs.**
>
> This is the authoritative spec. Decisions are locked — implement them.

## Environment (already verified on this machine)

- JDK **21** at `$JAVA_HOME` (`/opt/homebrew/opt/openjdk@21/...`). `java`/`javac` work.
- Android SDK at `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` — already
  has `platform-tools`, `build-tools`, `platforms/android-36`, and accepted `licenses`.
  Use `sdkmanager` to add anything missing; `yes | sdkmanager --licenses` to accept.
- **No system `gradle`** and no `kotlin` CLI — that's fine. Generate the Gradle
  **wrapper** and build with `./gradlew`. To create the wrapper without system gradle:
  `brew install gradle` (brew is available) then `gradle wrapper --gradle-version 8.10.2`,
  OR write the wrapper files directly. After that, ALWAYS use `./gradlew`.
- 75 GB free disk. `adb` is available for install/test.

## Locked tech choices

- **Kotlin + Jetpack Compose + Material 3** (Compose BOM). Single-module app.
- **compileSdk/targetSdk 35** (install `platforms;android-35` + `build-tools;35.0.0` if the
  build prefers them; android-36 is present but 35 is the safe stable target), **minSdk 26**.
- **AGP 8.7.x**, **Gradle 8.10.x**, **Kotlin 2.0.x**, Compose compiler via the Kotlin
  Compose plugin (Kotlin 2.0). JDK 21 toolchain.
- **Networking**: Retrofit + OkHttp + Moshi (or kotlinx.serialization) — pick one and be
  consistent. Bearer-token interceptor. Base URL is the user-entered instance URL + `/api/v1`.
- **Navigation**: Navigation-Compose. **State**: ViewModel + StateFlow. **DI**: Hilt OR
  manual (keep it simple; manual is fine to reduce build risk).
- **Image loading**: Coil (book covers — use the absolute cover URLs the API returns).
- **Secure token storage**: EncryptedSharedPreferences (androidx.security-crypto) or
  DataStore; store the instance URL + bearer token per the auth flow.

## Visual identity (the user emphasized style — get this right)

- **Primary = brand magenta `#D70161`** (the Pinakes logo colour), **Secondary = indigo
  `#6366F1`**, neutrals slate/gray. Material 3 dynamic-free, brand-coloured scheme.
- **Typography: Inter** (bundle the font, or use the closest system fallback if bundling
  is risky). Clean, generous spacing, rounded cards, soft shadows.
- **Light AND dark theme** (proper M3 color schemes for both).
- **Bottom navigation** (Search / My Library / Wishlist / Profile) + a top app bar.
- Book cards with cover thumbnails, availability chips, tasteful empty/loading/error states.
- Brand assets are in `_contract/brand/` (logo.png, logo_small.png, pinakes.png) — use the
  logo on the onboarding/splash and the app icon (adaptive icon, magenta background).
- Aim for **bello, moderno, in linea con Pinakes** — polished, not a wireframe.

## The API contract

- OpenAPI 3.1 snapshot: `_contract/openapi.json` (the source of truth for every endpoint,
  request/response shape, and the `{data, meta, error}` envelope). Generate models + the API
  interface from it.
- Endpoint manifest (one row per route, with the idempotency/ETag contract):
  `_contract/endpoint-manifest.spec.js`.
- Backend design doc: `_contract/MOBILE_API_SPEC.md`.
- `_contract/health-sample.json` shows the discovery payload shape.
- **Auth flow**: (1) user enters the instance URL → call `GET /api/v1/health` → show the
  library name/identity + check `app_access_enabled` and https; (2) `POST /auth/login`
  `{email,password,device_name,device_id,platform}` → store the returned bearer `token`;
  (3) send `Authorization: Bearer <token>` on every authed call; (4) `POST /auth/logout`
  revokes it. Envelope: success `{data,meta,error:null}`, error `{data:null,meta,error:{code,message}}`.
- All dates are ISO-8601 UTC; format locally. Catalog GETs support ETag/304 (honour it via
  OkHttp cache if easy, otherwise ignore — not required for v1).

## Screens (core scope — locked)

1. **Onboarding**: instance URL entry → `/health` discovery (show library name, logo, https
   warning) → continue.
2. **Login** (email + password). Show clear API error messages. Persist token + URL.
3. **Catalog search**: query + filters (text/author/publisher/genre/language/available),
   cursor pagination (infinite scroll), book cards with covers + availability chips.
4. **Book detail**: full payload (cover, metadata, availability, copies, shelf), personal
   history (read/reserved/wishlisted), and actions: **Reserve/Request loan**, **Wishlist toggle**.
5. **My Library**: my loans (active + history) and reservations; cancel a pending reservation.
6. **Wishlist**: list + remove.
7. **Profile**: view/edit profile, change password, **logout**, list devices.
8. **Notifications feed** (`GET /me/notifications`): loan due/overdue, reservation ready,
   book available — pull-to-refresh.
9. **Contact/message**: a simple "message the library" form (`POST /messages`).

**Push (UnifiedPush)**: only if the build is green and time remains — wire device
registration (`POST /me/push/subscribe`) + a UnifiedPush distributor receiver and the prefs
screen (`/me/push/prefs`). Otherwise stub the prefs screen and note it in STATUS.

## Build & deliver (the hard gate)

1. De-risk EARLY: scaffold a minimal Compose app and get `./gradlew assembleDebug` GREEN
   **before** piling on features. Prove the APK pipeline first.
2. Then implement features, rebuilding frequently.
3. Final: `./gradlew assembleDebug`; the APK lands at
   `app/build/outputs/apk/debug/app-debug.apk`. **Copy it to the repo root as
   `pinakes-debug.apk`** so it's trivial to find.
4. Run `./gradlew lint` and `./gradlew testDebugUnitTest` (if any tests) — best-effort.
5. Write **`STATUS.md`** at the repo root: what builds, what's implemented vs partial vs
   TODO, the exact APK path, how to install (`adb install -r pinakes-debug.apk`), and how to
   point it at a Pinakes instance (the local dev API is `http://<lan-ip>:8081`, app access
   must be enabled). Be honest — no spin.
6. Write a short **README.md** (what it is, how to build, the tech stack).

## Conventions

- This folder is OUTSIDE the Pinakes git repo — do NOT touch the Pinakes repo. `git init`
  here is fine but optional; do NOT push anywhere.
- Keep the build deterministic: pin all versions in `gradle/libs.versions.toml` (version
  catalog). No `+`/dynamic versions.
- Prefer fewer, well-chosen dependencies to keep the build converging.
- If the build loop can't fully converge, leave the project build-ready, the furthest-along
  APK if any, and a precise STATUS — never claim a green build that isn't.
