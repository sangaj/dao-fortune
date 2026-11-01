# Privacy Policy — Dao Fortune

**Effective date:** 31 October 2026
**Application:** Dao Fortune — Daily Luopan Wisdom (Android)
**Package:** `com.laki.daofortune`
**Developer contact:** gubingjing@hotmail.com

## 1. Summary

Dao Fortune collects **no personal data**. The app itself has no account system, no analytics, no
advertising SDKs, and no crash-reporting or tracking libraries. Your reading is calculated entirely on
your device from your device's local clock and time zone.

The app lets you choose a **year sign** — one of the twelve branches of the Chinese zodiac — so that the
plate can be read against your own position. That choice is a single branch stored in a private
preferences file on your device. It is never transmitted, never included in an Android backup, and never
seen by us or by Google. **The app never asks for your date of birth, your name, your age or any other
identifier.** A year sign is not a personal identifier in the sense Play's Data safety form uses: it is
one choice out of twelve, it identifies nobody, and it is deleted the moment you clear it.

The app can also read a dream you write yourself (梦占). **The words of your dream are read on your
device and are never written to storage, never transmitted and never seen by us.** The picture that
accompanies the reading is drawn on the device by the app itself — it is not uploaded, and no cloud
service or AI model is involved. Closing the dream sheet erases the text; there is nothing left behind
to delete.

The only data handled by a third party is the purchase itself: if you unlock the app, the transaction is
processed by **Google Play Billing**, and Google Play handles the payment details, your Google account
and the receipt. Dao Fortune never sees or stores your card details.

The app bundles Google's Play Billing library, which brings a small number of Google Play services
components with it. Those components are Google's code, operate under Google's own privacy policy, and
can transmit technical diagnostics to Google in the ordinary course of talking to the Play Store. The
app does not use them for analytics and does not read anything they collect.

## 2. What the app stores on your device

| Data | Where | Why | How long |
|---|---|---|---|
| Whether your free reading has been used | App preferences (private storage) | To honour the "first reading of each module beyond Today's Fortune free, later readings paid" rule | Until you clear app data or uninstall |
| Whether you have purchased the unlock | App preferences (private storage) | To avoid asking you to pay twice | Until you clear app data or uninstall |
| Your year sign (one of twelve branches) | A separate preferences file (private storage) | To read the plate against your own position | Until you clear it, clear app data, or uninstall |

These two flags are included in Android's device backup so that a phone transfer does not reset your purchase. They are not transmitted anywhere. The year sign is not — see below.

### Your year sign

If you set a year sign, the app stores **one integer** — the ordinal of your branch out of twelve — in a
preferences file named `dao_fortune_profile.xml`. Nothing else about you is recorded:

* **No birth date is collected.** The app never asks for your birthday, and it cannot derive one.
* **It is kept in a different file from the purchase flags.** The purchase flags live in
  `dao_fortune_access.xml`; the year sign lives in `dao_fortune_profile.xml`.
* **It is excluded from backup.** Android's backup rules (`backup_rules.xml` and
  `data_extraction_rules.xml`) allow-list only `dao_fortune_access.xml`. Your year sign is left out on
  purpose, so it never leaves the device — not into cloud backup, not into a device-to-device transfer.
* **It is never transmitted.** There is no server behind this app. The year sign is used to compute a
  reading locally and is then forgotten until you change it.
* **You can erase it in one tap** — the year-sign sheet in the app has a "clear" action — or by clearing
  the app's storage in Android Settings, or by uninstalling.

### Dates you look at

You can move the plate to any day — today, tomorrow, or a date in the next sixty days. The date you are
looking at is **not stored at all**: it lives in memory for as long as the app is open and disappears
when you close it. The forward scan of the coming sixty days is computed on your device each time the
app starts; no result of it is saved or sent anywhere.

### The dream you write

The Dream & Omens module (梦占) lets you type what you remember of a dream, and reads it against the
plate of the night it belongs to.

* **It is never written to disk.** The text lives in the app's memory while the dream sheet is open and
  nowhere else. There is no file, no database, no log entry, and no cache copy.
* **It is never transmitted.** The app makes no network request of any kind while reading a dream. There
  is no server, no model, no image service behind it.
* **The picture is drawn locally.** The ink-wash plate you see is generated on your device from the dream
  itself, using a fixed set of classical images. Nothing is uploaded to be drawn "in the cloud".
* **Closing the sheet erases it.** Leave the dream sheet — by the button or by swiping it away — and the
  text, the wake-up time and the reading are all dropped from memory.
* **The only thing that survives** is the reading you were shown, and even that is not stored: it is
  re-created if you read the same dream again.

## 3. Permissions

**Dao Fortune itself declares no permissions at all.** The Google Play Billing library that the app
bundles adds two at the manifest level:

| Permission | Where it comes from | Why |
|---|---|---|
| `INTERNET` | Play Billing library | To reach the Play Store when you unlock the app or restore a purchase |
| `ACCESS_NETWORK_STATE` | Play Billing library | To check whether the device is online before contacting the Play Store |

Neither is a runtime permission — you are never asked to approve anything. The app does **not** request
location, camera, microphone, contacts, storage or any sensor. The Play services components bundled
with the billing library include code that could read location, but the app never asks for the location
permission, so that code cannot collect anything.

## 4. Network activity

- **Google Play Billing**: used only when you choose to unlock the app or restore a previous purchase.
  Google's Play services handle the network traffic for this.
- **Google Play services diagnostics**: the components bundled with the billing library may send
  technical diagnostics to Google under Google's own privacy policy. The app neither reads that data
  nor uses it for anything.
- **No analytics, no ads, no tracking, no remote logging.** There is no server behind this app, and the
  app itself makes no network requests at all.

## 5. Children

Dao Fortune is not directed at children under 13. It contains no chat, no accounts, no user accounts visible to anyone else, and no data collection. The optional year sign is a preference the reader sets on their own device; it is not transmitted and is excluded from backup. A dream written in the Dream & Omens module is read privately on the device and is never shared with anybody, published, or transmitted — it is not user-generated content in the sense Play's policies mean, because no other person can ever see it.

## 6. Your rights

Because we collect no personal data, there is nothing for us to export, correct or delete on request. Everything the app holds is on your device, and you can erase it yourself at any time:

* **Your year sign** — clear it inside the app (the year-sign sheet has a clear action), or by clearing the
  app's storage in Android Settings, or by uninstalling.
* **A dream you wrote** — nothing to delete: it is never written to storage. Closing the dream sheet
  erases it from memory.
* **The free-reading flag and the unlock** — clear the app's storage in Android Settings, or uninstall.
  Note that uninstalling also removes your purchase entitlement from this device; you can restore it at
  any time with the **Restore purchase** button in the app, or through your Google Play purchase history.
* **A date you were looking at** — nothing to delete; it is never written to storage.

We hold no copy of anything, because we receive nothing.

## 7. Third parties

| Third party | What it does | Privacy policy |
|---|---|---|
| Google Play (Google LLC) | Processes the in-app purchase, and provides the Play services components bundled with the billing library | https://policies.google.com/privacy |

## 8. Disclaimer

Dao Fortune is a cultural and entertainment product based on classical Chinese divination (the luopan tradition). Its output is **not** financial, medical, legal, investment or psychological advice, it does not promise any outcome, and it must not be relied upon for financial decisions. See the in-app disclaimer and the Google Play listing for details.

## 9. Changes

If this policy changes, the effective date above will be updated and the new version will be published with the app listing before the change takes effect.

## 10. Contact

Questions about this policy: **gubingjing@hotmail.com**
