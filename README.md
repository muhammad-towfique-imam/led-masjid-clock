# Matrix Clock

Islamic prayer times display application.

## Requirements

- [uv](https://github.com/astral-sh/uv) - Fast Python package manager
- Kivy requires SDL2 and OpenGL dependencies

### Linux dependencies

```bash
# Debian/Ubuntu
sudo apt-get install python3-kivy libsdl2-dev libsdl2-image-dev libsdl2-mixer-dev libsdl2-ttf-dev libportmidi-dev libswscale-dev libavformat-dev libavcodec-dev zlib1g-dev xclip

# Arch Linux
sudo pacman -S python-kivy sdl2
```

## Setup

Install dependencies using uv:

```bash
uv sync
```

## Run

```bash
uv run python mc.py
```

## Build .exe

Using uvx (no venv required):

```bash
uvx pyinstaller mc.py --onefile --windowed --add-data settings.json:. --add-data templates:templates --add-data images:images --hidden-import kivy
```

Output will be in `dist/mc`.

## Build .exe (Windows)

For Windows .exe, run the build command on Windows.
