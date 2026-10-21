"""The m5 waqt program of the desktop app (m5/m5_waqt.py, waqt_utils.py, hijri_utils.py), without BeautifulSoup.

make_xml(times, changes, now) gives the same program as get_m5_waqt_xml for the same inputs and the same "now".
"""
import base64
import copy
import datetime
import os
import re
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from praytimes import PrayTimes  # noqa: E402
DT_FMT = '%d/%m/%y %H:%M'
CHANGES_DATE_FMT = "%d/%m/%y"
LABEL_DT_FMT = '%d.%m/%H:%M'
WHOLE_DAY_IN_SEC = 86340

_pt = PrayTimes()
_pt.setMethod('Karachi')


def get_prayer_times(d):
    return _pt.getTimes(d, (23.777176, 90.399452), 6, 0)


def convert_salah_time(time):
    a = str(time).split(':')[:2]
    return [int(a[0]), int(a[1])]


def adjust_12_hour(a):
    return [int(a[0]) - 12, int(a[1])]


def get_apply_date(dt_str, widx, w_time):
    h, m = w_time
    if widx > 0 and h < 12:
        h = h + 12
    dt = datetime.datetime.strptime(dt_str + " " + str(h) + ":" + str(m), DT_FMT)
    return dt - datetime.timedelta(days=1) + datetime.timedelta(minutes=30)


def strip_time(d):
    return datetime.datetime(d.year, d.month, d.day)


def get_time_as_seconds(d):
    return d.hour * 60 * 60 + d.minute * 60 + d.second


def break_date(dt):
    return strip_time(dt), get_time_as_seconds(dt)


def join_time_str(d, time_sec):
    sec = 0 if time_sec is None else time_sec
    return (d + datetime.timedelta(seconds=sec)).strftime(LABEL_DT_FMT)


def join_time(time):
    return f'{time[0]}:{time[1]:02}'


def pad(s):
    return s.rjust(5, " ")


def _attr(tag, name, value):
    value = str(value)
    if re.search(r'\b' + name + r'="', tag):
        return re.sub(r'(\b' + name + r'=")[^"]*(")', lambda m: m.group(1) + value + m.group(2), tag, count=1)
    return tag[:-1] + f' {name}="{value}">'


def _replace_edit(data, s, d):
    decoded = base64.decodebytes(data.encode("utf-8"))
    find = '{}\x00{}\x00{}\x00{}\x00{}\x00'.format(s[0], s[1], s[2], s[3], s[4])
    updated = '{}\x00{}\x00{}\x00{}\x00{}\x00'.format(d[0], d[1], d[2], d[3], d[4])
    return base64.b64encode(decoded.replace(find.encode(), updated.encode())).decode("utf-8")


def _replace_rtf(data, s, d):
    decoded = base64.b64decode(data).decode('utf-8').replace(s, d)
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')


def _set_waqt(program, times):
    for prefix in ("s1-", "s2-", "s3-"):
        def area(m):
            a = m.group(0)
            name = re.search(r'nodeName="([^"]*)"', a).group(1)
            if name != prefix + "waqt-time":
                return a
            def text(t):
                tag = t.group(0)
                for i, ph in enumerate(("11:11", "22:22", "33:33", "44:44", "55:55", "66:66", "77:77", "88:88")):
                    new = pad(join_time(times[i]))
                    tag = re.sub(r'(editData=")([^"]*)(")', lambda x: x.group(1) + _replace_edit(x.group(2), ph, new) + x.group(3), tag)
                    tag = re.sub(r'(rtfData=")([^"]*)(")', lambda x: x.group(1) + _replace_rtf(x.group(2), ph, new) + x.group(3), tag)
                return tag
            return re.sub(r'<text\b[^>]*>', text, a)
        program = re.sub(r'<area\b.*?</area>', area, program, flags=re.S)
    return program


def _new_program(tmpl, date_start, date_end, time_start, time_end, times):
    head = re.match(r'<program\b[^>]*>', tmpl).group(0)
    h = _attr(head, "nodeName", join_time_str(date_start, time_start) + " -- " + join_time_str(date_end, time_end))
    h = _attr(h, "specifedDateEnabled", 1)
    h = _attr(h, "dateStart", int(date_start.timestamp()))
    h = _attr(h, "dateEnd", int(date_end.timestamp()))
    if time_start is not None:
        h = _attr(h, "specifedTimeEnabled", 1)
        h = _attr(h, "timeStart", time_start)
        h = _attr(h, "timeEnd", time_end)
    return _set_waqt(h + tmpl[len(head):], times)


def make_xml(times, changes, now=None, save_name=None, node_name=None):
    """times: 8 [h, m] (Sunrise/Sunset slots are ignored); changes: [["dd/mm/yy", index, [h, m]], ...]."""
    now = now or datetime.datetime.today()
    xml = open(os.path.join(HERE, "templates", "m5-waqt.xml"), encoding="utf-8").read()
    changes = copy.deepcopy(changes)
    changes.sort(key=lambda x: get_apply_date(x[0], x[1], x[2]))
    base_date = now - datetime.timedelta(days=1)
    diff = get_apply_date(*changes[-1]) - base_date if changes else datetime.timedelta(0)
    p_sr = p_ss = None
    for i in range(diff.days + 2):
        dt = base_date + datetime.timedelta(days=i)
        t = get_prayer_times(dt)
        sr = convert_salah_time(t['sunrise'])
        ss = adjust_12_hour(convert_salah_time(t['sunset']))
        if sr != p_sr:
            changes.append([dt.strftime(CHANGES_DATE_FMT), 6, sr])
            p_sr = sr
        if ss != p_ss:
            changes.append([dt.strftime(CHANGES_DATE_FMT), 7, ss])
            p_ss = ss
    changes.sort(key=lambda x: get_apply_date(x[0], x[1], x[2]))
    if changes:
        base_date = strip_time(get_apply_date(changes[0][0], 0, [0, 0])) - datetime.timedelta(days=1)
    base = [list(t) for t in times]
    table = [[base_date] + [list(t) for t in base]]
    for dt, w_idx, w_time in changes:
        base[w_idx] = w_time
        table.append([get_apply_date(dt, w_idx, w_time)] + [list(t) for t in base])

    def end_of(idx):
        if idx < len(table):
            return table[idx][0] - datetime.timedelta(seconds=1)
        return now + datetime.timedelta(days=30)

    m = re.search(r'<program\b[^>]*nodeName="salah".*?</program>', xml, re.S)
    tmpl = m.group(0)
    out = []
    for i, row in enumerate(table):
        waqt_times = row[1:]
        date_start, time_start = break_date(row[0])
        date_end, time_end = break_date(end_of(i + 1))
        if date_start == date_end:
            out.append(_new_program(tmpl, date_start, date_end, time_start, time_end, waqt_times))
        else:
            if time_start != 0:
                out.append(_new_program(tmpl, date_start, date_start, time_start, WHOLE_DAY_IN_SEC, waqt_times))
            if (date_end - date_start).days > 1:
                out.append(_new_program(tmpl, date_start + datetime.timedelta(days=1), date_end - datetime.timedelta(days=1), None, None, waqt_times))
            out.append(_new_program(tmpl, date_end, date_end, 0, time_end, waqt_times))
    xml = xml[:m.start()] + "\n".join(out) + xml[m.end():]
    if save_name:
        xml = re.sub(r'(<screen\b[^>]*?saveFileName=")[^"]*(")', lambda x: x.group(1) + save_name + x.group(2), xml, count=1)
    if node_name:
        xml = re.sub(r'(<screen\b[^>]*?nodeName=")[^"]*(")', lambda x: x.group(1) + node_name + x.group(2), xml, count=1)
    return re.sub(r'tempGuid="[^"]*"', lambda x: 'tempGuid="{' + str(uuid.uuid4()) + '}"', xml)
