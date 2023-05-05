from hijri_converter import Gregorian

import base64

def replace_edit_data(data, s, d):
    edit_data = data.encode("utf-8")
    decoded = base64.decodebytes(edit_data)
    find = '\x00{}\x00{}\x00{}\x00{}\x00'.format(s[0], s[1], s[2], s[3])
    updated = '\x00{}\x00{}\x00{}\x00{}\x00'.format(d[0], d[1], d[2], d[3])
    decoded = decoded.replace(str.encode(find), str.encode(updated))
    return base64.b64encode(decoded).decode("utf-8")

def replace_rtf_data(data, s, d):
    decoded = base64.b64decode(data).decode('utf-8')
    decoded = decoded.replace(s, d)
    return base64.b64encode(decoded.encode('utf-8')).decode('utf-8')

def replace_data(text, s, d):
    text['editData'] = replace_edit_data(text['editData'], s, d)
    text['rtfData'] = replace_rtf_data(text['rtfData'], s, d)

def set_waqt(bs_data, times):
    area = bs_data.find('area', {"nodeName" : "times"})
    if area:
        text = area.find('text')
        replace_data(text, "1:11", times[0])
        replace_data(text, "2:22", times[1])
        replace_data(text, "3:33", times[2])
        replace_data(text, "4:44", times[3])
        replace_data(text, "5:55", times[4])

