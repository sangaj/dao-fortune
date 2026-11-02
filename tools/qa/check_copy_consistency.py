"""Checks that the product copy agrees everywhere it is repeated.

Some sentences are load-bearing: the paywall split, the one free reading, the
birth-day storage promise, the disclaimer. They appear in the app, the store
listing, the privacy policy and the docs, and Play's deceptive-behaviour
review reads them side by side. Drift between them is a policy risk, not a
style issue — so the agreement is checked here, in the release gate, instead
of being left to memory.

Each rule is a set of (file, pattern) pairs: every pattern must match its
file. Patterns are regular expressions over the whole file with DOTALL, so a
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
README = "README.md"
AGENTS = "AGENTS.md"
TESTING = "docs/TESTING.md"
VM = "app/src/main/java/com/laki/daofortune/vm/MainViewModel.kt"
ACTIVITY = "app/src/main/java/com/laki/daofortune/MainActivity.kt"
BILLING = "app/src/main/java/com/laki/daofortune/billing/BillingManager.kt"

# Each rule: (name, why it matters, [(file, pattern), ...]).
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
    ),
    (
        "One free reading in total",
        "AccessState grants a single free reading; anywhere that implies one per module overpromises",
        [
            (LISTING, r"one free reading in total"),
            (README, r"One free reading in total"),
            (AGENTS, r"exactly\s+\*?\*?one\*?\*?\s+free reading"),
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
    ),
    (
        "No data collected",
        "the Data safety answer rests on this sentence; it must be identical wherever it is claimed",
        [
            (LISTING, r"No data collected"),
            (README, r"no data collected"),
            (POLICY, r"collects\s+\*?\*?no personal data"),
        ],
    ),
    (
        "The entertainment disclaimer",
        "Play's deceptive-behaviour policy wants this wording; it must survive every edit",
        [
            (LISTING, r"not financial, medical, legal, investment or psychological advice"),
            (ABOUT, r"not financial, medical, legal, investment or psychological advice"),
        ],
    ),
]


def main() -> int:
    failures = []
    for name, why, checks in RULES:
        for rel_path, pattern in checks:
            path = ROOT / rel_path
            if not path.exists():
                failures.append(f"{name}: missing file {rel_path}")
                continue
            text = path.read_text(encoding="utf-8")
            if re.search(pattern, text, re.DOTALL):
                print(f"[PASS] {name} — {rel_path}")
            else:
                failures.append(f"{name}: pattern not found in {rel_path}\n       /{pattern}/")

    for failure in failures:
        print(f"[FAIL] {failure}")

    if failures:
        print("\nFAILED: the product copy has drifted between the places that must agree")
        return 1
    print("\nCOPY CONSISTENCY OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
