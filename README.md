# REQUIRMENTS #

Run all the command from `requirments.txt`  before working on the project

# COMPILE PyInstaller

Follow these steps to compile PyInstaller bootleader to avoid false positive virus detection. 

1. Download `Microsoft Build Tools 2015` and install.
2. Download PyInstaller source code from: https://github.com/pyinstaller/pyinstaller/archive/refs/tags/v5.13.2.zip
3. Execute the following from the root folder of pyInstaller source code.

    ```
    pip install .
    ```

# GENERATE EXE #

Fix path in `auto-py-to-exe-settings.json` and load the file in auto-py-to-exe program and generate exe using it