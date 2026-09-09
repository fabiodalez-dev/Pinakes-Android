# Privacy Policy — Pinakes Android

_Technical privacy description updated: 2026-09-09. The publisher has confirmed
the controller and contact below. Retention details and the remaining release
gates still need verification before this becomes the final Play privacy policy._

Pinakes Android is an open-source client app that connects to a **Pinakes
library instance chosen by you**. Library services use that instance's server.
The app also integrates Sentry for crash diagnostics and loads media from URLs
supplied by the library; not all requests are limited to the library's domain.

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

For library services, the app communicates with the Pinakes instance you configure. It
sends your email and password (over HTTPS) at login, and your bearer token on
subsequent requests, to authenticate and to perform the actions you initiate
(search, loans/reservations, wishlist, profile edits, contact messages). It also
fetches book cover images from URLs returned by that instance.

The app includes **Sentry crash reporting**, configured during app startup.
Default PII attachment is disabled and performance trace sampling is zero.
These settings do not mean no data is transmitted: error events, technical
device/app information and diagnostic context can reach Sentry. Retention and
the precise diagnostic fields must be verified against the publisher's Sentry
configuration before completing the Play Data Safety declaration.

Catalog and HTTP caches are also stored locally. Signing out or switching
instances clears the relevant caches. Signing out revokes the session; it does
**not** delete the account, loans or other records held by the library.

## Network security

All traffic must use HTTPS. Cleartext HTTP is permitted **only** for local
development hosts (`localhost`, `127.0.0.1`, `10.0.2.2`); every other host is
required to be HTTPS.

## Push notifications (optional)

The app has a notification feed and UnifiedPush-related API support. A complete
distributor integration has not been verified for this release; do not describe
background push delivery as universally available. Any enabled distributor and
its data handling must be included in the deployment's privacy review.

## Data controller

The publisher and controller for this Pinakes Android distribution and its app
diagnostics is **D'Alessandro Fabio Gaetano**, reachable at
**[info@fabiodalez.it](mailto:info@fabiodalez.it)**. This is also the contact for
the reference library at **https://biblioteca.fabiodalez.it**.

When you connect to another independently operated library, that library
operates its own account and circulation services. Consult its privacy policy
and contact it for access, correction or deletion requests relating to those
records. Do not post credentials, loan history or other private data in public
GitHub issues. Never send your password in a privacy request.

The app supports account registration. A compliant in-app account-deletion
request path and an external request URL remain release gates; logout must not
be presented as account deletion. A library may need to retain specific records
under its obligations, which its policy must explain accurately.

For the reference library, account and privacy requests may be sent to
info@fabiodalez.it, identifying the library URL and account email. Identity
verification may be necessary before processing a request. This contact does
not yet replace the in-app and public-web deletion paths listed above.
