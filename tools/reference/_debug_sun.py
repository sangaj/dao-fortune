import math, sys
sys.path.insert(0, r"E:\Minimax\dao-fortune\tools\reference")
from luopan_reference import sun_longitude, julian_day, delta_t, jd_to_datetime, find_crossing

def lam_utc(y, m, d, hh, mm):
    jd = julian_day(__import__("datetime").date(y, m, d), hh + mm / 60.0)
    jde = jd + delta_t(y + 0.5) / 86400.0
    return sun_longitude(jde)

print("model lambda at published instants (should be ~0/90/180/270):")
for args, expect in [
    ((2024, 3, 20, 3, 6), 0.0),
    ((2024, 6, 20, 20, 51), 90.0),
    ((2024, 9, 22, 12, 44), 180.0),
    ((2024, 12, 21, 9, 21), 270.0),
    ((2000, 3, 20, 7, 35), 0.0),
]:
    lam = lam_utc(*args)
    print(f"  {args}  model lambda = {lam:9.4f}   expected ~{expect}   error = {lam - expect:+.4f} deg")

print()
print("daily rate of model lambda around June 2024:")
prev = None
for day in range(17, 24):
    lam = lam_utc(2024, 6, day, 12, 0)
    delta = "" if prev is None else f"  delta = {lam - prev:+.5f} deg/day"
    print(f"  2024-06-{day} 12:00  lambda = {lam:9.4f}{delta}")
    prev = lam

print()
print("crossing check: bisection target vs known values")
for lon, y, expect in [(0, 2024, "2024-03-20 03:06"), (90, 2024, "2024-06-20 20:51"),
                        (180, 2024, "2024-09-22 12:44"), (270, 2024, "2024-12-21 09:21")]:
    jd_jan1 = julian_day(__import__("datetime").date(y, 1, 1), 12.0)
    doy = 79.0 + lon * 365.2422 / 360.0
    guess = jd_jan1 + (doy - 1.0)
    if doy > 366.0:
        guess -= 365.0
    jde = find_crossing(float(lon), guess)
    jd_ut = jde - delta_t(y + 0.5) / 86400.0
    print(f"  lon {lon:3d} -> {jd_to_datetime(jd_ut)}   (published: {expect})")

print()
print("sanity: model lambda on 2000-01-01 12:00 TT (should be ~280.4)")
jd = julian_day(__import__("datetime").date(2000, 1, 1), 12.0)
print("  ", sun_longitude(jd))
