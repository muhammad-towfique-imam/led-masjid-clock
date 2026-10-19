"""Makes the expected values for the Kotlin Maghrib and Hijri start tests.

The values come from the desktop app (praytimes.py and hijri_utils.get_min_margib_time).
Run from the repository root:  python3 app/tools/make_hijri_golden.py
"""
import datetime
import sys
import types

sys.path.insert(0, ".")
# hijri_utils imports hijri_converter, but get_min_margib_time does not use it.
sys.modules.setdefault("hijri_converter", types.SimpleNamespace(Gregorian=None))
import hijri_utils  # noqa: E402

OUT = "app/android/src/test/resources/hijri/"


def days(first, last):
    d = first
    while d <= last:
        yield d
        d += datetime.timedelta(days=1)


with open(OUT + "maghrib-dhaka.txt", "w") as f:
    for d in days(datetime.date(2020, 1, 1), datetime.date(2040, 12, 31)):
        f.write(f"{d.isoformat()} {hijri_utils.get_prayer_times(d)['maghrib']}\n")


class FixedToday(datetime.datetime):
    today_value = None

    @classmethod
    def today(cls):
        return cls.today_value


hijri_utils.datetime = types.SimpleNamespace(datetime=FixedToday, timedelta=datetime.timedelta)

with open(OUT + "hijri-start.txt", "w") as f:
    f.write("# month start, today, counter start\n")
    for start in days(datetime.date(2024, 1, 1), datetime.date(2035, 12, 31)):
        if start.toordinal() % 5:
            continue
        month_start = datetime.datetime(start.year, start.month, start.day)
        for shift in (-5, 0, 10, 29):
            today = month_start + datetime.timedelta(days=shift, hours=14)
            FixedToday.today_value = FixedToday(today.year, today.month, today.day, today.hour)
            h, m = hijri_utils.get_min_margib_time(month_start)
            counter = month_start - datetime.timedelta(days=1)
            f.write(f"{start.isoformat()} {today.date().isoformat()} "
                    f"{counter.date().isoformat()}T{h + 12:02d}:{m:02d}:00\n")
