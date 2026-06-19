# Pinakes Android — Design system (committed)

Color strategy: **Restrained**. Tinted-neutral surfaces + **one accent** (magenta).
Magenta carries primary actions, current selection, and key status ONLY. No
indigo/violet anywhere. Neutrals are cool and clean (faint mauve tint, never
brown). No gradients. Light is the default theme.

These are the ONLY colours allowed in the app. Do not invent new ones. Reference
them via `MaterialTheme.colorScheme.*`; the availability tints below are the only
extra semantic colours.

## Brand
- Magenta `#D70161` — primary / accent. Deep `#B0235A` for pressed.
- The logo is the magenta wordmark/palm. Show it bare (no circle, ring, or card).

## Light scheme (default)
- primary `#D70161` · onPrimary `#FFFFFF`
- primaryContainer `#FFD9E5` · onPrimaryContainer `#3E001C`  (selected nav indicator, selected chips)
- secondary `#6C5C62` · onSecondary `#FFFFFF` · secondaryContainer `#F3E6EB` · onSecondaryContainer `#25141B`  (muted mauve-grey, NOT purple; used rarely)
- tertiary `#B0235A` (magenta family) · onTertiary `#FFFFFF`
- error `#BA1A1A` · onError `#FFFFFF` · errorContainer `#FFDAD6` · onErrorContainer `#410002`
- background `#FCFAFB` · onBackground `#1C1B1C`
- surface `#FFFFFF` · onSurface `#1C1B1C`
- surfaceVariant `#ECE9EB` · onSurfaceVariant `#5A585B`
- surfaceContainerLowest `#FFFFFF` · Low `#F8F5F7` · Container `#F3F0F2` · High `#EDEAEC` · Highest `#E7E4E6`
- surfaceDim `#E0DDDF` · surfaceBright `#FCFAFB` · surfaceTint `#D70161`
- outline `#C7C4C7` (field borders) · outlineVariant `#E6E2E5`
- inverseSurface `#313031` · inverseOnSurface `#F3EFF1` · inversePrimary `#FFB1C8` · scrim `#000000`

## Dark scheme (opt-in)
- primary `#FFB1C8` · onPrimary `#5E1133`
- primaryContainer `#83254A` · onPrimaryContainer `#FFD9E5`
- secondary `#D7BFC6` · onSecondary `#3B2930` · secondaryContainer `#534149` · onSecondaryContainer `#F3E6EB`
- tertiary `#FFB1C5` · onTertiary `#5E112B`
- error `#FFB4AB` · onError `#690005` · errorContainer `#93000A` · onErrorContainer `#FFDAD6`
- background `#141315` · onBackground `#E6E1E3`
- surface `#141315` · onSurface `#E6E1E3`
- surfaceVariant `#48464A` · onSurfaceVariant `#C9C5CA`
- surfaceContainerLowest `#0F0E10` · Low `#1C1B1D` · Container `#201F21` · High `#2B292C` · Highest `#363437`
- surfaceDim `#141315` · surfaceBright `#3A383B` · surfaceTint `#FFB1C8`
- outline `#928F94` · outlineVariant `#48464A`
- inverseSurface `#E6E1E3` · inverseOnSurface `#313031` · inversePrimary `#D70161` · scrim `#000000`

## Availability tints (the only extra semantic colours)
- Available (green): light container `#C7EBD1` / on `#0B5733`; dark container `#14442B` / on `#A9E6C0`
- Limited (amber): light container `#FBE2B3` / on `#6B4E00`; dark container `#4A3500` / on `#F0C36B`
- Unavailable (red): use `error` / `errorContainer`.

## Typography
- One family: **Inter** (already bundled). Headings/body/labels/data all Inter.
- Fixed scale, ratio ~1.2. Weight contrast for hierarchy (Regular 400 / Medium 500 / SemiBold 600). No display fonts in labels or data.

## Layout & components
- **Cards sparingly.** Lists are rows with dividers or generous spacing, not a wall of elevated cards. Never nest cards.
- Vary spacing for rhythm (e.g. 8 / 12 / 16 / 24 / 32). Not the same padding everywhere.
- **Bottom nav**: selected item = magenta (indicator = primaryContainer, selected icon+label = primary/onPrimaryContainer). Unselected = onSurfaceVariant. NEVER purple.
- **Text fields**: outlined, `outline` border, `onSurface` text, `onSurfaceVariant` label/placeholder, magenta (`primary`) only on focus. Must be clearly readable on white.
- **Book metadata block** (ISBN, year, pages, language, format): a clean key/value list, label in `onSurfaceVariant` small-caps/medium, value in `onSurface`. Not cramped, not a grey blob.
- **Genre**: show as a magenta-tinted chip (primaryContainer) in the detail; keep the genre filter usable.
- Buttons: filled magenta primary (onPrimary text); secondary = outlined/text in `primary` or neutral. One button shape throughout.

## Motion
150–250ms, ease-out. State changes only. No page-load choreography, no gradient sweeps.

## Hard bans (in addition to the above)
Gradients · indigo/violet · brown surfaces · gradient text · glassmorphism · side-stripe borders · logo inside a circle/ring · light text on a light field · em dashes in copy.
