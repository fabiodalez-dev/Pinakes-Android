# Pinakes Android — Design system (2026)

This replaces the previous design system. It ports to the app the 2026 restyle of the Pinakes web frontend (branch `design/restyling-2026` of `fabiodalez-dev/Pinakes`). The web is the reference: when this file and the web disagree, look at the web. Source files on the web side:

- `public/assets/pinakes-2026.css`: every token and component below, as CSS
- `public/assets/pinakes-2026.js`: cover-tone sampling, wishlist hearts, grid/list toggle
- `app/Views/frontend/partials/pk-book-card.php`: the book card, element by element
- `app/Views/frontend/book-detail.php`: the book page
- `app/Views/frontend/home-sections/hero.php`: the home hero
- The original mockup: `Restyling home e catalogo Pinakes.zip` (`Pinakes Restyling.dc.html`), in Fabio's Downloads

Two rules sit above everything else:

1. **Change the form, never remove an element.** Every badge, icon, label, button and text the app shows today stays. A restyle that drops the media-type icon, the "wanted by the library" badge, a share target or a plugin button is wrong, even when the mockup does not show it. Restyle it in this language instead.
2. **The library's theme drives the colour.** Pinakes installs choose a theme in the admin (`/admin/themes`: Classic, Ocean Blue, Forest Green, Sunset Orange, Navy, Minimal, Coral, …). Nothing below hardcodes magenta: magenta is only the default theme. See *Theming*.

## What changed from the previous design system

| Before | Now |
|---|---|
| Inter everywhere | **Geist** (UI text) + **Fraunces** (titles, headings, book titles) |
| Fixed magenta brand | Accent = the server theme's `primary`; magenta only as default |
| "No gradients" | One gradient only: the soft accent wash at the top of the hero (see Book detail). Still no gradient on buttons, text or cards |
| "Cards sparingly" | Still true for lists of text. Books are shown as **3D book covers**, not as cards with borders |
| Availability as a coloured chip | A white pill with a coloured **dot** over the cover; a dot + label in the detail |

Everything else that was a hard ban stays banned: indigo/violet unless the theme says so, glassmorphism, gradient text, side-stripe borders, the logo inside a circle, light text on a light field, em dashes in copy.

## Theming

The web derives every accent shade from three theme values with `color-mix` in sRGB. Do the same in Kotlin (linear interpolation per channel in sRGB, 0–255):

```
mix(a, b, t) = a * t + b * (1 - t)      // t = share of a
```

| Token | Formula | Default (Classic theme) |
|---|---|---|
| `accent` | theme `primary` | `#D70161` |
| `accentStrong` | mix(accent, black, 0.78) | `#A8014C` |
| `accentSoft` | mix(accent, white, 0.09) | `#FBE8F1` |
| `accentSofter` | mix(accent, white, 0.05) | `#FDF2F7` |
| `accentLine` | mix(accent, white, 0.16) | `#F9D6E6` |
| `heroWash` | mix(accent, `#F7F1F3`, 0.06) | `#F5E3EA` |
| `dark` | theme `secondary` | `#111827` (web fallback `#1B1720`) |
| `button` | theme `button` (fallback accent) | `#D70262` |
| `buttonText` | theme `button_text` (fallback white) | `#FFFFFF` |
| `buttonHover`/pressed | mix(button, black, 0.85) | |

The other themes, for testing (primary · button · secondary · button_text):

- Ocean Blue `#0284C7` · `#0EA5E9` · `#0C4A6E` · `#FFFFFF`
- Forest Green `#059669` · `#10B981` · `#064E3B` · `#FFFFFF`
- Sunset Orange `#EA580C` · `#F97316` · `#7C2D12` · `#FFFFFF`
- Navy Classic `#1E40AF` · `#3B82F6` · `#1E3A8A` · `#FFFFFF`
- Minimal `#404040` · `#808080` · `#000000` · `#FFFFFF`
- Coral Warm `#F43F5E` · `#FB7185` · `#9F1239` · `#FFFFFF`

**Pairing rule (the most common bug on the web):** a filled surface always carries its paired text colour. `button` ↔ `buttonText`. `dark` ↔ white. In dark mode the dark action fill is lifted just enough to contrast 3:1 with its card, while retaining 4.5:1 with white text. `accent` is for text, icons, dots, outlines and tinted backgrounds (`accentSoft`), and is never the fill under accent-coloured text. Never put `accent` text on a `button` fill. Check every themed screen with at least Classic, Ocean Blue and Minimal.

**Contrast:** the current web uses `ThemeColorizer::readableSurface()` and readable accent tokens. Preserve each theme's hue and the `button` / `buttonText` pairing, moving the surface or accent text only as far as needed for 4.5:1. The Android palette applies the same rule in sRGB; raw `primary` remains the source for washes, rules and cover tints. This supersedes the earlier instruction to retain inaccessible light-button/white-text pairs.

**Where the app gets the theme:** the Mobile API plugin does not expose it today (`storage/plugins/mobile-api`, no theme endpoint). Until it does, ship Classic as the built-in palette, and build the colour scheme from a `ThemePalette(primary, secondary, button, buttonText)` data class so a server palette can be plugged in later. Adding the endpoint is a server change in the Pinakes repo: ask Fabio before doing it.

### Neutrals (fixed, not themed)

| Token | Value | Use |
|---|---|---|
| `ink` | `#1B1720` | titles, primary text |
| `ink2` / `ink3` | `#2D2630` / `#3D3640` | citation text / long-form body |
| `text` | `#5D5662` | secondary body |
| `muted` | `#6B6470` | labels, metadata, eyebrow captions |
| `faint` | `#6B6470` | quick-fact keys, disabled, separators |
| `bg` | `#FBFAF9` | screen background (warm off-white) |
| `surface` | `#FFFFFF` | boxes, sheets, chips |
| `soft` / `soft2` | `#F1EDEF` / `#F4F0F2` | search field fill, segmented control track |
| `line` | `#ECE8EA` | box borders, dividers |
| `line2` | `#E2DCE0` | chip and outline-button borders |
| `line3` | `#F3EFF1` | rows inside a box |
| `coverBlank` | `#2A2230` | a book without a cover |
| available dot | `#16A34A`, halo `#DCFCE7` | |
| unavailable dot | `#9CA3AF`, halo `#F1F1F3` | |

Map to Material 3: `background = bg`, `surface = surface`, `onSurface = ink`, `onSurfaceVariant = muted`, `outline = line2`, `outlineVariant = line`, `primary = accent`, `onPrimary = white`, `primaryContainer = accentSoft`, `onPrimaryContainer = accentStrong`, `secondary = dark`, `onSecondary = white`. Keep the old dark scheme as an opt-in, deriving `primary` from the theme accent lightened (mix(accent, white, 0.55)).

## Typography

- **Geist** (sans, OFL) for all interface text. **Fraunces** (serif, OFL, with italic) for: screen titles, section headings, book and article titles, big numbers in stats, the citation text, a book's subtitle (italic).
- Bundle both as TTF in `res/font/` (Google Fonts has both). The current web self-hosts Geist and Fraunces, latin + latin-ext: keep latin-ext, the catalogue has Danish and German titles.

| Role | Font | Size / line | Weight | Tracking |
|---|---|---|---|---|
| Hero title | Fraunces | 40–54 / 1.04 | 500 | −2% |
| Screen title (Catalogo, Eventi) | Fraunces | 34–44 / 1.0 | 500 | −2% |
| Section heading (Descrizione, Potrebbero interessarti) | Fraunces | 28–32 | 500 | −1.5% |
| Card book title | Fraunces | 17 / 1.2 | 500 | 0, max 2 lines |
| Body | Geist | 16–17 / 1.7 | 400 | 0 |
| UI label, button | Geist | 15 | 600 (primary) / 500 (secondary) | 0 |
| Metadata, author line | Geist | 13 | 400 | 0 |
| Eyebrow / small caps label (CONTENUTI DIGITALI, ANNO) | Geist | 12 | 600 | +8%, uppercase, `muted` |
| Badge on a cover | Geist | 11 | 600 | 0 |

In a hero title the **last word is italic, in accent colour** ("La tua biblioteca *digitale*").

## Shape, spacing, elevation, motion

- Radii: boxes 20, digital-file cards 16, cover panels 16, buttons 12, small buttons 10, inputs 10, chips and pills fully round, the 3D book 2/5/5/2 (spine side tighter).
- Screen side padding 16 on phones (web gutter 28, max width 1240 on tablets).
- Vertical rhythm between sections 48–80. Inside a box 18–22.
- One elevation: the availability box, `0 16 40 −28` in `rgba(80,20,50,.35)`. Book covers have their own book shadow (below). Nothing else floats.
- Motion: ease-out `cubic-bezier(.2,.8,.2,1)`, 300ms. A card lifts 4dp on press. Covers tilt slightly (rotateY −14°) on hover on the web; on Android use the 4dp lift only.

## Components

### Book cover (3D book)
The signature element. A 2:3 cover drawn as a book:

- the **complete** cover image, fitted inside the 2:3 frame without cropping, on an opaque `coverBlank` background (or `coverBlank` with the title in Fraunces 17 white, a 22×2 accent rule and the word "PINAKES" at the bottom when there is no cover);
- a 14dp spine on the left: a horizontal gradient of dark/light streaks (`rgba(0,0,0,.28)` → `rgba(255,255,255,.28)` → transparent);
- a page block on the right edge: 12dp wide, inset 3dp top and bottom, offset −5dp, striped `#F6F2EE`/`#E4DDD6` 1dp;
- a soft gloss (115°, white 22% fading to black 12%) and a 1dp inner hairline;
- shadow `0 1 1 rgba(0,0,0,.12)`, `0 6 10 −4 rgba(50,15,35,.25)`, `0 24 32 −16 rgba(50,15,35,.5)`.

### Book card (home, catalogue, related, wishlist)
The theme has a **card style** option, stored next to the colours as `card_style`:

- `classic` (the default after every install and upgrade): the book alone, no panel behind it. The status pill and the heart sit on the cover itself, so a blank book's title starts 40dp down, below the pill.
- `tinted`: the book on a 16dp-radius panel tinted from the cover's average colour (16% colour + 84% white), with 18/22/22 padding.

Both come with the theme, like `hero_style`. Until the Mobile API exposes them, use `classic`.

Top to bottom:

1. The 3D book. Over it, top-left at 10dp: the **status pill**, white 94% with a 6dp dot and the label: Disponibile (green), Non disponibile (grey), In prestito, Prenotato, Cercato dalla biblioteca (the wanted badge, accent). Digital-content icons (eBook, audiobook) sit inside the same pill after the label. Top-right: the **wishlist heart**, a 32dp white circle, outline heart in accent, filled when the book is in the wishlist. Signed out, it leads to login. Not shown for wanted books or in catalogue-only mode. If the item is not a book (CD, DVD, …), show the **media-type icon** too.
2. Title, Fraunces 17, 2 lines.
3. Subtitle, Geist 13 italic, `faint` (when present).
4. Author, Geist 13 `muted`, one line. No author → "Autore sconosciuto".
5. Publisher line "Editore: …", 12 `faint` (catalogue only; keep the row height when empty so rows align).
6. "Dettagli" with an eye icon, accent, 13 semibold.

Grid: 2 columns on phones, 28 vertical / 14 horizontal gap. An optional list view shows the cover small on the left and the same texts on the right.

### Buttons
- **Primary:** fill `button`, text `buttonText`, 13×22 padding, radius 12, Geist 15/600. Pressed → `buttonHover`. Leading icon allowed (keep the ones the app has).
- **Secondary (outline):** fill `surface`, 1dp `line2`, text `ink` 15/500. Pressed → border `accent`.
- **Dark:** fill `dark`, white text (Leggi PDF, Copia negli appunti, Carica altri).
- **Small:** 9×14, radius 10, 13sp.
- **Chip link** (Cerca su, Condividi): transparent, 1dp `line2`, 13sp `ink`, fully round, 5×11. Pressed → accent border and text.

### Inputs
Fill `soft` on white cards and dialogs (including search), radius 10, **no border** (Fabio's call). The neutral fill keeps an unfocused field distinguishable from its host. Edit-profile and password dialogs explicitly use `surface`, so their container never equals the `soft` field fill in either mode. Use `ink` text, `faint` placeholder. On Android, outlined fields use Material’s accent focus outline so floating labels and validation copy stay outside it; the label-free search control adds a 3dp `accentLine` ring.

### Chips
- Author chip: white pill, 1dp `line`, a 24dp circle with the initials (`accentSoft` fill, `accentStrong` text, 11/700), the name 14/500. Roles other than author follow the name: "· Traduttore".
- Keyword chip: `accentSoft` fill, `accentStrong` text, 14/500, keep the tag icon small.
- Genre path: plain text with "›" separators, tag icon in `faint`.

## Screens

### Home
1. **Hero:** an `accentSofter` → `bg` wash. Content:
   - a count badge ("2.268 titoli in catalogo");
   - the title with the italic accent last word, and the subtitle;
   - the search field (white, round, with a search icon and a primary "Cerca" button inside);
   - two quick links with icons: latest arrivals ↓, catalogue →.
   - **No background photo any more.** Beside or under the text, a **fan of up to 4 covers** (3D books, slightly rotated and overlapping). The admin picks either the latest covers or 4 chosen books.
   - The theme has a **hero style** option: `covers` (default, the layout above) or `centered` (everything centred in an 860dp column, title up to 84sp, no fan). Implement both; default to `covers`.
2. **Stats strip:** 4 cells, Fraunces 40 numbers, Geist 13 `muted` labels (Libri totali, Disponibili in accent, Categorie, Sempre online). 2×2 on phones.
3. **Latest arrivals:** eyebrow "Novità" in accent, heading, "Visualizza tutto il catalogo" outline pill, 2-column book grid, then a dark "Carica altri" pill.
4. **Genre carousels:** a heading per genre with a "Vedi tutti" outline pill, then a horizontal row of book cards (2.3 visible on phones).
5. **Features:** numbered cards 01–04 with the CMS icon, white, 1dp `line`, radius 20.
6. **Story band** (the "Πίνακες" text section): `dark` background, white text, the big word in Fraunces.
7. **Events:** eyebrow, heading, "Vedi tutti gli eventi" pill, event cards in **landscape** (16:10 image or a light `line` placeholder with a calendar icon), radius 20, title Fraunces 20, date 13 `muted`.
8. **Call to action:** a `button`-filled band, radius 24, title and text in `buttonText`, an inverted button (white fill, `button` text) and an outline one (white border and text).

### Catalogue / search
- Title block: breadcrumb, Fraunces title, subtitle.
- Filters: a sheet (phones) with sections separated by 28. Each section has a small-caps label with its icon. The sections:
  - availability (Tutti, Disponibili, Prenotati, In prestito as rows with an icon tile and a count);
  - search;
  - authors (with a filter field and "N autori");
  - publishers (chips with counts);
  - genres (tree);
  - media type;
  - publication year (range);
  - "Pulisci filtri".
- Results bar: "N Risultati", sort dropdown, Grid/List segmented control (`soft2` track, white selected segment).
- Pagination: 40dp squares radius 12, current one `ink` filled.

### Book detail
The reference layout (top to bottom on a phone):

1. **Hero** on the wash `heroWash` → `bg`. It is the theme's accent, not the cover colour.
2. Breadcrumb (Home / Catalogo / title, 13sp, `muted`, last item `ink`).
3. The big 3D cover (max 340dp wide, radius 4/8/8/4, shadow `0 2 4 rgba(0,0,0,.08)`, `0 40 60 −28 rgba(50,15,35,.55)`).
4. Kicker row: media-type pill (white, 1dp `line`, with its icon), publisher in accent, "· year" in `faint`.
5. Title, Fraunces 34–54, balanced wrap. Subtitle in Fraunces italic, `muted`.
6. Author chips.
7. Genre path.
8. **Availability box:** white, radius 20, 1dp `line`, the one shadow, padding 22.
   - Left: a 9dp status dot with a 4dp halo plus the status 16/600, and "1 copia disponibile su 1" 14 `muted` right under it (4dp gap).
   - Right: primary "Richiedi prestito" and outline "Aggiungi ai preferiti" with an outline heart (filled when added).
   - On phones the buttons go full width under the status.
9. **Quick facts:** a row of 4 (2×2 on phones) between two `line` dividers: key 12 uppercase `faint` (ANNO, PAGINE, FORMATO, ISBN), value 15/600. Other media keep their own keys (tracks, duration).
10. **Contenuti digitali** (only when the book has a digital file): eyebrow, then **one card per file**.
    - Card: white, radius 16, 1dp `line`, padding 14×16.
    - A type tile: 40×48, radius 6, `accentSoft` fill, `accentStrong` "PDF"/"EPUB" 11/700; round 40dp for audio, "MP3".
    - The file name (14/600, one line, ellipsis) over the kind ("Edizione digitale", "Audiobook", "Recensione o documento correlato").
    - Actions on the right: dark small "Leggi PDF" (opens the PDF under the card), outline small "Scarica".
    - An audiobook card holds the player under the header: 34dp tall, `soft` track.
    - Only one audio plays at a time.
11. **Cerca su** (external sources, GoodLib plugin): one line of small chip links after the digital cards (Anna's Archive, Z-Library, Project Gutenberg, each with its icon and an external-link glyph).
12. Body sections, Fraunces 32 headings (the section icons stay, small, `faint`):
    - **Descrizione:** 17/1.7, `ink3`, max ~720dp, "Leggi tutto" in accent.
    - **Dettagli libro:** key/value rows, key `muted` left, value 500 right, `line` divider. Covers ISBN-13, ISBN-10, EAN, genre, language, price, year, publication date, pages, format, dimensions, weight, series, …
    - **Parole chiave:** keyword chips.
    - Plugin sections (book club, desiderata) come after, in the same style.
13. **Informazioni libro** box (publisher, place, status, copies, added on) and **Condividi** box, both white radius 20.
    - Info rows are 14sp with `line3` dividers.
    - Share targets are chip links with icon + short name (Facebook, X, WhatsApp, Telegram, Email, Copy link) and use the system share sheet where available.
14. **Cita questo libro:**
    - style tabs (APA, Chicago, MLA, Harvard, Oxford; selected = `ink` fill, white text; others `soft`);
    - the citation in Fraunces 18 on a white radius-16 box;
    - then dark "Copia negli appunti", outline "Scarica RIS" and outline "Cita" (all styles).
15. **Potrebbero interessarti:** white band with a top `line` border, Fraunces 40 heading, a horizontal row of book cards.

### Other screens
Wishlist, loans and reservations, profile, events, periodicals (Emeroteca), archive, static pages:

- same `bg`, same title block (breadcrumb + Fraunces title + subtitle);
- white radius-20 boxes for grouped content;
- the book card wherever a book appears;
- landscape cards for events.

The account dashboard keeps its list rows (icon tile, title, author, status pill, eye button in a 40dp outline square), not the book card.

Sign-in and registration:

- the gradient wash background;
- a centred white card, radius 20, soft shadow;
- inputs radius 10, primary button radius 12;
- links in accent.

## Checks before calling a screen done

- Every element the old screen had is still there.
- Classic, Ocean Blue and Minimal themes: no accent text on a `button` fill; every filled surface uses its paired text colour.
- 360dp wide: nothing scrolls sideways; chips and buttons wrap.
- A book with no cover, no author, no subtitle and no publisher still lays out (blank book, "Autore sconosciuto", reserved rows).
- A book with an eBook **and** an audiobook shows two cards. A book with neither shows no "Contenuti digitali".

## Android implementation scope

The client applies these shared tokens to every existing screen. Home uses the real available-now/recent shelf from the Mobile API, with a searchable hero and cover fan. Catalog has a two-column grid and a compact list, preserving sort, every supported filter and cursor pagination. Account rows keep their status and action slots. Book detail keeps zoom, the date-based circulation flow, personal history, wishlist, reviews, metadata, PDF reading and audio playback. Each exposed digital asset gets a separate card.

`ThemePalette` supports both hero styles and both card styles. Defaults remain Classic / Covers until discovery supplies server theme settings. Tinted cards sample the loaded cover on a 12×12 bitmap; they do not fetch a second image. Dark mode remains opt-in and follows the app preference, including calendar colours and status dots.

The coordinated server contract supplies library Desiderata, Archives, complete digital attachments and citations. Home CMS sections/statistics/selected covers, author/publisher count facets, related-title recommendations and external-source/share targets remain outside the mobile API; the client does not fabricate them. The original web layouts remain the reference for future additions.

Fonts are bundled as static faces for reliable rendering from API 26, with Latin Extended coverage. Font sources and OFL licences are recorded in `docs/fonts/`.

## Implemented optional collections (2026-10-08)

Archives and library Desiderata are reachable from Home/Profile, preserving
Home/Catalog/Library/Wishlist/Profile. They use dedicated paginated models, not
book availability. Articles appear in Catalog and issue detail, carry analytic
and anthology fields and the shared five-style Cite dialog; author IDs connect
books and articles without conflating homonyms. Staff actions open protected
PHP pages. New routes/capabilities require the matching server plugin update;
older servers cannot supply native collections merely because the client updates.
