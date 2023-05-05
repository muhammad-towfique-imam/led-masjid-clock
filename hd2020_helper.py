import os
from bs4 import BeautifulSoup

HD_HOME = os.getenv('APPDATA').replace('\\', '/') + "/Huidu/HD2020" if os.getenv('APPDATA') else ""
HD_PROGRAM_DIR = HD_HOME + "\\program/"
HD_ROOT_FILE = HD_PROGRAM_DIR + "root.xml"

def hd_installed():
    return os.path.isdir(HD_PROGRAM_DIR)

def hd_root_exists():
    return os.path.isfile(HD_ROOT_FILE)

def find_filename(xml):
    bs_data = BeautifulSoup(xml, "xml")
    screen = bs_data.find('screen')
    return screen['saveFileName'] + ".xml"

def register_program(root_xml, filename):
    path = HD_PROGRAM_DIR + filename
    bs_data = BeautifulSoup(root_xml, "xml")
    group = bs_data.find('group')
    screens = group.find_all('screen')
    found = False
    for screen in screens:    
        if filename in screen['path']:
            found = True
    if not found:
        screen = bs_data.new_tag("screen", path=path)
        group.append(screen)
    return bs_data.prettify()

def hd_register(xml):
    if not hd_installed():
        raise Exception("HD2020 program is not installed")
    root_path = HD_ROOT_FILE
    if not hd_root_exists():
        root_path = "root.xml"

    with open(root_path) as f:
        root_xml = f.read()

    filename = find_filename(xml)
    updated_root_xml = register_program(root_xml, filename)
    with open(HD_ROOT_FILE, 'w') as f:
        f.write(updated_root_xml)

    with open(HD_PROGRAM_DIR + filename, 'w') as f:
        f.write(xml)

if __name__ == "__main__":
    with open("m1/m1-bangla.xml") as f:
        hd_register(f.read())
    with open("m1/m1-hijri.xml") as f:
        hd_register(f.read())


        



