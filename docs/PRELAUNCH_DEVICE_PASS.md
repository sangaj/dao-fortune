# The pre-launch device pass

One evening with a real phone, top to bottom. Every line here is the short form of a row in
`docs/TESTING.md` (Layer 2) — the row numbers point back to the details and the why. Install the
build from Play **internal testing**, not by sideloading: the Play build is the artifact that ships.

## 1. First run — rows 1–5

- ☐ Launches, plate renders, nothing in logcat
- ☐ Date, time and time zone correct; solar term matches an independent source
- ☐ Four pillars sensible; sun, wealth and avoid markers on the right mountains

## 2. The one free reading — rows 12–14, 12a–12c

- ☐ Today's Fortune shows its score; the other four show PREVIEW plus a verdict line
- ☐ Open Today's Fortune: the full reading, and the free-reading counter is untouched
- ☐ Tap another module: the "Your one free reading" dialog asks first — dismiss it and nothing is
  spent; tap again and it asks again; confirm and only then is the reading spent
- ☐ After it is spent: locks on the four paid modules, Today's Fortune never locks

## 3. The paywall and the purchase — rows 6–11, 43–51

- ☐ Open a second module → the paywall; the price is your **local currency**, never a hard-coded figure
- ☐ Buy → sheet closes, the module you wanted opens; afterwards no paywall anywhere
- ☐ Uninstall, reinstall → **Restore purchase** brings the unlock back
- ☐ Airplane mode: the paywall says Play could not be reached — it must never pretend "no purchase"
- ☐ Network back on: it reconnects on its own (2s, 4s, 8s, 16s, 30s) and the price appears
- ☐ Restore on an account that bought nothing → "No previous purchase found…", no crash
- ☐ Buy, force-stop, reopen → still unlocked; install on a second device → unlocked on first launch
- ☐ After the free reading is spent, the home banner says so and can be dismissed

## 4. The tip jar — rows 11a–11b

*(Needs `dao_fortune_tip` created in the Console; without it the row must simply not appear.)*

- ☐ The tip row sits under **Restore purchase**, priced locally, saying plainly it unlocks nothing
- ☐ Leave a tip → payment completes → thank-you banner; locked cards stay locked; the jar can be
  filled again

## 5. Dream & Omens — rows 28–34, 29a–29b

- ☐ Write a dream, tap **Read the dream** → the same "Your one free reading" dialog asks first;
  dismiss → nothing spent; confirm → the reading appears
- ☐ The reading: score, headline, ink-wash plate, insights, advice, the factors behind the score
- ☐ Read the same dream again → the plate is **identical** (seeded, not random)
- ☐ Write something unreadable → it says so; it must not invent an interpretation
- ☐ Privacy: the text is in no file under the app's storage; closing the sheet erases it
- ☐ Free reading spent → the dream goes to the paywall; buying reads it with your words still there

## 6. Personalisation — rows 22–27

- ☐ Pick / change / clear the year sign; the hollow ring follows on the plate
- ☐ Add a birth day → the readings re-anchor on its pillar; the birth day wins over the year sign,
  and removing it falls back
- ☐ Stored locally only; excluded from backup — a device transfer drops them, the unlock survives

## 7. Notifications, the widget and reminders — rows 21a–21f

- ☐ Top-bar bell → permission ask on Android 13+ → one silent notification next morning ~08:00;
  tapping it opens the app
- ☐ Reboot the phone → the daily work is rescheduled
- ☐ Bell on a favourable day → reminder that morning; the mark clears itself
- ☐ Home-screen widget: today's score, verdict, headline and best window; it refreshes
- ☐ Revoke the permission → no crash, the bells still toggle

## 8. Looking ahead — rows 15–21

- ☐ Chevrons and the calendar walk the days; a future or past day reads as a day plate
- ☐ Favourable Days: five days, ranked, each with pillar, term, score and verdict
- ☐ Pick the top day → the plate matches the list; a chosen day has **no hour**

## 9. Edge cases — rows 35–42

- ☐ Airplane mode: everything works except the purchase
- ☐ Another time zone; the 12-hour clock; the clock set a day forward — the pillars follow
- ☐ Dark and light themes; the largest system font — nothing clipped
- ☐ Resize on a foldable or tablet — the layout holds
- ☐ Clear app data → the free reading comes back (expected, documented)

## 10. Console gates before the upload

- ☐ `dao_fortune_full_access` (one-off) is live; `dao_fortune_tip` (consumable) created — or
  deliberately skipped, which simply hides the tip row
- ☐ Privacy policy on a public URL, linked in App content; Data safety answered "no data collected,
  no data shared"; content rating questionnaire done
- ☐ `dao-fortune-release.jks` and `keystore.properties` backed up **off this machine**;
  Play App Signing enrolled
- ☐ `versionCode` higher than any previous upload

When every box is ticked: promote the internal release to production.
