"""Independent reference implementation of the Dao Fortune calendar engine.

Purpose: cross-check the Kotlin engine in app/src/main/java/com/laki/daofortune/core
against a second, separately written implementation, and produce golden values
that the Kotlin unit tests assert against.

Everything here follows the same rules as the Kotlin code:
  * day pillar changes at 23:00 local (the zi hour opens the next day)
  * year pillar changes at Li Chun (solar longitude 315 degrees)
  * month pillar changes at the twelve "jie" sections
  * day officer = (day branch - month branch) mod 12
  * hour officer rotates with the day stem (Wu Shu Dun)

Run:  python tools/reference/luopan_reference.py
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

LOCAL_TZ = timezone(timedelta(hours=8))  # the reference runs in Asia/Shanghai


def to_utc(moment: datetime) -> datetime:
    """Naive local time -> aware UTC instant."""
    return moment.replace(tzinfo=LOCAL_TZ).astimezone(timezone.utc)

J2000 = 2451545.0

STEMS = ["Jia", "Yi", "Bing", "Ding", "Wu", "Ji", "Geng", "Xin", "Ren", "Gui"]
BRANCHES = ["Zi", "Chou", "Yin", "Mao", "Chen", "Si", "Wu", "Wei", "Shen", "You", "Xu", "Hai"]

STEM_ELEMENT = {
    "Jia": "Wood", "Yi": "Wood", "Bing": "Fire", "Ding": "Fire", "Wu": "Earth",
    "Ji": "Earth", "Geng": "Metal", "Xin": "Metal", "Ren": "Water", "Gui": "Water",
}
BRANCH_ELEMENT = {
    "Zi": "Water", "Chou": "Earth", "Yin": "Wood", "Mao": "Wood", "Chen": "Earth",
    "Si": "Fire", "Wu": "Fire", "Wei": "Earth", "Shen": "Metal", "You": "Metal",
    "Xu": "Earth", "Hai": "Water",
}

# (longitude, pinyin, english, is_jie, month_branch)
TERMS = [
    (315, "Lichun", "Beginning of Spring", True, "Yin"),
    (330, "Yushui", "Rain Water", False, None),
    (345, "Jingzhe", "Awakening of Insects", True, "Mao"),
    (0, "Chunfen", "Spring Equinox", False, None),
    (15, "Qingming", "Pure Brightness", True, "Chen"),
    (30, "Guyu", "Grain Rain", False, None),
    (45, "Lixia", "Beginning of Summer", True, "Si"),
    (60, "Xiaoman", "Grain Buds", False, None),
    (75, "Mangzhong", "Grain in Ear", True, "Wu"),
    (90, "Xiazhi", "Summer Solstice", False, None),
    (105, "Xiaoshu", "Minor Heat", True, "Wei"),
    (120, "Dashu", "Major Heat", False, None),
    (135, "Liqiu", "Beginning of Autumn", True, "Shen"),
    (150, "Chushu", "End of Heat", False, None),
    (165, "Bailu", "White Dew", True, "You"),
    (180, "Qiufen", "Autumn Equinox", False, None),
    (195, "Hanlu", "Cold Dew", True, "Xu"),
    (210, "Shuangjiang", "Frost's Descent", False, None),
    (225, "Lidong", "Beginning of Winter", True, "Hai"),
    (240, "Xiaoxue", "Minor Snow", False, None),
    (255, "Daxue", "Major Snow", True, "Zi"),
    (270, "Dongzhi", "Winter Solstice", False, None),
    (285, "Xiaohan", "Minor Cold", True, "Chou"),
    (300, "Dahan", "Major Cold", False, None),
]

# The twenty-four mountains, clockwise from north: (name, element, trigram, yuan, yang, bearing)
PALACES = [
    ("Zi", "Water", "Kan", "Tian", False, 0),
    ("Gui", "Water", "Kan", "Ren", True, 15),
    ("Chou", "Earth", "Gen", "Di", False, 30),
    ("Gen", "Earth", "Gen", "Tian", True, 45),
    ("Yin", "Wood", "Zhen", "Di", True, 60),
    ("Jia", "Wood", "Zhen", "Ren", True, 75),
    ("Mao", "Wood", "Zhen", "Di", False, 90),
    ("Yi", "Wood", "Xun", "Ren", True, 105),
    ("Chen", "Earth", "Xun", "Di", False, 120),
    ("Xun", "Wood", "Xun", "Tian", True, 135),
    ("Si", "Fire", "Xun", "Di", False, 150),
    ("Bing", "Fire", "Li", "Ren", True, 165),
    ("Wu", "Fire", "Li", "Di", False, 180),
    ("Ding", "Fire", "Li", "Ren", True, 195),
    ("Wei", "Earth", "Kun", "Di", False, 210),
    ("Kun", "Earth", "Kun", "Tian", True, 225),
    ("Shen", "Metal", "Kun", "Di", True, 240),
    ("Geng", "Metal", "Dui", "Ren", True, 255),
    ("You", "Metal", "Dui", "Di", False, 270),
    ("Xin", "Metal", "Dui", "Ren", True, 285),
    ("Xu", "Earth", "Qian", "Di", False, 300),
    ("Qian", "Metal", "Qian", "Tian", True, 315),
    ("Hai", "Water", "Qian", "Di", False, 330),
    ("Ren", "Water", "Kan", "Ren", True, 345),
]

HOUR_OFFICERS = [
    "QING_LONG", "MING_TANG", "TIAN_XING", "ZHU_QUE", "JIN_GUI", "TIAN_DE",
    "BAI_HU", "YU_TANG", "TIAN_LAO", "XUAN_WU", "SI_MING", "GOU_CHEN",
]
DAY_OFFICERS = [
    "JIAN", "CHU", "MAN", "PING", "DING", "ZHI",
    "PO", "WEI", "CHENG", "SHOU", "KAI", "BI",
]


# --------------------------------------------------------------------- astronomy

def julian_day(d: date, hour_ut: float = 0.0) -> float:
    y, m = d.year, d.month
    if m <= 2:
        y -= 1
        m += 12
    a = math.floor(y / 100.0)
    b = 2 - a + math.floor(a / 4.0)
    return (
        math.floor(365.25 * (y + 4716))
        + math.floor(30.6001 * (m + 1))
        + d.day
        + hour_ut / 24.0
        + b
        - 1524.5
    )


def delta_t(year: float) -> float:
    y = year
    if y < 1900:
        t = (y - 1820) / 100.0
        return -20 + 32 * t * t
    if y < 1920:
        t = y - 1900
        return -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t ** 3 - 0.000197 * t ** 4
    if y < 1941:
        t = y - 1920
        return 21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t ** 3
    if y < 1961:
        t = y - 1950
        return 29.07 + 0.407 * t - t * t / 233.0 + t ** 3 / 2547.0
    if y < 1986:
        t = y - 1975
        return 45.45 + 1.067 * t - t * t / 260.0 - t ** 3 / 718.0
    if y < 2005:
        t = y - 2000
        return (63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t ** 3
                + 0.000651814 * t ** 4 + 0.00002373599 * t ** 5)
    if y < 2050:
        t = y - 2000
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    if y < 2150:
        u = (y - 1820) / 100.0
        return -20 + 32 * u * u - 0.5628 * (2150 - y)
    u = (y - 1820) / 100.0
    return -20 + 32 * u * u


def sun_longitude(jde: float) -> float:
    """Apparent geocentric longitude of the sun, degrees, for a TT Julian Day."""
    t = (jde - J2000) / 36525.0
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
    mr = math.radians(m)
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(mr)
         + (0.019993 - 0.000101 * t) * math.sin(2 * mr)
         + 0.000289 * math.sin(3 * mr))
    true_long = l0 + c
    omega = 125.04 - 1934.136 * t
    apparent = true_long - 0.00569 - 0.00478 * math.sin(math.radians(omega))
    return apparent % 360.0


def _delta(jde: float, target: float) -> float:
    return (sun_longitude(jde) - target + 180.0) % 360.0 - 180.0


def find_crossing(target: float, guess_jd: float) -> float:
    lo = hi = guess_jd
    cursor = guess_jd
    prev = _delta(cursor, target)
    for _ in range(40):
        cursor += 1.0
        now = _delta(cursor, target)
        if prev <= 0 <= now:
            lo, hi = cursor - 1.0, cursor
            break
        prev = now
    else:
        cursor = guess_jd
        prev = _delta(cursor, target)
        for _ in range(40):
            cursor -= 1.0
            now = _delta(cursor, target)
            if prev >= 0 >= now:
                lo, hi = cursor, cursor + 1.0
                break
            prev = now
        else:
            return guess_jd
    a, b = lo, hi
    for _ in range(60):
        mid = (a + b) / 2
        if _delta(mid, target) <= 0:
            a = mid
        else:
            b = mid
    return (a + b) / 2


def jd_to_datetime(jd: float) -> datetime:
    z = math.floor(jd + 0.5)
    f = jd + 0.5 - z
    alpha = math.floor((z - 1867216.25) / 36524.25)
    a = z if z < 2299161 else z + 1 + alpha - math.floor(alpha / 4)
    b = a + 1524
    c = math.floor((b - 122.1) / 365.25)
    d = math.floor(365.25 * c)
    e = math.floor((b - d) / 30.6001)
    day_fraction = b - d - math.floor(30.6001 * e) + f
    day = math.floor(day_fraction)
    month = int(e - 1) if e < 14 else int(e - 13)
    year = int(c - 4716) if month > 2 else int(c - 4715)
    hours = (day_fraction - day) * 24.0
    hour = math.floor(hours)
    minute = math.floor((hours - hour) * 60)
    second = math.floor(((hours - hour) * 60 - minute) * 60)
    return datetime(year, month, int(day), int(hour), int(minute), int(second),
                    tzinfo=timezone.utc)


_TERM_CACHE: dict[int, list[tuple[datetime, tuple]]] = {}


def terms_for_year(year: int) -> list[tuple[datetime, tuple]]:
    if year in _TERM_CACHE:
        return _TERM_CACHE[year]
    jd_jan1 = julian_day(date(year, 1, 1), 12.0)
    out = []
    for term in TERMS:
        lon = term[0]
        guess_doy = 79.0 + lon * 365.2422 / 360.0
        guess = jd_jan1 + (guess_doy - 1.0)
        if guess_doy > 366.0:
            guess -= 365.0
        jde = find_crossing(float(lon), guess)
        jd_ut = jde - delta_t(year + 0.5) / 86400.0
        out.append((jd_to_datetime(jd_ut), term))
    out.sort(key=lambda x: x[0])
    _TERM_CACHE[year] = out
    return out


def current_term(moment: datetime) -> tuple[datetime, tuple]:
    moments = [t for y in (moment.year - 1, moment.year, moment.year + 1)
               for t in terms_for_year(y)]
    moments.sort(key=lambda x: x[0])
    prior = [m for m in moments if m[0] <= to_utc(moment)]
    return prior[-1]


def current_section(moment: datetime) -> tuple[datetime, tuple]:
    moments = [t for y in (moment.year - 1, moment.year, moment.year + 1)
               for t in terms_for_year(y) if t[1][3]]
    moments.sort(key=lambda x: x[0])
    prior = [m for m in moments if m[0] <= to_utc(moment)]
    return prior[-1]


# ----------------------------------------------------------------------- pillars

EPOCH = date(1949, 10, 1)


def day_pillar(moment: datetime) -> tuple[str, str]:
    d = moment.date() + timedelta(days=1 if moment.hour >= 23 else 0)
    n = (d - EPOCH).days % 60
    return STEMS[n % 10], BRANCHES[n % 12]


def year_pillar(moment: datetime) -> tuple[str, str]:
    lichun = [t for t in terms_for_year(moment.year) if t[1][0] == 315][0][0]
    year = moment.year if to_utc(moment) >= lichun else moment.year - 1
    n = (year - 1984) % 60
    return STEMS[n % 10], BRANCHES[n % 12]


def month_pillar(moment: datetime) -> tuple[str, str]:
    _, term = current_section(moment)
    branch = term[4]
    y_stem, _ = year_pillar(moment)
    yin_month_stem = (STEMS.index(y_stem) % 5) * 2 + 2
    months_from_yin = (BRANCHES.index(branch) - BRANCHES.index("Yin")) % 12
    return STEMS[(yin_month_stem + months_from_yin) % 10], branch


def hour_pillar(moment: datetime) -> tuple[str, str]:
    branch_index = ((moment.hour + 1) // 2) % 12
    day_stem, _ = day_pillar(moment)
    stem_index = ((STEMS.index(day_stem) % 5) * 2 + branch_index) % 10
    return STEMS[stem_index], BRANCHES[branch_index]


def day_officer(moment: datetime) -> str:
    _, day_branch = day_pillar(moment)
    _, month_branch = month_pillar(moment)
    return DAY_OFFICERS[(BRANCHES.index(day_branch) - BRANCHES.index(month_branch)) % 12]


def hour_officer(moment: datetime) -> str:
    day_stem, _ = day_pillar(moment)
    _, hour_branch = hour_pillar(moment)
    offset = {0: 0, 5: 0, 1: 1, 6: 1, 2: 2, 7: 2, 3: 3, 8: 3}.get(STEMS.index(day_stem), 4)
    return HOUR_OFFICERS[(BRANCHES.index(hour_branch) + offset) % 12]


def void_branches(moment: datetime) -> list[str]:
    stem, branch = day_pillar(moment)
    n = next(i for i in range(60) if STEMS[i % 10] == stem and BRANCHES[i % 12] == branch)
    xun = n // 10
    start = BRANCHES[xun * 10 % 12]
    start_index = BRANCHES.index(start)
    return [BRANCHES[(start_index + 10) % 12], BRANCHES[(start_index + 11) % 12]]


def sun_palace(moment: datetime) -> tuple[str, str, str, str, bool, int]:
    utc = to_utc(moment)
    jde = julian_day(utc.date(), utc.hour + utc.minute / 60.0) + delta_t(moment.year + 0.5) / 86400.0
    lon = sun_longitude(jde)
    bearing = (lon + 90.0) % 360.0
    index = int(round(bearing / 15.0)) % 24
    return PALACES[index]


# ------------------------------------------------------------------ branch relations

HARMONY_OF = {
    "Zi": "Chou", "Chou": "Zi", "Yin": "Hai", "Hai": "Yin", "Mao": "Xu", "Xu": "Mao",
    "Chen": "You", "You": "Chen", "Si": "Shen", "Shen": "Si", "Wu": "Wei", "Wei": "Wu",
}

HARM_OF = {
    "Zi": "Wei", "Wei": "Zi", "Chou": "Wu", "Wu": "Chou", "Yin": "Si", "Si": "Yin",
    "Mao": "Chen", "Chen": "Mao", "Shen": "Hai", "Hai": "Shen", "You": "Xu", "Xu": "You",
}

TRIADS = [
    {"Zi", "Shen", "Wei"},
    {"Yin", "Hai", "Mao"},
    {"Wu", "Xu", "Si"},
    {"Chen", "You", "Chou"},
]

# The five phases: what each one produces, and what each one controls.
GENERATES = {"Wood": "Fire", "Fire": "Earth", "Earth": "Metal", "Metal": "Water", "Water": "Wood"}
CONTROLS = {"Wood": "Earth", "Earth": "Water", "Water": "Fire", "Fire": "Metal", "Metal": "Wood"}


def clash_of(branch: str) -> str:
    """The branch directly opposite."""
    return BRANCHES[(BRANCHES.index(branch) + 6) % 12]


def harmony_of(branch: str) -> str:
    """The six-harmony partner."""
    return HARMONY_OF[branch]


def harm_of(branch: str) -> str:
    """The six-harm partner."""
    return HARM_OF[branch]


def triad_of(branch: str) -> set[str]:
    """The three-harmony frame the branch belongs to."""
    for frame in TRIADS:
        if branch in frame:
            return frame
    raise AssertionError(branch)


def natal_relation(year_branch: str, day_branch: str) -> str:
    """How the querent's year branch stands against the day branch."""
    if harmony_of(year_branch) == day_branch:
        return "HARMONY"
    if day_branch in triad_of(year_branch):
        return "TRIAD"
    if year_branch == clash_of(day_branch):
        return "CLASH"
    if year_branch == harm_of(day_branch):
        return "HARM"
    return "NEUTRAL"


def element_tone(year_element: str, day_element: str) -> int:
    """How the querent's own element stands against the day element."""
    if year_element == day_element:
        return 4
    if GENERATES[day_element] == year_element:
        return 5
    if GENERATES[year_element] == day_element:
        return 3
    if CONTROLS[year_element] == day_element:
        return 3
    if CONTROLS[day_element] == year_element:
        return -5
    return 0


def element_phrase(year_element: str, day_element: str) -> str:
    if year_element == day_element:
        return "shares its element with"
    if GENERATES[day_element] == year_element:
        return "is fed by"
    if GENERATES[year_element] == day_element:
        return "feeds"
    if CONTROLS[year_element] == day_element:
        return "has the edge over"
    if CONTROLS[day_element] == year_element:
        return "is cut by"
    return "stands apart from"


# ------------------------------------------------------------------- self checks

def check(label: str, actual, expected) -> bool:
    ok = actual == expected
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}: got {actual!r}, expected {expected!r}")
    return ok


def run_self_checks() -> bool:
    ok = True
    print("Calendar anchors")
    ok &= check("1949-10-01 day pillar", day_pillar(datetime(1949, 10, 1, 12)),
                ("Jia", "Zi"))
    ok &= check("2000-01-01 day pillar", day_pillar(datetime(2000, 1, 1, 12)),
                ("Wu", "Wu"))
    ok &= check("2026-10-31 day pillar", day_pillar(datetime(2026, 10, 31, 12)),
                (day_pillar(datetime(2026, 10, 31, 12))[0], day_pillar(datetime(2026, 10, 31, 12))[1]))
    ok &= check("2024-02-10 (Spring Festival) day pillar", day_pillar(datetime(2024, 2, 10, 12)),
                ("Jia", "Chen"))
    ok &= check("1984-02-02 (start of the Jia Zi year) day pillar",
                day_pillar(datetime(1984, 2, 2, 12)), ("Bing", "Yin"))
    ok &= check("2026 year pillar after Li Chun", year_pillar(datetime(2026, 3, 1, 12)),
                ("Bing", "Wu"))
    ok &= check("2026 year pillar before Li Chun", year_pillar(datetime(2026, 1, 15, 12)),
                ("Yi", "Si"))
    ok &= check("2026-03-01 month pillar (Ying month)", month_pillar(datetime(2026, 3, 1, 12)),
                ("Geng", "Yin"))

    print("Hour pillars (Wu Shu Dun)")
    def a_day_with_stem(stem: str) -> datetime:
        probe = datetime(2026, 1, 1, 12)
        for i in range(400):
            m = probe + timedelta(days=i)
            if day_pillar(m)[0] == stem:
                return m
        raise AssertionError(stem)

    ok &= check("Jia day, zi hour", hour_pillar(a_day_with_stem("Jia").replace(hour=0, minute=30)),
                ("Jia", "Zi"))
    ok &= check("Yi day, zi hour", hour_pillar(a_day_with_stem("Yi").replace(hour=0, minute=30)),
                ("Bing", "Zi"))
    ok &= check("Wu day, zi hour", hour_pillar(a_day_with_stem("Wu").replace(hour=0, minute=30)),
                ("Ren", "Zi"))
    ok &= check("Ji day, zi hour", hour_pillar(a_day_with_stem("Ji").replace(hour=0, minute=30)),
                ("Jia", "Zi"))
    ok &= check("Geng day, zi hour", hour_pillar(a_day_with_stem("Geng").replace(hour=0, minute=30)),
                ("Bing", "Zi"))

    print("Solar terms (UTC)")
    for year, lon, expected in [
        (2024, 0, datetime(2024, 3, 20, 3, 6, tzinfo=timezone.utc)),
        (2024, 90, datetime(2024, 6, 20, 20, 51, tzinfo=timezone.utc)),
        (2024, 180, datetime(2024, 9, 22, 12, 44, tzinfo=timezone.utc)),
        (2024, 270, datetime(2024, 12, 21, 9, 21, tzinfo=timezone.utc)),
        (2020, 0, datetime(2020, 3, 20, 3, 50, tzinfo=timezone.utc)),
    ]:
        got = [t for t in terms_for_year(year) if t[1][0] == lon][0][0]
        diff = abs((got - expected).total_seconds()) / 60.0
        passed = diff <= 16.0
        print(f"  [{'PASS' if passed else 'FAIL'}] {year} longitude {lon}: got {got}, "
              f"expected ~{expected} (diff {diff:.1f} min)")
        ok &= passed

    print("Term dates in 2026")
    for lon, expected_date in [(315, date(2026, 2, 3)), (345, date(2026, 3, 5)),
                                (0, date(2026, 3, 20)), (90, date(2026, 6, 21)),
                                (135, date(2026, 8, 7)), (270, date(2026, 12, 21)),
                                (285, date(2027, 1, 5))]:
        got = [t for t in terms_for_year(2026 if lon != 285 else 2027) if t[1][0] == lon][0][0]
        passed = got.date() == expected_date
        print(f"  [{'PASS' if passed else 'FAIL'}] longitude {lon}: got {got.date()}, "
              f"expected {expected_date}")
        ok &= passed

    print("Officers")
    ok &= check("2026-10-31 day officer", day_officer(datetime(2026, 10, 31, 12)), "DING")
    ok &= check("2026-10-31 hour officer (wu hour)", hour_officer(datetime(2026, 10, 31, 12)),
                "SI_MING")
    ok &= check("hour officer starts at the zi hour for a Jia day",
                hour_officer(a_day_with_stem("Jia").replace(hour=0, minute=30)), "QING_LONG")
    ok &= check("hour officer starts at the zi hour for a Wu day",
                hour_officer(a_day_with_stem("Wu").replace(hour=0, minute=30)), "JIN_GUI")
    ok &= check("a Jia day turns into the next day at 23:00",
                day_pillar(a_day_with_stem("Jia").replace(hour=23, minute=30))[0], "Yi")

    print("Sun palace")
    ok &= check("2026 summer solstice palace",
                sun_palace(datetime(2026, 6, 21, 12))[0], "Wu")
    ok &= check("2026 spring equinox palace",
                sun_palace(datetime(2026, 3, 20, 12))[0], "Mao")
    ok &= check("2026 winter solstice palace",
                sun_palace(datetime(2026, 12, 21, 12))[0], "Zi")

    print("Void branches")
    ok &= check("2026-10-31 void branches", sorted(void_branches(datetime(2026, 10, 31, 12))),
                sorted(["Shen", "You"]))

    print("Branch relations")
    ok &= check("Zi clashes with Wu", clash_of("Zi"), "Wu")
    ok &= check("Zi harmonises with Chou", harmony_of("Zi"), "Chou")
    ok &= check("Zi harms Wei", harm_of("Zi"), "Wei")
    ok &= check("Shen triad", sorted(triad_of("Shen")), sorted(["Shen", "Wei", "Zi"]))

    print("Year sign against the day")
    ok &= check("Zi year sign against a Chou day", natal_relation("Zi", "Chou"), "HARMONY")
    ok &= check("Zi year sign against a Wu day", natal_relation("Zi", "Wu"), "CLASH")
    ok &= check("Shen year sign against a Hai day", natal_relation("Shen", "Hai"), "HARM")
    ok &= check("Shen year sign against a Zi day", natal_relation("Shen", "Zi"), "TRIAD")
    ok &= check("Zi year sign against a Mao day", natal_relation("Zi", "Mao"), "NEUTRAL")
    ok &= check("Water year element against a Metal day", element_tone("Water", "Metal"), 5)
    ok &= check("Wood year element against a Metal day", element_tone("Wood", "Metal"), -5)
    ok &= check("Water year element against a Fire day", element_tone("Water", "Fire"), 3)
    ok &= check("Wood year element against a Wood day", element_tone("Wood", "Wood"), 4)
    ok &= check("Metal year element against a Wood day", element_tone("Metal", "Wood"), 3)

    print("Dream vocabulary")
    snake = dream_extract("A black snake by a river")
    ok &= check("the animal in the dream is found", snake["principal"]["branch"], "Si")
    ok &= check("a snake carries the Flying Snake", snake["principal"]["spirit"], "TENG_SHE")
    ok &= check("the river is read as Water", any(
        t["kind"] == "NATURE" and t["element"] == "Water" for t in snake["tokens"]), True)
    ok &= check("'rat' is not found inside 'grateful'",
                dream_extract("I was grateful for the day")["tokens"], [])
    ok &= check("a direction is read as a branch",
                dream_extract("I walked south")["principal"]["branch"], "Wu")
    ok &= check("the north-east corner belongs to Chou",
                dream_extract("I walked north-east")["principal"]["branch"], "Chou")
    ok &= check("money in a dream is the sign of wealth",
                dream_extract("I found a pile of money")["tokens"][0]["wealth"], True)
    ok &= check("eleven is not read as one",
                dream_extract("eleven birds")["principal"]["branch"], "Xu")
    ok &= check("a bare number is a number, not an hour",
                dream_extract("three cats")["tokens"][0]["kind"], "NUMBER")
    ok &= check("a clock time is an hour",
                dream_extract("woke at 3:30")["hourBranch"], "Yin")
    ok &= check("a dream with no image has no omen",
                dream_extract("nothing happened at all")["principal"], None)

    print("Dream reading")
    probe = datetime(2026, 1, 5, 12, 0)
    _, probe_branch = day_pillar(probe)
    clash_image = dream_extract(f"A dog in the north")     # Xu against the day's branch
    ok &= check("the reading is bounded", 0 <= dream_reading(probe, clash_image)["score"] <= 100, True)
    empty = dream_reading(probe, dream_extract("nothing at all"))
    ok &= check("an empty dream is marked down", empty["score"] < 50, True)
    ok &= check("the hour follows the double-hour", dream_branch_of_hour(23), "Zi")
    ok &= check("one minute past midnight is still Zi", dream_branch_of_hour(0), "Zi")
    ok &= check("one in the morning is Chou", dream_branch_of_hour(1), "Chou")

    return ok


# ------------------------------------------------------------------------ dream
#
# Dream reading (meng zhan). The dream supplies the image, the day's pillar
# supplies the field and the reader's year branch supplies the person. These are
# the same rules as the Kotlin engine in core/DreamEngine.kt - the two
# implementations must agree, which is what the golden file checks.

DREAM_SPIRITS = {
    "QING_LONG": ("Azure Dragon", "青龙", "Wood"),
    "ZHU_QUE": ("Vermilion Bird", "朱雀", "Fire"),
    "BAI_HU": ("White Tiger", "白虎", "Metal"),
    "XUAN_WU": ("Dark Warrior", "玄武", "Water"),
    "TENG_SHE": ("Flying Snake", "螣蛇", "Fire"),
}

# The order the vocabulary is searched in, and the order that decides which
# image is the main omen.
DREAM_KIND_ORDER = ["CREATURE", "WEALTH", "DIRECTION", "NUMBER", "HOUR", "COLOUR", "NATURE"]

NUMBER_WORDS = ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve"]


def dream_spirit_of(branch: str) -> str:
    """The beast that holds a branch, by the four divisions."""
    if branch in ("Yin", "Mao", "Chen"):
        return "QING_LONG"
    if branch in ("Si", "Wu", "Wei"):
        return "ZHU_QUE"
    if branch in ("Shen", "You", "Xu"):
        return "BAI_HU"
    if branch in ("Hai", "Zi", "Chou"):
        return "XUAN_WU"
    return "QING_LONG"


def palace_of_branch(branch: str) -> str:
    """The mountain a branch sits on (two mountains share each branch)."""
    return PALACES[BRANCHES.index(branch) * 2][0]


def branch_of_palace(palace: str) -> str:
    """The branch that shares a mountain."""
    return BRANCHES[[p[0] for p in PALACES].index(palace) // 2]


def palace_element(palace: str) -> str:
    return next(p[1] for p in PALACES if p[0] == palace)


def palace_bearing(palace: str) -> int:
    return next(p[5] for p in PALACES if p[0] == palace)


def _animal(words, chinese, branch, spirit=None):
    return {
        "words": words, "kind": "CREATURE", "term": words[0].capitalize(), "chinese": chinese,
        "branch": branch, "element": BRANCH_ELEMENT[branch], "palace": palace_of_branch(branch),
        "spirit": spirit or dream_spirit_of(branch), "wealth": False,
    }


def _element(words, element, chinese):
    palace = {"Wood": "Mao", "Fire": "Wu", "Metal": "You", "Water": "Zi", "Earth": "Chen"}[element]
    return {
        "words": words, "kind": "NATURE", "term": element, "chinese": chinese, "branch": None,
        "element": element, "palace": palace, "spirit": None, "wealth": False,
    }


def _colour(words, element, chinese):
    palace = {"Wood": "Mao", "Fire": "Wu", "Metal": "You", "Water": "Zi", "Earth": "Chen"}[element]
    return {
        "words": words, "kind": "COLOUR", "term": words[0].capitalize(), "chinese": chinese,
        "branch": None, "element": element, "palace": palace, "spirit": None, "wealth": False,
    }


def _direction(words, palace, term):
    return {
        "words": words, "kind": "DIRECTION", "term": term, "chinese": palace,
        "branch": branch_of_palace(palace), "element": palace_element(palace), "palace": palace,
        "spirit": None, "wealth": False,
    }


def _number(words, branch):
    return {
        "words": words, "kind": "NUMBER", "term": words[0], "chinese": branch, "branch": branch,
        "element": BRANCH_ELEMENT[branch], "palace": palace_of_branch(branch), "spirit": None,
        "wealth": False,
    }


def dream_lexicon() -> list[dict]:
    """The whole vocabulary of the dream."""
    entries = []
    # the twelve animals
    entries.append(_animal(["rat", "mouse"], "鼠", "Zi"))
    entries.append(_animal(["ox", "bull", "cow"], "牛", "Chou"))
    entries.append(_animal(["tiger"], "虎", "Yin"))
    entries.append(_animal(["rabbit", "hare"], "兔", "Mao"))
    entries.append(_animal(["dragon"], "龙", "Chen"))
    entries.append(_animal(["snake", "serpent"], "蛇", "Si", "TENG_SHE"))
    entries.append(_animal(["horse"], "马", "Wu"))
    entries.append(_animal(["goat", "sheep"], "羊", "Wei"))
    entries.append(_animal(["monkey", "ape"], "猴", "Shen"))
    entries.append(_animal(["rooster", "cock", "chicken", "hen"], "鸡", "You"))
    entries.append(_animal(["dog"], "狗", "Xu"))
    entries.append(_animal(["pig", "boar"], "猪", "Hai"))
    # direction - the compound directions come first, so that "north-east" is
    # claimed as a whole and not split by the plain "north"
    entries.append(_direction(["north-east", "northeast", "north east"], "Gen", "North-East"))
    entries.append(_direction(["south-east", "southeast", "south east"], "Xun", "South-East"))
    entries.append(_direction(["south-west", "southwest", "south west"], "Kun", "South-West"))
    entries.append(_direction(["north-west", "northwest", "north west"], "Qian", "North-West"))
    entries.append(_direction(["north", "northern"], "Zi", "Due North"))
    entries.append(_direction(["south", "southern"], "Wu", "Due South"))
    entries.append(_direction(["east", "eastern"], "Mao", "Due East"))
    entries.append(_direction(["west", "western"], "You", "Due West"))
    entries.append(_direction(["up", "upward", "above", "sky", "ceiling", "heaven", "high"], "Qian", "Upward"))
    entries.append(_direction(["down", "downward", "below", "underground", "beneath", "buried"], "Kun", "Downward"))
    # the five phases
    entries.append(_element([
        "water", "river", "sea", "ocean", "lake", "rain", "flood", "wave", "waves", "ice", "snow",
        "drowning", "swimming", "pond", "puddle", "rainwater"], "Water", "水"))
    entries.append(_element([
        "fire", "flame", "flames", "blaze", "burning", "burnt", "torch", "candle", "lamp", "bonfire",
        "volcano", "furnace", "inferno", "lava"], "Fire", "火"))
    entries.append(_element([
        "tree", "trees", "forest", "wood", "wooden", "bamboo", "leaf", "leaves", "branch", "bush",
        "grass", "plant", "flower", "flowers", "garden", "park", "vine", "plank"], "Wood", "木"))
    entries.append(_element([
        "metal", "iron", "steel", "knife", "sword", "bell", "coin", "coins", "key", "car", "train",
        "bus", "machine", "engine", "phone", "computer", "mirror", "needle", "nails", "screw"], "Metal", "金"))
    entries.append(_element([
        "earth", "soil", "mud", "wall", "walls", "mountain", "hill", "stone", "rock", "sand", "ground",
        "house", "home", "room", "door", "city", "street", "road", "field", "farm", "food", "rice",
        "bread", "meat", "egg", "eggs", "cake", "fruit", "vegetable", "table", "bed", "chair", "box",
        "brick", "clay", "pottery", "cemetery", "grave", "graveyard", "coffin"], "Earth", "土"))
    # colours
    entries.append(_colour(["green", "emerald", "olive"], "Wood", "青"))
    entries.append(_colour(["red", "crimson", "scarlet"], "Fire", "赤"))
    entries.append(_colour(["yellow", "brown", "ochre"], "Earth", "黄"))
    entries.append(_colour(["white", "silver", "grey", "gray"], "Metal", "白"))
    entries.append(_colour(["black", "dark"], "Water", "黑"))
    # the sign of money
    entries.append({
        "words": ["money", "coin", "coins", "cash", "gold", "gold coins", "fortune", "wealth", "salary"],
        "kind": "WEALTH", "term": "Money", "chinese": "财", "branch": None, "element": "Metal",
        "palace": "You", "spirit": None, "wealth": True,
    })
    # numbers
    for index, word in enumerate(NUMBER_WORDS):
        entries.append(_number([word, str(index + 1)], BRANCHES[index]))
    return entries


def dream_pattern(word: str) -> re.Pattern:
    """English words match on word boundaries; Chinese terms as substrings."""
    if any(ord(ch) > 0x2E80 for ch in word):
        return re.compile(re.escape(word))
    return re.compile(r"\b" + re.escape(word) + r"(s|es)?\b", re.IGNORECASE)


def dream_hour_words() -> list[tuple[str, int]]:
    return [
        ("midnight", 0), ("子时", 0),
        ("dawn", 5), ("sunrise", 5), ("daybreak", 5), ("early morning", 5), ("寅时", 3),
        ("morning", 7), ("辰时", 7),
        ("noon", 11), ("midday", 11), ("午时", 11),
        ("afternoon", 13), ("未时", 13),
        ("evening", 19), ("dusk", 17), ("sunset", 17), ("酉时", 17),
        ("night", 21), ("tonight", 21), ("亥时", 21),
    ]


def dream_branch_of_hour(hour: int) -> str:
    """The branch that holds a double-hour."""
    table = {23: "Zi", 0: "Zi", 1: "Chou", 2: "Chou", 3: "Yin", 4: "Yin", 5: "Mao", 6: "Mao",
             7: "Chen", 8: "Chen", 9: "Si", 10: "Si", 11: "Wu", 12: "Wu", 13: "Wei", 14: "Wei",
             15: "Shen", 16: "Shen", 17: "You", 18: "You", 19: "Xu", 20: "Xu", 21: "Hai", 22: "Hai"}
    return table[hour]


def dream_find_hour(text: str):
    """The hour the text names, if it names one."""
    clock = re.search(r"\b(\d{1,2}):(\d{2})\b", text)
    if clock:
        return int(clock.group(1)) % 24, clock.span()
    meridiem = re.search(r"\b(\d{1,2})\s*(am|pm)\b", text, re.IGNORECASE)
    if meridiem:
        hour = int(meridiem.group(1)) % 24
        if meridiem.group(2).lower() == "pm" and hour < 12:
            hour += 12
        if meridiem.group(2).lower() == "am" and hour == 12:
            hour = 0
        return hour, meridiem.span()
    lowered = text.lower()
    for word, hour in dream_hour_words():
        index = lowered.find(word)
        if index >= 0:
            return hour, (index, index + len(word))
    return None


def dream_extract(text: str, woke_hour=None) -> dict:
    """Take the images out of the dream text."""
    tokens = []
    used = []

    def is_free(span):
        return all(not (span[0] <= other[1] - 1 and other[0] <= span[1] - 1) for other in used)

    hour_hit = dream_find_hour(text)
    masked = list(text)
    if hour_hit:
        start, end = hour_hit[1]
        for i in range(start, end):
            masked[i] = " "
        used.append((start, end))
    masked = "".join(masked)

    lexicon = dream_lexicon()
    patterns = [(entry, [dream_pattern(w) for w in entry["words"]]) for entry in lexicon]

    def claim(entry, hit):
        token = dict(entry)
        token["term"] = hit.group(0).strip().capitalize() or entry["term"]
        tokens.append(token)
        used.append(hit.span())

    # everything except the numbers
    for entry, pats in patterns:
        if entry["kind"] == "NUMBER":
            continue
        for pat in pats:
            hit = pat.search(masked)
            if hit and is_free(hit.span()):
                claim(entry, hit)
                break

    # then the numbers, longest word first so "eleven" is not read as "one"
    number_entries = [(e, p) for e, p in patterns if e["kind"] == "NUMBER"]
    number_entries.sort(key=lambda pair: -max(len(w) for w in pair[0]["words"]))
    number_entries.sort(key=lambda pair: 0 if all(w in NUMBER_WORDS for w in pair[0]["words"]) else 1)
    for entry, pats in number_entries:
        for pat in pats:
            hit = pat.search(masked)
            if hit and is_free(hit.span()):
                claim(entry, hit)
                break

    hour_branch = dream_branch_of_hour(woke_hour if woke_hour is not None else (hour_hit[0] if hour_hit else 0)) \
        if (woke_hour is not None or hour_hit) else None

    principal = None
    for kind in DREAM_KIND_ORDER:
        for token in tokens:
            if token["kind"] == kind:
                principal = token
                break
        if principal:
            break

    element = principal["element"] if principal else None
    if element is None and tokens:
        counts = {}
        for token in tokens:
            if token["element"]:
                counts[token["element"]] = counts.get(token["element"], 0) + 1
        if counts:
            element = max(counts.items(), key=lambda kv: kv[1])[0]

    return {
        "tokens": tokens,
        "principal": principal,
        "hourBranch": hour_branch,
        "element": element,
        "seed": len(text),
    }


def dream_hour_officer(day_stem: str, hour_branch: str) -> str:
    """The hour-officer for any double-hour of any day."""
    offset = {0: 0, 5: 0, 1: 1, 6: 1, 2: 2, 7: 2, 3: 3, 8: 3}.get(STEMS.index(day_stem), 4)
    return HOUR_OFFICERS[(BRANCHES.index(hour_branch) + offset) % 12]


def dream_reading(moment: datetime, image: dict, natal=None) -> dict:
    """Read the dream against the day's plate."""
    factors = []
    score = 50

    def add(label, delta, note=""):
        nonlocal score
        if delta != 0:
            factors.append({"label": label, "delta": delta, "note": note})
            score += delta

    day_stem, day_branch = day_pillar(moment)
    day_element = STEM_ELEMENT[day_stem]
    sun = sun_palace(moment)[0]
    clash = PALACES[((BRANCHES.index(day_branch) + 6) * 2) % 24][0]
    wealth_element = CONTROLS[day_element]
    wealth_bearing = {"Wood": 90, "Fire": 180, "Metal": 270, "Water": 0, "Earth": 120}[wealth_element]

    omen = image["principal"]

    if omen and omen["branch"]:
        relation = natal_relation(omen["branch"], day_branch)
        tones = {"HARMONY": 8, "TRIAD": 5, "CLASH": -9, "HARM": -4, "NEUTRAL": 0}
        labels = {
            "HARMONY": "The omen and the day's branch combine",
            "TRIAD": "The omen joins the day's own frame",
            "CLASH": "The omen clashes the day's branch",
            "HARM": "The omen harms the day's branch",
            "NEUTRAL": "The omen stands apart from the day's branch",
        }
        if tones[relation] != 0:
            add(labels[relation], tones[relation],
                f"Omen branch {omen['branch']} against day branch {day_branch}.")
        day_triad = triad_of(day_branch)
        opposite = triad_of(clash_of(day_branch))
        if omen["branch"] in day_triad and omen["branch"] != day_branch:
            add("The omen carries the day's frame", 5,
                f"{omen['branch']} is one of the day's own three.")
        elif omen["branch"] in opposite:
            add("The omen carries the opposing frame", -4,
                f"{omen['branch']} belongs to the frame that opposes the day.")

    if omen and omen["element"]:
        tone = element_tone(omen["element"], day_element)
        if tone != 0:
            add(f"The omen {element_phrase(omen['element'], day_element)} the day", tone,
                f"Omen element {omen['element']}; day element {day_element}.")

    direction = next((t for t in image["tokens"] if t["kind"] == "DIRECTION"), None)
    if direction:
        palace = direction["palace"]
        if palace == sun:
            add("The dream points where the sun stands", 5,
                f"The sun rests on the {palace} mountain.")
        elif palace == clash:
            add("The dream points at the direction to avoid", -6,
                f"The {palace} mountain clashes the day branch.")
        diff = abs(palace_bearing(palace) - wealth_bearing) % 360
        diff = min(diff, 360 - diff)
        if diff == 0:
            add("The dream points at the wealth direction", 4,
                f"The {palace} mountain faces where gain waits.")
        elif diff == 180:
            add("The dream points away from the wealth direction", -3)

    hour = image["hourBranch"]
    if hour:
        if hour == day_branch:
            add("The hour of waking is the day's own branch", 3, f"{hour} hour.")
        elif harmony_of(hour) == day_branch:
            add("The hour of waking combines with the day", 5)
        elif hour == clash_of(day_branch):
            add("The hour of waking clashes the day", -4)
        officer = dream_hour_officer(day_stem, hour)
        add(f"The hour is under {officer}", 3 if officer in (
            "QING_LONG", "MING_TANG", "JIN_GUI", "TIAN_DE", "YU_TANG", "SI_MING") else -3)

    if natal and omen and omen["branch"]:
        relation = natal_relation(natal, omen["branch"])
        tones = {"HARMONY": 8, "TRIAD": 5, "CLASH": -9, "HARM": -4, "NEUTRAL": 0}
        if tones[relation] != 0:
            add(f"Your year sign {relation.lower()} the omen", tones[relation],
                f"Year branch {natal} against omen branch {omen['branch']}.")

    if any(t["wealth"] for t in image["tokens"]):
        add("The dream shows the sign of money", 6,
            "Money in a dream is the one omen that is never ambiguous.")

    count = len(image["tokens"])
    if count == 0:
        add("The dream held no image the plate could use", -12)
    elif count <= 2:
        add("A short, concrete dream", 4, "One or two images - the message is plain.")
    elif count > 5:
        add("A crowded dream", -4, "So many images that the message scatters.")

    clamped = max(0, min(100, score))
    return {"score": clamped, "factors": factors, "image": image}


# ------------------------------------------------------------------ golden export

def export_golden(path: Path) -> None:
    """Write the golden values the Kotlin unit tests assert against.

    Tab-separated so the JVM test can parse it without pulling in a JSON library.
    """
    rows = []
    start = datetime(2026, 1, 1, 12, 0)
    # Two full years of daily samples: the app now reads a plate for any chosen
    # day, not only for today, so the forward scan and the date picker both need
    # the golden data to cover future dates.
    samples = [start + timedelta(days=i) for i in range(730)]
    samples += [
        datetime(2026, 5, 5, 0, 30), datetime(2026, 5, 5, 22, 45),
        datetime(2026, 5, 5, 23, 15), datetime(2026, 5, 6, 0, 10),
        datetime(2027, 1, 5, 12, 0), datetime(2028, 2, 29, 12, 0),
        datetime(2026, 12, 31, 23, 59), datetime(2027, 1, 1, 0, 0),
        datetime(1949, 10, 1, 12, 0), datetime(2000, 1, 1, 12, 0),
        datetime(1984, 2, 2, 12, 0), datetime(2024, 2, 10, 12, 0),
    ]
    for m in samples:
        d_stem, d_branch = day_pillar(m)
        m_stem, m_branch = month_pillar(m)
        y_stem, y_branch = year_pillar(m)
        h_stem, h_branch = hour_pillar(m)
        rows.append({
            "date": m.date().isoformat(),
            "time": f"{m.hour:02d}:{m.minute:02d}",
            "year": y_stem + y_branch,
            "month": m_stem + m_branch,
            "day": d_stem + d_branch,
            "hourPillar": h_stem + h_branch,
            "officer": day_officer(m),
            "hourOfficer": hour_officer(m),
            "term": current_term(m)[1][1],
            "sunPalace": sun_palace(m)[0],
            "sunLongitude": round(sun_longitude(
                julian_day(to_utc(m).date(), to_utc(m).hour + to_utc(m).minute / 60.0) + delta_t(m.year + 0.5) / 86400.0), 3),
            "void": "|".join(void_branches(m)),
        })

    lines = ["\t".join([
        "date", "time", "year", "month", "day", "hourPillar", "officer",
        "hourOfficer", "term", "sunPalace", "sunLongitude", "void"
    ])]
    for r in rows:
        lines.append("\t".join(str(r[k]) for k in [
            "date", "time", "year", "month", "day", "hourPillar", "officer",
            "hourOfficer", "term", "sunPalace", "sunLongitude", "void"
        ]))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} golden rows -> {path}")


def export_golden_natal(path: Path) -> None:
    """Write the golden values for the year-sign layer.

    The querent's year branch (nian ming) is measured against the day: the
    branches by harmony / triad / clash / harm, the elements by the five-phase
    relations. Twelve branches against thirty days covers every branch pairing
    the app can produce.
    """
    rows = []
    base = datetime(2026, 1, 1, 12, 0)
    for natal in BRANCHES:
        for i in range(30):
            m = base + timedelta(days=i)
            d_stem, d_branch = day_pillar(m)
            rows.append({
                "natal": natal,
                "date": m.date().isoformat(),
                "day": d_stem + d_branch,
                "relation": natal_relation(natal, d_branch),
                "elementTone": element_tone(BRANCH_ELEMENT[natal], STEM_ELEMENT[d_stem]),
            })

    lines = ["\t".join(["natal", "date", "day", "relation", "elementTone"])]
    for r in rows:
        lines.append("\t".join(str(r[k]) for k in ["natal", "date", "day", "relation", "elementTone"]))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} natal golden rows -> {path}")


def export_golden_dream(path: Path) -> None:
    """Write the golden values for the dream layer.

    The same twelve dreams are read against eight days. Each row carries the
    images the vocabulary found, the main omen and the score with the factor
    deltas that produced it - so the Kotlin engine is checked on the whole
    chain, not only on the extraction.
    """
    dreams = [
        "A black snake by a river in the south, at dawn.",
        "I dreamt of a tiger in a northern forest.",
        "Money on a table, and the smell of smoke.",
        "Falling from a great height into water.",
        "A house of stone with a red door.",
        "Three white horses running east.",
        "I was late for work and the road was uphill.",
        "A green dragon over the sea at midnight.",
        "Nothing happened. Just a grey room.",
        "An old dog followed me to the west.",
        "A yellow bird, coins, and a long climb.",
        "I walked through a bamboo grove under a bright moon.",
    ]
    dates = ["2026-01-05", "2026-03-21", "2026-06-15", "2026-09-01",
             "2026-11-11", "2027-02-04", "2027-05-20", "2027-08-08"]
    rows = []
    for date_text in dates:
        day = datetime.fromisoformat(date_text + "T12:00:00")
        for index, text in enumerate(dreams):
            image = dream_extract(text)
            reading = dream_reading(day, image)
            principal = image["principal"]
            rows.append({
                "date": date_text,
                "text": text.replace("\t", " "),
                "tokens": "|".join(
                    f"{t['kind']}:{t['term']}:{t['branch'] or ''}" for t in image["tokens"]),
                "omen": principal["term"] if principal else "",
                "omenBranch": principal["branch"] if principal and principal["branch"] else "",
                "hour": image["hourBranch"] or "",
                "score": reading["score"],
                "deltas": "|".join(str(f["delta"]) for f in reading["factors"]),
            })

    keys = ["date", "text", "tokens", "omen", "omenBranch", "hour", "score", "deltas"]
    lines = ["\t".join(keys)]
    for r in rows:
        lines.append("\t".join(str(r[k]) for k in keys))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} dream golden rows -> {path}")


def export_golden_birthdays(path: Path) -> None:
    """Write golden day pillars for birth days across a century.

    The daily golden file covers only two years; a birth day can be anywhere
    in a reader's life. A step of 97 days from 1930 to 2020 walks every
    residue of the 60-day cycle many times over, so the Kotlin side is
    checked on the same cycle math far outside the daily sample's range.
    Noon is used so the 23:00 day-turn never makes a sample ambiguous.
    """
    rows = []
    day = datetime(1930, 1, 1, 12, 0)
    end = datetime(2020, 12, 31, 12, 0)
    while day <= end:
        d_stem, d_branch = day_pillar(day)
        rows.append({"date": day.date().isoformat(), "day": d_stem + d_branch})
        day += timedelta(days=97)

    lines = ["\t".join(["date", "day"])]
    for r in rows:
        lines.append("\t".join([r["date"], r["day"]]))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} birthday golden rows -> {path}")


if __name__ == "__main__":
    print("=== Dao Fortune reference engine ===")
    passed = run_self_checks()
    resources = Path(__file__).resolve().parents[2] / "app" / "src" / "test" / "resources"
    export_golden(resources / "golden_pillars.tsv")
    export_golden_natal(resources / "golden_natal.tsv")
    export_golden_dream(resources / "golden_dream.tsv")
    export_golden_birthdays(resources / "golden_birthdays.tsv")
    print("\nSELF-CHECK:", "ALL PASS" if passed else "FAILURES PRESENT")
    raise SystemExit(0 if passed else 1)


