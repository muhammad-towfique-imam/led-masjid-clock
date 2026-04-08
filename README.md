# Matrix Clock

Islamic prayer times display application.

## Requirements

- [uv](https://github.com/astral-sh/uv) - Fast Python package manager

## Setup

Install dependencies using uv:

```bash
uv sync
```

Or manually:

```bash
uv venv .venv
uv pip install -e .
```

## Run

```bash
source .venv/bin/activate
python mp.py
```

Or without activating the venv:

```bash
uv run python mp.py
```

## Build .exe

Using uvx (no venv required):

```bash
uvx pyinstaller mp.py --onefile --windowed --icon images/icon.ico --add-data settings.json:. --add-data templates:templates --add-data images:images
```

Output will be in `dist/mc`.

## Build .exe (Windows)

For Windows .exe, run the build command on Windows. The icon will only work on Windows/macOS.
