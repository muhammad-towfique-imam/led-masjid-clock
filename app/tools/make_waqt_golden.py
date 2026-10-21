"""Makes the expected values for the Kotlin waqt tests, from the desktop app's code.

sun-dhaka.txt: Sunrise and Sunset (praytimes.py, as hijri_utils.get_prayer_times) for each day 2020-2040.
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


if __name__ == "__main__":
    sun_file()
