from hijri_converter import Gregorian

import base64
import datetime

DT_FMT = '%d/%m/%y %H:%M'
CHANGES_DATE_FMT = "%d/%m/%y"

def replace_edit_data(data, s, d):
    edit_data = data.encode("utf-8")
    decoded = base64.decodebytes(edit_data)
    find = '{}\x00{}\x00{}\x00{}\x00{}\x00'.format(s[0], s[1], s[2], s[3], s[4])
    updated = '{}\x00{}\x00{}\x00{}\x00{}\x00'.format(d[0], d[1], d[2], d[3], d[4])
    decoded = decoded.replace(str.encode(find), str.encode(updated))
    return base64.b64encode(decoded).decode("utf-8")

def replace_rtf_data(data, s, d):
    decoded = base64.b64decode(data).decode('utf-8')
    decoded = decoded.replace(s, d)
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')

def replace_data(text, s, d):
    text['editData'] = replace_edit_data(text['editData'], s, d)
    text['rtfData'] = replace_rtf_data(text['rtfData'], s, d)

def set_waqt(tmpl, times, section_prefix = ""):
    set_waqt_time(tmpl, section_prefix + "waqt-time", times)

def set_waqt_time(tmpl, node, times):
    area = tmpl.find('area', {"nodeName" : node})
    if area:
        text = area.find('text')
        replace_data(text, "11:11", pad(join_time(times[0])))
        replace_data(text, "22:22", pad(join_time(times[1])))
        replace_data(text, "33:33", pad(join_time(times[2])))
        replace_data(text, "44:44", pad(join_time(times[3])))
        replace_data(text, "55:55", pad(join_time(times[4])))
        replace_data(text, "66:66", pad(join_time(times[5])))
        replace_data(text, "77:77", pad(join_time(times[6])))
        replace_data(text, "88:88", pad(join_time(times[7])))

def pad(s):
    return s.rjust(5, " ")

def join_time(time):
    return f'{time[0]}:{time[1]:02}'

def split_time(s):
    a = s.split(':')
    h = int(a[0])
    m = int(a[1])
    return (h, m)

def get_apply_date(dt_str, widx, w_time):
    h, m = w_time
    if widx > 0 and h < 12:
        h = h + 12
    dt = datetime.datetime.strptime(dt_str + " " + str(h) + ":" + str(m), DT_FMT)
    return dt - datetime.timedelta(days=1) + datetime.timedelta(minutes=30)

def get_apply_date_from_array(a):
    return get_apply_date(a[0], a[1], a[2]);
    
def changes_date_to_string(dt):
    return dt.strftime(CHANGES_DATE_FMT);
