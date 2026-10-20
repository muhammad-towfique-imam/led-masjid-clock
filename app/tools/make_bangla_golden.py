"""Makes the expected Bangla date ranges for the Kotlin tests, from the desktop app (bangla_utils.set_month).

The time zone is fixed UTC+6 (the design: the only Bangladesh summer time, 2009, is not relevant).
Run from the repository root:  python3 app/tools/make_bangla_golden.py
"""
import datetime
import os
import sys
import time
import types

os.environ["TZ"] = "Etc/GMT-6"  # fixed UTC+6 (POSIX sign is reversed)
time.tzset()

sys.path.insert(0, ".")
# bangla_utils imports bangladatetime, but set_month does not use it.
sys.modules.setdefault("bangladatetime", types.SimpleNamespace(date=None))
import bangla_utils  # noqa: E402

UTC6 = datetime.timezone(datetime.timedelta(hours=6))


class FakeProgram(dict):
    def find_all(self, name):
        return []


with open("app/android/src/test/resources/bangla/ranges.txt", "w") as f:
    for year in range(1400, 1500):
        programs = [FakeProgram() for _ in range(13)]
        bangla_utils.set_month(year, programs)
        for i, p in enumerate(programs, start=1):
            start = datetime.datetime.fromtimestamp(p["dateStart"], UTC6).date()
            end = datetime.datetime.fromtimestamp(p["dateEnd"], UTC6).date()
            f.write(f"{year} {i} {start.isoformat()} {end.isoformat()}\n")
