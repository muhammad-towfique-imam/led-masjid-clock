from pickletools import int4
from waqt_utils import set_waqt

from bs4 import BeautifulSoup

def get_m2_waqt_xml(times):
    with open('templates/m2/m2-waqt.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")

    set_waqt(bs_data, times)

    return bs_data.prettify()
