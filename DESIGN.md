---
name: Lectern
description: A speed reader that holds one word in one place, on the web and on Android.
colors:
  amber: "#ffb527"
  amber-deep: "#7a5200"
  near-black: "#171310"
  surface-raised: "#211c15"
  parchment: "#f4eedd"
  muted-ink: "#ab9f85"
  faint-ink: "#7f755f"
  hairline: "#3b3327"
  paper: "#fbf6ec"
  paper-ink: "#211c15"
typography:
  stage:
    fontFamily: "Menlo, ui-monospace, SF Mono, monospace"
    fontSize: "clamp(24px, calc(100vw / 9.3), 72px)"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "normal"
  display:
    fontFamily: "Menlo, ui-monospace, SF Mono, monospace"
    fontSize: "clamp(2.75rem, 7vw, 4.5rem)"
    fontWeight: 400
    lineHeight: 1.1
    letterSpacing: "0.01em"
  title:
    fontFamily: "Menlo, ui-monospace, SF Mono, monospace"
    fontSize: "22px"
    fontWeight: 400
    lineHeight: 1.4
  body:
    fontFamily: "Menlo, ui-monospace, SF Mono, monospace"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Menlo, ui-monospace, SF Mono, monospace"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: "0.1em"
rounded:
  hairline: "4px"
  card: "8px"
  panel: "12px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "20px"
  xl: "24px"
components:
  button-primary:
    backgroundColor: "{colors.amber}"
    textColor: "{colors.near-black}"
    rounded: "{rounded.card}"
    padding: "12px 24px"
  card-outlined:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.parchment}"
    rounded: "{rounded.card}"
    padding: "12px 20px"
  card-continue:
    backgroundColor: "{colors.amber-deep}"
    textColor: "{colors.parchment}"
    rounded: "{rounded.card}"
    padding: "16px"
  stage-rail:
    backgroundColor: "{colors.hairline}"
    height: "1px"
    width: "88%"
  transport-play:
    backgroundColor: "{colors.amber}"
    textColor: "{colors.near-black}"
    size: "64px"
    rounded: "999px"
---

# Design System: Lectern

## Overview

**Creative North Star: "The Reading Rail"**

Everything in Lectern is arranged around one vertical column of screen. Two
hairlines and a tick mark it, the pivot letter of every word lands on it, and no
other element is allowed to compete with it while words are moving. The rail is
not decoration; it is the product's mechanism drawn at full size.

Around that column the interface is deliberately thin. Type is monospace so
every word occupies a predictable width, surfaces separate by tone rather than
by ornament, and colour appears in one place at a time. When reading starts, the
controls fade to 40% and the word is the only thing at full strength. That fade
is the system's strongest gesture, and it is a fade, not a transition to a
different screen: the reader can always reach a control without leaving the text.

Two implementations share this world. On Android it is expressed through
Material 3 — colour roles, the type scale, tonal surfaces, Material components —
with Material You taking the palette from the wallpaper and four hand-made
palettes underneath it. On the web it is a hand-written CSS world with a fixed
lamp-lit palette. The rail, the pivot, the fade and the monospace measure are
what make them the same product.

**Key Characteristics:**
- One reading column, marked by rails and a tick, that nothing else may cross
- Monospace everywhere, so word width is predictable and numbers align
- One accent at a time, spent on the pivot letter first
- Chrome that fades to 40% while reading and returns on pause
- Depth by tone, never by ornament
- Dark is the working default; light is a real scheme, not an inversion

## Colors

A lamp-lit study: warm near-black grounds, parchment type, and a single amber
that the reader's eye is trained to find.

### Primary
- **Reading Amber** (`{colors.amber}`): the pivot letter, the play control, the
  progress fill, and selected states. On Android this is the Material `primary`
  role, so with Material You on it becomes the wallpaper's colour and the pivot
  follows. Never used as a large fill behind text.
- **Deep Amber** (`{colors.amber-deep}`): the same hue at reading weight on light
  grounds, and the container fill behind "Continue reading".

### Neutral
- **Near Black** (`{colors.near-black}`): the ground for the dark scheme, warm
  rather than neutral, so amber sits in the same family rather than on top of it.
- **Raised Surface** (`{colors.surface-raised}`): cards, sheets and the top bar,
  one tonal step above the ground.
- **Parchment** (`{colors.parchment}`): body and stage type on dark grounds.
- **Muted Ink** (`{colors.muted-ink}`): secondary type, labels, and the context
  line's unread words.
- **Faint Ink** (`{colors.faint-ink}`): the rail tick, hint lines, and anything
  that should be legible only when looked at directly.
- **Hairline** (`{colors.hairline}`): the rails and every 1px divider.
- **Paper** / **Paper Ink** (`{colors.paper}`, `{colors.paper-ink}`): the light
  scheme's ground and type, warm to match.

### Named Rules
**The One Accent Rule.** Amber belongs to the pivot letter first. Any other use
(play control, progress, selection) must be one element the eye can find without
searching. Two amber elements competing on one screen is a defect.

**The Warm Neutral Rule.** Greys are warm (hue held with the amber family).
A neutral grey next to this amber reads as a bug, not a choice.

## Typography

**Display Font:** Menlo / ui-monospace stack
**Body Font:** the same monospace stack
**Reader's Choice:** monospace (default), system sans, serif, or Atkinson
Hyperlegible on Android; the reader picks, and the choice applies to the whole app

**Character:** One typeface doing every job. Monospace is not a costume here: the
stage depends on predictable glyph advance to hold the pivot letter in a fixed
column, and the same face carries the shelf and settings so the app reads as one
instrument. Numbers (wpm, ×2.7, percentages) align because of it.

### Hierarchy
- **Stage** (400, sized to fit, line-height 1): the flashed word or chunk. Size
  is computed, not chosen: the type shrinks so that twice the longer half of the
  chunk still fits inside the rails.
- **Display** (400, `clamp(2.75rem, 7vw, 4.5rem)`): the web wordmark only.
- **Title** (400, 22px): book titles, sheet headings, the shelf wordmark.
- **Body** (400, 16px, 1.5): settings descriptions, paragraph view, the context
  line at label size.
- **Label** (400, 12px, +0.1em tracking): hints, time remaining, chapter meta,
  format lists. Tracking is the only place letter-spacing is used.

### Named Rules
**The Fixed Column Rule.** The pivot letter's centre sits at 50% of the stage
width. Type size is derived from the chunk, never the other way round; a word
that would overflow shrinks the type rather than moving the column.

**The Italic Title Rule.** A book's own title is italic in the reader's top bar.
It is the one italic in the system, and it marks "this text belongs to the book,
not to the app".

## Layout

The reader is a vertical stack: top bar, stage taking all remaining height,
hint, scrubber, transport, speed, pause dial. On a screen wider than it is tall
the stack splits into two columns, stage at 1.4 weight beside the controls, so
the rail keeps its height in landscape.

The shelf is a grid with `Adaptive(minSize = 320dp)` columns: one column on a
phone, more as the window grows, with the continue card, the importer, the
filter row and the section label spanning the full width at any size. Content
padding is 20dp horizontal; cards are separated by 12dp vertically and 16dp
horizontally.

Settings is a single column of 24dp-inset rows grouped by section label, each
row a title, an inline value, the control, and a description in body-small.
Sections are separated by a divider, never by a card.

Edge-to-edge on Android with insets applied; the reader adds
`navigationBarsPadding()` so controls clear the gesture bar. Sheets and dialogs
take the same insets.

## Elevation & Depth

Material's own elevation, used at its quietest. Surfaces separate by tonal step
(`surfaceContainerLow` through `surfaceContainerHighest`, mixed from the ground
toward a raised tone in each palette), with Material's standard shadows only
where its components bring them: bottom sheets, dialogs, menus. Nothing in the
app draws a shadow of its own, and the reading stage draws none at all.

### Named Rules
**The Undisturbed Stage Rule.** The reading surface carries no elevation, no
border and no card. It is the page ground with two hairlines on it.

## Shapes

Corners are gentle and small: 4px on word chips and the demo stage clip, 8px on
cards and buttons, 12px on the largest panels, and a full pill on the transport
button. Nothing is a sharp rectangle except the rails, which are 1px lines, and
nothing is a circle except the single play control.

Borders are 1px and used sparingly: the outlined cards on the shelf, the rails,
and dividers between settings sections. A border and a tonal fill never appear
on the same element on the dark scheme.

## Components

### Buttons
- **Shape:** gentle 8px on filled and text buttons; the transport control is a
  64dp circle.
- **Primary:** amber fill with near-black type (`{components.button-primary}`),
  used once per screen: "Read the rest" on the welcome, "Read this" in the paste
  sheet, the play control in the reader.
- **Text buttons** carry every secondary action (Paste text, Read a link, sheet
  actions). They take `onSurface`, not the accent, so the amber stays rare.
- **Hover / Focus:** Material ripple and state layers; the web uses a 2px amber
  focus ring at 2px offset.

### Chips
- **Style:** Material `FilterChip`, outlined when unselected, filled with the
  secondary container when selected.
- **Use:** the shelf filter row (All / Reading / Finished / Archived / tags) and
  every either-or setting (theme, palette, typeface, word size, pivot marking).

### Cards / Containers
- **Corner Style:** 8px.
- **Outlined card** is the default for a book on the shelf and for the importer:
  1px outline, `surfaceContainerLow` fill.
- **Filled card** appears twice only: the continue-reading card (primary
  container) and the first-run welcome (`surfaceContainerLow`).
- **Internal Padding:** 12–20dp; a book row is 12dp top and bottom with a 44×62dp
  cover, a 12dp gap, then the text column.

### Inputs / Fields
- **Style:** Material `OutlinedTextField`, 1px outline, no fill.
- **Focus:** the outline takes the accent and thickens, per Material.
- **Placement:** paste in a bottom sheet, rename and tags in dialogs, links in a
  dialog with a supporting line stating the network use.

### Navigation
- Top app bar only; no bottom bar and no drawer. The shelf's bar carries the
  wordmark, sort and settings; the reader's is centre-aligned with the book
  title, time remaining, and four actions.
- Back always leaves: system Back, the gesture, and the arrow do the same thing.

### The Stage (signature)
Two 1px rails 88% of the width apart, each with a 9dp tick at the centre
column. Between them, three text runs measured separately and placed so the
pivot glyph straddles the centre. The pivot is marked by colour, underline, bold
or nothing, at the reader's choice. Rails can be hidden; the column stays.

### The Context Line (signature)
The current sentence under the stage, at label size, unread words at 55% opacity
and the current chunk in the accent. It is the only place two type colours meet
in one line.

## Do's and Don'ts

### Do:
- **Do** keep the pivot centred at 50% of the stage and derive type size from the
  chunk, shrinking to fit rather than moving the column.
- **Do** fade the reader's chrome to 40% while words are moving, and bring it
  back at full strength on pause.
- **Do** spend amber on one element per screen, the pivot first.
- **Do** state a value in numbers next to its control (`300 wpm`, `×2.7`,
  `2 words`), in monospace so it does not shift as it changes.
- **Do** use Material components as they come and theme them through colour
  roles, so Material You and the four palettes both work untouched.
- **Do** give every setting a description in the product's voice: what it does,
  and why a reader would want it.

### Don't:
- **Don't** draw a shadow, a border or a card around the reading stage.
- **Don't** put a second accent-coloured element beside the pivot while reading.
- **Don't** introduce a proportional face for the stage without keeping the
  fixed-column guarantee; the reader's typeface choice already covers taste.
- **Don't** use a neutral grey: greys here are warm and belong to the ground.
- **Don't** add a bottom navigation bar. Three destinations on one back stack is
  the whole structure.
- **Don't** interrupt reading with a dialog. Transient feedback is a snackbar;
  anything larger waits for a pause.
