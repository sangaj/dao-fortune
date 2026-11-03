# Google Play listing — Dao Fortune

All copy below is ready to paste into Play Console. Character counts are noted where Play enforces a limit.

---

## App name (30 characters max)

```
Dao Fortune: Luopan Wisdom
```
*(26 characters ✓)*

The full brand line — **Dao Fortune — Daily Luopan Wisdom** — is 32 characters, one over Play's limit,
so it cannot be the listing title. It is kept where it still works: the app's own header, the 512×512
icon, the feature graphic and the short description all carry it. If you would rather use the long
form, `Dao Fortune: Daily Wisdom` is 26 characters and keeps the "daily" promise — but check it still
fits your brand before using it.

## Short description (80 characters max)

```
Ancient Chinese luopan divination. Is today a day for a lucky windfall?
```
*(71 characters ✓)*

## Full description (4000 characters max)

```
Dao Fortune reads your day through the twenty-four mountains of the ancient Chinese luopan — the compass of the Daoist tradition — and tells you what the plate says.

Is today a day to expect an unexpected gain, push a deal, open a conversation, or hold still and keep your hands off the purse?

FIVE READINGS, ONE PLATE
• Today's Fortune — the quality of the day: your day element, the day's officer and the twelve double-hours.
• Windfall & Wealth — whether money will arrive by surprise, which direction it comes from, and when the hours are open.
• Love & Bonds — the emotional weather: the harmony or clash between your day branch and the hour.
• Career & Ambition — whether to push, pitch, sign or hold, and which hours the plate favours.
• Body & Balance — which of the five phases is loudest in your body today, and how to settle it.

LOOK AHEAD
• Favourable Days — the next sixty days scored on the same rules and ranked, so you see which dates the plate favours.
• Any date you choose — open the calendar, and that day's plate is drawn for you.
• Your year sign — pick your zodiac branch once; every reading is measured against your position.

DREAM & OMENS (梦占)
• Write what you remember of a dream, and the plate reads it against the night it belongs to.
• The classical images are found in your own words: the direction you walked, the animal that followed you, the number, the colour, the hour you woke.
• The reading asks whether the omen answers the day or argues with it — the same branch relations, elements and rules as the modules above.
• The picture is drawn on your phone in ink wash, on the mountains where those images belong. Your words never leave the device — nothing uploaded, nothing stored, no AI service involved.

A REAL ENGINE, NOT A RANDOM NUMBER
Everything is computed on your device from the classical rules:
• The sun's true position on the twenty-four mountains, for your exact date and time.
• The four pillars of the day — year, month, day and hour — from the sixty-cycle, with the year turning at Li Chun, the day at 23:00.
• The twelve day-officers (Jian, Chu, Man, Ping, Ding, Zhi, Po, Wei, Cheng, Shou, Kai, Bi) that set the day's tone.
• The twelve hour-officers, so you get the exact good windows, not a vague verdict.
• Your wealth element against the day's element, the sun's mountain, and the mountain opposite your day branch.
• Nobleman stars, the Star of Motion, Peach Blossom, the void of the ten-day frame, and your day element's strength.

EVERY READING SHOWS ITS WORKING
Each score comes with the factors behind it — you see which rule moved the number, and by how much. Never a number without its reason.

BUILT FOR THE ENGLISH-SPEAKING READER
Every term keeps the classical vocabulary: English rendering, original transliteration. The tradition is not flattened into generic horoscope language.

HONEST BY DESIGN
• No account, no sign-up, no email. Everything is computed on your device.
• No analytics, no advertising, no tracking, no crash reporting.
• The only third-party components are Google's Play Billing and Play In-App Review libraries. Nothing is sold on to anyone.

PRIVACY
Dao Fortune collects no personal data. It never asks for your name or your age. You may optionally give your birth day to anchor the reading — it stays on this phone, is excluded from backup and is never transmitted; your year sign is stored the same way. The dream you write is read and drawn here and erased when you close the sheet. A reading you share is an image you send yourself. Notifications are local and can be turned off.

A NOTE ON WHAT THIS IS
Dao Fortune is a cultural and entertainment product built on classical Chinese divination — not financial, medical, legal, investment or psychological advice. It promises no outcome, and you remain responsible for your own choices.

Today's Fortune is free every day, in full. Your first reading is free too. One payment unlocks everything, every day — no subscription.
```

*(3945 characters. Play caps the full description at 4000 — keep it under. The count is checked by
`tools/qa/check_listing_lengths.py`, which the release gate runs.)*

The last two lines cover the paywall model in the listing itself. Keep them accurate: **Today's
Fortune (the overall module) is free every day, in full** — score, factors, advice and hours — and the
first *full* reading of anything else (another module, or a dream) is free as well. That is **one free
reading in total**, not one per module: the app keeps a single free-reading flag, so the second paid
reading of the day reaches the unlock. If you ever change that balance, change this copy with it —
Play's deceptive-behaviour policy is easier to defend when the listing says exactly what the app does.

---

## Category and contact

| Field | Value |
|---|---|
| App or game | **App** |
| Category | **Lifestyle** (secondary: *Entertainment*) |
| Tags | Divination, Horoscope, Culture, Lifestyle |
| Content rating | questionnaire completed: no violence, no sexuality, no user interaction, no location sharing |
| Target audience | 18+ (recommended); contains no adult content |
| Contains ads | **No** |
| In-app purchases | **Yes** — `dao_fortune_full_access` (one-off, not a subscription) |
| Data safety | **No data collected, no data shared by the app.** The app has no account and never asks for a name or an age. Play's *Personal info* category includes a date of birth, so the optional birth day is named here: the reader enters it only if they choose to, it is stored in a private preferences file on the device, it is excluded from Android backup, and it is never transmitted. On Play's form, "collection" means data leaving the device — nothing here does — so the developer's own data types stay empty. The optional year sign is stored the same way, and the dream text written in Dream & Omens is read on the device, drawn on the device and erased when the sheet closes: it is never written to storage and never transmitted. The only third-party components are Google's Play Billing and Play In-App Review libraries and the Play services components they bundle; those are processed by Google under Google's own privacy policy. If the form offers a separate option for data handled by Google Play services, select that one. |
| Permissions | The app declares two permissions itself: `POST_NOTIFICATIONS` — Android asks you for it the first time you turn on the daily reading (which also brings the Sunday-evening week-ahead note) or a day reminder, and you can revoke it in Settings — and `RECEIVE_BOOT_COMPLETED`, to put the standing reminders back after a restart. The bundled libraries add `INTERNET` and `ACCESS_NETWORK_STATE` (the Play Billing library, to reach the Play Store), `WAKE_LOCK` and `FOREGROUND_SERVICE` (WorkManager, for scheduled work the app does not start in the foreground), and `com.android.vending.BILLING` to talk to the Play Store. No location, camera, microphone, contacts or storage permission is declared or requested. |
| Government app | No |
| Financial features | Declare "No" — the app does not offer financial services, trading, or money transfers. The disclaimer explicitly states it is not financial advice. |
| Health features | Declare "No" — the Body & Balance module is wellness/cultural and is not a health feature. |

---

## Graphics checklist

| Asset | Requirement | File |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit | `docs/store/icon_512.png` |
| Feature graphic | 1024 × 500 PNG/JPEG | `docs/store/feature_graphic_1024x500.png` |
| Phone screenshots | 2–8, 16:9 or 9:16, each side ≥ 320 px | `docs/store/screenshots/` |
| Short description | ≤ 80 characters | above |
| Full description | ≤ 4000 characters | above |

---

## Release notes (v1.0.0, English)

```
First release.

• Five readings on the twenty-four mountain plate: fortune, windfall, love, career, body.
• At a glance: lucky colour, best direction, best hour.
• The Year Officer: your sign against the year.
• Favourable Days, ranked for your plans.
• Dream & Omens: write your dream, the plate reads it.
• Sunday evening: your best day of the coming week.
• Your year sign personalises every reading.
• Today's Fortune free daily; your first reading free too.
• One-off unlock, no subscription.
```

*(497 characters. Play caps release notes at 500 — keep it under.)*

---

## Store listing notes (internal, not for the listing)

- The free-reading mechanic ("Today's Fortune always free in full; one free reading in total for
  everything else; every later reading paid") is a **one-off in-app product**, so Play Billing is used.
  Do not also list the app as a paid app — one model only.
- **Never say "the first reading of each module is free".** The app keeps a *single* free-reading flag
  (`data/AccessState.kt`), so only one paid reading is free in total. Promising one per module is
  exactly the kind of mismatch Play's deceptive-behaviour policy looks for.
- Google Play's *Deceptive Behaviour* policy restricts fortune-telling apps that promise outcomes. The listing and the in-app copy avoid guarantees ("the plate says", "the reading points to"), and both the listing and the About sheet carry the entertainment/advice disclaimer. Keep that wording if the listing is edited.
- The Data safety form should answer **"No data collected"** and **"No data shared"**. The year sign and
  the optional birth day live only in an on-device preferences file that is excluded from backup, so
  nothing is "collected" in Play's sense. The purchase is handled by Google Play, which is documented in
  the privacy policy as a third party.
- The privacy policy must be hosted on a public URL and linked from Play Console → App content → Privacy policy. `docs/PRIVACY_POLICY.md` is the source; host it on GitHub Pages or the developer site.
