# Pinakes Android — Build Status

**Build: GREEN.** `./gradlew assembleDebug` succeeds; `./gradlew lintDebug` passes (0 errors). Kotlin compiles clean.

- **APK:** `pinakes-debug.apk` (repo root, ~20 MB) — copied from `app/build/outputs/apk/debug/app-debug.apk`.
  Install: `adb install -r pinakes-debug.apk`.
- **Package:** `com.pinakes.app` · versionName `1.0` · minSdk 26 · target/compileSdk 35 · launchable `MainActivity`.
- **Verified on an emulator against a live Pinakes instance** (Android 15 / API 35 AVD → `http://10.0.2.2:8081`):
  onboarding → `/health` discovery → login (`200`) → catalog search with real books → book cards with
  correct titles/authors/availability. Two real bugs were found and fixed during this smoke test — see
  **Fixes applied** below. The app is **fully localized in 4 languages** (German verified live) — see **i18n**.

## Install & point at an instance

```bash
adb install -r pinakes-debug.apk
```

On first launch the app shows **Onboarding**: enter your Pinakes instance URL.
- The app calls `GET <url>/api/v1/health`, shows the library name/logo and checks
  `app_access_enabled` + transport. **HTTPS is required** except for localhost / `127.0.0.1` /
  `10.0.2.2` (emulator → host) / `[::1]`.
- Local dev API: `http://<lan-ip>:8081` (Apache on :8081). From the Android **emulator** use
  `http://10.0.2.2:8081`. The Pinakes instance must have **mobile app access enabled** or login is refused.
- Then **Login** (email/password) → bearer token is stored in EncryptedSharedPreferences and sent
  as `Authorization: Bearer` on every authed call.

## Implemented (core scope — all 9 screens)

| Screen | State | Notes |
|---|---|---|
| 1. Onboarding | ✅ | URL → `/health` discovery card (name, logo, https + app-access status), continue gated on app-access + secure transport |
| 2. Login | ✅ | Email/password, mapped API error messages (invalid creds, app disabled, rate-limited w/ Retry-After, network), "use a different library" |
| 3. Catalog Search | ✅ | Debounced query, **filter bottom sheet** (available / genre / author / publisher / language) w/ active-count badge, cursor **infinite scroll**, BookCards w/ covers + availability chips, loading skeletons / empty / error |
| 4. Book Detail | ✅ | Full payload (cover, metadata, copies, shelf, ISBNs), personal-history chips, **Reserve/Request loan**, **Wishlist toggle**, ETag/304 reuse via repo |
| 5. My Library | ✅ | Tabs: Active (active+pending) / History / Reservations; **cancel pending reservation** w/ confirm; pull-to-refresh |
| 6. Wishlist | ✅ | List + remove (optimistic), pull-to-refresh, empty state |
| 7. Profile | ✅ | View, **edit** (nome/cognome), **change password**, **in-app language switcher** (System / it / en / fr / de), **devices list** w/ per-device sign-out, **logout** |
| 8. Notifications | ✅ | Feed w/ per-type icons, read/unread styling, **pull-to-refresh** |
| 9. Contact | ✅ | `POST /messages` subject+body form, success state |

- **Bottom nav:** Search / Library / Wishlist / Profile. **Nested routes:** Book Detail, Notifications, Contact.
- **Design system:** Material 3 light **and** dark, brand magenta `#D70161` + indigo `#6366F1`,
  Inter (bundled), rounded cards, soft shadows, brand-gradient header on onboarding/login,
  navigation transitions + list/press animations, adaptive launcher icon.
- **Architecture:** Navigation-Compose + ViewModel/StateFlow, manual DI (`ServiceLocator` via a
  `LocalServices` composition local), Retrofit + OkHttp + kotlinx.serialization, Coil for covers.
  All loading/empty/error states handled per screen.

## Internationalization (i18n)

The app is **fully localized in 4 languages — Italian, English, French, German** — matching the
Pinakes backend locales. It follows the **device locale** by default and offers an **in-app language
switcher** in Profile (System default / Italiano / English / Français / Deutsch) via
`AppCompatDelegate.setApplicationLocales(...)`, persisted across restarts (`autoStoreLocales`).

- **Single source of truth = JSON.** Translations live in `i18n/{en,it,fr,de}.json` (209 keys each,
  en = default/source). A Gradle task (`GenerateI18nResTask`) generates `res/values*/strings.xml` from
  those JSONs at build time, so the app uses standard Android string resources but the editable source
  stays JSON — syncable with the web app's `locale/*.json`.
- All user-facing strings use `stringResource(...)`; no hardcoded UI text remains. Server-provided
  messages (API errors) still pass through verbatim.
- **Verified live:** switching to *Deutsch* in-app re-localized the entire UI instantly (Profil,
  bottom nav Suchen/Bibliothek/Merkliste/Profil, all rows), and the choice survived an app restart.

## Fixes applied (from the emulator smoke test)

The app built green from day one, but running it on a real emulator against a live instance surfaced
two genuine bugs that a build-only check could not have caught:

1. **Cleartext HTTP was blocked** (Android 9+ default). The app could not reach *any* `http://`
   instance — including a loopback/dev server. Fixed by adding
   `res/xml/network_security_config.xml` whitelisting cleartext for `localhost` / `127.0.0.1` /
   `10.0.2.2` (mirrors the app's own "HTTPS-except-loopback" onboarding rule) + referencing it in the
   manifest. Every other host still requires HTTPS.
2. **Blank book titles + wrong availability.** The catalog Kotlin models used Italian field names
   while the API serializes **English** snake_case keys (`title`, `author`, `cover_url`,
   `copies_available`, `loanable_now`, …). kotlinx.serialization left every field empty, so cards
   showed no title and always read "On loan". Fixed by aligning `BookSummary` / `BookDetail` (and the
   loan / reservation / wishlist / notification item models) to the real API keys, and fixing the
   availability logic (`loanable_now || copies_available > 0`). Verified: titles, authors and the
   green *Available* / red *On loan* chips now render correctly.

## Book Club plugin integration

The optional server-side **Book Club** plugin is now surfaced in the app. It is
**auto-discovered and gated**: after login the app probes `GET /api/v1/bookclub/health`
(public, no token) and only shows the section when the plugin + its `mobile` module are
active for the instance (a 404 hides it). The flag is cached in an encrypted store and
refreshed alongside `/health`, so the entry never flickers and a first-run/offline probe
keeps it hidden — same "confirm before showing" rule as public registration.

- **Data layer** — `BookClubApi` (Retrofit) + `BookClubRepository` + `BookClubStore`, all
  reusing the same base URL and bearer token as the core client. The plugin uses a
  **different envelope** (`{success, data, error}` vs the core `{data, meta, error}`), handled
  by a dedicated `bookClubCall` that maps into the shared `ApiResult`/`ErrorCodes`.
- **Screens** — a **Book Club home** (your reading dashboard, your clubs, discover directory,
  reached from Profile) and a **club detail** (reading list with state chips + progress,
  polls, meetings). Actions wired end-to-end: **join**, **vote** (simple / multi / weighted,
  with the ballot pre-seeded from `my_option_ids`), **RSVP** (yes / maybe / no), and
  **reading progress**. Guests are read-only. Advanced poll modes and proposing a title
  deep-link to the web page (per the API contract).
- **i18n** — all new strings added to the 4 locale JSONs (it/en/fr/de), in parity.
- **Build** — `assembleDebug`, `lintDebug` and `assembleRelease` (R8/minify — exercises the
  keep rules for the new `@Serializable` models) all **BUILD SUCCESSFUL**.

## Partial / TODO

- **Push (UnifiedPush): STUBBED — not wired.** The data layer is ready (`/me/push/subscribe`,
  `/me/push/prefs` in `PinakesApi` + `NotificationsRepository`), and `/health` exposes
  `vapid_public_key`. A UnifiedPush distributor receiver + push-prefs screen were **deliberately
  not added** to keep the build minimal and green per the spec's "stub if time-limited" clause.
  Next step: add the `org.unifiedpush.android:connector` dependency, a `MessagingReceiver`, call
  `subscribePush()` on registration, and build a prefs screen on top of `pushPrefs()`/`updatePushPrefs()`.
- **Register / forgot-password:** API + repository methods exist (`AuthRepository.register/forgotPassword`)
  but no dedicated screens — login is the only auth entry point in this build.

## Verification done

- `./gradlew assembleDebug` → BUILD SUCCESSFUL; `./gradlew lintDebug` → 0 errors.
- APK badging verified (`aapt dump badging`): correct package, label, launchable activity.
- **Manual smoke test on an Android 15 / API 35 emulator against a live instance** (`http://10.0.2.2:8081`):
  onboarding → `/health` discovery card → admin login (`POST /auth/login` → `200`) →
  catalog `GET /catalog/search` → book cards with real titles/authors + correct availability chips →
  Profile loaded. Two bugs found and fixed (see **Fixes applied**); re-verified green afterwards.
- **i18n verified live:** in-app switch to *Deutsch* re-localized the whole UI immediately and persisted
  across an app restart.
- Not run: automated instrumented tests / `testDebugUnitTest` (no unit tests authored for this build).
