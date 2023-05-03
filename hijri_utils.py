import datetime

from hijri_converter import Gregorian

from hijri_converter.locales import EnglishLocale

from praytimes import PrayTimes

prayTimes = PrayTimes()
prayTimes.setMethod('Karachi')
DATE_FMT = "%m/%d/%y"

def get_prayer_times(d):
    return prayTimes.getTimes(
        d,
        (23.777176, 90.399452),
        6,
        0
    )

def str_to_time_delta(s):
    a = s.split(':')
    h = int(a[0]) - 12
    m = int(a[1])
    return datetime.timedelta(hours=h, minutes=m)

def get_maghrib_time(start):
    maghrib = str_to_time_delta(get_prayer_times(start)['maghrib'])
    a = str(maghrib).split(':')[:2]
    return int(a[0]), int(a[1])

def get_next_hijri_month():
    now = datetime.datetime.today()
    now_5 = now + datetime.timedelta(days=5)
    hijri_5 = Gregorian(now_5.year, now_5.month, now_5.day).to_hijri()
    return hijri_5.year, hijri_5.month

def get_min_margib_time(month_start_date):
    now = datetime.datetime.today()
    end_date = month_start_date + datetime.timedelta(days=30)
    start_date = month_start_date
    if (now > month_start_date):
        start_date = now
    h1, m1 = get_maghrib_time(start_date)
    h2, m2 = get_maghrib_time(end_date)
    if (h1*60+m1 < h2*60+m2):
        h, m = h1, m1
    else:
        h, m = h2, m2
    return h, m

def str_to_date(date_str):
    return datetime.datetime.strptime(date_str, DATE_FMT)

def get_hijri_months():
    return EnglishLocale.month_names

if __name__ == "__main__":
    m = get_hijri_months()
    print (m)
