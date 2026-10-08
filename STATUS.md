# Pinakes Android — Build Status

## 2026 restyle (2026-10-08)

The app now uses the 2026 web design: Geist / Fraunces, warm neutrals, a server-ready `ThemePalette`, complete 3D book covers, a searchable home hero with a cover fan, catalog grid/list views, dot status pills, grouped availability/actions and one card per exposed digital asset. Shared tokens apply to all existing account and plugin screens. The five-section bottom navigation is retained. Article lists and details now show server-resolved covers, subtitles, published online resources and access conditions, with a website action for the existing staff management flows. The Mobile API and all circulation/authentication contracts are unchanged.

- Debug APK: `pinakes-debug.apk`, copied from `app/build/outputs/apk/debug/app-debug.apk` (generated locally and ignored by Git).
- Package: `com.pinakes.app`, version `1.5.2` (16), minSdk 26, target/compileSdk 35.
- Local verification: **171 unit tests, 15 Compose device tests, zero lint errors**, and successful debug + R8 release builds.
- Verification commands: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease`, `assembleDebugAndroidTest`; the 15 device tests run through `am instrument` on a separate Android 15 AVD, preserving the authenticated demo device.
- Unit tests cover the existing contracts and theme contrast/mixing. Compose device tests cover full tall artwork, missing metadata, grid actions, view selection, digital-file cards, narrow search placeholders, circulation actions theme pairings, reachable empty-home actions and query preservation during loading.
- Release builds exercise R8 and resource shrinking. Without release credentials the output is unsigned; no store release is published by this change.
- macOS standalone emulator startup: `tools/run-emulator.sh` keeps a power assertion scoped to the emulator PID. A control with the identical debug APK reproduced a 21-second startup timeout at host priority 4; three starts with the assertion completed in 2.3–3.1 seconds. After a VM restart through the launcher, five more cold starts completed in 1.66–1.80 seconds with no ANR events. The original startup ANR also coincided with system/launcher stalls. No Sentry events are suppressed, and no production-device ANR fix is claimed.
- `ThemePalette` defaults to Classic / Covers. Discovery does not expose theme, CMS home sections, richer catalog facets, wanted flags or related/citation/share data yet; these are documented as future API work in DESIGN.md.

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
- **Design system:** Material 3 light **and** dark, 2026 theme-derived colours (Classic magenta by default),
  bundled Geist / Fraunces, warm neutrals, complete book covers, rounded controls and subtle hero washes.
  Calendar and status colours follow the app theme. The adaptive launcher icon is preserved.
- **Architecture:** Navigation-Compose + ViewModel/StateFlow + Hilt, Retrofit + OkHttp + kotlinx.serialization, Coil for covers.
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

- **Data layer** — `BookClubApi` (Retrofit) + `BookClubRepository`, reusing the same base URL
  and bearer token as the core client; the availability flag lives in `FeatureStore`
  (`InstanceFeatures.bookClubAvailable`) next to the other instance gates. The plugin uses a
  **different envelope** (`{success, data, error}` vs the core `{data, meta, error}`), handled
  by a dedicated `bookClubCall` that reuses the core error pipeline (`parseErrorBody`,
  status fallbacks, `Retry-After`) and the shared `ApiResult`/`ErrorCodes`.
- **Screens** — a **Book Club home** (your reading dashboard, your clubs, discover directory,
  reached from Profile) and a **club detail** (reading list with state chips + progress,
  polls, meetings). Actions wired end-to-end: **join**, **vote** (simple / multi / weighted,
  with the ballot pre-seeded from `my_option_ids`), **RSVP** (yes / maybe / no), and
  **reading progress**. Guests are read-only. Advanced poll modes and proposing a title
  deep-link to the web page (per the API contract).
- **i18n** — all new strings added to the 4 locale JSONs (it/en/fr/de), in parity.
- **Build** — `testDebugUnitTest`, `assembleDebug`, `lintDebug` and `assembleRelease`
  (R8/minify — exercises the keep rules for the new `@Serializable` models) all
  **BUILD SUCCESSFUL**.

### PHP-compatibility review (verified against the live plugin sources)

A full adversarial review against the Book Club plugin + mobile-api PHP sources confirmed
the wire contract (paths, regexes, casts, nullability, envelopes, error codes) and fixed
every confirmed finding:

- **Rejoin parity**: `canJoin` now mirrors the server — members with status `left`/`suspended`
  can re-join (the web shows the join form for them; only `banned` is blocked).
- **Expired polls**: the mobile API never lazy-closes polls (only the web page/cron do), so a
  past `closes_at` now renders as *Closed* instead of a ballot that can only 409 `poll_closed`.
- **Full meetings**: the *Going* chip disables when `yes_count >= seats` (unless already
  going), mirroring the server's 409 `no_seats` rule; *maybe*/*no* stay enabled.
- **MySQL DATETIME timestamps**: the server emits raw `Y-m-d H:i:s` for reviews, devices and
  book club dates — `DateFormat` now parses wall-clock values, fixing raw strings that
  previously rendered verbatim on review cards and the device list.
- **Home "Available now" shelf**: restored the server-side `available=true` query (the cached
  first page is only the offline fallback), so availability beyond the newest 40 titles shows
  again.
- **Instance switch hygiene**: `forgetInstance()` now purges the Room catalog cache + ETag
  cache (no cross-library leak), and a late availability probe can no longer resurrect the
  Book Club flag after switching (probe results are guarded by instance URL).
- **App-access gate**: the Book Club section also hides when core `/health` reports
  `app_access_enabled=false` (the plugin's public health answers 200 regardless).
- **Startup/login latency**: the core health call and the plugin probe now run concurrently.
- **Error codes**: `ErrorCodes` now matches the server's real `app_access_disabled` (the old
  `app_disabled` constant matched nothing the server emits).
- **Partial failures**: a dashboard fetch failure on the Book Club home now surfaces a
  snackbar instead of silently dropping the "Your reading" section.

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
