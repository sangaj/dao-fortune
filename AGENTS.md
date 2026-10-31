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
  Play Console product. The app is free to download. The **verdict line of all five modules is
  free**, the **first full reading is free**, and every later reading needs the one-off unlock
  (`data/AccessState.kt`, `vm/MainViewModel.kt` for the preview map). If that split changes, change
  `docs/PLAY_STORE_LISTING.md` with it — the listing describes the paywall model in its own words.
- **The price is never hard-coded in the app.** It is $9.99 in every market, set once in Play
  Console; the paywall reads `formattedPrice` from Play Billing. The only literal `$9.99` in the
  repo is in the store screenshot generator and the screenshot test.
- **The engine must stay in parity with `tools/reference/luopan_reference.py`.** The Python
  reference is a second, independent implementation of the same classical rules and emits
  `app/src/test/resources/golden_pillars.tsv` (742 rows — two years of daily samples) and
  `app/src/test/resources/golden_natal.tsv` (360 rows — the year-sign relations and element tones);
  `EngineTest` asserts the Kotlin engine against both.
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
- **The year sign is not a birth date and never may become one.** `ProfileState` stores exactly one
  integer — the branch ordinal out of twelve — in `dao_fortune_profile.xml`. Do not add a birth date, a
  birth year, a name or any other identifier; the whole point of the design is that the app never asks
  for personal information, which is what lets the Play Data safety form say "no data collected".
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
  nothing; opening the full reading of any day still goes through the normal free-reading-then-paywall
  path.

## Store material

`docs/store/*` is generated, not hand-drawn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_assets.ps1        # icon + feature graphic
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1   # phone screenshots
```

Screenshots read `docs/store/reading_sample.json`, which the `SampleReadingDump` test writes from
real engine output — regenerate it by running the tests, not by editing it.

`docs/store/screenshots/` must hold exactly the five final PNGs (`01_home`, `02_home_unlocked`,
`03_favourable_days`, `04_windfall_reading`, `05_unlock`) — that is the folder you upload to Play.
Robolectric captures go to `build/screenshots/` instead, so a test run can never leave stray files in
the upload folder.

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
- `keystore.properties` must stay **UTF-8 without a BOM**. A BOM makes Java's `Properties` read
  the first key as `﻿storeFile`, and the release bundle is then silently left unsigned.
