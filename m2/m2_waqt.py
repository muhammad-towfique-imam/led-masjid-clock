from pickletools import int4
from waqt_utils import set_waqt
from bs4 import BeautifulSoup
import copy
import datetime
DT_FMT = '%d/%m/%y'

def get_m2_waqt_xml(times):
    with open('templates/m2/m2-waqt.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")

    tmpl = bs_data.find('program', {"nodeName" : "salah"})
    times.sort(key=lambda x:datetime.datetime.strptime(x[0], DT_FMT))
    for i in range(len(times)):
        time = times[i]
        ts_str = time[0]
        start_date = datetime.datetime.strptime(ts_str, DT_FMT)
        end_date = get_end_date(times, i+1)
        waqt_times = time[1:]
        new_program(bs_data, tmpl,  ts_str, start_date, end_date, waqt_times)

    tmpl.decompose()

    return bs_data.prettify()

def get_end_date(times, idx):
    if idx < len(times):
        time = times[idx]
        ts_str = time[0]
        start_date = datetime.datetime.strptime(ts_str, DT_FMT)
        prev_end_date = start_date - datetime.timedelta(days=1)
        return prev_end_date
    return datetime.datetime.today() + datetime.timedelta(days=365)

def new_program(bs_data, tmpl, name, start_date, end_date, times):
    tmpl_copy = copy.copy(tmpl)
    tmpl_copy['nodeName'] = name
    tmpl_copy['dateStart'] = int(start_date.timestamp())
    tmpl_copy['dateEnd'] = int(end_date.timestamp())
    set_waqt(tmpl_copy, times)
    bs_data.screen.append(tmpl_copy)
