from app_ui import AppUI
import sys

if __name__ == "__main__":
    model = None
    if len(sys.argv) == 2:
        model = sys.argv[1]
    app = AppUI(model=model)
    app.run()
