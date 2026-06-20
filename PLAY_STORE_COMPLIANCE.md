# Pinakes Android — Play Store & Structural Compliance Audit

_Audit date: 2026-06-19. Reviewed against the live Google Play target-API policy
(2026) and the Pinakes Mobile API contract in `_contract/`._

## 1. Structural correctness vs Pinakes — ✅ PASS

The app is built against the **Pinakes Mobile API** (`/api/v1`), a bundled
plugin of the Pinakes ILS (`fabiodalez-dev/biblioteca`). The contract snapshot
lives in `_contract/` (`openapi.json` v0.7.20.2, `MOBILE_API_SPEC.md`,
`health-sample.json`, `endpoint-manifest.spec.js`).

| Check | Result |
|---|---|
| Every OpenAPI path implemented in `PinakesApi.kt` | ✅ 1:1 (28/28 paths) |
| Response envelope `{data, meta, error}` | ✅ `Envelope<T>` / `Meta` / `ApiError` |
| Auth flow: `/health` discovery → `/auth/login` → Bearer on all calls → `/auth/logout` | ✅ matches spec §"Auth flow" |
| Cursor pagination (`meta.next_cursor`, `?cursor&limit`) | ✅ used in catalog search |
| ETag/304 on book detail | ✅ `Response<>` + `If-None-Match` honoured in repo |
| Token storage: secure, per-device, revocable | ✅ `EncryptedSharedPreferences`; device list + revoke wired |
| Transport: HTTPS enforced except loopback | ✅ `isTransportAllowed()` + `network_security_config.xml` agree |
| Catalogue-only / feature flags from `/health` | ✅ `FeatureStore` hides loans/reservations/wishlist |
| i18n it/en/fr/de, English source | ✅ `i18n/*.json` → generated `strings.xml` (289 keys) |

**Notes / deviations (intentional, documented in code):**
- `BookSummary`/`BookDetail` use the live API's English snake_case keys, not the
  (stale) OpenAPI field names — annotated in `Models.kt`. The running
  `CatalogController` is the source of truth; verified live per `STATUS.md`.
- The app adds `GET /catalog/books/{id}/availability` (availability calendar),
  present on the live API but absent from the OpenAPI snapshot.
- **Push (UnifiedPush) is stubbed**: the data layer (`/me/push/*`) exists but no
  distributor receiver / prefs screen. This is a feature gap, not a structural
  mismatch.

**Verify before publishing:** the README and `openapi.json` declare the license
as **AGPL-3.0** ("same license as Pinakes"), but the upstream `biblioteca`
repository's public README states **GPL-3.0**. Confirm which is correct and make
the two consistent — this is a legal/licensing decision for the maintainer.

## 2. Google Play compliance

### Already compliant
- **Target API level:** `targetSdk = 35` (Android 15) — meets the Play
  requirement in force for 2026 (new apps & updates must target API ≥ 35 since
  2025-08-31). `minSdk = 26`, `compileSdk = 35`.
- **Permissions:** only `INTERNET` + `ACCESS_NETWORK_STATE` — minimal and
  justified; no dangerous/sensitive permissions.
- **Cleartext traffic:** disabled by default, allowed only for loopback/emulator
  — acceptable, no production cleartext.
- **Adaptive launcher icon** (`mipmap-anydpi-v26`), valid for minSdk 26.
- **Secrets:** `.gitignore` excludes `*.jks`, `*.keystore`, `*.aab`, `*.apk`,
  `local.properties` — no credentials in the repo.

### Fixed in this change
1. **Encrypted-prefs backup hazard** → added `data_extraction_rules.xml`
   (API 31+) and `backup_rules.xml` (API ≤30), both excluding
   `pinakes_session_secure`, and referenced them from the manifest. Without this,
   Auto Backup would restore an encrypted prefs file whose Keystore master key
   did not travel with it → undecryptable token / possible crash after a restore.
2. **No release signing** → added a guarded `signingConfigs.release` in
   `app/build.gradle.kts` that reads `keystore.properties` or `PINAKES_*` env
   vars. When absent, debug builds are unaffected and release stays unsigned
   (no failure for contributors). **You must create a keystore and supply it.**
3. **Incomplete ProGuard rules** → `proguard-rules.pro` now has complete keep
   rules for Retrofit, OkHttp/Okio, kotlinx.serialization and Coil, so a future
   minified release won't break on reflection. (Minification left **off** until a
   release build can be smoke-tested — see Recommended below.)

### Required before you can publish (action needed — outside this checkout)
1. **Upload an Android App Bundle, not an APK.** Play requires `.aab` for new
   apps. Build `./gradlew bundleRelease` (the current README/STATUS ship a
   *debug APK*, which Play will reject). AAB support needs no code change.
2. **Provide a release keystore** and enroll in **Play App Signing.** Populate
   `keystore.properties` (git-ignored) or `PINAKES_*` env vars (see the comment
   in `app/build.gradle.kts`). A debug-signed build cannot be uploaded.
3. **Host the privacy policy** (draft added as `PRIVACY.md`) at a public URL and
   enter it in Play Console → App content → Privacy policy. Required because the
   app handles account data (email/password/token).
4. **Complete the Data Safety form** in Play Console: declare that the app
   collects email + (transient) credentials and a device id, sent only to the
   user's library instance, encrypted in transit, deletable on logout. No
   third-party sharing, no analytics/ads SDKs (true for this app).
5. **Store listing assets:** app icon (have it), feature graphic, phone
   screenshots (the `docs/screenshots/*` can be reused), short/full description,
   content rating questionnaire, target audience.

### Recommended (not blocking)
- **Enable R8** (`isMinifyEnabled = true` + `isShrinkResources = true`) once a
  release build has been smoke-tested on a device — keep rules are ready.
- **Wire `POST_NOTIFICATIONS`** (API 33+) and request it at runtime **when** push
  (UnifiedPush) is implemented; not needed for the current in-app-only feed.
- **Background audiobook playback:** the Media3 player is in-page only (released
  when the screen leaves). If background/lock-screen playback is desired, add a
  `MediaSessionService` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (and declare the
  foreground-service type) — this is a Play-scrutinised area, so only add it if
  the feature is actually built.
- Consider `android:localeConfig` (per-app languages, API 33+) to surface the
  it/en/fr/de locales in system settings.

## 3. Build verification status

This checkout has **no Android SDK** (`ANDROID_HOME` unset), so `./gradlew
assembleDebug`/`lint`/`bundleRelease` could **not** be run here. The changes
above are static/config-only and low-risk, but should be confirmed with a real
build (`./gradlew assembleDebug lintDebug bundleRelease`) on a machine with the
SDK before release. `STATUS.md` records the last verified green build + emulator
smoke test.
