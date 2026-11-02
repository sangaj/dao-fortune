# AGENTS.md — Dao Fortune

Android app (Kotlin + Compose) that reads the day through the Chinese luopan and reports on
windfall, love, career and balance. Package id `com.laki.daofortune`.

## Build

The toolchain is portable and lives outside the repo:

```powershell
$env:JAVA_HOME = 'E:\Minimax\toolchain\jdk17'
$env:ANDROID_HOME = 'E:\Minimax\toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = 'E:\Minimax\toolchain\gradle-home'
cd E:\Minimax\dao-fortune
.\gradlew.bat :app:testDebugUnitTest      # engine parity tests + UI smoke tests
.\gradlew.bat :app:lintRelease            # errors here are release blockers
.\gradlew.bat :app:bundleRelease          # signed bundle -> app/build/outputs/bundle/release
powershell -NoProfile -ExecutionPolicy Bypass -File tools\release\package_release.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\qa\verify_release.ps1   # whole Layer 1 gate
```

## Invariants that must not drift

- **In-app product id `dao_fortune_full_access`** must match `BillingManager.PRODUCT_ID` and the
  Play Console product. The app is free to download. **Today's Fortune (`FortuneModule.OVERALL`) is
  free every day, in full** — it never spends the free reading and never hits the paywall. Of the
  other four modules and the dream, the **first full reading is free**, and every later reading
  needs the one-off unlock (`data/AccessState.kt`, `vm/MainViewModel.kt`). There is exactly **one**
  free reading, and a tap that would spend it asks first (`UiState.confirmFreeSpend` for a module,
  `UiState.confirmFreeDream` for a dream) — it must
  never be spent silently. If that split changes,
  change `docs/PLAY_STORE_LISTING.md` with it — the listing describes the paywall model in its own
  words.
- **The repeated copy must agree everywhere.** The paywall split, the one free reading and its
  confirm-first step, the birth-day storage promise, "no data collected" and the entertainment
  disclaimer all appear in the app, the listing, the privacy policy and these docs. The agreement
  is not left to memory: `tools/qa/check_copy_consistency.py` checks every pair in the release
  gate. When any of that copy changes, change the rule's patterns in the same commit.
- **The price is never hard-coded in the app.** It is $9.99 in every market, set once in Play
  Console; the paywall reads `formattedPrice` from Play Billing. The only literal `$9.99` in the
  repo is in the store screenshot generator and the screenshot test.
- **The engine must stay in parity with `tools/reference/luopan_reference.py`.** The Python
  reference is a second, independent implementation of the same classical rules and emits
  `app/src/test/resources/golden_pillars.tsv` (742 rows — two years of daily samples),
  `app/src/test/resources/golden_natal.tsv` (360 rows — the year-sign relations and element tones) and
  `app/src/test/resources/golden_dream.tsv` (96 rows — twelve dreams read against eight days) and
  `app/src/test/resources/golden_birthdays.tsv` (343 rows — day pillars across 1930–2020, for the
  optional birth-day anchor); `EngineTest` and `DreamTest` assert the Kotlin engine against all four.
  If you change a rule, change the Python too and regenerate:
  ```bash
  python tools\reference\luopan_reference.py
  ```
  Never edit the golden file by hand.
- **Convention for the twelve branches**: a branch sits on the compass at `ordinal * 30`
  degrees, and every branch lands exactly on its own mountain (`Branch.bearingOf` /
  `Branch.palaceOf`). Use those helpers rather than doing arithmetic on ordinals.
- **Day turns at 23:00, year turns at Li Chun, month turns at the twelve jie sections.**
  Term instants come from the sun's apparent longitude (Meeus) and are compared as real instants
  in UTC against the device's local time — the Python reference was wrong here once and was fixed;
  do not reintroduce naive/UTC comparisons.
- The engine must stay free of Android dependencies so it can be unit tested on the JVM.
- **`minSdk` is 26 and must not be lowered below it.** The engine uses `java.time` throughout, which is
  API 26+; on API 24/25 the app would die with `NoClassDefFoundError` on the first screen. If you ever
  need API 24/25 back, enable core library desugaring in `app/build.gradle.kts` — do not just lower
  the number. Lint's `NewApi` check is what catches this, and it is reported as an **error**.
- **Lint is configured with `abortOnError = false`, so a green build does not mean lint is clean.**
  Run `.\gradlew.bat :app:lintRelease` and read `app/build/reports/lint-results-release.txt` before
  every release. Errors are upload blockers.
- **The year sign and the optional birth day are anchors, not an identity.** `ProfileState` stores the
  year-sign branch ordinal and, optionally, the birth day as a plain date — in
  `dao_fortune_profile.xml`. When a birth day is set it anchors readings on the day pillar of that day
  (`UiState.natalBranch`), taking precedence over the year sign. No name, no birth time, no place, no
  account: both values stay on the device and are used only to compute a pillar locally, which is what
  lets the Play Data safety form say "no data collected".
- **`dao_fortune_profile.xml` must stay out of the backup allow-list.** `backup_rules.xml` and
  `data_extraction_rules.xml` allow-list only `dao_fortune_access.xml` (the purchase flags). The year
  sign is left out on purpose so it never leaves the device, not even into cloud backup. If you add a
  preference to the profile file, it inherits that exclusion — keep it that way.
- **A chosen date is a *day plate*, not a now-plate.** `LuopanSnapshot.dayOnly` guards every
  hour-dependent factor, and `Narrative.dayWord` / `dayPossessive` switch the prose from "today" to
  "that day". A future reading must never speak in the present tense about itself — that was a real bug
  caught by the store screenshots, where the favourable-days list said "Money has a way in today".
- **The year sign is never called a chart, a BaZi or a horoscope.** The copy is "your year sign" or
  "your birth-year branch". The listing, the About sheet and the privacy policy must agree with
  whatever the UI says.
- **The forward scan is free.** The Favourable Days list (date, score, one-line verdict) costs
  nothing; opening the full reading of any scanned day follows the normal module rule — the overall
  module is free, the others are first-free-then-paid.
- **The dream text is never stored, never logged and never transmitted.** It lives in `UiState` while
  the dream sheet is open and is dropped by `MainViewModel.closeDream()`. Do not add a preference, a
  file, a database, a log line or a network call for it. The ink-wash plate is drawn procedurally on
  the device from the dream's own text (`DreamPlate` in `ui/DreamSheet.kt`), seeded by
  `DreamImage.seed` so the same dream always draws the same plate — there is deliberately **no cloud
  AI image generation**, because a dream is sensitive personal data and uploading it would break the
  "no data collected" promise that the whole app's Play Data safety form rests on.
- **The dream module obeys the same product rule as windfall, love, career and body.** First reading
  free, later readings behind the one-off unlock (`readDream()` → paywall → `produceDream()` after
  the purchase). Only Today's Fortune is always free.
  It is not a `FortuneModule`: it needs the reader's own words, so it has its own entry card and sheet.
- **A dream is read against a *day plate*, never a now-plate** — it is a past event and has no hour of
  its own. The double-hour of waking is a separate input (`DreamEngine.branchOfHour`), and the day a
  dream belongs to is the previous day when the reader woke before 03:00 (`MainViewModel.dreamDay`).
- **The dream vocabulary is bounded on purpose** — directions, the twelve animals, the five phases,
  colours, numbers, hours and the sign of money. English words match on word boundaries, compound
  directions are claimed whole before cardinals, and numbers are matched last so "eleven" is never read
  as "one". Do not widen the lexicon to cover everything a dream can contain; a dream the lexicon cannot
  read is reported as having no usable image.
- **Billing must never fail silently.** Every Play call has to check its response code, and a dropped
  connection has to be retried with the backoff in `billing/ReconnectPolicy.kt` (2s, 4s, 8s, 16s,
  then 30s, six attempts). `ReconnectPolicy` is pure Kotlin on purpose — it is unit tested
  (`ReconnectPolicyTest`). Do not put Play types into it.
- **The ownership check runs on every connection.** `BillingManager.checkOwnership()` is what makes a
  purchase that completed while the app was closed still unlock the app. Do not remove it as
  "redundant" — it is the only path that catches a settled pending purchase.
- **The privacy policy has two forms and they must agree.** `docs/PRIVACY_POLICY.md` is the source;
  `docs/privacy-policy.html` is what gets hosted. After editing the Markdown run
  `python tools/docs/make_privacy_html.py`; the release gate fails if the HTML is stale.
- **The keystore must never be committed.** `.gitignore` excludes `keystore.properties` and `*.jks`;
  the release gate also checks that git is not tracking them. `dist/` is ignored too — the bundle and
  the source zip are rebuilt, not committed. The repo is public, so GitHub's **secret scanning push
  protection** (Settings → Code security → Push protection) must stay enabled as the second net.
- **Two builds of the same source do not produce the same AAB bytes.** An AAB is a zip of nested jars
  and the nested jars are re-compressed on every build, so a few bytes of deflate stream differ; on top
  of that AGP writes the current commit into
  `base/root/META-INF/version-control-info.textproto`, and the jar signatures are derived from the
  payload. So the gate compares **payloads** (`tools/qa/compare_bundles.py`: entry names, order and
  CRC32, excluding the commit id and the three signature files) and never the whole-file hash. If you
  ever see "dist bundle matches the freshly built one" fail, compare the payloads first — if the
  payload matches, the file is right and only the zip bytes moved.
- **Today's Fortune is drawn the moment the plate is known, and only that one.**
  `FortuneEngine.alwaysFree(date, snapshot, natal)` returns the single reading the home screen carries
  without asking for anything, and `MainViewModel` puts it in `UiState.readings` whenever the plate or
  the anchor changes. That is why the card shows its score instead of a `PREVIEW` tag, and why opening
  it never spends the free reading. The four other modules and the dream stay behind the unlock. If the
  split changes, the listing, the paywall bullets, the home banner and the store screenshots change
  with it.

## Store material

`docs/store/*` is generated, not hand-drawn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_assets.ps1        # icon + feature graphic
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1   # phone screenshots
```

Screenshots read `docs/store/reading_sample.json`, which the `SampleReadingDump` test writes from
real engine output — regenerate it by running the tests, not by editing it.

**The screenshots are a re-implementation of the Compose layout in GDI+, not captures of it.** They do
not follow the app: when a visible string or a row changes, `tools/store_assets/build_screenshots.ps1`
has to be edited by hand and the six PNGs regenerated, or the store shows a product that no longer
exists. The home shot in particular carries the free-reading banner, the anchor row and the five
module cards — copy that text out of `ui/HomeScreen.kt` and `ui/DateAndYearSign.kt`, never from
memory. Two traps in that script: PowerShell's comma operator binds tighter than `+`, so a
concatenated array element needs its own parentheses; and `$h` clobbers `$H` because the shell is
case-insensitive.

`docs/store/screenshots/` must hold exactly the final PNGs — `01_home`, `02_home_unlocked`,
`03_favourable_days`, `04_windfall_reading`, `05_unlock` and `06_dream` — that is the folder you upload
to Play. Robolectric captures go to `build/screenshots/` instead, so a test run can never leave stray
files in the upload folder.

## What the bundle actually contains

`com.android.billingclient:billing:9.1.0` pulls in more than it looks like. `bundletool dump manifest`
on the release AAB shows, merged into the app:

* `INTERNET` and `ACCESS_NETWORK_STATE` (from the billing library)
* `com.google.android.gms:play-services-location` and `play-services-places-placereport`
* `com.google.android.datatransport:transport-runtime` and `transport-backend-cct` (Google's telemetry
  channel) plus `com.google.firebase:firebase-encoders*`

None of it is used by the app: the billing library's own bytecode has **zero** references to the
transport or location stacks, no location permission is declared, and nothing schedules a transport
event. The copy in `docs/PLAY_STORE_LISTING.md` and `docs/PRIVACY_POLICY.md` says exactly that — do not
write "works completely offline" or "no third-party components" again.

Do **not** strip these with Gradle `exclude` blocks. The purchase flow is the one thing that cannot be
tested on this machine, so the billing dependency graph stays untouched.

## Environment gotchas on this machine

- Maven Central is very slow from here: `settings.gradle.kts` lists Aliyun mirrors first, and the
  Gradle wrapper points at the Tencent mirror. Robolectric downloads its own runtime, so
  `robolectric.dependency.repo.url` is set to the Aliyun mirror in `app/build.gradle.kts`.
- The 2026 command-line tools use slash-style package names (`platforms/android-36`), the
  `sdkmanager` CLI is deprecated in favour of `android sdk install`, and AGP's own SDK
  auto-download hangs on the newer repository XML. `android.builder.sdkDownload=false` and a
  pinned `buildToolsVersion` keep the build offline-friendly.
- Only ~6.9 GB of RAM and no hardware virtualisation, so the Gradle heap is capped and the Kotlin
  compiler runs in-process. Do not raise the heap.
- No emulator and no device is available: the UI is verified by Robolectric smoke tests and by
  code-rendered store screenshots, not by a real device.
- **Git is portable too**: `E:\Minimax\toolchain\git\cmd\git.exe` (2.56). Put that directory on
  `PATH` before using `git`, and note that the system has no `git` of its own.
- `keystore.properties` must stay **UTF-8 without a BOM**. A BOM makes Java's `Properties` read
  the first key as `﻿storeFile`, and the release bundle is then silently left unsigned.
