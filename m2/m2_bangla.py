from pickletools import int4

from bs4 import BeautifulSoup
from bangla_utils import set_year, set_month

def get_m2_bangla_xml(bn_year):
    with open('m2/m2-bangla.xml', 'r') as f:
        data = f.read()
    
    bs_data = BeautifulSoup(data, "xml")
    
    programs = bs_data.find_all('program')

    set_month(bn_year, programs)

    set_year(bs_data, bn_year, ['s1-month-year'])

    return bs_data.prettify()
