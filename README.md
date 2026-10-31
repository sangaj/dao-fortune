# Dao Fortune — Daily Luopan Wisdom

An Android app that reads your day through the **twenty-four mountains of the Chinese luopan** and
answers one practical question: is today a day to expect an unexpected gain, to push a deal, to open
a conversation, or to hold still?

Everything is computed on-device from classical rules. There is no server, no account, no analytics,
and no random number anywhere in the reading.

![App icon](docs/store/icon_512.png)

---

## What the app does

| Module | The question it answers |
|---|---|
| **Today's Fortune** | What is the overall quality of the day for me? |
| **Windfall & Wealth** | Is unexpected money likely to arrive today, from which direction, and in which hours? |
| **Love & Bonds** | Is this a day to open up, or a day to say it carefully? |
| **Career & Ambition** | Push, pitch, sign — or hold? |
| **Body & Balance** | Which of the five phases is loudest in my body today? |

Every reading shows **the factors that produced its score**, so the number is never a black box.

### Around the reading

| Feature | What it does |
|---|---|
| **Favourable Days** | The next sixty days are scored on the same day-plate rules and ranked by the windfall reading, so you can see *when* to move, not just whether today is good. |
| **Any date** | Step day by day or open the calendar. A day that is not today is read as a **day plate** — no hour is involved, because a day that has not happened yet has no hour. |
| **Your year sign** | Choose your zodiac branch once and every reading is measured against your own position on the plate: harmony, three-harmony, clash or harm against the day's branch, and the element tone between your branch and the day's. |

The year sign is **not** a birth date, a birth chart or a BaZi reading. The app never asks for your
birthday, your name or your age — it asks which of the twelve branches you were born under, which is
how the *nian ming* works in this tradition. That single choice is stored on your device in its own
preferences file, is excluded from Android's backup, and is never transmitted.

---

## The engine

The engine lives in `app/src/main/java/com/laki/daofortune/core/` and is pure Kotlin with no Android
dependencies, so it is fully unit-testable.

### 1. The plate (`Luopan.kt`)

The twenty-four mountains are laid out clockwise from north, each covering 15° of bearing:

```
Zi Gui Chou Gen Yin Jia Mao Yi Chen Xun Si Bing Wu Ding Wei Kun Shen Geng You Xin Xu Qian Hai Ren
```

Each mountain carries its element, its trigram (Kan, Gen, Zhen, Xun, Li, Kun, Dui, Qian), its
three-yuan dragon (Di / Ren / Tian) and its yin-yang polarity — the classical luopan attributes.

The **sun's apparent longitude** puts the day on the plate. The mapping is `bearing = longitude + 90°`,
which places the equinoxes on Mao (due east) and You (due west) and the solstices on Wu (due south) and
Zi (due north).

### 2. The calendar (`CalendarEngine.kt`, `SolarTerms.kt`)

* **Day pillar** — counted from the Jia-Zi day of 1 October 1949; the day turns at **23:00**, when the
  Zi hour opens the next day.
* **Year pillar** — turns at **Li Chun** (solar longitude 315°), not on 1 January.
* **Month pillar** — turns at the twelve *jie* sections, with the month stem fixed by the year stem
  (the Wu Hu Dun rule: a Bing or Xin year opens with Geng-Yin, and so on).
* **Hour pillar** — the Wu Shu Dun rule: a Jia or Ji day opens with Jia-Zi, Yi or Geng with Bing-Zi, and
  so on.
* **Solar terms** — computed from the sun's apparent longitude (Meeus' formulae, with the Espenak–Meeus
  ΔT polynomials). Accuracy is about ±0.01°, i.e. a few minutes of time.

### 3. The officers

* **Twelve day-officers** — Jian, Chu, Man, Ping, Ding, Zhi, Po, Wei, Cheng, Shou, Kai, Bi, positioned
  by the month's branch. These set the day's basic tone.
* **Twelve hour-officers** — Qing Long, Ming Tang, Tian Xing, Zhu Que, Jin Gui, Tian De, Bai Hu, Yu Tang,
  Tian Lao, Xuan Wu, Si Ming, Gou Chen, rotated by the day's stem. These give the exact good windows
  rather than a vague verdict.

### 4. The supporting stars

Nobleman (Tian Yi Gui Ren), the Star of Motion (Yi Ma), Peach Blossom (Xian Chi), Red Phoenix and
Heaven's Blessing, the void of the ten-day frame (Xun Kong), and the strength of the day element within
the month (Wang, Xiang, Xiu, Qiu, Si).

### 5. The wealth logic (`FortuneEngine.kt`)

Your **wealth element** is the phase you control. The windfall module asks:

* Is the wealth element in season, or is the season eating it?
* Does the sun's mountain match, feed, drain or cut the wealth element?
* Do the sun's bearing and the wealth bearing coincide, sit a quarter turn apart, or oppose each other?
* Is the *unexpected* wealth branch (Pian Cai — the side branch of your wealth element, which is what
  windfall money actually is) falling into the void?
* Are you strong enough to hold it — "wealth beyond the body's strength" is the classic warning.

### 6. The reader's own position (`Natal.kt`)

The querent is identified by their **year branch** (*nian ming*), which is what this tradition uses:

| Relation | Score | Meaning |
|---|---|---|
| Harmony (六合) | +8 | The day's branch pairs with yours |
| Three-harmony (三合) | +5 | The day's branch joins your frame |
| Clash (六冲) | −9 | The day's branch stands opposite yours |
| Harm (六害) | −4 | A quiet friction rather than an open row |
| Neutral | 0 | The day leaves you neutral |

On top of that, an element tone: the same element (+4), the day feeding your year element (+5), your
year element carrying the day (+3), the day carrying yours (+3), or the day cutting across yours (−5).
The natal factors ride on **every** module, and the windfall module gets one more: whether your sign
sits in the wealth sector.

### 7. Looking ahead (`FavourableDates.kt`)

Sixty day plates are scored off the main thread and ranked by the windfall reading. Each entry carries
the date, the day pillar, the solar term, the score and a one-line verdict. Tapping a day selects that
date's plate.

### Two kinds of plate

* **Now plate** — today, with the hour: the hour pillar, the hour-officers and the twelve double-hours
  are all in play.
* **Day plate** — any other date, without the hour. `LuopanSnapshot.dayOnly` guards every hour-dependent
  factor, and the prose switches from "today" to "that day" so a future reading never speaks in the
  present tense about itself.

---

## Verification

`tools/reference/luopan_reference.py` is a **second, independently written implementation** of the same
rules in Python. It carries its own anchor tests and emits the golden file that the Kotlin unit tests
assert against:

```bash
python tools/reference/luopan_reference.py     # runs the self-checks, writes the golden TSV
./gradlew :app:testDebugUnitTest               # checks Kotlin against the golden values
```

The self-checks cover the 1949 Jia-Zi epoch, the 1984 Jia-Zi year start, the year turning at Li Chun,
the day turning at 23:00, the five Wu Shu Dun hour-stem cases, the hour-officer rotation, the solar-term
dates and times against published equinox and solstice instants, the sun's palace at each solstice and
equinox, and the void branches.

Two golden files come out of it: `golden_pillars.tsv` (742 rows — two years of daily samples) and
`golden_natal.tsv` (360 rows — the year-sign relations and element tones). The Kotlin suite is **31
tests**: `EngineTest` (25), `SampleReadingDump` (1) and the Robolectric `ScreenshotTest` (5).

---

## Building

### Requirements

* JDK 17
* Android SDK with **platform 36** and **build-tools 36.0.0**
* Gradle (the wrapper is included)

### Commands

```bash
# unit tests
./gradlew :app:testDebugUnitTest

# lint (errors are release blockers — the build itself does not fail on them)
./gradlew :app:lintRelease

# debug APK
./gradlew :app:assembleDebug

# signed release bundle for Play
./gradlew :app:bundleRelease
```

### Checking a release

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\qa\verify_release.ps1
```

Runs the Python reference self-check, the unit tests, lint, `bundletool validate`, the manifest
assertions, the signature check and the store/source-package checks in one go. `docs\TESTING.md`
describes the full three-layer plan, including the part that has to be done on a real phone.

### Signing

Release signing reads `keystore.properties` in the project root:

```properties
storeFile=dao-fortune-release.jks
storePassword=…
keyAlias=dao-fortune
keyPassword=…
```

That file and the `.jks` are in `.gitignore`. **Keep the keystore safe — if you lose it you can never
update the app on Play under this listing.** Generate your own with:

```bash
keytool -genkeypair -v -keystore my-release.jks -alias my-alias \
  -keyalg RSA -keysize 4096 -validity 10950
```

A validity of 10950 days (30 years) is required: Play requires the signing key to remain valid until
22 October 2033.

> The Gradle wrapper points at `mirrors.cloud.tencent.com` because the official `services.gradle.org`
> CDN is unreliable from mainland China. Swap `distributionUrl` back to
> `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` if you prefer.

---

## Publishing to Google Play

1. **Create the app** in Play Console with package id `com.laki.daofortune` and the listing title
   `Dao Fortune: Luopan Wisdom` (26 characters — Play's title limit is 30, and the full brand line
   *Dao Fortune — Daily Luopan Wisdom* is 32, so it lives in the icon and the feature graphic instead).
2. **Create the one-off product** `dao_fortune_full_access` (in-app products, not a subscription).
   The id must match `BillingManager.PRODUCT_ID`.
3. **Target API 36** is mandatory for new listings since August 2026 — this project already targets it.
4. **Privacy policy** — `docs/PRIVACY_POLICY.md` is the source; host it on a public URL and link it in
   Play Console → App content → Privacy policy.
5. **Data safety** — answer *no data collected* and *no data shared*. The purchase is processed by
   Google Play, which is disclosed in the privacy policy. The optional year sign is a device-local
   preference, excluded from backup and never transmitted, so it is not collected data.
6. **Listing copy** — `docs/PLAY_STORE_LISTING.md` contains the title, short and full descriptions,
   release notes and the store settings checklist.
7. **Graphics** — `docs/store/icon_512.png` (512×512) and `docs/store/feature_graphic_1024x500.png`
   are generated from code by `tools/store_assets/build_assets.ps1`.

### Why the app shows the verdict free

The reading is computed locally, so the only thing being sold is access to it. The rule is implemented in
`data/AccessState.kt`:

* **All five verdict lines are free.** The home screen shows the one-line verdict of every module
  (marked `PREVIEW`) before any payment, so the user can see what the plate says and decide on
  substance rather than on a blind paywall.
* **The first full reading is free** — score, factors, hours and advice. That is the second click
  after the free preview.
* **After that, every reading needs the one-off unlock** (`dao_fortune_full_access`, $9.99 in every
  market; the app reads the converted price straight from Play Billing).

The flag is kept in shared preferences and is included in Android's backup, so a device transfer does
not reset it. Clearing the app's storage does reset it — that is a deliberate trade-off of a purely
local model, and it is noted here so you are not surprised.

![Home screen with free verdict previews](docs/store/screenshots/01_home.png)

---

## Known limitations

* **Solar-term instants** carry roughly ±0.01° of error (a few minutes). A term that falls within
  minutes of midnight can therefore land on the neighbouring day in rare cases.
* **One local flag** guards the free reading; it is not tied to a Play account.
* The windfall module deliberately does **not** forecast amounts. It reports whether the classical
  configuration supports an unexpected gain and where to look — it never promises money.
* The app is explicitly **not** financial, medical, legal or psychological advice, and the listing and
  the in-app copy both say so, because Google Play's deceptive-behaviour policy applies to fortune
  telling that promises outcomes.

---

## Layout

```
app/src/main/java/com/laki/daofortune/
  core/         engine: Model, SolarTerms, CalendarEngine, Luopan, FortuneEngine, Narrative,
                Natal (year sign), FavourableDates (the forward scan)
  billing/      Play Billing wrapper (com.android.billingclient:billing:9.1.0)
  data/         AccessState — free reading counter and unlock flag; ProfileState — the year sign
  ui/           Compose UI: theme, luopan dial, screens, date selector and year-sign sheet
  vm/           MainViewModel
tools/
  reference/    independent Python implementation + golden data generator
  store_assets/ GDI+ generator for the Play icon, feature graphic and screenshots
  qa/           verify_release.ps1 — the one-command Layer 1 gate
  release/      package_release.ps1 — signed bundle + source zip
docs/
  PRIVACY_POLICY.md
  PLAY_STORE_LISTING.md
  RELEASE_CHECKLIST.md
  TESTING.md
  store/        generated graphics
```
