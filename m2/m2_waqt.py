from pickletools import int4
from waqt_utils import get_apply_date, set_waqt, join_time
from bs4 import BeautifulSoup
import copy
import datetime
import uuid

LABEL_DT_FMT = '%d.%m/%H:%M'
WHOLE_DAY_IN_SEC = 86340

def get_m2_waqt_xml(waqt_data):
    with open('templates/m2/m2-waqt.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")
    changes = waqt_data["changes"]
    changes.sort(key=lambda x:get_apply_date(x[0], x[1], x[2]))
    base_date = datetime.datetime.today() - datetime.timedelta(days=30)
    if len(changes):
        first_change = changes[0]
        base_date = strip_time(get_apply_date(first_change[0], 0, [0, 0])) - datetime.timedelta(days=30)
    base = waqt_data["times"].copy()
    times = []
    times.append([base_date] + base)
    for (dt, w_idx, w_time) in changes:
        apply_date = get_apply_date(dt, w_idx, w_time)
        base[w_idx] = w_time
        times.append([apply_date] + base)

    tmpl = bs_data.find('program', {"nodeName" : "salah"})
    for i in range(len(times)):
        time = times[i]
        ts = time[0]
        waqt_times = time[1:]
        date_start, time_start = break_date(ts)
        date_end, time_end = break_date(get_end_date(times, i+1))
        if date_start == date_end:
            new_program(bs_data, tmpl, date_start, date_end, time_start, time_end, waqt_times)
        else:
            if time_start != 0:
                new_program(bs_data, tmpl, date_start, date_start, time_start, WHOLE_DAY_IN_SEC, waqt_times)
            if (date_end - date_start).days > 1:
                new_program(bs_data, tmpl, date_start + datetime.timedelta(days=1), date_end - datetime.timedelta(days=1), None, None, waqt_times)
            new_program(bs_data, tmpl, date_end, date_end, 0, time_end, waqt_times)

    tmpl.decompose()
    areas = bs_data.find_all('area')
    for area in areas:
        guid = '{' + str(uuid.uuid4()) + '}'
        area['tempGuid'] = guid

    return bs_data.prettify()

def break_date(dt):
    return strip_time(dt), get_time_as_seconds(dt)

def strip_time(d):
    return datetime.datetime(d.year, d.month, d.day)

def join_time(d, time_sec):
    sec = 0 if time_sec is None else time_sec
    return d + datetime.timedelta(seconds=sec)

def join_time_str(d, time_sec):
    return join_time(d, time_sec).strftime(LABEL_DT_FMT)

def get_time_as_seconds(d):
    return d.hour * 60 *60 + d.minute * 60 + d.second

def get_end_date(times, idx):
    if idx < len(times):
        time = times[idx]
        date_start = time[0]
        prev_date_end = date_start - datetime.timedelta(seconds=1)
        return prev_date_end
    return datetime.datetime.today() + datetime.timedelta(days=30)

def new_program(bs_data, tmpl, date_start, date_end, time_start, time_end, times):
    tmpl_copy = copy.copy(tmpl)
    tmpl_copy['nodeName'] = join_time_str(date_start, time_start) + " -- " + join_time_str(date_end, time_end)
    tmpl_copy['specifedDateEnabled'] = 1
    tmpl_copy['dateStart'] = int(date_start.timestamp())
    tmpl_copy['dateEnd'] = int(date_end.timestamp())
    if time_start is not None:
        tmpl_copy['specifedTimeEnabled'] = 1
        tmpl_copy['timeStart'] = time_start
        tmpl_copy['timeEnd'] = time_end
    set_waqt(tmpl_copy, times)
    bs_data.screen.append(tmpl_copy)
