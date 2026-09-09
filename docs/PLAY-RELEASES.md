# Play releases

GitHub is the source of truth. Pull requests run Android CI. After merging Android changes into main, `Play release bundle` runs release unit tests and lint, then signs an AAB and retains the bundle, checksum, R8 mapping and reports as GitHub artifacts. Manual dispatch is supported on main only. No PR code receives signing secrets.

Environment `play-internal` must allow main only. Required environment secrets: `PINAKES_KEYSTORE_BASE64`, `PINAKES_KEYSTORE_PASSWORD`, `PINAKES_KEY_ALIAS`, `PINAKES_KEY_PASSWORD`. Use the existing release key; never generate a replacement silently. Keys are materialized in runner temporary storage and removed after signing; configuration caching is disabled.

This workflow builds bundles; it does NOT upload to Play or publish to production. Publisher API credentials and app-scoped Play permissions are still required for that separate stage. Increment versionCode before each new Play upload, including corrective rebuilds after a code has been accepted. Debug GitHub releases keep their existing path and are not Play artifacts.

Review library: https://biblioteca.fabiodalez.it. Mobile API was enabled on 2026-09-09. A dedicated standard reader was created; credentials are private outside Git, not in this document or release artifacts.

Remaining release gates: accurate privacy policy including Sentry, account-deletion path and public request URL, reviewer access test, minified-device smoke test, native 16 KB compatibility and Play questionnaires. The personal developer account requires the closed-test period before production access. Do not interpret a green bundle build as policy approval.

The older PLAY_STORE_COMPLIANCE.md audit is historical: its no-third-party-SDK and disabled-R8 statements no longer describe the app. Sentry is present, R8 is enabled, and logout does not delete the user's server account.
