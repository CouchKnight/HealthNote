# HealthNote — design

An Android app that reads health data from Samsung Health via Health Connect, tracks
medication adherence itself, and publishes an auto-updating, navigable document to a
Supernote e-ink tablet.

- **Primary device:** Supernote Nomad (A6X2, 7.8", 1404 × 1872, 300 ppi)
- **Also supported:** Supernote Manta (A5X2, 10.7", 1920 × 2560, 300 ppi)
- **Scope now:** personal sideloaded APK. Publishable later — see [Path to a Play release](#path-to-a-play-release).

---

## 1. Research findings that shaped this design

Four findings are load-bearing. Each is sourced, because getting any of them wrong
invalidates a large part of the build.

### 1.1 Health Connect has no medication data type

Health Connect's catalogue covers Activity, Body Measurement, Cycle Tracking, Nutrition,
Sleep, Vitals and Wellness. There is **no medication or medication-adherence record type**
([Health Connect data types][hc-types]). Samsung's own Health Data SDK type list is the same
shape — steps, exercise, sleep, nutrition, vitals — with no medication entry
([Samsung Health data types][sh-types]).

Samsung Health *does* ship an in-app Medications feature, but it is not exposed through
Health Connect or the Samsung Health Data SDK. The only way to get it out of the app is the
manual GDPR-style **Download personal data** export, which is e-mailed as a CSV zip and can
take up to 24 hours to arrive ([Samsung support][sh-export]).

**Consequence: "automatically update a habit tracker page to show that medicine is taken"
cannot be driven from Samsung Health.** HealthNote owns medication logging itself. Sleep is
unaffected and comes from Health Connect as planned.

### 1.2 Generate PDF, not `.note`

The `.note` format is reverse-engineered. Readers are mature
([jya-dev/supernote-tool][sn-tool], [sntool][sntool]), and the format's metadata keys for
links, titles and keywords are known (`LINKTYPE`, `LINKRECT`, `LINKFILE`, `PAGEID`,
`TITLELEVEL`, `KEYWORD`, …). But **writers are partial and undocumented**:

- The only published link-record writer is a single function, `generate_link_json()`, in
  [mmujynya/snex][snex].
- The only published RATTA_RLE bitmap encoder, [dbmcco/paia-supernote][paia], deliberately
  gates real writes behind a probe because generated cross-notebook links were **never
  confirmed to survive upload, device sync and a tap on-device**.

Meanwhile the device reliably honours two PDF constructs:

- **Internal `/GoTo` link annotations.** The entire commercial hyperlinked-planner ecosystem
  for Supernote depends on this working ([hyperpaper][hyperpaper], [onplanners][onplanners]).
- **The `/Outlines` tree**, which populates the device's native Contents sidebar. Supernote's
  own documentation notes that the Contents page is blank when the document has no
  accessible table of contents ([Contents, bookmarks and annotations][sn-contents]).

**What we give up.** Keywords, titles and stars are `.note`-only features driven by on-device
handwriting recognition — you lasso ink and tap the keyword icon
([Using titles, keywords and stars][sn-keywords]). A generated PDF cannot author them. The
substitutes are the `/Outlines` tree and an on-page link hub; the user can still add their own
handwritten keywords to the PDF on-device afterwards.

**Unverified — do not depend on it.** External `/URI` link annotations inside an imported PDF.
Every "web link" reference found refers to Supernote's own lasso-created links inside `.note`
files, not `/URI` annotations in an imported PDF. No navigation in this design uses them.

### 1.3 Overwriting a PDF in place is the main correctness hazard

Supernote never writes ink into an imported PDF. The file on disk stays byte-identical and
handwriting lives in a **`.mark` sidecar** beside it, composited at render time
([Sioyek integration][sioyek], [SupernoteSharp][snsharp]). Deleting the `.mark` makes the PDF
plain again.

No authoritative source documents what happens when the PDF is replaced under the same
filename. The architecture implies the `.mark` re-attaches by path and replays strokes at the
same page coordinates — so ink is not lost, it is **re-composited onto different content**,
which is worse than losing it. The community pattern for daily-refreshed documents is
therefore one immutable dated file per day ([NYT crossword gist][crossword]).

**Design response — the geometry invariant.** One PDF per month,
`HealthNote-YYYY-MM.pdf`, regenerated daily, where **layout geometry is a pure function of
(month, config) and never of the data**. Only what is painted *inside* fixed cells changes, so
ink stays aligned for the whole month; a new month is a new file. This is enforced by a golden
test (§6).

### 1.4 Supernote Cloud is the only route with an automatic device-side pull

| Route | Device-side | Verdict |
|---|---|---|
| **Supernote Cloud** | **Auto Sync — automatic** | Daily path |
| Dropbox / Google Drive / OneDrive | Manual swipe-down + tap sync, every time | Unusable for automation |
| Browse & Access (Wi-Fi, port 8089) | Must be enabled by hand each time; **cannot overwrite or delete** | "Send now" button only |
| NetVirtualDisk / WebDAV | A manual file browser, not a sync engine | No |
| Self-hosted Private Cloud (fw 3.25+) | Auto Sync applies — it speaks the official protocol | Power-user option, needs a server |

Auto Sync is Supernote-Cloud-only; third-party clouds require a manual sync tap
([eWritable firmware notes][ewritable-324], [Supernote Auto Sync announcement][sn-autosync]).
This contradicts the marketing framing, so it is worth re-verifying on your own firmware.

The cloud API is unofficial but well mapped:

1. `POST official/user/query/random/code` → `randomCode` + `timestamp`
2. `POST official/user/account/login/new` with the password hashed as
   **`SHA256(MD5(password) + randomCode)`** → token
3. `x-access-token: <token>` on every subsequent call
4. Upload is three-phase: `file/upload/apply` → **HTTP PUT direct to S3** → `file/upload/finish`

Reference implementations: [bwhitman/supernote-cloud-python][sncloud-py] (clearest auth and
upload flow) and [julianprester/sncloud][sncloud]. Note `sncloud` **cannot delete, move or
rename** — see the spike in §5.

Browse & Access is a genuine HTTP server: `OPTIONS` to verify, `GET` to list (HTML, scraped),
and **multipart `POST <base>/<dir>` with field key `file`** to upload, INBOX only, no
overwrite ([jbchouinard/supernote-sync][snsync]).

**Accepted risks:** the cloud API is reverse-engineered and can break without notice; it
requires holding the user's Supernote **password** (not a token) to derive the login hash; and
the device's pull is opportunistic — on wake, on Wi-Fi, on entering Files — so budget for
"appears within hours", not "appears at 06:00".

---

## 2. Architecture

Single Gradle project, Kotlin + Compose, min SDK 28 / target 35.

```
:app          Compose UI, onboarding, permission flows, settings, NFC tag writer
:core-model   Domain types (DoseEvent, NightSleep, MonthReport) — no Android deps
:core-data    Room (schedules + dose events), DataStore settings, Keystore-backed creds
:core-health  Health Connect reader + sleep-night attribution
:core-render  PDF generation: layout engine, chart, link + outline emission
:core-sync    SupernoteCloudClient, BrowseAccessClient, upload orchestration
:core-work    WorkManager workers, dose alarms, widget + notification receivers
```

Key dependencies: `androidx.health.connect:connect-client`,
**`com.tom-roush:pdfbox-android`** (Apache 2.0 — chosen over iText, which is AGPL and would
block a Play release), Room, WorkManager, `androidx.glance:glance-appwidget`, OkHttp,
kotlinx-serialization.

---

## 3. Reading sleep from Samsung Health

**Permissions.** `READ_SLEEP`, plus two that are not optional here:

- **`PERMISSION_READ_HEALTH_DATA_HISTORY`** — without it the read window is only the 30 days
  preceding the permission grant, and reading a single older record errors. A month chart with
  trend context needs more ([Health Connect read data][hc-read]).
- **`PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`** — the daily refresh runs with no UI. Gate it
  behind `HealthConnectFeatures.getFeatureStatus`, since older Health Connect builds lack it.

Optional extras for header tiles: `READ_STEPS`, `READ_RESTING_HEART_RATE`.

**Reading.** Use **raw `readRecords`** for `SleepSessionRecord` with `pageToken` pagination —
`aggregate()` exposes `SLEEP_DURATION_TOTAL` but discards the stage breakdown the detail page
needs. Use `aggregateGroupByPeriod` only for the cheap monthly summary numbers; note it
requires `LocalDateTime` and throws `IllegalStateException` if given an `Instant`
([aggregate data][hc-aggregate]). Use the Changes API (`getChangesToken` / `getChanges`) for
incremental refreshes, falling back to a full month read when the token expires.

**Night attribution.** `SleepNightAttributor` maps a session to a calendar night by the
**local date of `endTime`** (the wake date), configurable. Multiple sessions in one night are
merged; stages are summed from `SleepSessionRecord.Stage`
(`STAGE_TYPE_DEEP` / `REM` / `LIGHT` / `AWAKE` / `SLEEPING`).

**Expect gaps.** Samsung Health can take more than a day to push a night into Health Connect,
and watch-recorded sleep is only processed once the watch reconnects to the phone. The chart
**must render a missing night as a gap, never as zero.**

---

## 4. The document

### 4.1 Page geometry

MediaBox **1404 × 1872 PDF units**, so 1 unit = 1 Nomad pixel and link-rect maths is trivial.
Nomad (1404 × 1872) and Manta (1920 × 2560) are both exactly 3:4, so **a single PDF scales
cleanly to both** — no separate Manta build. A `LayoutProfile(NOMAD | MANTA)` setting changes
only *content density* (the Manta's larger physical panel can carry an extra summary block or
more table rows), never the page box.

Typography and strokes for e-ink: at 300 ppi, 10 pt ≈ 42 units, so body text 40–44 units and
labels ~30. Hairlines never thinner than 2 units — sub-pixel lines dither or vanish. Pure
black plus two or three discrete greys rather than continuous tone.

### 4.2 Pages

Every page carries a persistent footer nav bar of `/GoTo` link annotations, and every page is
emitted into the `/Outlines` tree so the device's Contents sidebar works.

1. **Hub** — today at a glance: doses taken/due, last night's sleep, current streak, month
   adherence %. Large link targets to each section.
2. **Medication habit tracker** — calendar-month grid, 7 columns × 5–6 rows. Each day cell is
   ~190 × 250 units (≈16 × 21 mm) and carries one marker per medication: filled = taken,
   hollow = missed, faint = future. Right-hand rail shows per-medication adherence and streak.
   Above four medications, a `PerMedStrip` layout (one row per med, 31 columns) is selected
   automatically.
3. **Sleep** — the month line chart. X axis is day 1…31 on a **fixed** axis for the whole
   month, Y is hours 0–12, with a shaded target band (default 7–9 h), the nightly duration
   line broken at missing nights, and a 7-day rolling mean. Below: mean duration, mean bed and
   wake time, best and worst night.
4. **Sleep detail** — per-night table: date, bedtime, wake, total, deep / REM / light / awake.
5. **Notes** — a static ruled page, byte-identical on every regeneration, giving a
   guaranteed-safe place to write.

### 4.3 Link and outline emission

`PDAnnotationLink` + `PDActionGoTo` with an explicit `PDPageFitWidthDestination` — explicit
page references, not named destinations, since that is what the proven template ecosystem
uses. Zero-width borders. `PDDocumentOutline` + `PDOutlineItem` per page.

---

## 5. Medication capture and sync

### 5.1 Data model

- `MedicationSchedule(id, name, dose, timesOfDay, daysOfWeek, startDate, endDate?, colorSlot, active)`
- `DoseEvent(id, scheduleId, scheduledFor, loggedAt, status: TAKEN|SKIPPED, source: WIDGET|NFC|NOTIFICATION|MANUAL)`

`DoseRepository.log()` matches a log to the nearest scheduled slot within a tolerance window
and is **idempotent per slot**, so a double NFC tap cannot create two events.

### 5.2 Capture surfaces

- **Glance widget** — today's doses as tap targets; tapping logs and repaints.
- **Notifications** — one alarm per scheduled dose via `AlarmManager.setExactAndAllowWhileIdle`
  (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`), rescheduled on `BOOT_COMPLETED` and after each
  fire. Actions **Taken / Skip / Snooze 15m** handled by a `BroadcastReceiver`, so nothing has
  to open.
- **NFC** — a transparent activity with an `NDEF_DISCOVERED` filter on `healthnote://dose/<id>`;
  the in-app tag writer programs the tag. Logs the dose and finishes with a toast. Requires the
  phone unlocked with the screen on — an Android limitation worth stating in onboarding.

### 5.3 Publishing

- `SupernoteCloudClient` (OkHttp) implementing the flow in §1.4, uploading to
  `Document/HealthNote/`. Password stored Keystore-encrypted, never logged.
- `BrowseAccessClient` for the manual "Send to device now" action: `OPTIONS` to verify, then
  multipart `POST http://<ip>:8089/INBOX`.
- `DailyPublishWorker` — periodic, network-constrained: read Health Connect → regenerate →
  upload. Skips the upload when the rendered bytes hash equal to the last upload.
- `DebouncedPublishWorker` — one-shot with a ~10 minute collapse window, enqueued on every dose
  log so a tick reaches the tablet the same day.
- Onboarding must walk the user through **exempting the app from battery optimisation** —
  aggressive OEM background killing is the most common cause of these jobs silently not running.

### 5.4 Spike to run first

Against a throwaway folder, determine whether **re-uploading the same filename replaces the
file or creates a duplicate**, and whether the raw API exposes a delete endpoint (`sncloud`
notably cannot delete, move or rename). If overwrite duplicates and no delete exists, fall back
to immutable dated files plus a stable hub file. This can invalidate the monthly-file scheme,
so it goes before the renderer is finished.

---

## 6. Verification

- **Unit** — night attribution across DST and midnight-crossing sessions; missing-night gaps;
  dose-slot idempotence; adherence and streak maths.
- **Golden geometry test** — render the same month with empty, partial and full data; assert
  the geometry tree is identical and only the painted layer differs. This is the regression
  guard for `.mark` alignment (§1.3).
- **PDF structure test** — parse the output back with PDFBox; assert every nav rect carries a
  `PDActionGoTo` resolving to the intended page and the `/Outlines` tree has five entries.
- **Instrumented** — seed Health Connect with synthetic `SleepSessionRecord`s from a debug
  build and assert the rendered chart matches.
- **On hardware** (none of this is provable from a desktop, and items 3 and 4 settle open
  questions the research could not):
  1. Nomad: footer nav links jump correctly; Contents sidebar lists all five pages.
  2. The same file renders correctly on a Manta — scaling, no clipping.
  3. Write on page 2, regenerate, replace the file — observe what happens to the ink.
  4. Time how long Auto Sync takes to pull an uploaded file.

---

## 7. Build order

1. Cloud overwrite spike (§5.4).
2. `:core-model` + `:core-data` + medication CRUD UI — dose logging end to end.
3. Capture surfaces: widget, notifications, NFC.
4. `:core-health` sleep reader, permissions, night attribution.
5. `:core-render`: geometry engine → golden test → painters → links and outline.
6. `:core-sync`: cloud + Browse & Access.
7. `:core-work`: scheduling and battery-optimisation onboarding.

---

## 8. Deferred

**A native `.note` generator.** Would unlock keywords, titles and stars and full on-device
annotation, but no complete writer exists and generated links are unproven on-device (§1.2).

**An on-device `.snplg` plugin.** Ratta now ships an official React Native plugin SDK
([Supernote-Ratta][ratta-gh]) with `plugin.permission.INTERNET` and `FILE:WRITE`, and
[task-hub-supernote-plugin][taskhub] proves the fetch-remote-data-and-write-a-file pattern.
It would remove the password-holding problem entirely. Two blockers: the evidence points to
**refresh-on-open rather than a background timer**, and it needs Chauvet 3.29.43+ (2.26.40+ on
A5X/A6X), which narrows the installed base. Worth re-checking `docs.supernote.com` directly —
that domain was unreachable during this research, so the scheduling question is unresolved.

### Path to a Play release

Health Connect permissions require the health permissions declaration form and review, plus a
privacy policy. Separately, an app that asks for a user's Supernote **password** rather than a
token is a review and trust problem — that is the point at which the plugin route or a
self-hosted Private Cloud becomes worth the work.

---

## References

[hc-types]: https://developer.android.com/health-and-fitness/health-connect/data-types
[hc-read]: https://developer.android.com/health-and-fitness/health-connect/read-data
[hc-aggregate]: https://developer.android.com/health-and-fitness/health-connect/aggregate-data
[sh-types]: https://developer.samsung.com/health/data/guide/features/data-types.html
[sh-export]: https://www.samsung.com/us/support/answer/ANS10003847/
[sn-tool]: https://github.com/jya-dev/supernote-tool
[sntool]: https://pkg.go.dev/github.com/jdlugosz963/sntool/format
[snex]: https://github.com/mmujynya/snex
[paia]: https://github.com/dbmcco/paia-supernote
[hyperpaper]: https://hyperpaper.me/planner/supernote
[onplanners]: https://onplanners.com/digital-planners/supernote-daily
[sn-contents]: https://support.supernote.com/en_US/organizing/contents-bookmarks-and-annotations
[sn-keywords]: https://support.supernote.com/en_US/organizing/1759244-using-titles-keywords-and-stars
[sioyek]: https://mil.ad/blog/2025/supernote-in-sioyek.html
[snsharp]: https://github.com/nelinory/SupernoteSharp
[crossword]: https://gist.github.com/nathanbuchar/8c7a87a8383ee83c7c636f861b0d86a0
[ewritable-324]: https://ewritable.net/brands/ratta-supernote/firmware/3-24/
[sn-autosync]: https://supernote.com/blogs/supernote-blog/the-long-awaited-onedrive-integration-along-with-useful-features-like-atelier-landscape-mode-and-auto-sync-with-supernote-cloud-are-now-live
[sncloud-py]: https://github.com/bwhitman/supernote-cloud-python
[sncloud]: https://github.com/julianprester/sncloud
[snsync]: https://github.com/jbchouinard/supernote-sync
[ratta-gh]: https://github.com/Supernote-Ratta
[taskhub]: https://github.com/Sparkinman/task-hub-supernote-plugin

**Health Connect / Samsung Health**
- [Health Connect data types][hc-types] · [Read raw data][hc-read] · [Read aggregated data][hc-aggregate]
- [Samsung Health Data SDK data types][sh-types] · [Samsung Health data export][sh-export]

**Supernote format and features**
- [jya-dev/supernote-tool][sn-tool] · [sntool format docs][sntool] · [mmujynya/snex][snex] · [dbmcco/paia-supernote][paia] · [SupernoteSharp][snsharp]
- [Contents, bookmarks and annotations][sn-contents] · [Using titles, keywords and stars][sn-keywords]
- [Supernote ink in Sioyek — how `.mark` works][sioyek]

**Sync**
- [bwhitman/supernote-cloud-python][sncloud-py] · [julianprester/sncloud][sncloud] · [jbchouinard/supernote-sync][snsync]
- [eWritable firmware 3.24 notes][ewritable-324] · [Supernote Auto Sync announcement][sn-autosync] · [NYT crossword auto-upload gist][crossword]

**Plugin SDK (deferred route)**
- [Supernote-Ratta on GitHub][ratta-gh] · [task-hub-supernote-plugin][taskhub]

**Hyperlinked PDF planners — evidence that `/GoTo` links work on-device**
- [hyperpaper.me][hyperpaper] · [onplanners][onplanners]
