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
.\gradlew.bat :app:testDebugUnitTest             # 110 tests
```

`SampleReadingDump` also writes `docs/store/reading_sample.json` — the real output the store
screenshots are rendered from. Run the tests before regenerating the screenshots, never edit that file
by hand.

**Expected:** `EngineTest` 30, `DreamTest` 12, `ScreenshotTest` 6, `ReconnectPolicyTest` 6,
`PurchaseClassifierTest` 10, `WidgetCopyTest` 4, `ShareCardTest` 1, `GlanceAndYearTest` 10,
`PickActivityTest` 6, `ReviewPromptTest` 7, `WeekAheadTest` 8, `SampleReadingDump` 1,
zero failures.

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

### 1.8 The money path

The one place where a reader who has **paid** can be left locked out is the decision made when Google
Play reports a purchase. That decision is therefore written as a pure function —
`app/src/main/java/com/laki/daofortune/billing/PurchaseOutcome.kt` — with no Android and no Play
types in it, and it is tested on the JVM:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.laki.daofortune.billing.PurchaseClassifierTest'
```

The rules it pins down: a purchase in the state *Purchased* unlocks the app **whatever** the callback
code says (the money is in the bank); a purchase in the state *Pending* is reported as still settling,
not as a failure; a cancelled callback is not an error; and a purchase of some **other** product never
unlocks anything. `BillingManager` uses the same function to decide whether it already owns the
product, so the app has exactly one definition of "the reader owns it".

### 1.9 The store screenshots

The screenshots are drawn by `tools/store_assets/build_screenshots.ps1`, not captured from the app,
so they do not follow it on their own. Two checks keep them honest, both inside the release gate:

```powershell
python tools\qa\check_screenshot_sync.py
```

* **script → app** — every phrase the script draws has to exist in the app's own source, so the store
  can never show a sentence the app does not say.
* **app → script** — the sentences the app leads with (the free-reading banner, the anchor row, the
  paywall's benefits, the share buttons) have to reach the script.

The gate also draws the screenshots again into a scratch folder and compares the bytes — the render
is byte-for-byte reproducible, so a PNG that the current script no longer produces is caught:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1 -OutDir build\_screens_check
```

Both directions were verified with a negative control: a script that draws `Save to gallery` instead
of the app's `Save image` fails, and so does an app whose banner no longer matches the store.

### 1.10 The home-screen cards

The three cards the home screen grew this round — the year officer, the day glance and the
activity-ranked forward scan — are plain Kotlin over the same plate the readings use, so they are
pinned down on the JVM:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.laki.daofortune.core.PickActivityTest' `
                                    --tests 'com.laki.daofortune.core.GlanceAndYearTest' `
                                    --tests 'com.laki.daofortune.core.WeekAheadTest' `
                                    --tests 'com.laki.daofortune.core.ReviewPromptTest'
```

The rules they lock down:

* **The list shows the lead module's own score**, with the module's name under the number, and the
  **order** comes from the activity's weighted rank. The two numbers are deliberately different, and
  a test asserts that they differ — a list whose number is not the number the reader meets in the
  reading is the one bug a screenshot cannot show.
* **The year turns at Li Chun.** `CalendarEngine.yearNumber` is the single source for the year on
  the card, and a test pins the turn to the solar year rather than 1 January.
* **The week ahead is a calendar week** — `WeekAhead.weekDays(today)` finds the coming Monday, so the
  Sunday-evening note names Monday to Sunday.
* **The review ask depends on the reader using the app, not on the day's luck**: five days opened
  **and** an unlock, with a thirty-day cooldown. The day's score is not even a parameter of the
  rule, so the ask cannot depend on how good the day happened to be.
* **A glance never says the same thing twice** — the value names the thing, the note says what it is
  for, and both come from one source, so the ten-second read cannot drift from the long one.

---

## Layer 2 — On a real phone

This machine has no emulator (the CPU has no hardware virtualisation) and no device, so Layer 2 is a
human pass. Install from Play (internal testing track) rather than sideloading — the Play build is
the artifact that matters. The one-page working list for that evening is
`docs/PRELAUNCH_DEVICE_PASS.md`; the rows below are the details it points back to.

### 2.1 Install and first run

```powershell
adb install -r <path-to.apk-from-internal-testing>
adb shell am start -n com.laki.daofortune/.MainActivity
adb logcat -c            # clear the log before the run
```

| # | Check | Expected |
|---|---|---|
| 1 | App launches | No crash in logcat; on a **fresh install** the three-page introduction shows first; dismissing it lands on the plate, and it never shows again |
| 1a | Introduction, page two | Picking an animal sets the year sign straight away; the Year Officer card on the home screen reflects it |
| 1b | Introduction, page three | "Turn on the morning bell" asks the notification permission on Android 13+ and switches the bell on; skipping leaves it off |
| 1c | Clear app data and reopen | The introduction shows once more (the flag lives in the profile file, by design) |
| 2 | Date and time line | Today's real date, local time, correct time zone |
| 3 | Solar term | Matches an independent source for today |
| 4 | Four pillars | Year / month / day / hour, non-empty, sensible |
| 5 | Dial | Sun marker, wealth marker and "avoid" marker on the right mountains |
| 5a | The glance row under the plate | Colour, face direction and best hour; the direction matches the full reading's "Turn towards" line, and the colour is the wealth element's five-phase colour |
| 5b | The Year Officer card without a sign | A teaser naming the year's animal; tapping it opens the year-sign picker |
| 5c | The Year Officer card with a sign set | One headline and body matching the relation — e.g. a Rat in a Horse year reads "Your sign clashes the year" |

### 2.2 The paywall path — the part that cannot be skipped

| # | Action | Expected |
|---|---|---|
| 6 | Open any module | The **first** reading opens free, full detail |
| 7 | Open a second module | The paywall sheet appears |
| 8 | Look at the price | Your **local currency**, converted by Play — never a hard-coded `$9.99` |
| 9 | Buy | Purchase completes, sheet closes, the module you wanted opens |
| 10 | Open every module | No paywall anywhere |
| 11 | Restore purchase | After uninstalling and reinstalling, **Restore purchase** brings the unlock back |
| 11a | Read the paywall's small print | The entertainment disclaimer sits under the buttons, and it names **investment** among the things this is not advice on |

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
| 19 | Check the list itself | Five days, ranked best first, each with its day pillar, solar term, score, one-line verdict and **the name of the module the score belongs to** |
| 19a | Pick another activity (signing, love & weddings, moving, body, launches) | The list re-ranks for it; the lead line under the section title says what the days are ranked for; the row still shows the **lead module's** own score, with that module's name under it |
| 19b | Return to "a financial move" | The ranking is exactly the windfall ranking again |
| 19c | Open the day at the top of the list | The score on the plate is **the same number** the list showed — the list never quotes a number the reading does not carry |
| 19d | Close the app and open it again | The activity is still the one you picked — it is remembered, so the list is ranked the same way tomorrow |
| 20 | Pick the top day (with "a financial move" selected) | The date selector jumps to it; the windfall score on the plate matches the list |
| 21 | Check the hour | On a chosen date the reading has **no hour** — no hour pillar, no hour-officers, no hour windows |

### 2.4a Notifications, the widget and reminders

| # | Action | Expected |
|---|---|---|
| 21a | Tap the bell in the top bar | Android 13+ asks for the notification permission; the bell turns on |
| 21b | Next morning at ~08:00 | One silent notification with the day's score and headline; tapping it opens the app |
| 21c | Reboot the phone | The daily work is rescheduled by the boot receiver |
| 21d | Tap the bell on a favourable day | The bell fills in; on that morning a reminder notification posts, and the mark clears itself |
| 21e | Place the home-screen widget | Today's score, verdict, headline and best window; the launcher refreshes it every 30 minutes, and the app refreshes it whenever it opens |
| 21f | Revoke the notification permission in Settings, then reopen the app | The bell turns **itself off** and a banner says the reminder cannot reach you — the app must not go on promising a reminder it cannot deliver |
| 21g | Tap the bell and **deny** the permission | The bell stays off and a banner explains that notifications are off and where to turn them on |
| 21h | Mark a day for a reminder and deny the permission | The day is not marked and a banner says the reminder cannot fire |
| 21i | Wait for Sunday evening (~20:00) with the bell on | One silent notification names the best day of the **coming calendar week** — Monday to Sunday — with its score and verdict; the same bell governs it, so turning the bell off cancels it too |

### 2.4b The day at a glance

The three chips under the plate: the ten-second read for the reader who opens the app with their
morning tea.

| # | Action | Expected |
|---|---|---|
| 21j | Read the glance row | Three chips — **COLOUR**, **DIRECTION**, **BEST HOUR**; each has a value and a note under it |
| 21k | Compare the colour note with the windfall reading | The colour is the five-phase colour of the day's wealth element, and the note says which phase it is — the listing must not call it a lucky colour |
| 21l | Compare the direction with the windfall reading | The same bearing and the same phase; the note says *why* that direction, and never repeats the value above it |
| 21m | Rotate the device / change the font size | Nothing in the glance is clipped |

### 2.5 The year sign

| # | Action | Expected |
|---|---|---|
| 22 | Open the year-sign sheet | Twelve zodiac branches, one tap each; the birth day below them is optional |
| 23 | Pick one | The plate draws your sign as a hollow ring; the readings gain the harmony / triad / clash / harm note |
| 24 | Change it | The readings change immediately; the ring moves to the new mountain |
| 25 | Clear it | The ring disappears and the readings return to the unpersonalised form |
| 25a | Add a birth day | The readings re-anchor on the birth day's pillar; the home row shows the birth day instead of the animal |
| 25b | Set both a year sign and a birth day | The birth day wins; removing it falls back to the year sign |
| 25c | Open the birth-day calendar | Today and the past are selectable, **the future is greyed out** — a birth day in the future would anchor every reading on a day that has not happened |
| 25d | Read the **year officer** card on the home screen | It names the year — "Year of the Horse · 2026" — and offers to set the sign |
| 25e | Check that year in late January | The card still says the **old** year: the year turns at Li Chun, not on 1 January |
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

### 2.8a The Play review ask

The app asks for a review only after it has been opened on five distinct days **and** the reader has
unlocked the app, and at most once every thirty days. The day's own score plays **no** part: an app
that asks for a review on a lucky day and never on an ordinary one is asking the reader to rate the
plate. Google's quota decides whether the card actually appears.

| # | Action | Expected |
|---|---|---|
| 51a | Open the app on five distinct days and unlock it | The Play review card may appear; if Google's quota is spent, nothing shows — both are correct |
| 51a1 | Open the app on five distinct days but never unlock it | **No** review ask — the reader has not used the thing they would be reviewing |
| 51a2 | Open the app on an ordinary day, with the unlock bought | The ask may still appear — the day's luck is not the trigger |
| 51b | Keep opening the app afterwards | The ask does not repeat within thirty days of the last one |
| 51c | Try the same on a **sideloaded** build | The flow is prepared but Google never shows the card — expected; the review API only displays for Play-installed apps |

### 2.9 Sharing a card

| # | Action | Expected |
|---|---|---|
| 52 | Open a reading and tap **Share as image** | The share sheet opens with the card as a PNG; the screen does not freeze while the image is written |
| 52a | Share the same reading twice, and share a different reading | Each share gets its **own** file — the second share must not hand out the first picture |
| 53 | Tap **Save image** | The card lands in `Pictures/DaoFortune`; a toast says so |

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
  currency conversion

---

## Pre-release gate

Everything below must be true before an upload. If any line is unchecked, stop.

| Gate | How to verify | Status |
|---|---|---|
| Engine parity green | `python tools\reference\luopan_reference.py` → ALL PASS | ☐ |
| Unit tests green | `:app:testDebugUnitTest` → 110/110 | ☐ |
| Lint has zero errors | `:app:lintRelease` → `app/build/reports/lint-results-release.txt` | ☐ |
| Bundle validates | `bundletool validate` → exit 0 | ☐ |
| 16 KB page size | `python tools\qa\check_16kb_pages.py dist\app-release.aab` → PASSED | ☐ |
| Manifest correct | package / versionCode / minSdk 26 / targetSdk 36 / no location permission | ☐ |
| Signature present and valid | `keytool -printcert` | ☐ |
| Source zip has no secrets | no `keystore.properties`, no `.jks` | ☐ |
| Store folder is clean | `docs/store/screenshots/` holds exactly 6 PNGs | ☐ |
| Store screenshots match the app | `python tools\qa\check_screenshot_sync.py` → SCREENSHOT SYNC OK | ☐ |
| Store screenshots are current | the gate's byte comparison of a fresh render → all six match | ☐ |
| Privacy policy page is current | `python tools\docs\make_privacy_html.py --check` | ☐ |
| Copy matches the app | free previews / first free reading / one-off unlock / year sign / dream described the same way in the listing, the privacy policy and in-app | ☐ |
| Real-device pass done | Layer 2 complete, including the purchase | ☐ |
| Keystore safe | `dao-fortune-release.jks` + `keystore.properties` backed up **off this machine** | ☐ |
| Play App Signing | enrolled in Play Console → App integrity | ☐ |
| Console products created | `dao_fortune_full_access` (one-off) is live — it is the only product the app knows about | ☐ |
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

