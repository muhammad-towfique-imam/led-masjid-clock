import base64
import datetime
import uuid

import bangladatetime

def bn_date(dt):
    d = bangladatetime.date.fromgregorian(dt.year, dt.month, dt.day)
    return '[' + str(d.day) + '/' + str(d.month) + '/' + str(d.year) + ' = ' + str(dt.day) + '/' + str(dt.month) + '/' + str(dt.year) + ']'

def replace_edit_data(data, year):
    edit_data = data.encode("utf-8")
    decoded = base64.decodebytes(edit_data)
    y = str(year)
    updated = '\x00{}\x00{}\x00{}\x00{}\x00'.format(y[0], y[1], y[2], y[3])
    decoded = decoded.replace(b'\x001\x004\x002\x009\x00', str.encode(updated))
    return base64.b64encode(decoded).decode("utf-8")

def replace_rtf_data(data, year):
    decoded = base64.b64decode(data).decode('utf-8')
    decoded = decoded.replace('1429', str(year))
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')

def set_year(bs_data, year, nodes):
    for node in nodes:
        areas = bs_data.find_all('area', {"nodeName" : node})
        i = 1
        for area in areas:
            text = area.find('text')
            target_year = year
            if i == 13:
                target_year = year + 1
            text['editData'] = replace_edit_data(text['editData'], target_year)
            text['rtfData'] = replace_rtf_data(text['rtfData'], target_year)
            i = i + 1

def set_month(bn_year, programs):
    en_year = bn_year + 593
    year_start_date = datetime.datetime(en_year, 4, 14)
    start_date = year_start_date
    next_year_start_date = datetime.datetime(en_year + 1, 4, 14)
    i = 1 
    for program in programs:
        if i == 1:
            start_date = year_start_date
            end_date = start_date + datetime.timedelta(days=30)
        elif (i >= 2 and i <= 6) or i == 13:
            start_date = end_date + datetime.timedelta(days=1)
            end_date = start_date + datetime.timedelta(days=30)
        elif i >= 7 and i <= 10:
            start_date = end_date + datetime.timedelta(days=1)
            end_date = start_date + datetime.timedelta(days=29)
        elif i == 11:
            start_date = end_date + datetime.timedelta(days=1)
            end_date = next_year_start_date - datetime.timedelta(days=31)
        elif i == 12:
            end_date = next_year_start_date - datetime.timedelta(days=1)
            start_date = end_date - datetime.timedelta(days=29)

        counter_date = start_date - datetime.timedelta(days=1)
        program['dateStart'] = int(start_date.timestamp())
        program['dateEnd'] = int(end_date.timestamp())

        timings = program.find_all('timing')
        for timing in timings:
            timing['year_'] = counter_date.year
            timing['month_'] = counter_date.month
            timing['day_'] = counter_date.day
            timing['cumulative_'] = "1"

        i = i + 1
