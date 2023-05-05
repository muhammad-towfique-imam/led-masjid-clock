import datetime

from hijri_converter import Gregorian

from praytimes import PrayTimes

import base64

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

def adjust_salah_times(t, d):
    r0 = t[0] + d[0]
    r1 = t[1] + d[1]
    if r1 > 59:
        r0 = r0 + 1
        r1 = r1 - 60
    if (r0 > 12):
        r0 = r0 - 12
    return (r0, r1)

def get_salah_times(start):
    times = get_prayer_times(start)
    return [
        adjust_salah_times(convert_salah_time(times['fajr']), (0, 50)),
        (1, 15),
        adjust_salah_times(convert_salah_time(times['asr']), (1, 40)),
        adjust_salah_times(convert_salah_time(times['maghrib']), (0, 0)),
        adjust_salah_times(convert_salah_time(times['isha']), (0, 25))
    ]

def convert_salah_time(time):
    a = str(time).split(':')[:2]
    return int(a[0]), int(a[1])

def get_maghrib_time(start):
    maghrib = str_to_time_delta(get_prayer_times(start)['maghrib'])
    return convert_salah_time(maghrib)

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
    return [
        "Muharram",
        "Safar",
        "Rabiul Awl",
        "Rabiul Akhr",
        "Juma Ula",
        "Juma Akhir",
        "Rajab",
        "Shaban",
        "Ramadan",
        "Shawwal",
        "Zul-qaadh",
        "Zul-hijjah",
    ]

def replace_edit_data(data, year):
    edit_data = data.encode("utf-8")
    decoded = base64.decodebytes(edit_data)
    y = str(year)
    updated = '\x00{}\x00{}\x00{}\x00{}\x00'.format(y[0], y[1], y[2], y[3])
    decoded = decoded.replace(b'\x001\x004\x004\x003\x00', str.encode(updated))
    return base64.b64encode(decoded).decode("utf-8")

def replace_rtf_data(data, year):
    decoded = base64.b64decode(data).decode('utf-8')
    decoded = decoded.replace('1443', str(year))
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')

def set_year(bs_data, year, nodes):
    for node in nodes:
        area = bs_data.find('area', {"nodeName" : node})
        text = area.find('text')
        text['editData'] = replace_edit_data(text['editData'], year)
        text['rtfData'] = replace_rtf_data(text['rtfData'], year)

def set_month(hijri_month, start_date, programs, hour, min):
    i = 1
    for program in programs:
        if i == hijri_month:
            counter_date = start_date - datetime.timedelta(days=1)
            program['nodeChecked'] = "1"
            timings = program.find_all('timing')
            for timing in timings:
                timing['year_'] = counter_date.year
                timing['month_'] = counter_date.month
                timing['day_'] = counter_date.day
                timing['hour_'] = hour + 12
                timing['minute_'] = min
                timing['second_'] = 0
                timing['cumulative_'] = "1"                
        else:
            program.decompose()
        i = i + 1


if __name__ == "__main__":
    m = get_hijri_months()
    print (m)
