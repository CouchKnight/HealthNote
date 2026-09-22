# Implementation notes

What exists so far, how it was verified, and where it departs from the Claude Design mockups
(`HealthNote App` and `HealthNote Document`) and why.

## Modules

| Module | What it is | Status |
|---|---|---|
| `:core-model` | Domain types and rules: schedules and slots, dose logging (nearest-slot match, idempotent per slot), adherence and streaks, sleep-night attribution, month sleep stats, `MonthReport`, shared text formats, the design's September 2026 sample data | Built and unit-tested (23 tests) |
| `:core-render` | The document as display lists. Page geometry is computed from `RenderConfig` (month + schedules + target) only; data is painted into it. Includes a small TrueType metrics reader so layout needs no PDF library | Built and tested (82 tests, incl. the golden geometry test) |
| `:core-render-pdf` | `PdfWriter`: display list → PDF with PDFBox 2.0.27. 1404 × 1872 MediaBox, DeviceGray, embedded font subsets, `/GoTo` links with fit-width destinations, `/Outlines`, deterministic bytes | Built and tested (6 tests, incl. parse-back of every link) |
| `:core-render-android` | The same `PdfWriter` source recompiled against pdfbox-android (import prefix rewritten at build time) | **Not built here.** The rewritten source was compiled against pdfbox-android's `classes.jar` in a throwaway JVM project, which is as far as it can go without an Android SDK |
| `:app` | Compose UI: Today, Medications, schedule editor, Sleep, Document, NFC tag writer, confirm sheet, toast, bottom tabs | **Written but never compiled** — see below |

`:core-data`, `:core-health`, `:core-sync` and `:core-work` from DESIGN.md §2 do not exist yet.
The app talks to them through interfaces in `app/.../data/AppGraph.kt`, currently backed by
in-memory fakes seeded with the sample month and a clock pinned to 09:31 on Tue 22 Sep 2026
(the moment the mockups depict).

### Why the app is unverified

This work was done in a sandbox whose network policy blocks `maven.google.com` and
`dl.google.com`, so the Android Gradle Plugin, AndroidX, Compose and the SDK could not be
downloaded. `settings.gradle.kts` includes `:app` and `:core-render-android` only when an SDK is
configured, so the JVM modules still build anywhere. Expect a round of compile fixes the first
time `:app` is built on a machine with the Android SDK.

## Building

```sh
./gradlew test                          # JVM modules: model, layout, PDF
./gradlew :core-render-pdf:renderSample # writes core-render-pdf/build/sample/HealthNote-2026-09.pdf
./gradlew :app:installDebug             # needs ANDROID_HOME or sdk.dir in local.properties
```

`./gradlew :core-render-pdf:test` also writes PNG previews of every page to
`core-render-pdf/build/previews/`.

## How the geometry invariant is enforced

DESIGN.md §1.3 requires that ink written on the tablet never lands on different content after a
regeneration. Each page class builds its **frame** (rules, labels, grids, nav, link rects) in its
constructor from `RenderConfig` alone, and only `paint(report)` sees the data, so the frame
cannot depend on data. `GeometryTest` then renders the same month with empty, partial, full and
finished data and asserts that frames and links are identical while the painted layers differ.
It also sweeps every month of 2026 with 1–6 schedules and checks that nothing leaves the page,
overlaps the footer, draws a stroke under 2 units or uses a character missing from its font.

Consequences that shaped the layout:

- **Sleep detail** has a row for every night of the month from day one. The mockup listed nights
  only up to today, which would push the Mean row down every day.
- **Hub "Today"** always has as many ruled rows as the busiest day of the month, so weekends do not
  change the page.
- **Schedules are config.** Adding or editing one mid-month changes the frame. The app should
  apply schedule edits to the document from the next month, or warn first. This is not built yet.

## One emitter source, two PDF libraries

pdfbox-android 2.0.27.0 is a port of Apache PDFBox 2.0.27 with the package renamed to
`com.tom_roush.pdfbox`. `:core-render-pdf` is compiled and tested against Apache PDFBox 2.0.27.
`:core-render-android` copies the same source at build time, rewriting only
`org.apache.pdfbox.` → `com.tom_roush.pdfbox.`. The API surface used (content stream, fonts,
link annotations, destinations, outline, catalog) was checked with `javap` to be identical in
both jars. Keep `PdfWriter` free of `java.awt` and other JVM-only APIs.

## Departures from the mockups

Data and arithmetic

- **Slot counts are computed.** The app said "a month is 96 slots"; the schedule set gives 74 in
  September 2026. The tracker rail said "44 slots still ahead"; the figure is 22.
- **Skipped doses are excluded from adherence** (taken ÷ (taken + missed)), as decided for this
  build. The mockup's tracker rail counted Vitamin C's skip on Thu 3 as a miss.
- **Streaks and best/worst nights are computed** from the sample data, so they differ from the
  mockups' illustrative numbers (Vyvanse streak 7, not 12; best night Fri 4 at 8:32).
- **The hub's "sleep through …"** names the latest night actually present. The mockup said
  "Sat 19" while showing Tue 22's night.
- **The schedule editor's slot summary** counts the month's real slots. The mockup used
  `days × 4 + 2`.

E-ink document

- **En dash instead of "→"** in "23:24–06:36". Neither Libre Franklin nor Libre Caslon has an
  arrow glyph; the browser silently fell back to a system font, but PDFBox throws. Any other
  character missing from a font is drawn as "?" instead of failing the publish.
- **"Month to date" → "To date"** on the tracker rail. At 30 units with 0.1 em tracking it
  overflowed the 271-unit rail; the HTML just wrapped it.
- **Axis labels sit on their gridlines and columns.** The mockup spaced the sleep chart's y
  labels and the strip's day ticks evenly (`justify-content: space-between`), which misaligned
  them — the same class of bug fixed for 1e in the design chat.
- **A 6 h gridline** was added; the mockup drew 2, 4, 8 and 10 only.
- **Six-row months** shrink the calendar cells to stay clear of the footer, and the detail table
  shrinks its rows to fit 31 nights (30-unit text rather than 32).
- **Missing nights keep a black date** in the detail table, because the date column is part of the
  static frame; the dashes are grey.
- **The Notes page** was not in the mockups. It uses the same header and footer, with 2-unit ruled
  lines every 88 units, and is byte-identical on every regeneration.

Phone app

- **`#8a8da3` removed everywhere.** The design chat says it was replaced by `#62667e`, but three
  uses survived in the mockup (inactive tabs, unselected day chips, gap-night rows).
- **Nav is the bottom tab bar** and page 3 is the line chart (1d), as chosen. The segmented-control
  nav and the bed-to-wake chart (1e) were not built. The "confirm before logging" and "sync line"
  tweaks are flags in `UiState`, both on.
- **Confirm sheet footnote** says a skip shows a *grey* marker on the tablet. The mockup said
  "hollow", but hollow means missed in the final e-ink legend.
- **Battery optimisation** is checked for real with `PowerManager`; when not exempt the row links to
  the system setting instead of claiming "Granted".
- **The Document screen's file size** comes from rendering the real PDF on the device.

## Not done yet

In build order (DESIGN.md §7): the Supernote Cloud overwrite spike; Room storage with schedule
versioning; the Glance widget, dose notifications and the NFC `NDEF_DISCOVERED` activity (the
tag-writer screen exists, the NFC I/O does not); the Health Connect reader and permissions;
`SupernoteCloudClient` and `BrowseAccessClient`; the WorkManager jobs. The Manta layout profile
exists as a setting but changes nothing yet.
