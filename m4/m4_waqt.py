from pickletools import int4
from common_utils import fix_temp_guid
from hijri_utils import adjust_12_hour, convert_salah_time, get_prayer_times
from waqt_utils import changes_date_to_string, get_apply_date, get_apply_date_from_array, set_waqt, join_time
from bs4 import BeautifulSoup
import copy
import datetime
import uuid

LABEL_DT_FMT = '%d.%m/%H:%M'
WHOLE_DAY_IN_SEC = 86340

def get_m4_waqt_xml(waqt_data):
    with open('templates/m4/m4-waqt.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")
    changes = waqt_data["changes"]
    changes.sort(key=lambda x:get_apply_date(x[0], x[1], x[2]))
    base_date = datetime.datetime.today() - datetime.timedelta(days=1)
    last_date = get_apply_date_from_array(changes[-1])
    diff = last_date - base_date
    p_sr = p_ss = None
    for i in range(diff.days + 2):  # including base and last dates
        dt = base_date + datetime.timedelta(days=i)
        t = get_prayer_times(dt)
        sr = convert_salah_time(t['sunrise'])
        ss = adjust_12_hour(convert_salah_time(t['sunset']))
        if sr != p_sr:
            changes.append([changes_date_to_string(dt), 6, sr])
            p_sr = sr
        if ss != p_ss:
            changes.append([changes_date_to_string(dt), 7, ss])
            p_ss = ss
    changes.sort(key=lambda x:get_apply_date(x[0], x[1], x[2]))
    if len(changes):
        first_change = changes[0]
        base_date = strip_time(get_apply_date(first_change[0], 0, [0, 0])) - datetime.timedelta(days=1)
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
    fix_temp_guid(bs_data)
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
