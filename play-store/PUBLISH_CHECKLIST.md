# Pinakes Android — Google Play publish checklist

Everything the developer can automate is **done**; the rest is Play Console
work + manual assets. Follow top to bottom.

## ✅ Already done (in this repo, locally)
- **Upload keystore** generated: `pinakes-upload.jks` (RSA 2048, valid to 2053).
  Credentials in `keystore.properties`. **Both are git-ignored — never commit
  them.** Back up `pinakes-upload.jks` + the passwords somewhere safe
  (password manager). If lost, the upload key is resettable via Play App
  Signing, but keep it anyway.
- **Signed release AAB** built and verified:
  `app/build/outputs/bundle/release/app-release.aab` (versionCode 9,
  versionName 1.3.3, R8 minify + resource shrink, signed with the upload key).
- Release build type is already production-ready (non-debuggable, ProGuard
  keep-rules for Retrofit/OkHttp/serialization/Coil/Room).

**Upload certificate SHA-256** (you may need it when enrolling in Play App
Signing / to register the upload key):
```
D5:29:E0:E0:9F:C8:B0:3C:BC:67:25:FB:ED:3A:FD:3C:8E:52:9B:C1:38:78:2C:0A:BF:41:31:A4:9D:B5:6D:23
```

## 1. Play Console account
- [ ] Create a Google Play Developer account (**$25** one-time).
- [ ] Complete **identity verification** (ID + address + phone). Can take days.
- [ ] ⚠️ **If this is a new personal account:** you must run a **closed test
      with ≥12 testers opted in for 14 continuous days** before you can request
      production access. Start the closed test early — it's the long pole.
      (Not required for older/organisation accounts.)

## 2. Create the app
- [ ] Create app → name "Pinakes — Library", language, **app (not game)**,
      **free**.
- [ ] App category: **Books & Reference**.

## 3. Upload the build
- [ ] Enable **Play App Signing** (recommended default) — Google holds the app
      signing key; you upload with `pinakes-upload.jks`.
- [ ] Create a release in **Internal testing** first → upload
      `app-release.aab` → roll out to yourself to smoke-test end to end.
- [ ] Then promote to **Closed testing** (satisfies the 12-tester rule for new
      accounts) → later **Production**.

## 4. Store listing
- [ ] Paste title / short / full description from `STORE_LISTING.md`.
- [ ] Upload **app icon 512×512**, **feature graphic 1024×500**, **≥2 phone
      screenshots** (grab from the `pinakes_test` emulator on your demo
      instance).
- [ ] Set contact email + website.

## 5. Privacy & compliance (App content)
- [x] **Privacy policy URL** — DONE and live:
      **https://fabiodalez.it/pinakes-privacy.html** (contact email
      `info@fabiodalez.it` already filled in). Paste this URL in the Console.
- [ ] **Data safety** — enter exactly as `DATA_SAFETY.md`.
- [ ] **Content rating** — questionnaire per `CONTENT_RATING.md` → Everyone.
- [ ] **Target audience** — 18+ (or 13+), not designed for children.
- [ ] **Ads** — declare **No ads**.
- [ ] **Government / financial / health** — No.

## 6. ⚠️ App access — the one that gets library apps rejected
Pinakes needs a login **to a server**, so Google's reviewers cannot test it
without one. In **App content → App access**, you MUST provide:
- [ ] A reachable **demo Pinakes instance URL** (public, HTTPS).
- [ ] A **demo account** (username + password) with sample catalogue data.
- [ ] Step-by-step login notes: "Open app → enter server URL `<demo-url>` →
      log in with `<demo-user>` / `<demo-pass>` → browse catalogue, request a
      loan."

Without this the app is rejected for "unable to access content behind
authentication." Stand up a small demo instance (you can reuse the Docker
image `ghcr.io/.../pinakes` you just published) with a seeded catalogue.

## 7. Submit
- [ ] Complete the release dashboard (all green), then submit for review.
- [ ] Review typically takes a few days (longer for a brand-new account).

## Rebuilding the AAB later (for each new version)
Bump `versionCode`/`versionName` in `app/build.gradle.kts`, then:
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab (signed with the upload key)
```
The signing is automatic because `keystore.properties` is present.
