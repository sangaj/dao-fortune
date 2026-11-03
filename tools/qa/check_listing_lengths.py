"""Checks the Play Store listing copy against the character limits Play enforces.

Play rejects a listing whose title, short description, full description or release
notes go over the cap, and the cap is easy to cross by accident: one extra line
of marketing copy is enough. This script reads docs/PLAY_STORE_LISTING.md,
pulls the fenced blocks out of it and measures them.

Usage:
    python tools/qa/check_listing_lengths.py
    (exit code 0 = all within the limits, 1 = something is over)
"""

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LISTING = ROOT / "docs" / "PLAY_STORE_LISTING.md"

# Play's own caps.
LIMITS = {
    "App name": 30,
    "Short description": 80,
    "Full description": 4000,
    "Release notes": 500,
}

# Where to start worrying. Sitting a few characters under the cap is a trap:
# the next edit crosses it, and Play rejects the upload. The warning line is
# not a failure — it is a nudge to make room before it is needed.
WARN_AT = {
    "Full description": 3950,
    "Release notes": 480,
}


def blocks(path: pathlib.Path) -> list[tuple[str, str]]:
    """Every fenced code block in the file, with the heading above it."""
    lines = path.read_text(encoding="utf-8").split("\n")
    heading = ""
    found = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if line.startswith("#"):
            heading = line.lstrip("#").strip()
        if line.strip() == "```":
            body = []
            index += 1
            while index < len(lines) and lines[index].strip() != "```":
                body.append(lines[index])
                index += 1
            found.append((heading, "\n".join(body)))
        index += 1
    return found


def main() -> int:
    if not LISTING.exists():
        print(f"missing {LISTING}")
        return 1

    blocks_found = blocks(LISTING)
    problems = []
    for heading, body in blocks_found:
        limit = None
        for name, cap in LIMITS.items():
            if heading.startswith(name):
                limit = cap
                break
        if limit is None:
            continue
        # Play counts the characters as typed, line breaks included.
        length = len(body)
        if length > limit:
            problems.append(f"{heading}: {length} characters, over the {limit} limit")
        else:
            print(f"[PASS] {heading}: {length}/{limit} characters")
            warn_at = WARN_AT.get(heading)
            if warn_at is not None and length >= warn_at:
                print(
                    f"[WARN] {heading}: {length} characters - only {limit - length} left, "
                    f"make room before the next edit"
                )

    for problem in problems:
        print(f"[FAIL] {problem}")

    if problems:
        print("\nFAILED: the listing copy is over a Play limit")
        return 1
    print("\nLISTING LENGTHS OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
