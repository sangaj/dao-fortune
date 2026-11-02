# Release checklist — Dao Fortune

Everything that has to happen between "the code is ready" and "the app is on Google Play".

---

## 1. Code state (already done on this machine)

| Item | Where |
|---|---|
| Android toolchain (portable) | `E:\Minimax\toolchain` — JDK 17, SDK platform 36, build-tools 36.0.0, Gradle 8.11.1, Git 2.56 |
| Project | `E:\Minimax\dao-fortune` |
| Version control | git repository in the project root; `main` branch; the keystore is excluded by `.gitignore` |
| Release keystore | `E:\Minimax\dao-fortune\dao-fortune-release.jks` |
| Keystore properties | `E:\Minimax\dao-fortune\keystore.properties` |
| Second copy of the key | `E:\Minimax\dao-fortune-backup\` — **still on the same drive, see §1.1** |
| Signed bundle | `app\build\outputs\bundle\release\app-release.aab` |
| Test plan | `docs\TESTING.md` — the three layers, and the pre-release gate |

> **Keep the keystore and `keystore.properties` somewhere safe and private.** If the upload key is lost,
> the app can never be updated under this Play listing — unless you have enrolled for **Play App
> Signing** (§1.1). The passwords are in `keystore.properties` — change them and re-sign before your
> first production upload if you prefer your own.

### 1.1 The signing key: back it up, and enrol for Play App Signing

The key currently exists in one place on one machine. A second copy has been made at
`E:\Minimax\dao-fortune-backup\` (key + `keystore.properties` + `BACKUP-README.txt` with the SHA-256
fingerprint), but **a copy on the same drive is not an offline backup** — it only survives an accidental
deletion, not a failing disk.

Do this before the first upload:

1. Copy `E:\Minimax\dao-fortune-backup\` to an encrypted USB stick or encrypted disk image, kept
   physically separate from this PC.
2. Put the `.jks` into a password manager as a secure-note attachment, with the two passwords in the
   same note.
3. **Enrol for Play App Signing** — see §2.2. This is what turns "lost key = dead listing" into
   "lost key = one button in Play Console".

| | Without Play App Signing | With Play App Signing |
|---|---|---|
| Who holds the app signing key | You — the key you upload is the key Google signs with | Google |
| If the upload key is lost | The listing can never be updated again | Play Console → **App integrity → App signing key → Reset upload key** |
| What you must keep | The key, forever | Only the upload key |

Do **not** commit the key to Git — not even to a private repository. `.gitignore` excludes `*.jks` and
`keystore.properties` so that a stray `git add .` cannot publish them.

### 1.2 Version control

The project is a git repository (`main` branch). Before every release:

```powershell
cd E:\Minimax\dao-fortune
git status                 # nothing unexpected, no *.jks, no keystore.properties
git add -A
git commit -m "v1.0.1: …"
git push
```

`dist/` is ignored — the signed bundle and the source zip are rebuilt by `package_release.ps1`, so
they are never committed.

### Checking a release

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\qa\verify_release.ps1
```

Everything that can be verified without a device is checked by that one command. Run it before every
upload — `lint` in particular is configured not to fail the build, so a green Gradle run does **not**
mean the release is clean.

### Rebuilding

```powershell
$env:JAVA_HOME = 'E:\Minimax\toolchain\jdk17'
$env:ANDROID_HOME = 'E:\Minimax\toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = 'E:\Minimax\toolchain\gradle-home'
cd E:\Minimax\dao-fortune
.\gradlew.bat :app:testDebugUnitTest :app:bundleRelease
```

---

## 2. Play Console setup

### 2.1 Create the app

* **Contact email:** `gubingjing@hotmail.com`
* **App name:** `Dao Fortune: Luopan Wisdom`
  (26 characters — Play's title limit is 30, so the full brand line *Dao Fortune — Daily Luopan
  Wisdom* cannot be the title; it stays in the icon, the feature graphic and the in-app header)
* **Default language:** English (United States)
* **App or game:** App
* **Free or paid:** **Free** — the app itself is free, the unlock is an in-app product
* **App category:** Lifestyle
* **Contact email, privacy policy URL, marketing site** — required before the first upload

### 2.2 Create the in-app product

* Console → **Monetize → Products → Create product**
* **Product ID:** `dao_fortune_full_access`  *(must match `BillingManager.PRODUCT_ID`)*
* **Type:** One-time product
* **Name:** `Full Access — Daily Luopan Readings`
* **Description:** Every module, every day. One payment, no subscription.
* **Price:** **$9.99**, the same in every market. Set it once in Play Console; Play converts it to
  each buyer's local currency automatically and shows the converted price on the paywall — the app
  reads that converted price straight from Play Billing, so nothing is hard-coded.
  * Leave regional pricing **off** (Play Console → the product's pricing → "Manage pricing").
  * The app's paywall copy says "One payment. No subscription." — keep the $9.99 figure out of the
    listing description, because the displayed price varies by buyer's currency and a hard-coded
    number in the description can read as misleading.
  * The number used in the store screenshots is `$9.99` (`tools/store_assets/build_screenshots.ps1`).

> Use an **in-app product**, not a subscription. The listing copy in `docs/PLAY_STORE_LISTING.md` says
> "no subscription" — keep that promise or change the copy. `dao_fortune_full_access` is the only
> product the app knows about; there is no second SKU.

### 2.3 Play App Signing — turn this on at the first upload

Console -> the app -> **App integrity -> App signing key -> Enrol for Play App Signing**.

With Play App Signing, Google holds the **app signing key** and you keep only the **upload key**. If
the upload key is lost, Play Console can issue a new one (App integrity -> App signing key -> Reset
upload key) and the listing survives. Without it, the key you upload *is* the signing key, and losing
it means the listing can never be updated again.

Do it **before the first upload** if you can. If the app is already live, the key you uploaded is
kept by Google as the app signing key, and you can request an upload-key reset from the same page.

Back the upload key up offline at the same time - see §1.1.
### 2.4 Target level

`compileSdk` / `targetSdk` are **36**. Google Play requires new listings to target API 36 as of
August 2026 — this build already complies. No action needed, but verify in Console →
**Release → App bundle explorer** after the first upload.

### 2.5 Privacy and data safety

* **App content → Privacy policy** — the policy has to live on a **public URL that a reviewer can
  open**. The repository ships two forms of the same document:

  | File | Use it for |
  |---|---|
  | `docs/PRIVACY_POLICY.md` | the source of truth — edit this |
  | `docs/privacy-policy.html` | the hosted page — generated from the Markdown |

  Regenerate the HTML after every edit to the Markdown:

  ```powershell
  python tools\docs\make_privacy_html.py
  ```

  Then publish it. **It is already published:**

  | What | Where |
  |---|---|
  | Repository | https://github.com/sangaj/dao-fortune (public) |
  | Privacy policy for Play | **https://sangaj.github.io/dao-fortune/privacy-policy.html** |
  | Pages source | branch `main`, folder `/docs` |

  Paste the **sangaj.github.io** link into Play Console → App content → Privacy policy. The old
  custom-domain binding (`gubingjing.com`) was removed from the Pages settings, so the github.io
  address now serves the page directly over HTTPS with no redirect.

  If the policy text changes, regenerate and push:
  ```powershell
  python tools\docs\make_privacy_html.py
  git add -A && git commit -m "policy update" && git push
  ```
* **App content → Data safety** — answer:
  * Does your app collect or share any required user data types? → **No**
  * Is all user data encrypted in transit? → *N/A* (there is no user data)
  * Do you provide a way for users to request that their data be deleted? → *N/A*
  * Data types: leave empty
* The purchase is processed by Google Play. Disclose that in the privacy policy (already done).
* **The optional year sign** is a preference on the device, excluded from Android's backup and never
  transmitted — so it is not collected data and the form stays empty. It is described in the privacy
  policy and in the listing anyway, so the form, the policy and the app all tell the same story.
* **The dream you write in Dream & Omens** is read on the device, drawn on the device and erased when
  the sheet closes. It is never written to storage and never transmitted, so it is not collected data
  either — the privacy policy says so in its own section.
* **The developer contact** in the policy is `gubingjing@hotmail.com`. Make sure that mailbox accepts
  mail from `@play.google.com` — Play reviewers use it if they need to reach you.

### 2.6 Content rating

Complete the questionnaire. Expected outcome: no violence, no sexuality, no controlled substances,
no user interaction, no location sharing. **Do not tick anything about gambling** — the app makes no
wagers and no real-money transactions. The Content rating questionnaire's gambling category asks about
games where you can win real money; that does not apply here.

### 2.7 Target audience and content

Pick **18+** (recommended). The app contains no adult content but reads adult themes (money,
relationships), so 18+ avoids any friction with the advertising ID requirement.

### 2.8 App access

All functionality is available without credentials, so leave the app access section empty. If you
later add a reviewer's shortcut, note that **Today's Fortune is free in full every day** and one
further reading is free — a reviewer should still be able to see a full reading.

### 2.9 Ads

**No ads.** There is no advertising SDK in the app.

### 2.10 Government apps / financial features / health features

All three: **No**. The "Body & Balance" module is a wellness reading derived from the five phases,
not a health feature, and the app explicitly disclaims medical advice.

---

## 3. Graphics

| Asset | File | Requirement |
|---|---|---|
| App icon | `docs/store/icon_512.png` | exactly 512×512, 32-bit PNG |
| Feature graphic | `docs/store/feature_graphic_1024x500.png` | exactly 1024×500 |
| Phone screenshot — first run | `docs/store/screenshots/01_home.png` | 16:9, free verdict previews visible |
| Phone screenshot — unlocked | `docs/store/screenshots/02_home_unlocked.png` | 16:9, all five modules open, year sign set |
| Phone screenshot — looking ahead | `docs/store/screenshots/03_favourable_days.png` | 16:9, the Favourable Days list and the disclaimer |
| Phone screenshot — reading | `docs/store/screenshots/04_windfall_reading.png` | 16:9, full reading sheet |
| Phone screenshot — paywall | `docs/store/screenshots/05_unlock.png` | 16:9, one-off unlock sheet |
| Phone screenshot — dream | `docs/store/screenshots/06_dream.png` | 16:9, the dream sheet with its ink-wash plate |

Upload the six phone screenshots in that order. Nothing else belongs in `docs/store/screenshots/` —
the Robolectric capture folder writes to `build/screenshots/` on purpose, so a test run can never
leave stray files in the folder you upload from.

Both graphics are generated from code:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_assets.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1
```

The screenshots are rendered from the real engine output (`docs/store/reading_sample.json`, produced
by the unit tests), so the text in them is genuine app output for 31 October 2026.

**They are code-rendered, not a device capture.** Play does not forbid that, and the layout is the
app's own layout — but a real phone capture converts better and looks more trustworthy in the store
listing. When you have the phone in your hand, take the six shots on the device instead:

```powershell
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png docs\store\screenshots\01_home.png
```

Shoot the same six states (first run, unlocked, Favourable Days, a full reading, the paywall, the
dream), at 1080×1920 or higher, and keep the file names — the upload order below is the order Play
shows them.

---

## 4. Crash monitoring: Android Vitals, no SDK

The app deliberately ships **no crash-reporting SDK** — no Firebase Crashlytics, no Sentry, no
Bugsnag. That keeps the "no data collected" claim true and the bundle small, and it is not a
compromise: **Google collects the crash and ANR data for you**.

After the first rollout:

* Console → **Monitoring → Vitals** — crash rate, ANR rate and the error reports, per version and
  per device. Play aggregates them; you read them; you fix the version that broke.
* **Android vitals → Crashes** keeps the stack trace (obfuscated — the R8 mapping file is inside the
  bundle, so Play can deobfuscate it for you).
* Vitals also covers **startup time**, **ANRs**, and **wasted memory**, which is enough to tell
  whether a release is healthy without collecting anything on the reader's device.

So: nothing to install. Watch Vitals after each release instead.

---

## 5. Upload

1. Console → **Release → Testing → Internal testing → Create new release**
2. Upload `app\build\outputs\bundle\release\app-release.aab`
3. Play shows a warning that the bundle is signed with a **new key** — that is expected for the first
   upload of a new app; there is no existing Play key to match.
4. Fill in the release notes from `docs/PLAY_STORE_LISTING.md`.
5. **Start the rollout** to internal testing first, install the app from Play on a real phone, and
   check:
   * the plate shows today's date and the correct solar term
   * all five verdict headlines are visible on the home screen before any payment
   * **Today's Fortune opens in full for free**, every day, without spending the free reading
   * one further reading opens for free — the free-reading flag is spent once, not once per module
   * the next reading of those modules opens the paywall
   * the price shows in your local currency and the purchase completes
   * after purchase, every module opens without a paywall
   * the app still opens with no network (airplane mode)
   * the date selector moves the plate day by day, and a chosen date reads "that day", not "today"
   * the Favourable Days list shows five ranked days, and tapping one selects that date
   * the year sign sheet offers the twelve zodiac branches, and the birth day below them is optional
   * setting a birth day re-anchors the readings on that day's pillar, and removing it falls back to
     the year sign
   * setting a year sign changes the readings and draws the sign on the dial
   * the dream sheet reads a written dream, draws its images on the plate, and keeps the text on the
     device only — nothing about the dream appears in any file
   * the dream obeys the same rule as the modules: first reading free, later readings behind the
     one-off unlock
6. Promote to production when the internal test looks right.

---

## 6. What to check on a real device that could not be checked here

There is no emulator or physical device on this machine (the CPU has no hardware virtualisation), so
the following still need a human pass:

* **Real-device rendering** — the screenshots in `docs/store/` are rendered by the engine and laid out
  by the same design, but they are not a device capture. Re-take them on a phone if you want
  Play-authentic screenshots (the `adb` recipe is in §3).
* **The purchase flow** — Play Billing cannot be exercised without a real device with a Play account
  and a licensed test account. This is now the largest remaining unknown in the release.
* **Weak-network behaviour** — the billing wrapper reconnects with a backoff and reports failures
  instead of failing silently, but that path can only be exercised on a device with a flaky
  connection (§2 of `docs/TESTING.md`).
* **Edge cases** — a device in a different time zone, a device set to a 12-hour clock, and a device
  whose clock is deliberately wrong.
* **Notifications and the widget** — the daily reading notification, the favourable-day reminders and
  the home-screen widget can only be exercised on a real device: the permission prompt, the 08:00
  firing, the reboot reschedule, and the widget's rendering against a real launcher (§2.4a of
  `docs/TESTING.md`). The widget's own refresh no longer depends on any of that — the launcher asks
  for an update every 30 minutes — but the rendering still needs a real launcher to be seen.

---

## 7. Known limitations to be aware of

* **Solar-term instants** are accurate to roughly ±0.01° (a few minutes). A term landing within
  minutes of midnight can be attributed to the neighbouring day in rare cases.
* **The free reading is tracked locally.** Clearing the app's storage resets it. It survives a device
  transfer because it is included in Android's backup.
* **Locked modules still show a verdict line.** The teaser headline of all five readings is free;
  Today's Fortune is free in full every day; the score, the factors, the hours and the advice of the
  other four modules are behind the unlock (first reading free). If you change that split,
  change `docs/PLAY_STORE_LISTING.md` with it.
* **A chosen date is read without the hour.** Only today has an hour pillar, hour-officers and hour
  windows; any other date is a "day plate", because a day that has not happened yet has no hour.
* **The anchors are a zodiac branch and, optionally, a birth day.** Both are stored on the device in
  their own preferences file, excluded from Android's backup, never transmitted, and can be cleared in
  one tap — which is what keeps the Data safety form honest.
* **The app never forecasts an amount.** It reports whether the classical configuration supports an
  unexpected gain and where to look — deliberately, because Play's deceptive-behaviour policy
  restricts fortune telling that promises outcomes. Keep the disclaimer wording intact.
* **The dream vocabulary is deliberately narrow.** It reads directions, the twelve animals, the five
  phases, colours, numbers, hours and the sign of money. A dream written in words it does not hold is
  reported as having no usable image rather than interpreted anyway. Do not widen it to cover
  everything — the classical 梦占 lessons that cannot be verified from a source were left out rather
  than guessed.
* **Native code is present after all — but it is compliant.** The app is written in Kotlin and has no
  native code of its own, yet the bundle carries `libandroidx.graphics.path.so` (from Compose's path
  library) for four ABIs. Android 15+ requires 16 KB page size support, so this is checked rather than
  assumed: `tools/qa/check_16kb_pages.py` reads the ELF program headers and every PT_LOAD segment
  measures `p_align = 0x4000`. The check runs in the release gate.
