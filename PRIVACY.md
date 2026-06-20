# Privacy Policy — Pinakes Android

_Last updated: 2026-06-19_

Pinakes Android is an open-source client app that connects to a **Pinakes
library instance chosen by you**. The app itself has no backend of its own: all
your data lives on the library server you point it at, operated by that library.

## What the app stores on your device

- **Instance URL** and **library name** you entered during onboarding.
- A **bearer access token** issued by your library after login, used to
  authenticate API requests.
- A randomly generated **device identifier** sent at login so you can see and
  revoke this device in your profile.
- Your **theme** and **language** preferences.

The token, instance URL and device id are stored in
`EncryptedSharedPreferences` (AES-256, key held in the Android Keystore) and are
**excluded from cloud backup and device-to-device transfer**. They are deleted
when you log out or disconnect from the instance.

## What the app sends, and to whom

The app communicates **only** with the Pinakes instance URL you configure. It
sends your email and password (over HTTPS) at login, and your bearer token on
subsequent requests, to authenticate and to perform the actions you initiate
(search, loans/reservations, wishlist, profile edits, contact messages). It also
fetches book cover images from URLs returned by that instance.

The app does **not** include third-party analytics, advertising, or tracking
SDKs, and does not transmit your data to the app's authors or any party other
than your chosen library instance.

## Network security

All traffic must use HTTPS. Cleartext HTTP is permitted **only** for local
development hosts (`localhost`, `127.0.0.1`, `10.0.2.2`); every other host is
required to be HTTPS.

## Push notifications (optional)

When enabled by your library and by you, push delivery uses **UnifiedPush**
through a distributor of your choosing. Registration data (a push endpoint and
WebPush keys) is sent to your library instance only.

## Data controller

Your data is controlled by the **library operating the Pinakes instance** you
connect to. For requests about access, correction, or deletion of your account
data, contact that library directly. For questions about the app itself, open an
issue on the project repository.

> Libraries publishing their own build: replace this section with your
> organisation's contact details and host this policy at a public URL, then link
> that URL in the Google Play Console (App content → Privacy policy).
