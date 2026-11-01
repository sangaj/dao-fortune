# Compares two app bundles by payload, not by their zip bytes.
#
#   python tools\qa\compare_bundles.py <fresh.aab> <shipped.aab>
#
# The whole-file hash is the wrong test for this. An AAB is a zip of nested
# jars, and the nested jars are re-compressed on every build, so two builds of
# the same source differ in a few bytes of deflate stream. On top of that,
# AGP writes the current git commit into
# base/root/META-INF/version-control-info.textproto, so a build made after a
# commit carries a different commit id - and the jar signatures are derived
# from the payload, so they differ too.
#
# What actually has to match is everything else: the entry names, their
# order and their CRC32. Those three entries are listed separately instead
# of being silently ignored.
#
# Exits 0 when the payload matches, 1 otherwise.

import sys
import zipfile

# The embedded commit id, and the signatures derived from it.
VOLATILE = (
    "META-INF/MANIFEST.MF",
    "META-INF/DAO-FORT.SF",
    "META-INF/DAO-FORT.RSA",
)


def is_volatile(name: str) -> bool:
    return name in VOLATILE or name.endswith("META-INF/version-control-info.textproto")


def fingerprint(path):
    with zipfile.ZipFile(path) as z:
        bad = z.testzip()
        if bad is not None:
            raise SystemExit(f"corrupt entry in {path}: {bad}")
        return [(i.filename, i.CRC, i.file_size) for i in z.infolist()]


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: compare_bundles.py <fresh.aab> <shipped.aab>")
        return 2

    fresh_path, shipped_path = sys.argv[1], sys.argv[2]
    try:
        fresh = fingerprint(fresh_path)
        shipped = fingerprint(shipped_path)
    except zipfile.BadZipFile as exc:
        print(f"not a readable bundle: {exc}")
        return 1

    fresh_map = {n: (crc, size) for n, crc, size in fresh}
    shipped_map = {n: (crc, size) for n, crc, size in shipped}

    fresh_names = [n for n, _, _ in fresh]
    shipped_names = [n for n, _, _ in shipped]

    only_fresh = [n for n in fresh_names if n not in shipped_map]
    only_shipped = [n for n in shipped_names if n not in fresh_map]
    changed = [
        n for n, crc, size in fresh
        if n in shipped_map and not is_volatile(n) and shipped_map[n] != (crc, size)
    ]
    order_differs = [n for n, _, _ in fresh if n not in VOLATILE] != [
        n for n, _, _ in shipped if n not in VOLATILE
    ]

    volatile_seen = sorted(
        n for n in fresh_map
        if is_volatile(n) and n in shipped_map and fresh_map[n] != shipped_map[n]
    )

    if only_fresh or only_shipped or changed or order_differs:
        print("BUNDLE PAYLOAD DIFFERS")
        print(f"  entries: fresh={len(fresh)} shipped={len(shipped)}")
        if only_fresh:
            print(f"  only in fresh  : {only_fresh[:10]}")
        if only_shipped:
            print(f"  only in shipped: {only_shipped[:10]}")
        if changed:
            print(f"  different payload: {changed[:10]}")
        if order_differs:
            print("  the entries are in a different order")
        return 1

    print(
        f"BUNDLE PAYLOAD MATCH: {len(fresh)} entries, identical names, order and digests"
    )
    if volatile_seen:
        print(
            "  (differing by design: "
            + ", ".join(volatile_seen)
            + " - the embedded commit id and the signatures derived from it)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
