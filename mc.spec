# -*- mode: python ; coding: utf-8 -*-

import os
import shutil
import sys
from pathlib import Path

os.environ["KIVY_LOG_MODE"] = "PYTHON"
os.environ["KIVY_WINDOW"] = "sdl2"
os.environ["KIVY_GL_BACKEND"] = "angle_sdl2"

kivymd_path = Path(sys.executable).parent / "Lib" / "site-packages" / "kivymd"
icon_def_src = kivymd_path / "icon_definitions.py"

datas = []
if icon_def_src.exists():
    datas = [(str(icon_def_src), ".")]
else:
    venv_path = Path(".venv/Lib/site-packages/kivymd")
    icon_def_src = venv_path / "icon_definitions.py"
    if icon_def_src.exists():
        datas = [(str(icon_def_src), ".")]

datas += [
    ("templates", "templates"),
    ("images", "images"),
]

a = Analysis(
    ["mc.py"],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=[
        "kivymd",
        "kivymd.font_definitions",
        "kivymd.theming",
        "kivymd.uix",
        "kivymd.icon_definitions",
        "kivy",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="mc",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=["images/icon.ico"],
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="mc",
)

if os.path.exists(coll.name):
    dst = coll.name
    project_root = os.getcwd()
    for f in ["settings.json", "templates", "images"]:
        src = os.path.join(project_root, f)
        if os.path.exists(src):
            if os.path.isdir(src):
                dst_path = os.path.join(dst, f)
                if os.path.exists(dst_path):
                    shutil.rmtree(dst_path)
                shutil.copytree(src, dst_path)
            else:
                shutil.copy2(src, dst)
