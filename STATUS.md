# Pinakes Android — Build Status

## 1.8.0 (versionCode 21): circulation desk, book tools and plugin content for staff

Requires Mobile API 1.7.0. Every area is gated on its own `/health` flag (`staff_circulation`, `staff_copies`, `staff_series`, `staff_merges`, `staff_labels`, `staff_imports`, `staff_covers`, `staff_desiderata`, `staff_archives`) plus the staff role and `staff_catalog`; a server that answers `false` or omits a flag keeps the 1.7.0 staff area for that part. The 403 `forbidden` contract is unchanged: every new call goes through the shared `staffCall`, which drops the stored role.

- **Circulation desk** (hidden, never disabled, while the device is offline; an open desk screen closes when the connection drops): counters of the day, loans by bucket (requests, to pick up, scheduled, on loan, overdue, history) with search and multi-select bulk extend/recall, loan detail with the server's actions (approve, reject with reason, confirm/cancel pickup, return with the copy's outcome and a validated penalty, renew, edit user and dates, reminder, receipt PDF read in the app or shared through the FileProvider, receipt by email), new loan and new reservation (borrower search with eligibility, book search or a scanned copy code, per-user availability calendar, hand over now), reservations grouped by title (edit dates, cancel with reason, reactivate), borrower page. Creates carry an `Idempotency-Key` (one per submission, kept across the automatic retry after a dropped connection or a 409 `concurrent_retry`, discarded after success or `idempotency_conflict`).
- **Copy scanner**: the same CameraX + ZXing pipeline reading CODE 128 and CODE 39 inventory labels (and EAN-13); manual code entry always visible; the sheet shows the copy and its open loan, then returns it with an outcome, lends it, or opens the book or the loan.
- **Book tools**: per-copy list in the book editor (state, notes, delete with the server's refusal reasons, a copy with its own inventory code), book and copy label PDFs and the label settings sheet; series tree with search, details, rename (an existing name asks before merging), merge, delete, parent work, book numbers, removal and multi-select assignment; duplicate merges (suggested author groups or manual pick, publishers, genres); CSV and LibraryThing imports picked with the system document picker and run chunk by chunk from the server's progress (out-of-order recovery, busy retry, resumable after process death, pause/resume/cancel, error lines, past imports with their error CSV); missing covers fetched one book at a time with progress and the rate limit honoured; server-side cover sync and enrichment batches on the long-timeout client behind a confirmation; automatic enrichment switch.
- **Plugin content**: Desiderata offers by status (accept, reject, receive into the wanted title or a picked record, delete) and wanted titles marked received; Archives units (published or not) created and edited with the plugin's rules checked client-side, publish switch, authorities with roles, cover and document uploads, delete; authority records list and editor.
- Local verification: 369 debug unit tests (new: tolerant 1.7.0 parsing, loan actions, penalty validation, copy codes and export file names, staff areas × flags × connectivity, idempotency keys, series tree and series/label forms, import chunk rules, archive/authority forms and offer actions), zero lint errors (111 warnings, all pre-existing), debug and R8 release builds.
- Device tests: 80 instrumented tests on Android 15 (`staffe2e` with `FakePinakes`). New flows: return as damaged with penalty, invalid penalty not sent, borrower page, 409 message, new loan with user search and Idempotency-Key, ineligible borrower, return by typed copy code, unknown code, reservation cancel and create, circulation hidden offline and restored, desk screen closing offline, no section without flags; copy edit, refused delete, copy with code, taken code; series number and rename clash merge; suggested author merge; import resume with out-of-order recovery and an upload through the real system document picker; covers loop and enrichment switch; desiderata receive (linked and into a picked record) and closed offer; archive publish keeping every field and create after validation; a retried merge already done; digital upload through the system picker and its absence without the flag.
- Live smoke (`tools/e2e/live_smoke.py`) gains: loan created by API with an Idempotency-Key and its replay then returned in the app by typing the copy code, a copy with an explicit code from the book editor, a series created in the app, a two-row CSV import followed to completion, a free Desiderata offer received into a picked record, an unpublished archive unit absent from the public list. Each phase 2 step is skipped while its flag is off; cleanup returns loans the run left open and deletes only the books, series, units and offers the run created. Run on 2026-10-14 against the local server (Mobile API 1.7.0 branch) on an Android 15 emulator: 16 passed, 0 skipped, 0 failed.
- Tier 2: eBook and audiobook upload from the book editor (`staff_digital_library`, streamed, long-timeout client). The periodicals desk, book clubs and FRBR works are not in this build: their contract is frozen only at endpoint level and the server does not offer them yet; their flags are parsed and every entry stays hidden.

## 1.7.0 (versionCode 20): staff catalogue, offline catalogue, loans calendar

Requires Mobile API 1.6.0 (Pinakes 0.8.5). Every new entry point is gated on the matching `/health` feature, so an older server keeps showing exactly the 1.6.2 app.

- **Staff area** (`staff_catalog` + role `admin`/`staff`, refreshed from login and `GET /me`; a 403 on any staff call drops the stored role and leaves the screen): Profile → Staff area, and a scan button on the Catalog tab. Books (list with search, edit, delete with the active-circulation refusal), a shared create/edit form for records with or without an ISBN (identifiers validated live, authors/publishers pickers with "add new", genre/subgenre, shelf/level with automatic position, cover from URL / photo / picked image, copies), authors, publishers, genres (tree, find-or-create) and shelves/levels. Edits are true patches: only changed fields are sent.
- **ISBN scanner**: CameraX + ZXing EAN-13 (no Play services), checksum validation, two identical reads before a lookup, 3 s suppression, batch mode with remembered copies, shelf/level and optional auto-confirm, preview / duplicate ("add copies", "open existing") / not-found sheets. Manual ISBN/EAN entry is always visible (emulators, denied permission). CAMERA is requested only there, with rationale and a settings fallback; `camera.any` is optional.
- **Offline catalogue**: Room v2 holds the whole catalogue, kept current by `GET /catalog/sync` (delta with a server watermark, forced full resync every 7 days or when the server asks); loans, reservations and wishlist are written through on every fetch. Without a connection, search, book pages, Library and Wishlist answer from the cache with an offline banner. Loan and reservation actions are hidden offline (never shown disabled) and cached availability is shown only as "last known on <sync time>". The cache is excluded from backups.
- **Loans calendar**: Profile → Loans in your calendar (`calendar_feed`): create the private ICS link, copy it (primary, Google Calendar can only subscribe from its website), open it as `webcal://` with an HTTPS fallback, regenerate (old link stops) or turn it off.
- Local verification: 283 debug unit tests (new: ISBN/EAN codec, scan debouncer, staff access, staff form and write bodies, sync merge, offline policy, calendar links, tolerant 1.6.0 parsing, Room v2 catalogue/library DAOs), zero lint errors (111 warnings, mostly newer-dependency notices), debug and R8 release builds.
- Device tests (re-runnable): `app/src/androidTest/java/com/pinakes/app/staffe2e/` drives the real app (MainActivity, real Hilt graph, ViewModels, Room) against `FakePinakes`, a MockWebServer on the device loopback answering JSON fixtures from `app/src/androidTest/assets/fixtures/` and recording every request. Covered: staff entry only for admin/staff with `staff_catalog` (hidden for standard users, for the flag off and after a server-side downgrade, header included); scanner manual ISBN → preview → add → counter, duplicate → add copies, not found → manual form with the code, invalid code rejected, camera denied keeps manual entry (the camera prompt is denied through UiAutomator); manual form title required and shelf-without-level both send nothing, edit sends only the changed key (recorded PUT body); offline search banner with cached results and no availability or loan actions, offline detail "last known" wording without loan buttons, Library/Wishlist offline empty state with no raw exception, going offline after an online load hides the loan button and coming back restores it (real airplane mode); calendar create → masked link, copy flagged sensitive, regenerate and turn off both confirm; gender and tax code absent from the profile editor when the library hides them (present when shown) and never asked at registration; author detail with the padded info card and the author's books (`author_id` sent); loan-calendar month (7 weekday columns, every day cell inside the screen and inside a 320dp column, month navigation) and the birth-date picker within a 360dp screen. Run on a disposable emulator (it replaces the app's stored session and cache): `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` (one class: add `-Pandroid.testInstrumentationRunnerArguments.class=com.pinakes.app.staffe2e.ScannerFlowTest`).
- Live smoke (real app, real server): `tools/e2e/live-smoke.sh [--install]` with `PINAKES_E2E_URL` (as the device reaches it, e.g. `http://10.0.2.2:8081`), `PINAKES_E2E_EMAIL`, `PINAKES_E2E_PASSWORD` (admin or staff), optional `PINAKES_E2E_HOST_URL`, `PINAKES_E2E_ISBN` (default 9788845292613, must not be catalogued) and `ANDROID_SERIAL`. It clears the app, onboards, signs in, opens the staff hub, scan-adds an ISBN with 2 copies, adds a copy through the duplicate sheet, creates a record without ISBN, sets its shelf/level (skipped when the instance has none) and checks nothing else changed, searches and opens it in airplane mode, creates the calendar link and fetches the feed, and (with `PINAKES_E2E_DB_NAME/USER/PASSWORD/SOCKET` or `_HOST/_PORT`) hides gender and tax code in the settings table, checks they vanish from `/me` and from the profile editor, then restores the previous values; every step is checked through the API. Cleanup removes only what the run created (books, a calendar link that did not exist before, device tokens issued during the run) and clears the app. Texts follow the device language through `i18n/*.json`; `tools/e2e/ui.py` is the adb/uiautomator driver (`python3 tools/e2e/ui.py dump`).
- Not covered by automation: real camera decoding and photo capture (emulators have no barcode in view).

## 2026 restyle (2026-10-08)

The app now uses the 2026 web design: Geist / Fraunces, warm neutrals, a server-ready `ThemePalette`, complete 3D book covers, a searchable home hero with a cover fan, catalog grid/list views, dot status pills, grouped availability/actions and one card per exposed digital asset. Shared tokens apply to all existing account and plugin screens. The five-section bottom navigation is retained. Article lists and details now show server-resolved covers, subtitles, published online resources and access conditions, with a website action for the existing staff management flows. Native collections require the additive Mobile API 1.5.0 server update; existing circulation and authentication routes are retained.

- Debug APK: `pinakes-debug.apk`, copied from `app/build/outputs/apk/debug/app-debug.apk` (generated locally and ignored by Git).
- Package: `com.pinakes.app`, version `1.6.2` (19), minSdk 26, target/compileSdk 35.
- Local verification: **200 debug and 200 release unit tests, 22 instrumented tests on Android 15, zero lint errors (101 warnings)**, and successful debug + R8 release builds. The 1.6.2 debug APK installs over 1.6.1 with the same signing certificate.
- Verification commands: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease`, `assembleDebugAndroidTest`; the 22 device tests run through `am instrument` on a separate Android 15 AVD, preserving the authenticated demo device.
- Unit tests cover the existing contracts and theme contrast/mixing. Compose device tests cover full tall artwork, missing metadata, grid actions, view selection, digital-file cards, narrow search placeholders, circulation actions theme pairings, reachable empty-home actions and query preservation during loading.
- Release builds exercise R8 and resource shrinking. Without release credentials the output is unsigned; no store release is published by this change.
- macOS standalone emulator startup: `tools/run-emulator.sh` keeps a power assertion scoped to the emulator PID. A control with the identical debug APK reproduced a 21-second startup timeout at host priority 4; three starts with the assertion completed in 2.3–3.1 seconds. After a VM restart through the launcher, five more cold starts completed in 1.66–1.80 seconds with no ANR events. The original startup ANR also coincided with system/launcher stalls. An older AVD still stalled on a later cold boot, and a snapshot retained a system ANR dialog: the assertion does not repair an unhealthy AVD. No Sentry events are suppressed, and no production-device ANR fix is claimed.
- New native sections: Desiderata with verified-account donation proposals and durable retry recovery; Archives with hierarchy, filters, authorities, documents and exports; analytic articles with all bibliographic fields, five citation styles, rich clipboard, shared-author navigation and issue contributions. Home and catalog support author sorting; every digital attachment and publisher is shown. Staff management uses protected website pages. See [Uwe parity](docs/UWE-PARITY.md).
- Headless Android 15 completed the device tests while older GUI AVDs stalled despite the scoped power assertion. GUI and headless emulator processes had different host scheduling priorities; this is development-environment evidence, not a production ANR fix.
- `ThemePalette` defaults to Classic / Covers. Discovery does not expose theme, CMS home sections, richer catalog facets, CMS-driven home ordering or share data yet; these are documented as future API work in DESIGN.md.

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

- **Bottom nav:** Home / Catalog / Library / Wishlist / Profile. **Nested routes:** Book Detail, Notifications, Contact and optional collection details.
- **Design system:** Material 3 light **and** dark, 2026 theme-derived colours (Classic magenta by default),
  bundled Geist / Fraunces, warm neutrals, complete book covers, rounded controls and subtle hero washes.
  Calendar and status colours follow the app theme. The adaptive launcher icon is preserved.
- **Architecture:** Navigation-Compose + ViewModel/StateFlow + Hilt, Retrofit + OkHttp + kotlinx.serialization, Coil for covers.
  All loading/empty/error states handled per screen.

## Internationalization (i18n)

The app supports **4 languages — Italian, English, French, German**; the PHP server also supports Danish.
It follows the **device locale** by default and offers an **in-app language
switcher** in Profile (System default / Italiano / English / Français / Deutsch) via
`AppCompatDelegate.setApplicationLocales(...)`, persisted across restarts (`autoStoreLocales`).

- **Single source of truth = JSON.** Translations live in `i18n/{en,it,fr,de}.json` (815 keys each,
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
