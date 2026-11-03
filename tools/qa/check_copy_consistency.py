"""Checks that the product copy agrees everywhere it is repeated.

Some sentences are load-bearing: the paywall split, the one free reading, the
birth-day storage promise, the disclaimer. They appear in the app, the store
listing, the privacy policy and the docs, and Play's deceptive-behaviour
review reads them side by side. Drift between them is a policy risk, not a
style issue - so the agreement is checked here, in the release gate, instead
of being left to memory.

Every rule has two halves:

* **required** - a set of (file, pattern) pairs. Every pattern must match its
  file. This is the promise being made.
* **forbidden** - a set of (file, pattern) pairs. The pattern must NOT appear
  as an assertion. This is the promise that used to be made, or a wording that
  quietly contradicts the promise.

The forbidden half matters as much as the required half. A file that carries
the new sentence can still carry the old one two paragraphs down, and a gate
that only looks for what should be there cannot see it.

Patterns are regular expressions over the whole file with DOTALL, so a
sentence may wrap across lines.

Usage:
    python tools/qa/check_copy_consistency.py
    (exit code 0 = every rule holds, 1 = drift found)
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

LISTING = "docs/PLAY_STORE_LISTING.md"
POLICY = "docs/PRIVACY_POLICY.md"
ABOUT = "app/src/main/java/com/laki/daofortune/ui/AboutSheet.kt"
HOME = "app/src/main/java/com/laki/daofortune/ui/HomeScreen.kt"
DREAM = "app/src/main/java/com/laki/daofortune/ui/DreamSheet.kt"
PAYWALL = "app/src/main/java/com/laki/daofortune/ui/PaywallSheet.kt"
README = "README.md"
AGENTS = "AGENTS.md"
TESTING = "docs/TESTING.md"
VM = "app/src/main/java/com/laki/daofortune/vm/MainViewModel.kt"
ACTIVITY = "app/src/main/java/com/laki/daofortune/MainActivity.kt"
BILLING = "app/src/main/java/com/laki/daofortune/billing/BillingManager.kt"
SHOTS = "tools/store_assets/build_screenshots.ps1"

# The store screenshots are drawn by a script that re-implements the app's
# layout, so the script's own strings have to agree with the app's.
# Otherwise the listing shows a screen the app never draws.
COPY_SURFACES = (LISTING, POLICY, ABOUT, HOME, DREAM, PAYWALL, README, AGENTS)

# A line that quotes the wrong wording on purpose - the docs warn against it -
# is not a violation. Only an assertion counts.
EXEMPT = (r"never say", r"must not", r"do not", r"don't", r"not say", r"never write")


def read(rel_path):
    path = ROOT / rel_path
    if not path.exists():
        return None
    return normalise(path.read_text(encoding="utf-8"))


def normalise(text):
    """Join the pieces of a wrapped string literal.

    A sentence in Kotlin is often written as

        "It is not financial, medical, " +
            "legal, investment or psychological advice"

    so the phrase a rule looks for is broken by the quote, the plus and the
    line break. Joining the pieces first lets one pattern match the sentence
    wherever it is written.
    """
    text = re.sub(r'"\s*\+\s*\n\s*"', " ", text)
    text = re.sub(r"'\s*\+\s*\n\s*'", " ", text)
    return text


def violations(text, pattern):
    """The lines that assert the forbidden thing."""
    found = []
    for line in text.splitlines():
        if not re.search(pattern, line, re.IGNORECASE):
            continue
        if any(re.search(e, line, re.IGNORECASE) for e in EXEMPT):
            continue
        found.append(line.strip()[:140])
    return found


# Each rule: (name, why, required, forbidden).
RULES = [
    (
        "Today's Fortune is free every day, in full",
        "the headline of the paywall split; the listing, the app and the docs must say the same thing",
        [
            (LISTING, r"Today's Fortune is free every day, in full"),
            (HOME, r"Today's Fortune is free every day, in full"),
            (README, r"Today's Fortune is free every day, in full"),
            (AGENTS, r"Today's Fortune.*is\s+free every day, in full"),
        ],
        [
            # The claim that is no longer true: everything is free.
            (LISTING, r"every module is free|all (five|4|four)?\s*modules are free"),
            (README, r"every module is free|all (five|4|four)?\s*modules are free"),
            (POLICY, r"every module is free|all (five|4|four)?\s*modules are free"),
            (HOME, r"every module is free|all (five|4|four)?\s*modules are free"),
            (PAYWALL, r"every module is free|all (five|4|four)?\s*modules are free"),
        ],
    ),
    (
        "One free reading in total",
        "AccessState grants a single free reading; anywhere that implies one per module overpromises",
        [
            (LISTING, r"one free reading in total"),
            (README, r"One free reading in total"),
            (AGENTS, r"exactly\s+\*?\*?one\*?\*?\s+free reading"),
        ],
        [
            # The promise that was made before the split was decided. The
            # listing quotes it in a "never say" warning, which is exempt.
            (LISTING, r"first (full )?reading of each|first reading of each module"),
            (README, r"first (full )?reading of each|first reading of each module"),
            (POLICY, r"first (full )?reading of each|first reading of each module"),
            (HOME, r"first (full )?reading of each|first reading of each module"),
            (PAYWALL, r"first (full )?reading of each|first reading of each module"),
            (AGENTS, r"first (full )?reading of each|first reading of each module"),
            (TESTING, r"first (full )?reading of each|first reading of each module"),
            (LISTING, r"one free reading per|a free reading for each|each module (gets|has|comes with)"),
            (README, r"one free reading per|a free reading for each|each module (gets|has|comes with)"),
        ],
    ),
    (
        "The free reading is never spent silently",
        "the confirm-before-spend step is part of the product promise and must be documented with the code",
        [
            (VM, r"confirmFreeSpend"),
            (VM, r"confirmFreeDream"),
            (ACTIVITY, r"Your one free reading"),
            (ACTIVITY, r"Read your dream in full"),
            (README, r"asks before spending"),
            (AGENTS, r"asks first"),
            (AGENTS, r"confirmFreeDream"),
            (TESTING, r"Your one free reading"),
            (TESTING, r"Read the dream\*\* while the one free reading is unspent"),
        ],
        [],
    ),
    (
        "One in-app product only",
        "the tip jar was removed on purpose: a second product id would bring it back, and a consumable "
        "whose purchase can be lost in the same callback as the real unlock is a money bug waiting "
        "to happen",
        [
            (AGENTS, r"exactly one in-app product"),
            # The lookahead is the whole point: the billing code must not name a
            # second product at all.
            (BILLING, r"(?s)(?!.*TIP_PRODUCT_ID).*const val PRODUCT_ID = \"dao_fortune_full_access\""),
            (LISTING, r"In-app purchases \| \*\*Yes\*\*.*`dao_fortune_full_access`"),
        ],
        [],
    ),
    (
        "The birth day stays on the device",
        "the optional birth day is stored locally and excluded from backup; listing, policy and About must agree",
        [
            (LISTING, r"birth day.*excluded from (Android )?backup.*never transmitted"),
            (POLICY, r"birth day\*\*.*excluded from Android's backup.*never transmitted"),
            (ABOUT, r"birth day, if you add one.*excluded from Android's backup, never transmitted"),
            (README, r"birth day.*excluded from backup"),
        ],
        [
            # The sentence that was true before the birth day existed.
            (LISTING, r"never asks for (your |a )?(date of birth|birth date|birthday)"),
            (POLICY, r"never asks for (your |a )?(date of birth|birth date|birthday)"),
            (ABOUT, r"never asks for (your |a )?(date of birth|birth date|birthday)"),
            (README, r"never asks for (your |a )?(date of birth|birth date|birthday)"),
            (HOME, r"never asks for (your |a )?(date of birth|birth date|birthday)"),
        ],
    ),
    (
        "No data collected",
        "the Data safety answer rests on this sentence; it must be identical wherever it is claimed",
        [
            (LISTING, r"No data collected"),
            (README, r"no data collected"),
            (POLICY, r"collects\s+\*?\*?no personal data"),
        ],
        [],
    ),
    (
        "The entertainment disclaimer",
        "Play's deceptive-behaviour policy wants this wording; it must survive every edit, and the "
        "store screenshot must not show a sentence the app never draws",
        [
            (LISTING, r"financial, medical,\s+legal, investment or psychological advice"),
            (ABOUT, r"financial, medical,\s+legal, investment or psychological advice"),
            (HOME, r"financial, medical,\s+legal, investment or psychological advice"),
            (PAYWALL, r"financial, medical,\s+legal, investment or psychological advice"),
            (README, r"financial, medical,\s+legal, investment or psychological advice"),
            (POLICY, r"financial, medical,\s+legal, investment or psychological advice"),
            (SHOTS, r"financial, medical,\s+legal, investment or psychological advice"),
        ],
        [
            # The shortened wording that dropped "investment" - the app talks
            # about windfall, so the money word has to stay in.
            (LISTING, r"medical, legal or psychological advice"),
            (ABOUT, r"medical, legal or psychological advice"),
            (HOME, r"medical, legal or psychological advice"),
            (PAYWALL, r"medical, legal or psychological advice"),
            (README, r"medical, legal or psychological advice"),
            (POLICY, r"medical, legal or psychological advice"),
            (DREAM, r"medical, legal or psychological advice"),
            (SHOTS, r"medical, legal or psychological advice"),
        ],
    ),
    (
        "The colour is a phase, not a promise of luck",
        "the five-phase colour of the day's wealth element is a phase; calling it a 'lucky colour' "
        "is a promise the app never makes, and the app would then disagree with the store",
        [
            (LISTING, r"five-phase colour"),
        ],
        [
            (LISTING, r"lucky\s+(colour|color)"),
            (HOME, r"lucky\s+(colour|color)"),
            (SHOTS, r"lucky\s+(colour|color)"),
        ],
    ),
]


def main() -> int:
    failures = []
    for name, why, required, forbidden in RULES:
        for rel_path, pattern in required:
            text = read(rel_path)
            if text is None:
                failures.append(f"{name}: missing file {rel_path}")
                continue
            if re.search(pattern, text, re.DOTALL):
                print(f"[PASS] {name} - {rel_path}")
            else:
                failures.append(f"{name}: pattern not found in {rel_path}\n       /{pattern}/")
        for rel_path, pattern in forbidden:
            text = read(rel_path)
            if text is None:
                failures.append(f"{name}: missing file {rel_path}")
                continue
            bad = violations(text, pattern)
            if bad:
                failures.append(
                    f"{name}: {rel_path} still says the forbidden thing\n"
                    f"       /{pattern}/\n"
                    + "".join(f"         {line}\n" for line in bad[:3])
                )
            else:
                print(f"[PASS] {name} - {rel_path} carries no forbidden wording")

    for failure in failures:
        print(f"[FAIL] {failure}")

    if failures:
        print("\nFAILED: the product copy has drifted between the places that must agree")
        return 1
    print("\nCOPY CONSISTENCY OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
