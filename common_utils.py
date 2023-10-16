import uuid

def fix_temp_guid(bs_data):
    areas = bs_data.find_all('area')
    for area in areas:
        guid = '{' + str(uuid.uuid4()) + '}'
        area['tempGuid'] = guid
