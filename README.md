# HealthNote

Tracking health with Supernote pages and habit trackers.

An Android app that reads sleep data from Samsung Health via Health Connect, tracks
medication adherence itself, and publishes an auto-updating, hyperlinked PDF dashboard to a
Supernote e-ink tablet (Nomad primary, Manta supported).

**Status:** domain model and the Supernote PDF renderer are built and tested; the Compose UI
is written against in-memory fakes but has not been compiled yet. See
**[docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)** for what exists, how it was verified and
where it departs from the mockups.

```sh
./gradlew test                          # model, layout and PDF tests (JDK 17+, no Android SDK needed)
./gradlew :core-render-pdf:renderSample # sample month -> core-render-pdf/build/sample/*.pdf
./gradlew :app:installDebug             # needs an Android SDK
```

See **[docs/DESIGN.md](docs/DESIGN.md)** for the full design, including the sourced research
behind three decisions that are easy to get wrong:

- Health Connect has **no medication data type**, so medication logging cannot come from
  Samsung Health and is owned by this app.
- The document is generated as **PDF** (internal `/GoTo` links + an `/Outlines` tree), not
  `.note` — no complete `.note` writer exists.
- **Supernote Cloud is the only sync route with an automatic device-side pull**; the
  Dropbox/Drive/OneDrive integrations require a manual tap every time.
