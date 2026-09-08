# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

Two surfaces, each in its own design language, from one product idea. Android is
a native Kotlin/Compose app on Material 3 and Material You; the web app is a
Vite-built static site with its own CSS world. Android leads: a feature lands
there first, and the web app is the place people try the idea before installing.

## Stack

Answered by the existing codebase, not by a greenfield choice.

- **Android** — Kotlin, Jetpack Compose, Material 3, Navigation Compose with
  type-safe routes, kotlinx.serialization; PDFBox-Android for PDF text and
  first-page covers, jsoup for EPUB/DOCX/FB2/HTML. AGP 9.4, Gradle 9.7.1,
  Kotlin 2.4, minSdk 26, targetSdk 36, compileSdk 37.
- **Web** — plain JavaScript modules and CSS, bundled by Vite; pdfjs-dist and
  JSZip for parsing. No framework, no component library.
- Nix flake provides Node for the web build.

## Users

People who want to read faster without losing what they read: articles they have
saved, technical documents they must get through, and books. The reading happens
on a phone, often one-handed and often in short sittings, which is why the phone
app leads.

The audience is the public, not the author. Someone who finds the app through
Obtainium and never opens the README has to understand it from the interface
alone. That rules out anything that needs the repository to explain it.

## Product Purpose

Lectern presents text one flash at a time, with the optimal-recognition-point
letter pinned to a fixed column between two rails, so the eye fixates once
instead of travelling along a line.

Success is not raw speed. It is the fastest pace at which the reader still takes
the text in, which is why the pacing spends effort on comprehension: sentence
ends hold, commas and paragraph breaks pause, long and numeric words get more
time, and abbreviations are detected so they do not fire a false full stop.

## Positioning

Most RSVP readers are either a paste-a-box-of-text toy or a subscription app
that uploads your library. Lectern is a real library reader that parses whole
books on the device: EPUB spine and table of contents, PDF outline and re-flowed
page text, DOCX, FB2, HTML, Markdown headings, and articles fetched from a link.

The pacing model, not the word flashing, is the part a neighbouring product
would have to rebuild: per-word weights, three separate punctuation pauses,
chunking that never crosses a full stop, a warm-up ramp, and abbreviation-aware
sentence detection.

## Operating Context

- Reading happens in sittings of a few minutes, phone in hand, sometimes propped
  up, sometimes in the dark. Volume-key control, hold-to-peek, break reminders
  and the four palettes all come from that scene.
- Books arrive from a file picker, from another app via "open with" or share,
  or as a pasted link.
- The reader returns to a book many times, so position, per-book speed and
  bookmarks have to survive both leaving the app and Android killing it.
- Distribution is GitHub releases read by Obtainium. A version tag builds a
  signed APK in CI and publishes it; the version code rises with the tag so
  Obtainium sees the update. No store review gates a release, and no store
  policy constrains permissions or cadence.
- The web app deploys to GitHub Pages from `main` and is live at
  https://heerd.dev/lectern/.

## Capabilities and Constraints

Confirmed and shipped:

- Formats: PDF, EPUB, TXT and Markdown on both surfaces; DOCX, FB2, HTML and
  article links on Android.
- Reading: 60–1200 wpm, chunks of one to three words, warm-up ramp, rewind on
  resume, break reminders, stop at chapter end, context line, four typefaces
  including Atkinson Hyperlegible, four word sizes, four pivot markings.
- Library: covers, reading positions, per-book speed, bookmarks, tags,
  archiving, filters, backup and restore to a zip.
- Control: tap, tap zones, swipe, hold to peek, volume keys, hardware keyboard,
  launcher shortcuts for recent books.
- 51 unit tests cover the parsing and pacing logic; `core/` and `data/` hold no
  Compose UI so they stay testable.

Durable constraints future work must not break:

- **Everything on the device.** No accounts, no telemetry, no server, no
  uploaded files. `INTERNET` exists only to fetch a link the reader pastes, and
  that exception is stated in the interface, not just the README.
- **The pivot reading model.** One flash at a time with the ORP letter held in a
  fixed column. Features may surround it; they may not replace it.
- **Free and open source, GPL-3.0.** No paid tier, no ads, no proprietary
  dependency. Any future sync or trainer feature has to hold this line.

Open and explicitly undecided:

- No `LICENSE` file exists yet. GPL-3.0 is the decision; the file has not been
  written, and nothing should claim a licence until it is.
- English only. Strings are inline in Compose and HTML rather than in string
  resources; translation is a known, deliberate gap. The sentence and
  abbreviation rules are English-specific, and the pivot model assumes
  left-to-right alphabetic words, so non-Latin scripts would need a different
  reading rule, not just translated strings.
- No first-run onboarding exists. A public audience arriving from Obtainium
  meets the shelf with no explanation of what RSVP is or why the word sits where
  it does.
- The visual identity is not pinned. The current lamp-lit world (monospace type,
  warm near-black, parchment ink, one amber accent, the pivot `e` in the
  wordmark) is the incumbent implementation and was deliberately left out of the
  binding constraints, so it is open to replacement.

## Brand Commitments

- The name is **Lectern**. The wordmark colours the pivot letter `e`, matching
  what the reader sees on the stage.
- Voice, in the interface and the README: short declarative sentences, plain
  technical words, no marketing register.
- Assets in the repository: `docs/branding/` (wordmark in light and dark, hero),
  `docs/screenshots/`, and the adaptive launcher icon with its monochrome layer
  under `android/app/src/main/res/`.

## Evidence on Hand

- A working, published product: web app live at https://heerd.dev/lectern/ and
  Android v0.1.0 on GitHub releases as a signed APK.
- Real screenshots from an emulator running the built app, in
  `docs/screenshots/`.
- No users, downloads, reviews, testimonials, benchmarks or press exist. The app
  was released today. Nothing may claim adoption, ratings, or measured reading
  gains, and no reading-speed research is cited in the repository.

## Product Principles

1. **The word is the only bright thing.** Everything else on the reading screen
   recedes while words are moving.
2. **Comprehension is the metric.** A change that raises words per minute while
   lowering what the reader retains is a regression.
3. **The device is the whole system.** Parsing, storage and settings stay local;
   a feature that needs a server needs a different product.
4. **It must explain itself.** The audience arrives without the README, so
   meaning belongs in the interface.
5. **The reader sets the pace.** Speed, pauses, chunking, warm-up and stopping
   rules are theirs to tune, with defaults that work untouched.

## Accessibility & Inclusion

No external standard has been adopted. Product-specific needs already
established:

- Atkinson Hyperlegible ships as a typeface option, drawn for low vision, and
  the type scale is Material's, in `sp`, so system font size is respected
  outside the stage.
- The pivot letter can be marked by colour, underline, bold or not at all, for
  readers who find a coloured letter distracting.
- Four palettes cover OLED black and a dim red night mode; dark theme is a
  designed scheme, not an inversion.
- Motion is central to the product, so any future work must offer a way to read
  that does not depend on flashing text.
