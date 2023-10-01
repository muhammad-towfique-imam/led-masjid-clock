from pickletools import int4
from waqt_utils import set_waqt, time_str
from bs4 import BeautifulSoup
import copy
import datetime
DT_FMT = '%d/%m/%y %H:%M'
WHOLE_DAY_IN_SEC = 86340

def get_m2_waqt_xml(waqt_data):
    with open('templates/m2/m2-waqt.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")
    changes = waqt_data["changes"]
    changes.sort(key=lambda x:get_apply_date(x[0], x[2]))
    base = waqt_data["times"]
    times = []
    for (dt, w_idx, w_time) in changes:
        apply_date = get_apply_date(dt, w_time)
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
            new_program(bs_data, tmpl, date_start, date_start, time_start, WHOLE_DAY_IN_SEC, waqt_times)
            if (date_end - date_start).days > 1:
                new_program(bs_data, tmpl, date_start + datetime.timedelta(days=1), date_end - datetime.timedelta(days=1), None, None, waqt_times)
            new_program(bs_data, tmpl, date_end, date_end, 0, time_end, waqt_times)

    tmpl.decompose()

    return bs_data.prettify()

def get_apply_date(dt_str, w_time):
    h, m = w_time
    dt = datetime.datetime.strptime(dt_str + " " + str(h) + ":" + str(m), DT_FMT)
    return dt - datetime.timedelta(days=1) + datetime.timedelta(minutes=30)

def break_date(dt):
    return strip_time(dt), get_time_as_seconds(dt)

def strip_time(d):
    return datetime.datetime(d.year, d.month, d.day)

def get_time_as_seconds(d):
    return d.hour * 60 *60 + d.minute * 60 + d.second

def get_end_date(times, idx):
    if idx < len(times):
        time = times[idx]
        date_start = time[0]
        prev_date_end = date_start - datetime.timedelta(seconds=1)
        return prev_date_end
    return datetime.datetime.today() + datetime.timedelta(days=365)

def new_program(bs_data, tmpl, date_start, date_end, time_start, time_end, times):
    tmpl_copy = copy.copy(tmpl)
    time_in_sec = 0 if time_start is None else time_start
    dt = date_start + datetime.timedelta(seconds=time_in_sec)
    tmpl_copy['nodeName'] = dt.strftime(DT_FMT)
    tmpl_copy['specifedDateEnabled'] = 1
    tmpl_copy['dateStart'] = int(date_start.timestamp())
    tmpl_copy['dateEnd'] = int(date_end.timestamp())
    if time_start is not None:
        tmpl_copy['specifedTimeEnabled'] = 1
        tmpl_copy['timeStart'] = time_start
        tmpl_copy['timeEnd'] = time_end
    set_waqt(tmpl_copy, times)
    bs_data.screen.append(tmpl_copy)
