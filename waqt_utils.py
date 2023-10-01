from hijri_converter import Gregorian

import base64

def replace_edit_data(data, s, d):
    edit_data = data.encode("utf-8")
    decoded = base64.decodebytes(edit_data)
    find = '{}\x00{}\x00{}\x00{}\x00'.format(s[0], s[1], s[2], s[3])
    updated = '{}\x00{}\x00{}\x00{}\x00'.format(d[0], d[1], d[2], d[3])
    decoded = decoded.replace(str.encode(find), str.encode(updated))
    return base64.b64encode(decoded).decode("utf-8")

def replace_rtf_data(data, s, d):
    decoded = base64.b64decode(data).decode('utf-8')
    decoded = decoded.replace(s, d)
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')

def replace_data(text, s, d):
    text['editData'] = replace_edit_data(text['editData'], s, d)
    text['rtfData'] = replace_rtf_data(text['rtfData'], s, d)

def set_waqt(tmpl, times):
    set_waqt_time(tmpl, "fazr-time", "1:11", time_str(times[0]))
    set_waqt_time(tmpl, "duhr-time", "2:22", time_str(times[1]))
    set_waqt_time(tmpl, "asr-time", "3:33", time_str(times[2]))
    set_waqt_time(tmpl, "magrib-time", "4:44", time_str(times[3]))
    set_waqt_time(tmpl, "isha-time", "5:55", time_str(times[4]))

def set_waqt_time(tmpl, waqt, find_txt, replace_txt):
    area = tmpl.find('area', {"nodeName" : waqt})
    if area:
        text = area.find('text')
        replace_data(text, find_txt, replace_txt)

def time_str(time):
    return f'{time[0]}:{time[1]:02}'    