# Pinakes — Privacy Policy

_Last updated: 2026-07-31_

Pinakes ("the app") is an open-source client for the Pinakes library
management system. The app connects to a **Pinakes server that you or your
library operate** ("your instance"). Pinakes is self-hosted: the app has no
central backend operated by the developer, and your catalogue, account and
activity live on **your instance**, not on any server controlled by the
developer.

This policy explains what the app handles and why.

## Who is the data controller

For the account and library data you enter, the **operator of the Pinakes
instance you connect to** is the data controller. If you use a library's
instance, that library controls your data; if you run your own instance, you
do.

The app developer only operates the crash-reporting endpoint described below.

## What data the app processes

**1. Account and library data — sent only to your instance.**
When you sign in or use the app, it transmits, over an encrypted connection,
to the server URL you configure:

- Account identifiers you provide: **email address, display name, password**
  (password is used only to authenticate and is never stored in plain text on
  the device).
- A **device name / session token** so you can review and revoke your logged-in
  devices.
- **Library activity** you generate: loan requests, reservations, reviews,
  wishlist entries, and reading history.

This data is stored and controlled by your instance. The app keeps a local
copy (session token, cached catalogue, preferences) on your device so it works
offline; you can clear it by logging out or uninstalling the app.

**2. Crash diagnostics — sent to Sentry.**
To fix crashes, the app sends anonymous crash reports to **Sentry**
(Functional Software, Inc.), an EU-region ingest endpoint
(`*.ingest.de.sentry.io`). These reports contain the stack trace, app version,
device model and OS version. They are **crash reports only**: no performance
tracing is collected, and personally identifying information (including your IP
address) is **not** attached (`isSendDefaultPii = false`). This lets the
developer diagnose stability issues; it is not used for advertising or
profiling.

## What the app does NOT do

- No advertising and no advertising identifiers.
- No location, camera, microphone, contacts or file access — the app requests
  only `INTERNET` and `ACCESS_NETWORK_STATE`.
- No selling of data. No sharing of your account or library data with any third
  party other than the instance you choose to connect to.
- No analytics/tracking SDKs beyond the crash reporting described above.

## Data retention and deletion

- **On your device:** cleared on logout or uninstall.
- **On your instance:** governed by the operator of that instance. To delete
  your account or library data, contact the operator of the Pinakes instance
  you use (for a library, its staff; for a self-hosted instance, its admin).
- **Crash reports:** retained by Sentry per its retention settings and deleted
  automatically after that window.

## Children

The app is a library tool and is not directed at children. It collects no data
beyond what is described above.

## Changes

This policy may be updated; the "Last updated" date reflects the latest
version. Material changes will be noted in the app's release notes.

## Contact

For privacy questions about the app itself: **info@fabiodalez.it**

For questions about your account or library data, contact the operator of the
Pinakes instance you connect to.
