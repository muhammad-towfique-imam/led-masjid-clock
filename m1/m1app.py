import tkinter as tk
from tkinter import ttk
from tkinter.font import BOLD
from datetime import date
from tkcalendar import Calendar
from hijri_utils import get_hijri_info, get_hijri_months
import datetime
from m1.m1_bangla import get_bangla_program_xml
from hd2020_helper import hd_register
  
class M1App(tk.Tk):
     
    # __init__ function for class tkinterApp
    def __init__(self, *args, **kwargs):
        # __init__ function for class Tk
        tk.Tk.__init__(self, *args, **kwargs)

        style = ttk.Style(self)
        style.configure('heading.TLabel', font=(None, 35, BOLD))
        style.configure('form.TLabel', font=(None, 11))
        style.configure('form.TButton', font=(None, 11))

        self.title("Matrix Clock")
        self.geometry("600x400")
        self.resizable(0, 0)
         
        # creating a container
        container = tk.Frame(self) 
        container.pack(side = "top", fill = "both", expand = True)
  
        container.grid_rowconfigure(0, weight = 1)
        container.grid_columnconfigure(0, weight = 1)
  
        # initializing frames to an empty array
        self.frames = {} 
  
        # iterating through a tuple consisting
        # of the different page layouts
        for F in (StartPage, BanglaSetupPage, HijriSetupPage):
  
            frame = F(container, self)
  
            # initializing frame of that object from
            # startpage, page1, page2 respectively with
            # for loop
            self.frames[F] = frame
  
            frame.grid(row = 0, column = 0, sticky ="nsew")
  
        self.show_frame(StartPage)
  
    # to display the current frame passed as
    # parameter
    def show_frame(self, cont):
        frame = self.frames[cont]
        frame.tkraise()
  
class StartPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=18)
        self.rowconfigure(1, weight=1)
        self.rowconfigure(2, weight=1)
        self.rowconfigure(3, weight=18)

        lbl_heading = ttk.Label(self, text="Matrix Clock M1", style="heading.TLabel")
        lbl_heading.grid(row=0, column=0)

        bn_btn = ttk.Button(self, text="Bangla Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(BanglaSetupPage))
        bn_btn.grid(row=1, column=0, padx=20, pady=5)

        hz_btn = ttk.Button(self, text="Hijri Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(HijriSetupPage))
        hz_btn.grid(row=2, column=0, padx=20, pady=5)
  
class BanglaSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller

        lbl_heading = ttk.Label(self, text="Bangla Setup", style="heading.TLabel")
        lbl_heading.pack(pady=50)

        frame = ttk.Frame(self)
        lbl_year = ttk.Label(frame, text="Bangla Year:", style="form.TLabel")
        lbl_year.grid(row=1, column=0, sticky = tk.W, pady=5)
        current_year = date.today().year
        cmb_bn_year = ttk.Combobox(frame, font=(None, 11), values=list(range(current_year-2, current_year+3)))
        cmb_bn_year.current(2)
        cmb_bn_year.grid(row=1, column=1, padx=10, sticky = tk.W, pady=5)

        btn_apply = ttk.Button(frame, text="Apply", command=lambda: self.apply(cmb_bn_year.get()), style="form.TButton")
        btn_apply.grid(row=2, column=0, columnspan=2, sticky = tk.E, pady=5)
        
        frame.pack()

    def apply(self, bn_year):
        xml = get_bangla_program_xml(int(bn_year))
        hd_register(xml)
        self.controller.show_frame(StartPage)


class HijriSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)

        now = datetime.datetime.today()
        hz_year, hz_month, magrib_h, magrib_m = get_hijri_info()

        lbl_heading = ttk.Label(self, text="Hijri Setup", style="heading.TLabel")
        lbl_heading.pack()

        frame = ttk.Frame(self)
        lbl_year = ttk.Label(frame, text="Hijri Year:", style="form.TLabel")
        lbl_year.grid(row=0, column=0, sticky = tk.W, pady=5)
        cmb_hz_year = ttk.Combobox(frame, font=(None, 11), values=list(range(hz_year-2, hz_year+3)))
        cmb_hz_year.current(2)
        cmb_hz_year.grid(row=0, column=1, padx=10, sticky = tk.W, pady=5)

        lbl_month = ttk.Label(frame, text="Hijri Month:", style="form.TLabel")
        lbl_month.grid(row=1, column=0, sticky = tk.W, pady=5)
        cmb_hz_month = ttk.Combobox(frame, font=(None, 11), values=get_hijri_months())
        cmb_hz_month.current(hz_month - 1)   # 1 based month
        cmb_hz_month.grid(row=1, column=1, padx=10, sticky = tk.W, pady=5)

        lbl_start_date = ttk.Label(frame, text="Select start date of Hijri month:", style="form.TLabel")
        lbl_start_date.grid(row=2, column=0, columnspan=2, padx=0, sticky = tk.W, pady=5)

        cal = Calendar(frame, selectmode = 'day', year = now.year, month = now.month, day = now.day)
        cal.grid(row=3, column=0, columnspan=2, sticky = tk.E, pady=5)

        bn_apply = ttk.Button(frame, text="Apply", command=lambda: self.apply(cmb_hz_year.get(), cmb_hz_month.get(), magrib_h, magrib_m, cal.get_date()), style="form.TButton")
        bn_apply.grid(row=4, column=0, columnspan=2, sticky = tk.E, pady=5)
        
        frame.pack()

    def apply(self, year, month, h, m, start_date):
        
        self.controller.show_frame(StartPage)

  
