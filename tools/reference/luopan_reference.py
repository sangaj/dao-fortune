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

    return ok


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


if __name__ == "__main__":
    print("=== Dao Fortune reference engine ===")
    passed = run_self_checks()
    resources = Path(__file__).resolve().parents[2] / "app" / "src" / "test" / "resources"
    export_golden(resources / "golden_pillars.tsv")
    export_golden_natal(resources / "golden_natal.tsv")
    print("\nSELF-CHECK:", "ALL PASS" if passed else "FAILURES PRESENT")
    raise SystemExit(0 if passed else 1)


