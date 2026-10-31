"""Check that every native library in the bundle is aligned for 16 KB pages.

Android 15+ requires apps that target API 35 or later to support 16 KB page
sizes.  The requirement is that every PT_LOAD segment of every .so is aligned
to at least 16 KB (0x4000).  This reads the ELF program headers straight out of
the bundle and reports the alignment of each shared library.

Usage:
    python tools/qa/check_16kb_pages.py dist/app-release.aab
"""

from __future__ import annotations

import struct
import sys
import zipfile

PT_LOAD = 1
REQUIRED_ALIGNMENT = 0x4000  # 16 KB


def elf_load_alignments(data: bytes) -> list[int]:
    """The p_align of every PT_LOAD segment in an ELF file."""
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")

    is_64 = data[4] == 2
    if is_64:
        e_phoff, = struct.unpack_from("<Q", data, 0x20)
        e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x36)
    else:
        e_phoff, = struct.unpack_from("<I", data, 0x1C)
        e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x2A)

    aligns: list[int] = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + e_phentsize > len(data):
            break
        if is_64:
            p_type, = struct.unpack_from("<I", data, offset)
            p_align, = struct.unpack_from("<Q", data, offset + 48)
        else:
            p_type, = struct.unpack_from("<I", data, offset)
            p_align, = struct.unpack_from("<I", data, offset + 28)
        if p_type == PT_LOAD:
            aligns.append(p_align)
    return aligns


def main() -> int:
    bundle = sys.argv[1] if len(sys.argv) > 1 else "dist/app-release.aab"
    failures: list[str] = []
    checked = 0

    with zipfile.ZipFile(bundle) as zf:
        libs = sorted(name for name in zf.namelist() if name.endswith(".so"))
        if not libs:
            print("no native libraries in the bundle - nothing to check")
            return 0
        for name in libs:
            data = zf.read(name)
            try:
                aligns = elf_load_alignments(data)
            except ValueError as error:
                failures.append(f"{name}: {error}")
                continue
            if not aligns:
                failures.append(f"{name}: no PT_LOAD segment found")
                continue
            worst = min(aligns)
            checked += 1
            status = "ok" if worst >= REQUIRED_ALIGNMENT else "TOO SMALL"
            print(f"[{status}] {name}: p_align = {worst} (0x{worst:x})")
            if worst < REQUIRED_ALIGNMENT:
                failures.append(
                    f"{name}: p_align {worst} is below 0x{REQUIRED_ALIGNMENT:x}"
                )

    print()
    if failures:
        print(f"16 KB page size check FAILED for {len(failures)} librar(y/ies):")
        for line in failures:
            print(f"  - {line}")
        return 1
    print(f"16 KB page size check PASSED for {checked} librar(y/ies)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
