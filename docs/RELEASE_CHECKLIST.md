# Release checklist — Dao Fortune

Everything that has to happen between "the code is ready" and "the app is on Google Play".

---

## 1. Code state (already done on this machine)

| Item | Where |
|---|---|
| Android toolchain (portable) | `E:\Minimax\toolchain` — JDK 17, SDK platform 36, build-tools 36.0.0, Gradle 8.11.1 |
| Project | `E:\Minimax\dao-fortune` |
| Release keystore | `E:\Minimax\dao-fortune\dao-fortune-release.jks` |
| Keystore properties | `E:\Minimax\dao-fortune\keystore.properties` |
| Signed bundle | `app\build\outputs\bundle\release\app-release.aab` |
| Test plan | `docs\TESTING.md` — the three layers, and the pre-release gate |

> **Keep the keystore and `keystore.properties` somewhere safe and private.** If the upload key is lost,
> the app can never be updated under this Play listing. The passwords are in `keystore.properties` —
> change them and re-sign before your first production upload if you prefer your own.

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
> "no subscription" — keep that promise or change the copy.

### 2.3 Target level

`compileSdk` / `targetSdk` are **36**. Google Play requires new listings to target API 36 as of
August 2026 — this build already complies. No action needed, but verify in Console →
**Release → App bundle explorer** after the first upload.

### 2.4 Privacy and data safety

* **App content → Privacy policy** — host `docs/PRIVACY_POLICY.md` on a public URL (GitHub Pages is
  fine) and paste that URL in.
* **App content → Data safety** — answer:
  * Does your app collect or share any required user data types? → **No**
  * Is all user data encrypted in transit? → *N/A* (there is no user data)
  * Do you provide a way for users to request that their data be deleted? → *N/A*
  * Data types: leave empty
* The purchase is processed by Google Play. Disclose that in the privacy policy (already done).
* **The optional year sign** is a preference on the device, excluded from Android's backup and never
  transmitted — so it is not collected data and the form stays empty. It is described in the privacy
  policy and in the listing anyway, so the form, the policy and the app all tell the same story.

### 2.5 Content rating

Complete the questionnaire. Expected outcome: no violence, no sexuality, no controlled substances,
no user interaction, no location sharing. **Do not tick anything about gambling** — the app makes no
wagers and no real-money transactions. The Content rating questionnaire's gambling category asks about
games where you can win real money; that does not apply here.

### 2.6 Target audience and content

Pick **18+** (recommended). The app contains no adult content but reads adult themes (money,
relationships), so 18+ avoids any friction with the advertising ID requirement.

### 2.7 App access

All functionality is available without credentials, so leave the app access section empty. If you
later add a reviewer's shortcut, note that the **first reading is free and every later reading hits
the paywall** — a reviewer should still be able to see a full reading.

### 2.8 Ads

**No ads.** There is no advertising SDK in the app.

### 2.9 Government apps / financial features / health features

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

Upload the five phone screenshots in that order. Nothing else belongs in `docs/store/screenshots/` —
the Robolectric capture folder writes to `build/screenshots/` on purpose, so a test run can never
leave stray files in the folder you upload from.

Both graphics are generated from code:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_assets.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1
```

The screenshots are rendered from the real engine output (`docs/store/reading_sample.json`, produced
by the unit tests), so the text in them is genuine app output for 31 October 2026.

---

## 4. Upload

1. Console → **Release → Testing → Internal testing → Create new release**
2. Upload `app\build\outputs\bundle\release\app-release.aab`
3. Play shows a warning that the bundle is signed with a **new key** — that is expected for the first
   upload of a new app; there is no existing Play key to match.
4. Fill in the release notes from `docs/PLAY_STORE_LISTING.md`.
5. **Start the rollout** to internal testing first, install the app from Play on a real phone, and
   check:
   * the plate shows today's date and the correct solar term
   * all five verdict headlines are visible on the home screen before any payment
   * the first full reading opens for free
   * the second reading opens the paywall
   * the price shows in your local currency and the purchase completes
   * after purchase, every module opens without a paywall
   * the app still opens with no network (airplane mode)
   * the date selector moves the plate day by day, and a chosen date reads "that day", not "today"
   * the Favourable Days list shows five ranked days, and tapping one selects that date
   * the year sign sheet offers the twelve zodiac branches and never asks for a date of birth
   * setting a year sign changes the readings and draws the sign on the dial
6. Promote to production when the internal test looks right.

---

## 5. What to check on a real device that could not be checked here

There is no emulator or physical device on this machine (the CPU has no hardware virtualisation), so
the following still need a human pass:

* **Real-device rendering** — the screenshots in `docs/store/` are rendered by the engine and laid out
  by the same design, but they are not a device capture. Re-take them on a phone if you want
  Play-authentic screenshots.
* **The purchase flow** — Play Billing cannot be exercised without a real device with a Play account
  and a licensed test account.
* **Edge cases** — a device in a different time zone, a device set to a 12-hour clock, and a device
  whose clock is deliberately wrong.

---

## 6. Known limitations to be aware of

* **Solar-term instants** are accurate to roughly ±0.01° (a few minutes). A term landing within
  minutes of midnight can be attributed to the neighbouring day in rare cases.
* **The free reading is tracked locally.** Clearing the app's storage resets it. It survives a device
  transfer because it is included in Android's backup.
* **Locked modules still show a verdict line.** The teaser headline of all five readings is free;
  the score, the factors, the hours and the advice are behind the unlock. If you change that split,
  change `docs/PLAY_STORE_LISTING.md` with it.
* **A chosen date is read without the hour.** Only today has an hour pillar, hour-officers and hour
  windows; any other date is a "day plate", because a day that has not happened yet has no hour.
* **The year sign is a zodiac branch, not a birth date.** The app never asks for personal information.
  It is stored on the device in its own preferences file, excluded from Android's backup, and can be
  cleared in one tap — which is what keeps the Data safety form honest.
* **The app never forecasts an amount.** It reports whether the classical configuration supports an
  unexpected gain and where to look — deliberately, because Play's deceptive-behaviour policy
  restricts fortune telling that promises outcomes. Keep the disclaimer wording intact.
