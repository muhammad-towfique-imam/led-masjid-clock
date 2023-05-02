import datetime

from hijri_converter import Gregorian

from hijri_converter.locales import EnglishLocale

from praytimes import PrayTimes

prayTimes = PrayTimes()
prayTimes.setMethod('Karachi')

def get_hijri_date_after_5_days():
    now = datetime.datetime.today()
    
    now_5 = now + datetime.timedelta(days=5)

    hijri_5 = Gregorian(now_5.year, now_5.month, now_5.day).to_hijri()

    return hijri_5

def get_hijri_months():
    return EnglishLocale.month_names

if __name__ == "__main__":
    m = get_hijri_months()
    print (m)
