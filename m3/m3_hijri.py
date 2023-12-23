from pickletools import int4
from common_utils import fix_temp_guid
from hijri_utils import set_year, set_month

from bs4 import BeautifulSoup

def get_m3_hijri_xml(hijri_year, hijri_month, hour, min, start_date):
    with open('templates/m3/m3-hijri.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")

    programs = bs_data.find_all('program')

    set_month(hijri_month, start_date, programs, hour, min)

    set_year(bs_data, hijri_year, ['s1-month-year'])

    fix_temp_guid(bs_data)
    
    return bs_data.prettify()
