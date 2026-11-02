# Testing plan — Dao Fortune

Three layers, in order. Layer 1 is fully automated and runs on this machine. Layer 2 needs a phone.
Layer 3 is Play itself.

The rule: **nothing goes to Play until Layer 1 is green and Layer 2 has been walked through on real
hardware.** Layer 3 then re-runs the purchase path against the real Play Store, which is the only
place the billing flow can be proven end to end.

### One command for Layer 1

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\qa\verify_release.ps1
```

It runs the Python self-check, the unit tests, lint, `bundletool validate`, the manifest assertions,
the signature check and the store-folder / source-zip checks, then prints `ALL CHECKS PASSED` or the
list of what failed. The individual commands it runs are below, so you can also run them one at a
time while diagnosing a failure.

---

## Layer 1 — On this machine (no device required)

### 1.1 Engine parity

The engine is pure Kotlin with no Android dependencies, so it is tested on the JVM. A second,
independently written Python implementation produces the golden data; if the two ever disagree, one
of them has drifted.

```powershell
Set-Location E:\Minimax\dao-fortune
python tools\reference\luopan_reference.py       # SELF-CHECK: ALL PASS, rewrites the golden TSVs
.\gradlew.bat :app:testDebugUnitTest             # 55 tests
```

`SampleReadingDump` also writes `docs/store/reading_sample.json` — the real output the store
screenshots are rendered from. Run the tests before regenerating the screenshots, never edit that file
by hand.

**Expected:** `EngineTest` 26, `DreamTest` 12, `ScreenshotTest` 6, `ReconnectPolicyTest` 6,
`SampleReadingDump` 1, zero failures.

### 1.2 Lint

```powershell
.\gradlew.bat :app:lintRelease
```

Read `app/build/reports/lint-results-release.txt`.

> `lint { abortOnError = false }` is set in `app/build.gradle.kts`, so **a green Gradle build does not
> mean lint is clean.** Errors must be treated as release blockers.

This check has already earned its keep — it found 56 `NewApi` errors (the engine uses `java.time`,
which is API 26+, while `minSdk` was 24 → the app would have crashed on launch on Android 7.x) and two
`ByteOrderMark` errors in the signing config.

### 1.3 Bundle structure and signature

`bundletool` lives in the Gradle cache (no download needed):

```powershell
$bt = 'E:\Minimax\toolchain\gradle-home\caches\modules-2\files-2.1\com.android.tools.build\bundletool\1.18.0\e84038fddca7582ddf5c98568f517cae401172d9\bundletool-1.18.0.jar'
java -cp $bt com.android.tools.build.bundletool.BundleToolMain validate --bundle=dist\app-release.aab
java -cp $bt com.android.tools.build.bundletool.BundleToolMain dump manifest --bundle=dist\app-release.aab --module=base
```

The cache jar has no `Main-Class`, so it is launched by class name. Its dependencies come from the
same cache; a helper that assembles the classpath lives at `tools/qa/bundletool_cp.py`.

**What to read in the manifest:**

| Field | Expected |
|---|---|
| `package` | `com.laki.daofortune` |
| `versionCode` / `versionName` | `1` / `1.0.0` — **versionCode must increase on every upload** |
| `minSdkVersion` | `26` |
| `targetSdkVersion` | `36` (Play requires 36 for new listings since Aug 2026) |
| `uses-permission` | only `INTERNET`, `ACCESS_NETWORK_STATE` (both from the billing library), `com.android.vending.BILLING` |
| `uses-permission` location | **must be absent** |

### 1.4 Signature

```powershell
& 'E:\Minimax\toolchain\jdk17\bin\keytool.exe' -printcert -jarfile dist\app-release.aab
```
`META-INF/DAO-FORT.SF`, `META-INF/DAO-FORT.RSA` and `META-INF/MANIFEST.MF` must be present in the zip,
and the certificate must be `CN=Dao Fortune` and valid past **22 October 2033** (Play's requirement).

### 1.5 16 KB page size

Android 15+ requires apps targeting API 35 or later to support 16 KB page sizes. The app is written in
Kotlin and has no native code of its own, but the bundle still carries
`libandroidx.graphics.path.so` (from Compose) for four ABIs — so the alignment is **checked**, not
assumed:

```powershell
python tools\qa\check_16kb_pages.py dist\app-release.aab
```

Every PT_LOAD segment must report `p_align = 0x4000` (16 KB). This runs inside the release gate.

### 1.6 Deliverable packaging

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\release\package_release.ps1
```

Produces `dist/app-release.aab` and `dist/dao-fortune-source-v1.0.0.zip`. The source zip must contain
**no** `keystore.properties`, no `.jks` and no `__pycache__`.

### 1.7 Store copy

```powershell
python tools\qa\check_listing_lengths.py
```

Play rejects an over-long listing before a human ever sees it, so the caps are checked here: app name
30, short description 80, full description 4000, release notes 500. This runs inside the release
gate.

---

## Layer 2 — On a real phone

This machine has no emulator (the CPU has no hardware virtualisation) and no device, so Layer 2 is a
human pass. Install from Play (internal testing track) rather than sideloading — the Play build is
the artifact that matters.

### 2.1 Install and first run

```powershell
adb install -r <path-to.apk-from-internal-testing>
adb shell am start -n com.laki.daofortune/.MainActivity
adb logcat -c            # clear the log before the run
```

| # | Check | Expected |
|---|---|---|
| 1 | App launches | No crash in logcat; the plate renders |
| 2 | Date and time line | Today's real date, local time, correct time zone |
| 3 | Solar term | Matches an independent source for today |
| 4 | Four pillars | Year / month / day / hour, non-empty, sensible |
| 5 | Dial | Sun marker, wealth marker and "avoid" marker on the right mountains |

### 2.2 The paywall path — the part that cannot be skipped

| # | Action | Expected |
|---|---|---|
| 6 | Open any module | The **first** reading opens free, full detail |
| 7 | Open a second module | The paywall sheet appears |
| 8 | Look at the price | Your **local currency**, converted by Play — never a hard-coded `$9.99` |
| 9 | Buy | Purchase completes, sheet closes, the module you wanted opens |
| 10 | Open every module | No paywall anywhere |
| 11 | Restore purchase | After uninstalling and reinstalling, **Restore purchase** brings the unlock back |
| 11a | Look at the paywall (with the tip jar created in the Console) | A "leave a tip" row sits below **Restore purchase**, priced in your local currency, saying plainly that it unlocks nothing |
| 11b | Leave a tip | Play's sheet takes the payment, the paywall closes on a thank-you banner; nothing unlocks — locked cards stay locked — and the jar can be filled again |

For step 11, reinstalling does **not** reset the unlock: Play owns the entitlement. The local
"free reading" flag is a separate thing and *does* reset when app data is cleared — that is by
design and documented in `docs/RELEASE_CHECKLIST.md`.

Use a licensed tester account on a real device; the Play billing test companion is not available in
production.

### 2.3 The free-preview mechanic

| # | Action | Expected |
|---|---|---|
| 12 | Fresh install, before paying | The four paid modules show a verdict line and a `PREVIEW` tag; **Today's Fortune shows its score straight away** — it is free every day, in full |
| 12a | Open **Today's Fortune** without paying | The full reading opens — score, factors, hours, advice — and the free-reading counter is untouched |
| 12b | Tap another module while the free reading is unspent | A dialog asks "Your one free reading" first; nothing is spent until it is confirmed |
| 12c | Dismiss the dialog, then tap the module again | The dialog returns and the free reading is still there; confirming spends it and opens the reading |
| 13 | Locked cards (the other four, after the free reading is spent) | Lock icon, no score; Today's Fortune never locks |
| 14 | After unlocking | Scores appear on every card, `PREVIEW` tag gone |

### 2.4 Looking ahead — the date selector and the forward scan

| # | Action | Expected |
|---|---|---|
| 15 | Tap the ▶ chevron | The plate moves to tomorrow; the header shows the new date, and the reading says "that day", never "today" |
| 16 | Tap ◀ repeatedly | The plate walks back day by day; a past date is read the same way as a future one |
| 17 | Open the calendar | A date picker opens; picking any date loads that day's plate |
| 18 | Tap a day in **Favourable Days** | That date is selected and its plate is drawn |
| 19 | Check the list itself | Five days, ranked best first, each with its day pillar, solar term, score and one-line verdict |
| 20 | Pick the top day | The date selector jumps to it; the windfall score on the plate matches the list |
| 21 | Check the hour | On a chosen date the reading has **no hour** — no hour pillar, no hour-officers, no hour windows |

### 2.4a Notifications, the widget and reminders

| # | Action | Expected |
|---|---|---|
| 21a | Tap the bell in the top bar | Android 13+ asks for the notification permission; the bell turns on |
| 21b | Next morning at ~08:00 | One silent notification with the day's score and headline; tapping it opens the app |
| 21c | Reboot the phone | The daily work is rescheduled by the boot receiver |
| 21d | Tap the bell on a favourable day | The bell fills in; on that morning a reminder notification posts, and the mark clears itself |
| 21e | Place the home-screen widget | Today's score, verdict, headline and best window; it refreshes when the app opens and each morning |
| 21f | Revoke the notification permission | The app stops posting; no crash, and the bells still toggle |

### 2.5 The year sign

| # | Action | Expected |
|---|---|---|
| 22 | Open the year-sign sheet | Twelve zodiac branches, one tap each; the birth day below them is optional |
| 23 | Pick one | The plate draws your sign as a hollow ring; the readings gain the harmony / triad / clash / harm note |
| 24 | Change it | The readings change immediately; the ring moves to the new mountain |
| 25 | Clear it | The ring disappears and the readings return to the unpersonalised form |
| 25a | Add a birth day | The readings re-anchor on the birth day's pillar; the home row shows the birth day instead of the animal |
| 25b | Set both a year sign and a birth day | The birth day wins; removing it falls back to the year sign |
| 26 | Check what was stored | One integer and an optional ISO date in `dao_fortune_profile.xml`; nothing in `dao_fortune_access.xml` |
| 27 | Check the backup | The year sign and the birth day are **not** in the allow-list: after a device-to-device transfer they are gone, while the purchase unlock survives |

### 2.6 Dream & Omens (梦占)

| # | Action | Expected |
|---|---|---|
| 28 | Tap the Dream & Omens card | The sheet opens empty; the card is labelled "Dream & Omens / 梦占" |
| 29 | Write a dream with a direction, an animal and a colour, then tap **Read the dream** | A reading appears: score, headline, the ink-wash plate, the insights, the advice and the factors behind the score |
| 29a | Tap **Read the dream** while the one free reading is unspent | A dialog asks "Your one free reading" first; nothing is spent until it is confirmed |
| 29b | Dismiss the dialog, then tap **Read the dream** again | The dialog returns and the free reading is still there; confirming spends it and the reading appears |
| 30 | Look at the plate | The images you wrote sit on the mountains they belong to; the sun of the day is marked; your year sign is ringed if you set one |
| 31 | Read the same dream again | The plate is **identical** — the drawing is seeded by the text, not random |
| 32 | Write something the vocabulary cannot read ("a grey feeling of dread") | The reading says the dream held no image the plate could use — it must not invent an interpretation |
| 33 | Check the privacy | Nothing was written: the text is in no file under the app's storage, and closing the sheet erases it |
| 34 | After the free reading is spent | Reading a dream opens the paywall; buying it unlocks the app and the dream is read straight away, with your words still there |

### 2.7 Edge cases

| # | Check | How |
|---|---|---|
| 35 | Airplane mode | App opens and reads the plate; only the purchase needs the network |
| 36 | Another time zone | `adb shell service call alarm 3 s16 Asia/Tokyo` or change it in Settings; the pillars must follow |
| 37 | 12-hour clock | Change the system clock format; the header must still read correctly |
| 38 | Wrong clock | Set the clock forward a day; the plate must follow the device clock (the app does not use a server time) |
| 39 | Dark / light mode | Both themes render; the dial stays readable |
| 40 | Clear app data | The free reading comes back — expected and documented |
| 41 | Font scale | Set the system font size to the largest; nothing must be clipped |
| 42 | Tablet / foldable | Resize the window; the layout must not break |

### 2.8 Billing under a bad connection

This is the part that cannot be proven on this machine at all, and it is where the reconnector earns
its keep.

| # | Action | Expected |
|---|---|---|
| 43 | Open the paywall in airplane mode | The sheet says Play could not be reached — it must **not** pretend there is no purchase |
| 44 | Turn the network back on | The app reconnects on its own (backoff of 2s, 4s, 8s, 16s, then 30s) and the price appears without a restart |
| 45 | Watch the paywall during the retry | "Play connection lost — retrying in Ns (attempt N)" — the reader can see it is working |
| 46 | Tap **Restore purchase** with no network | A clear error, not silence |
| 47 | Tap **Restore purchase** on an account that bought nothing | "No previous purchase found on this Google account" — and no crash |
| 48 | Buy, then force-stop the app, then reopen | The app is still unlocked: the ownership check runs on every connection |
| 49 | Buy on one device, install on another | The second device unlocks on first launch, from Play's record |
| 50 | Read the first free reading | A banner appears on the home screen saying the free reading has been used — and it can be dismissed |
| 51 | Airplane mode, then try to buy | The purchase sheet does not open; the failure is reported |

---

## Layer 3 — Google Play internal testing

1. Console → **Release → Testing → Internal testing → Create new release**
2. Upload `dist/app-release.aab`
3. The "signed with a new key" warning is expected for a first upload
4. Roll out to internal testers, then **install from Play on a real phone**
5. Repeat Layer 2 against the Play-installed build
6. Promote to production only when the purchase path is proven

**Play's own pre-flight checks** (all of these must pass before you hit publish):

- Target API level 36 ✓
- AAB signed with a key Play accepts (the upload key, matching `keystore.properties`)
- `versionCode` higher than any previous upload — `1` for the first one
- Privacy policy hosted on a public URL and linked in Play Console → App content
- Data safety form filled in (see `docs/PLAY_STORE_LISTING.md`)
- Content rating questionnaire completed
- An in-app product with the id `dao_fortune_full_access`, priced once and left to Play's automatic
  currency conversion — and, if the tip jar is wanted, the consumable `dao_fortune_tip` alongside it

---

## Pre-release gate

Everything below must be true before an upload. If any line is unchecked, stop.

| Gate | How to verify | Status |
|---|---|---|
| Engine parity green | `python tools\reference\luopan_reference.py` → ALL PASS | ☐ |
| Unit tests green | `:app:testDebugUnitTest` → 55/55 | ☐ |
| Lint has zero errors | `:app:lintRelease` → `app/build/reports/lint-results-release.txt` | ☐ |
| Bundle validates | `bundletool validate` → exit 0 | ☐ |
| 16 KB page size | `python tools\qa\check_16kb_pages.py dist\app-release.aab` → PASSED | ☐ |
| Manifest correct | package / versionCode / minSdk 26 / targetSdk 36 / no location permission | ☐ |
| Signature present and valid | `keytool -printcert` | ☐ |
| Source zip has no secrets | no `keystore.properties`, no `.jks` | ☐ |
| Store folder is clean | `docs/store/screenshots/` holds exactly 6 PNGs | ☐ |
| Privacy policy page is current | `python tools\docs\make_privacy_html.py --check` | ☐ |
| Copy matches the app | free previews / first free reading / one-off unlock / year sign / dream described the same way in the listing, the privacy policy and in-app | ☐ |
| Real-device pass done | Layer 2 complete, including the purchase | ☐ |
| Keystore safe | `dao-fortune-release.jks` + `keystore.properties` backed up **off this machine** | ☐ |
| Play App Signing | enrolled in Play Console → App integrity | ☐ |
| Console products created | `dao_fortune_full_access` (one-off) is live; `dao_fortune_tip` (consumable) created too — or deliberately skipped, which simply hides the tip row | ☐ |
| Everything committed | `git status` clean, `git push` done | ☐ |

---

## What is still unverified, and why

| Item | Why it cannot be closed here |
|---|---|
| The purchase flow | Needs a device with a Play account and a licensed tester |
| Real-device rendering | The store screenshots are rendered from real engine output by the same design code, but they are not a device capture |
| Play's own validation | Only runs after upload |
| The Data safety form wording | The form's exact options and their labels change; the answer is "no data collected, no data shared", but read the form itself before submitting |
| Behaviour on Android 7.x | The app now requires API 26; Android 8.0 (2017) is the floor |
