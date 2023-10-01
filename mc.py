from app_ui import AppUI
from tkinter.messagebox import showerror
import tkinter as tk
import sys
import traceback

def report_callback_exception(self, exc, val, tb):
    showerror("Error", message=str(val))
    traceback.print_exc()

tk.Tk.report_callback_exception = report_callback_exception

if __name__ == "__main__":
    model = None
    if len(sys.argv) == 2:
        model = sys.argv[1]
    app = AppUI(model=model)
    app.mainloop()

