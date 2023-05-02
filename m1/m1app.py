from tkinter import *
from tkinter import ttk
from tkinter.font import BOLD, Font

from tkcalendar import Calendar


class M1App:
    def __init__(self, master) -> None:
        self.master = master
  
        self.master.columnconfigure(0, weight=1)
        self.master.rowconfigure(0, weight=18)
        self.master.rowconfigure(1, weight=1)
        self.master.rowconfigure(2, weight=1)
        self.master.rowconfigure(3, weight=18)

        lbl_heading = Label(text="Matrix Clock M1", font=Font(size=25, weight=BOLD), fg="Grey")
        lbl_heading.grid(row=0, column=0)
        bn_btn = Button(text="Bangla", width=25, height=2)
        bn_btn.grid(row=1, column=0, padx=20, pady=5)
        hz_btn = Button(text="Hijri", width=25, height=2)
        hz_btn.grid(row=2, column=0, padx=20, pady=5)
