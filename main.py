from app_ui import AppUI
from tkinter.messagebox import showerror
import tkinter as tk

def report_callback_exception(self, exc, val, tb):
    showerror("Error", message=str(val))

tk.Tk.report_callback_exception = report_callback_exception

if __name__ == "__main__":
    app = AppUI(model="m2")
    app.eval('tk::PlaceWindow . center')
    app.mainloop()

