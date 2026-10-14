# Pinakes Android

Native Android client for a [Pinakes](https://github.com/fabiodalez-dev/Pinakes)
library instance. Browse the catalog, check real availability, borrow and reserve
books, read ebooks and listen to audiobooks, and manage your loans, all from your
phone. Optional Archives, Desiderata and analytic articles are available when the
server advertises their APIs. The five bottom navigation destinations are retained.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![License](https://img.shields.io/badge/license-AGPL--3.0-green)

## ⬇ Download

**[Get the latest debug APK](https://github.com/fabiodalez-dev/Pinakes-Android/releases/latest)** from the Releases page (`pinakes-android-v<version>-debug.apk`), then install it on your phone (enable *install from unknown sources*) or `adb install -r <file>.apk`. Point it at your Pinakes instance URL on first launch.

> These are **debug-signed** builds for direct sideloading, not Play Store releases. Each release bumps `versionCode`, so a newer APK installs over the previous one in place.

## What it is

A single self-contained app any Pinakes library can hand to its members. On first
launch you enter your library's web address; the app discovers the instance
through its public `/api/v1/health` endpoint, you sign in with your library
account, and from then on everything talks to that instance's REST API over a
bearer token stored in `EncryptedSharedPreferences`.

The app is server-agnostic: it works against any Pinakes instance that has the
Mobile API enabled, and it adapts to that instance's settings (language,
catalogue-only mode, push availability).

## 2026 interface

The Android interface follows the public Pinakes 2026 restyle: shared theme tokens, Geist for controls, Fraunces for titles, fitted book artwork and a quiet warm background. `ThemePalette` accepts library colours and hero/card styles; Classic / Covers are the built-in defaults until the Mobile API exposes theme settings. Existing circulation, account and plugin flows remain available. See [DESIGN.md](DESIGN.md) for the component contract and API limitations.

## Screenshots

Login, home, catalog, book detail and calendar captures use the 2026 interface on Android 15 at 360 dp, with fixture data. The remaining account captures document the earlier flow layout.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/login.png" width="200" alt="Login screen"><br><sub>Login</sub></td>
    <td align="center"><img src="docs/screenshots/home.png" width="200" alt="Home screen"><br><sub>Home</sub></td>
    <td align="center"><img src="docs/screenshots/catalog.png" width="200" alt="Catalog with filters"><br><sub>Catalog + filters</sub></td>
    <td align="center"><img src="docs/screenshots/detail.png" width="200" alt="Book detail screen"><br><sub>Book detail</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/calendar.png" width="200" alt="Availability calendar"><br><sub>Availability calendar</sub></td>
    <td align="center"><img src="docs/screenshots/library.png" width="200" alt="My Library screen"><br><sub>My Library</sub></td>
    <td align="center"><img src="docs/screenshots/profile.png" width="200" alt="Profile screen"><br><sub>Profile</sub></td>
    <td></td>
  </tr>
</table>

### Staff area

Admin and staff accounts get a native staff area when the instance runs Mobile API 1.7.0: cataloguing with ISBN scanning, the whole circulation desk and the content of the Desiderata, Archives, Periodicals, Book Club and FRBR plugins. Captured on Android 15 at 360 dp against a local instance with demo data.

#### Circulation desk

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/circulation-desk.png" width="200" alt="Circulation desk with counters"><br><sub>Circulation desk</sub></td>
    <td align="center"><img src="docs/screenshots/staff/loans-overdue.png" width="200" alt="Overdue loans list"><br><sub>Loans by status</sub></td>
    <td align="center"><img src="docs/screenshots/staff/loan-detail-overdue.png" width="200" alt="Overdue loan with return, edit, reminder and receipt actions"><br><sub>Loan actions</sub></td>
    <td align="center"><img src="docs/screenshots/staff/loan-detail-request.png" width="200" alt="Pending request with approve and reject"><br><sub>Approve a request</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/new-loan.png" width="200" alt="New loan form with borrower and book search"><br><sub>New loan</sub></td>
    <td align="center"><img src="docs/screenshots/staff/reservations.png" width="200" alt="Reservations grouped by title with queue position"><br><sub>Reservations queue</sub></td>
    <td align="center"><img src="docs/screenshots/staff/copy-scanner.png" width="200" alt="Copy label scanner"><br><sub>Scan a copy label</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-hub.png" width="200" alt="Staff area hub"><br><sub>Staff area</sub></td>
  </tr>
</table>

#### Cataloguing

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/isbn-preview.png" width="200" alt="ISBN lookup preview from the server scrapers"><br><sub>Scan an ISBN</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-books.png" width="200" alt="Staff book list"><br><sub>Books</sub></td>
    <td align="center"><img src="docs/screenshots/staff/book-edit.png" width="200" alt="Book editor with cover sources"><br><sub>Edit a record</sub></td>
    <td align="center"><img src="docs/screenshots/staff/book-copies.png" width="200" alt="Copies, location and labels in the book editor"><br><sub>Copies and labels</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/author-detail.png" width="200" alt="Author page with books and record details"><br><sub>Author page</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-authors.png" width="200" alt="Author search"><br><sub>Authors</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-genres.png" width="200" alt="Genre tree"><br><sub>Genres</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-series.png" width="200" alt="Series search"><br><sub>Series</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/series-detail.png" width="200" alt="Series detail with its volumes"><br><sub>Series detail</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-merges.png" width="200" alt="Merge duplicate authors, publishers and genres"><br><sub>Merge duplicates</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-imports.png" width="200" alt="CSV and LibraryThing import"><br><sub>Import a file</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-covers.png" width="200" alt="Missing covers and enrichment"><br><sub>Covers and enrichment</sub></td>
  </tr>
</table>

#### Plugin content

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/staff/staff-desiderata.png" width="200" alt="Donation offers by status"><br><sub>Donation offers</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-archives.png" width="200" alt="Archival units with publishing filter"><br><sub>Archives</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-periodicals.png" width="200" alt="Periodicals desk with issue barcode lookup"><br><sub>Periodicals desk</sub></td>
    <td align="center"><img src="docs/screenshots/staff/staff-hub-2.png" width="200" alt="Staff hub plugin section"><br><sub>Staff tools</sub></td>
  </tr>
</table>

## Features

| Area | What you get |
|------|--------------|
| **Onboarding** | Enter the instance URL, `/health` discovery card (library name, logo, HTTPS check, mobile-access check) |
| **Sign in & sign up** | Email/password login, **in-app registration** and **password recovery**, mapped error messages, secure token storage |
| **Home** | Searchable library hero, a fan of real shelf covers, and an "Available now" / recent shelf |
| **Catalog** | Two-column book grid or compact list, infinite scroll, search, sort and a filter sheet (availability, genre, author, publisher, language) |
| **Book detail** | HTML-rendered description, tap-to-zoom cover, full metadata block (ISBN, year, pages …), genre hierarchy |
| **Availability** | Neutral status pills with a coloured dot: green available, red on loan, amber reserved |
| **Loan calendar** | Pick a start date on a calendar that paints already-booked days and pre-selects the first free day |
| **Audiobooks** | In-app player (Media3 ExoPlayer) when the title has an audio file |
| **Ebooks** | In-app PDF reader (PdfRenderer); other formats open externally |
| **My Library** | Loans grouped into Overdue / On loan / Ready & scheduled / Pending, with due dates; reservations with cancel |
| **Wishlist** | Add and remove titles |
| **Book Club** | When the instance runs the **Book Club** plugin: browse your clubs and the directory, open a club's reading list / polls / meetings, join, vote in-app (simple / multi / weighted ballots), RSVP to meetings and track your reading progress — advanced poll modes and proposing a title deep-link to the web |
| **Profile** | Edit profile, change password, device list, theme switcher, language switcher, logout |
| **Notifications** | Loan due/overdue, reservation ready, book available |
| **Themes** | 2026 warm neutrals, Geist + Fraunces, complete 3D covers and paired theme colours. Light by default; pick light/dark/system in Profile |
| **Staff area** | For admin and staff accounts: cataloguing (ISBN/EAN scanner with batch mode, manual records, copies with their own codes, series, merge duplicates, label PDFs, CSV and LibraryThing import, covers and enrichment, authors, publishers, genres, shelves) and plugin content (donation offers, archives, periodicals desk, book clubs, FRBR works, digital files) |
| **Circulation desk** | Counters, loans by status, new loan with borrower search and copy scanning, approve/reject, pickup, return with copy outcome, renew, bulk extend, reminders, receipt PDF, reservations queue. Hidden while offline |
| **Offline catalogue** | Full catalogue mirrored on the device with delta sync; search, book pages, My Library and Wishlist work offline, while loan and reservation actions stay hidden until the connection is back |
| **Loans in your calendar** | A private, revocable calendar link with your loans (start to due date) and reservations, for Google Calendar, Apple Calendar, Outlook and others |
| **Languages** | Italian, English, French, German, following the device locale or an in-app choice |

## Tech stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3), single-module app
- **Navigation-Compose** + `ViewModel`/`StateFlow` + Hilt
- **Retrofit + OkHttp + kotlinx.serialization** for the `/api/v1` client (`{data, meta, error}` envelope)
- **Coil** for cover images, **Media3 ExoPlayer** for audio, platform `PdfRenderer` for PDFs
- **AndroidX Security** (`EncryptedSharedPreferences`) for the bearer token and instance URL
- compileSdk/targetSdk **35**, minSdk **26**, JDK **21** toolchain, AGP **8.7**, Gradle **8.10**
- Pinned versions in `gradle/libs.versions.toml` (no dynamic versions)

## Requirements

- JDK 21
- Android SDK with `platforms;android-35` and matching build-tools
- No Android Studio needed; the Gradle wrapper is included

## Build

```bash
./gradlew assembleDebug   # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug       # static analysis
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest  # UI regression tests, with a running emulator
./gradlew assembleRelease # R8 + resource shrinking, unsigned without release credentials
```

Create a `local.properties` with `sdk.dir=/path/to/android-sdk` (or set `ANDROID_HOME`).
Always build with the Gradle wrapper (`./gradlew`).

## Install & run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A prebuilt debug APK is published on the [Releases](../../releases) page.

### Standalone emulator on macOS

```bash
./tools/run-emulator.sh my_avd -no-snapshot-load -gpu auto
```

The launcher forwards the remaining options to the Android SDK emulator. On macOS,
it holds a `caffeinate` assertion for that emulator PID, then releases it when the
emulator exits; Ctrl+C stops both. This reduces host power throttling during local
testing. It does not change global power
settings or Android crash reporting.

An Android 15 startup ANR was reproduced before application initialization, along
with system/launcher stalls. With the same debug APK, host priority dropped to 4
without the assertion and startup exceeded 21 seconds; with the assertion, three
cold starts completed in 2.3–3.1 seconds. After restarting the VM through this
launcher, five more cold starts completed in 1.66–1.80 seconds with no ANR events.
An older AVD still stalled after a cold boot, and a restored snapshot retained a
system-not-responding dialog. The assertion alone does not repair an unhealthy
AVD; use a fresh development AVD without deleting needed user data. This is a
development-emulator mitigation;
device ANRs still require their own [trace diagnosis](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs).

## Point it at a Pinakes instance

On first launch the app asks for the instance URL.

- Production: `https://your-library.example` (HTTPS required)
- Local emulator to host dev server: `http://10.0.2.2:8081`
- The instance must have **mobile app access enabled**, otherwise login is refused

Cleartext HTTP is only permitted for loopback and the emulator alias (`localhost`,
`127.0.0.1`, `10.0.2.2`); every other host must be HTTPS.

### Enable the Mobile API on the server

The app talks to Pinakes' bundled **Mobile API** plugin (`/api/v1`). A library
admin enables it once:

1. **Admin → Plugins → Mobile API** — activate the plugin. It is active by
   default on a fresh install; after an upgrade it ships disabled.
2. **Mobile API → Settings** — turn on **mobile app access** (otherwise login is
   refused with *"mobile app access is disabled on this library"*).

`/api/v1/health` and the interactive docs at `/api/v1/docs` are always reachable.
See the [Mobile API documentation](https://fabiodalez-dev.github.io/Pinakes/#/admin/mobile-api).

### Book Club (optional plugin)

If the instance runs the **Book Club** plugin (with its `mobile` module enabled),
the app auto-discovers it via `GET /api/v1/bookclub/health` (a 2xx after login) and
adds a **Book Club** entry to the Profile tab; otherwise the section stays hidden.
It reuses the same bearer token — no extra login — and talks to the plugin's
`/api/v1/bookclub/*` surface. Advanced poll modes (stars / ranking / elimination)
and proposing a title open the corresponding web page.

## Internationalization

Translations live in `i18n/{en,it,fr,de}.json` (one flat key/value map per
language; English is the source). A Gradle task generates the Android
`res/values*/strings.xml` from those JSON files at build time, so the editable
source stays in JSON and can be kept in sync with the Pinakes web locales. The
generated `strings.xml` files are git-ignored.

The app follows the device locale by default and offers an in-app language switcher
in Profile (System / Italiano / English / Français / Deutsch).

## Catalogue-only mode

If the instance runs in catalogue-only mode (`/health` reports
`catalogue_mode: true` and the matching `features.*` flags false), the app hides
loans, reservations and the wishlist: the Library and Wishlist tabs disappear and
the book detail drops its borrow/reserve actions, leaving a clean read-only
browsing experience.

## Architecture

```
app/src/main/java/com/pinakes/app/
├── data/
│   ├── model/        kotlinx.serialization models (mirror the live /api/v1 JSON)
│   ├── network/      PinakesApi (Retrofit) + interceptors
│   ├── repository/   Auth / Catalog / Library / Profile / Notifications
│   └── store/        SessionStore, FeatureStore, ThemeStore (EncryptedSharedPreferences)
├── di/               ServiceLocator
└── ui/
    ├── theme/        Color / Type / Theme (magenta #D70161 brand, light default)
    ├── components/   BookCard, AvailabilityChip, AudioPlayer, bottom bar …
    ├── navigation/   NavHost + scaffold
    └── screens/      onboarding, login, home, search, detail, library, wishlist, profile, bookclub …
i18n/                 en/it/fr/de.json  (source of truth for strings)
_contract/            OpenAPI snapshot + API spec the app is built against
```

## Status

All core screens are implemented and verified end-to-end on an emulator against a
live instance (onboarding, login, catalog, detail, loans, reservations, audio, PDF,
themes, languages). UnifiedPush registration is wired in the data layer; the
distributor receiver is a follow-up. See `STATUS.md` for the full breakdown.

## License

Released under the same license as Pinakes: **AGPL-3.0**.

### Standalone articles in Emeroteca

On compatible servers, **Emeroteca → Articles** searches and displays standalone
newspaper and magazine articles, without requiring ownership of their issues.
Publication screens also link to their associated articles. Article covers, subtitles
and published online resources follow the server data. Public PDFs use the
URL supplied by the server; the website action opens the article’s full page. Older servers retain the existing periodicals browser.
See [the article integration notes](docs/emeroteca-standalone-articles.md).

## Android 1.6 / current server parity

The updated client reads Mobile API 1.5.0, Desiderata 1.2.0, Emeroteca 1.13.0 and
Archives 1.5.1. Update the server/plugins before expecting the new optional
collection screens. Older instances remain usable; unsupported article facets
show an upgrade message rather than silently returning unfiltered results.

Desiderata is the library's wanted collection, separate from a member's wishlist.
Donation contact details come from the verified account. Proposals require
consent and survive transport retries/process death without creating inventory.
Archives supports paged hierarchy, text/level/year search, authorities, all
public documents and export links. Article/book citations are formatted by the
server, with five styles and text/HTML clipboard copies. Every digital book
attachment is shown; audio switches one native player at a time.

Administrative cataloguing and uploads open the protected PHP website, including
the existing article form. No native administrative CRUD API is invented.

See [the request-by-request Uwe verification](docs/UWE-PARITY.md).
