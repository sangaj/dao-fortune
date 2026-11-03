"""Checks that the store screenshots still say what the app says.

The screenshots are not captures. They are drawn by
`tools/store_assets/build_screenshots.ps1`, a GDI+ re-implementation of the
Compose layout, and it does not follow the app: when a visible string changes
in the app, the screenshot keeps the old wording. Play publishes the
screenshot, so that drift is what a reader sees in the store - and it is the
direction that hurts, because the store shows a product the app no longer is.

The check runs in both directions:

* **script -> app** — every phrase the script draws has to exist in the app's
  own source. The store can never show a sentence the app does not say.
* **app -> script** — the sentences the app leads with (the free-reading
  banner, the anchor row, the paywall's benefits) have to exist in the
  script, so a changed banner cannot leave the old one in the store.

Exceptions are listed one by one, with a reason. Every exception is a
decision on the record rather than a gap in the check.

Usage:
    python tools/qa/check_screenshot_sync.py
    (exit code 0 = the screenshots still agree with the app, 1 = drift)
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

SCRIPT = "tools/store_assets/build_screenshots.ps1"

# The app's own wording lives in the UI layer; the module titles and the
# verdicts come from the core model, because the screenshots draw those too.
# The year officer's teaser and the activity labels live in core as well, and
# the screenshots draw both of them.
APP_FILES = [
    "app/src/main/java/com/laki/daofortune/ui/HomeScreen.kt",
    "app/src/main/java/com/laki/daofortune/ui/DateAndYearSign.kt",
    "app/src/main/java/com/laki/daofortune/ui/ReadingSheet.kt",
    "app/src/main/java/com/laki/daofortune/ui/DreamSheet.kt",
    "app/src/main/java/com/laki/daofortune/ui/PaywallSheet.kt",
    "app/src/main/java/com/laki/daofortune/ui/AboutSheet.kt",
    "app/src/main/java/com/laki/daofortune/ui/share/ShareCard.kt",
    "app/src/main/java/com/laki/daofortune/core/Model.kt",
    "app/src/main/java/com/laki/daofortune/core/YearReading.kt",
    "app/src/main/java/com/laki/daofortune/core/FavourableDates.kt",
]

# Phrases the script may draw that are not app copy, with the reason.
ALLOWED = {
    "segoe ui": "a font name, not copy",
    "georgia": "a font name, not copy",
    "microsoft yahei ui": "a font name, not copy",
    "stop": "the PowerShell verb",
    "free": "a screenshot mode, not copy",
    "used": "a screenshot mode, not copy",
    "unlocked": "a screenshot mode, not copy",
    "docs\\store\\screenshots": "the output folder",
    "docs\\store\\reading_sample.json": "the sample file the script reads",
    "01_home.png": "an output file name",
    "02_home_unlocked.png": "an output file name",
    "03_favourable_days.png": "an output file name",
    "04_windfall_reading.png": "an output file name",
    "05_unlock.png": "an output file name",
    "06_dream.png": "an output file name",
    "unlock for $9.99": (
        "the price comes from Play at runtime, so the screenshot has to show one "
        "concrete figure; the app never hard-codes it"
    ),
    "asia/shanghai": (
        "the screenshot pins one time zone; the app shows the device's own zone"
    ),
    "missing reading_sample.json - run the unit tests first": (
        "a message to the console, not copy on a screen"
    ),
    "wrote": "a message to the console, not copy on a screen",
    "*windfall*": "a filter that picks the module to draw, not copy",
}


def scan_ps(text):
    """Walk a PowerShell script and return its string literals.

    A plain regex cannot do this: an apostrophe inside a double-quoted string
    ("TODAY'S READINGS") or inside a comment ("the app's header") would be
    read as the start of a single-quoted string, and every later string would
    be mis-paired. So the script is walked once, keeping track of whether a
    quote is open and of where a comment starts.

    Returns a list of (kind, literal) with kind either "single" or "double".
    """
    literals = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "#":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch == "'":
            i += 1
            buf = []
            while i < n:
                if text[i] == "'":
                    if i + 1 < n and text[i + 1] == "'":
                        buf.append("'")
                        i += 2
                        continue
                    i += 1
                    break
                buf.append(text[i])
                i += 1
            literals.append(("single", "".join(buf)))
            continue
        if ch == '"':
            i += 1
            buf = []
            while i < n:
                if text[i] == '"':
                    if i + 1 < n and text[i + 1] == '"':
                        buf.append('"')
                        i += 2
                        continue
                    i += 1
                    break
                buf.append(text[i])
                i += 1
            literals.append(("double", "".join(buf)))
            continue
        i += 1
    return literals


def unspace_letters(text):
    """Turn the letter-spaced brand line back into words.

    The script draws "D A I L Y   L U O P A N   W I S D O M" where the app
    writes "Daily Luopan Wisdom".
    """
    return re.sub(r"(?:\b\w\b[ ]){2,}\b\w\b", lambda m: m.group(0).replace(" ", ""), text)


def norm(text):
    """Fold the differences that are not wording: case, spacing, dashes."""
    text = unspace_letters(text)
    text = text.replace("·", " ").replace("—", " ").replace("–", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip().lower()


def norm_script(text):
    """Normalise a PowerShell script for the app -> script direction.

    A single-quoted string in PowerShell writes an apostrophe by doubling
    it, so the script says "Today''s Fortune" where the app says
    "Today's Fortune".
    """
    return norm(text.replace("''", "'"))


def script_phrases(script):
    """The phrases the screenshot script draws."""
    phrases = []
    for kind, literal in scan_ps(script):
        if kind == "single":
            phrases.append(literal)
            continue
        # A double-quoted string can hold interpolations; only the literal
        # parts are copy.
        text = re.sub(r"\$\([^)]*\)", "\x00", literal)
        text = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", "\x00", text)
        for part in text.split("\x00"):
            part = part.strip()
            if part:
                phrases.append(part)

    seen = []
    for phrase in phrases:
        if phrase not in seen:
            seen.append(phrase)
    return seen


def app_corpus():
    """Everything the app says, as one searchable blob."""
    parts = []
    for rel in APP_FILES:
        path = ROOT / rel
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        # Join the pieces of a wrapped Kotlin string literal.
        text = re.sub(r'"\s*\+\s*\n\s*"', " ", text)
        parts.append(text)
    return norm(" ".join(parts))


# The sentences the app leads with. Each has to be in the screenshot script
# as well, or the store keeps showing the old wording.
MUST_REACH_THE_SCRIPT = [
    (
        "the free-reading banner, before the reading is spent",
        "Today's Fortune is free every day, in full. Your first reading is free too",
    ),
    (
        "the free-reading banner, after the reading is spent",
        "Today's Fortune is free every day, in full. Your free reading has been used",
    ),
    (
        "the anchor row",
        "Add your birth day for a deeper reading",
    ),
    (
        "the forward scan",
        "The next sixty days, ranked by the windfall reading.",
    ),
    (
        "the paywall's first benefit",
        "Windfall, love, career and body in full",
    ),
    (
        "the paywall's last benefit",
        "No subscription, no ads, no account",
    ),
    (
        "the paywall's small print",
        "Payments are handled by Google Play. Your anchor stays on this device and is never transmitted.",
    ),
    (
        "the share button",
        "Share as image",
    ),
    (
        "the save button",
        "Save image",
    ),
]


def main() -> int:
    # The script path can be given on the command line, so the check can be
    # pointed at a tampered copy - that is how the negative control is run.
    script_rel = sys.argv[1] if len(sys.argv) > 1 else SCRIPT
    script_path = ROOT / script_rel
    if not script_path.exists():
        print(f"[FAIL] missing {script_rel}")
        return 1
    script = script_path.read_text(encoding="utf-8")
    corpus = app_corpus()

    failures = []

    print("=== script -> app: every drawn phrase must exist in the app ===")
    for phrase in script_phrases(script):
        key = norm(phrase)
        if not key or len(key) < 3:
            continue
        if key in ALLOWED:
            print(f"[PASS] allowed: {phrase[:70]}")
            continue
        if key in corpus:
            print(f"[PASS] {phrase[:70]}")
        else:
            failures.append(
                f"the screenshot draws a phrase the app does not say: {phrase!r}"
            )

    print()
    print("=== app -> script: the app's leading sentences must reach the store ===")
    script_norm = norm_script(script)
    for name, phrase in MUST_REACH_THE_SCRIPT:
        if norm(phrase) in script_norm:
            print(f"[PASS] {name}")
        else:
            failures.append(f"{name} is not in the screenshot script any more: {phrase!r}")

    for failure in failures:
        print(f"[FAIL] {failure}")

    if failures:
        print("\nFAILED: the store screenshots and the app have drifted apart")
        return 1
    print("\nSCREENSHOT SYNC OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
