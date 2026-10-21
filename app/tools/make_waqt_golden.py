"""Makes the expected values for the Kotlin waqt tests, from the desktop app's code.

sun-dhaka.txt: Sunrise and Sunset (praytimes.py, as hijri_utils.get_prayer_times) for each day 2020-2040.
copies.txt: the program copies of get_m5_waqt_xml (app/tools/waqt_program.py, checked against the desktop app's
code) for the 6 export cases and more; tab-separated.
Run from the repository root:  python3 app/tools/make_waqt_golden.py
"""
import datetime
import sys

sys.path.insert(0, ".")
from praytimes import PrayTimes  # noqa: E402

OUT = "app/android/src/test/resources/waqt/"

pt = PrayTimes()
pt.setMethod("Karachi")


def sun_file():
    with open(OUT + "sun-dhaka.txt", "w") as f:
        d = datetime.date(2020, 1, 1)
        while d <= datetime.date(2040, 12, 31):
            t = pt.getTimes(d, (23.777176, 90.399452), 6, 0)
            f.write(f"{d.isoformat()} {t['sunrise']} {t['sunset']}\n")
            d += datetime.timedelta(days=1)


EXPORTS = "/home/towfique/Documents/debug/waqt-kit/exports"
NOW = datetime.datetime(2026, 10, 21, 21, 8, 0)
T = [[5, 10], [1, 15], [4, 15], [5, 40], [7, 0], [1, 30], [0, 0], [0, 0]]
EXTRA = [
    ("first-day", T, [["21/10/26", 2, [4, 30]]]),
    ("zuhr-noon", T, [["31/10/26", 1, [12, 30]]]),
    ("jumuah", T, [["30/10/26", 5, [1, 0]]]),
    ("same-date", T, [["31/10/26", 2, [4, 30]], ["31/10/26", 3, [5, 30]]]),
    ("forty-days", T, [["30/11/26", 2, [4, 30]]]),
]


def copies_file():
    import json
    import os
    from waqt_program import copies
    cases = []
    for name in sorted(os.listdir(EXPORTS)):
        j = json.load(open(os.path.join(EXPORTS, name, "inputs.json")))
        now = datetime.datetime.fromisoformat(j["now"]).replace(microsecond=0)
        cases.append((name, now, j["times"], j["changes"]))
    cases += [(n, NOW, t, c) for n, t, c in EXTRA]
    with open(OUT + "copies.txt", "w") as f:
        for name, now, times, changes in cases:
            ts = ",".join(f"{h}:{m}" for h, m in times[:6])
            cs = ";".join(f"{d}|{i}|{h}:{m}" for d, i, (h, m) in changes) or "-"
            f.write(f"#\t{name}\t{now.isoformat()}\t{ts}\t{cs}\n")
            for d0, d1, t0, t1, texts in copies(times, changes, now):
                f.write("\t".join([d0.date().isoformat(), d1.date().isoformat(),
                                    "-" if t0 is None else str(t0), "-" if t1 is None else str(t1)] + texts) + "\n")


if __name__ == "__main__":
    sys.path.insert(0, "app/tools")
    sun_file()
    copies_file()
