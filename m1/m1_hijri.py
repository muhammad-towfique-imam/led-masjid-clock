import base64
import datetime
from pickletools import int4

from bs4 import BeautifulSoup


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

def set_year(bs_data, year):
    for node in ['s1-month-year', 's2-month-year']:
        area = bs_data.find('area', {"nodeName" : node})
        text = area.find('text')
        text['editData'] = replace_edit_data(text['editData'], year)
        text['rtfData'] = replace_rtf_data(text['rtfData'], year)

def get_hijri_program_xml(hijri_year, hijri_month, hour, min, start_date):
    with open('m1/m1-hijri.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")

    programs = bs_data.find_all('program')

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

    set_year(bs_data, hijri_year)

    return bs_data.prettify()
