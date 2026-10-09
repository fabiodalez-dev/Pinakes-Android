# Play releases

## Candidate 1.6.0 (17), 2026-10-08

[PR #41](https://github.com/fabiodalez-dev/Pinakes-Android/pull/41) includes the complete 2026 restyle, the five bottom navigation destinations, typeset missing covers, native Archives and library Desiderata, donation proposals with consent and durable outcome recovery, analytic articles, shared-author searches, five citation styles and all book attachments/publishers. The requirement-by-requirement record is [UWE-PARITY.md](UWE-PARITY.md); the [mobile comparison evidence](https://github.com/fabiodalez-dev/Pinakes/blob/design/restyling-2026/docs/reviews/mobile-parity-2026-10-08/README.md) uses disposable local records.

The matching backend is [Pinakes #458](https://github.com/fabiodalez-dev/Pinakes/pull/458), included in [release candidate #462](https://github.com/fabiodalez-dev/Pinakes/pull/462). Install the updated backend/plugins before checking the new collections: Mobile API 1.5.0, Desiderata 1.2.0, Emeroteca 1.13.0 and Archives 1.5.1. Earlier servers keep existing circulation features; unsupported analytic filters ask for an upgrade. Staff editing and uploads use protected PHP pages.

Functional commit `6c4b745` passed 185 unit tests, 19 Compose tests on Android 15, debug and minified R8 builds, and lint with zero errors (98 warnings). The local APKs are generated artifacts, not tracked source or Play releases. PR CI rebuilds from the latest head. Production signing, the remaining gates below and store publication remain separate release steps. No production-device ANR fix or FBI/DBC integration is claimed.

## Bundle workflow

GitHub is the source of truth. Pull requests run Android CI. After merging Android changes into main, `Play release bundle` runs release unit tests and lint, then signs an AAB and retains the bundle, checksum, R8 mapping and reports as GitHub artifacts. Manual dispatch is supported on main only. No PR code receives signing secrets.

Environment `play-internal` must allow main only. Required environment secrets: `PINAKES_KEYSTORE_BASE64`, `PINAKES_KEYSTORE_PASSWORD`, `PINAKES_KEY_ALIAS`, `PINAKES_KEY_PASSWORD`. Use the existing release key; never generate a replacement silently. Keys are materialized in runner temporary storage and removed after signing; configuration caching is disabled.

This workflow builds bundles; it does NOT upload to Play or publish to production. Publisher API credentials and app-scoped Play permissions are still required for that separate stage. Increment versionCode before each new Play upload, including corrective rebuilds after a code has been accepted. Debug GitHub releases keep their existing path and are not Play artifacts.

Review library: https://biblioteca.fabiodalez.it. Mobile API was enabled on 2026-09-09. A dedicated standard reader was created; credentials are private outside Git, not in this document or release artifacts.

Publisher/controller confirmed by the owner on 2026-09-09: **D'Alessandro Fabio Gaetano**. Public support/privacy email: **info@fabiodalez.it**. Use these for this distribution, not as the controller of independently operated third-party library instances.

Remaining release gates: accurate privacy policy including Sentry, account-deletion path and public request URL, reviewer access test, minified-device smoke test, native 16 KB compatibility and Play questionnaires. The personal developer account requires the closed-test period before production access. Do not interpret a green bundle build as policy approval.

The older PLAY_STORE_COMPLIANCE.md audit is historical: its no-third-party-SDK and disabled-R8 statements no longer describe the app. Sentry is present, R8 is enabled, and logout does not delete the user's server account.
