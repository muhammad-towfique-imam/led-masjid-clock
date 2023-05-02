import tkinter as tk
from tkinter import ttk
from tkinter.font import BOLD
from datetime import date
from tkcalendar import Calendar
from hijri_utils import get_hijri_date_after_5_days, get_hijri_months
import datetime
  
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

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=18)
        self.rowconfigure(1, weight=1)
        self.rowconfigure(2, weight=1)
        self.rowconfigure(3, weight=18)

        lbl_heading = ttk.Label(self, text="Bangla Setup", style="heading.TLabel")
        lbl_heading.grid(row=0, column=0)

        line1_frame = ttk.Frame(self)

        lbl_year = ttk.Label(line1_frame, text="Bangla Year:", style="form.TLabel")
        lbl_year.pack(side=tk.LEFT, padx=0)

        current_year = date.today().year
        cmb_bn_year = ttk.Combobox(line1_frame, font=(None, 11), values=list(range(current_year-2, current_year+3)))
        cmb_bn_year.current(2)
        cmb_bn_year.pack(side=tk.LEFT, padx=10)

        bn_apply = ttk.Button(line1_frame, text="Apply", command=lambda: controller.show_frame(StartPage), style="form.TButton")
        bn_apply.pack(side=tk.LEFT, padx=10)

        line1_frame.grid(row=1, column=0)

class HijriSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=18)
        self.rowconfigure(1, weight=1)
        self.rowconfigure(2, weight=1)
        self.rowconfigure(3, weight=1)
        self.rowconfigure(4, weight=1)
        self.rowconfigure(5, weight=1)
        self.rowconfigure(6, weight=1)
        self.rowconfigure(7, weight=18)
        now = datetime.datetime.today()
        next_hz_date = get_hijri_date_after_5_days()

        lbl_heading = ttk.Label(self, text="Hijri Setup", style="heading.TLabel")
        lbl_heading.grid(row=0, column=0)

        line1_frame = ttk.Frame(self)
        lbl_year = ttk.Label(line1_frame, text="Hijri Year:", style="form.TLabel")
        lbl_year.pack(side=tk.LEFT, padx=0)
        cmb_hz_year = ttk.Combobox(line1_frame, font=(None, 11), values=list(range(next_hz_date.year-2, next_hz_date.year+3)))
        cmb_hz_year.current(2)
        cmb_hz_year.pack(side=tk.LEFT, padx=10)
        line1_frame.grid(row=1, column=0, pady=5)

        line2_frame = ttk.Frame(self)
        lbl_month = ttk.Label(line2_frame, text="Hijri Month:", style="form.TLabel")
        lbl_month.pack(side=tk.LEFT, padx=0)
        cmb_hz_month = ttk.Combobox(line2_frame, font=(None, 11), values=get_hijri_months())
        cmb_hz_month.current(next_hz_date.month - 1)   # 1 based month
        cmb_hz_month.pack(side=tk.LEFT, padx=10)
        line2_frame.grid(row=2, column=0, pady=5)

        line3_frame = ttk.Frame(self)
        lbl_start_date = ttk.Label(line3_frame, text="Select start date of Hijri month:", style="form.TLabel")
        lbl_start_date.pack(side=tk.LEFT, padx=0)
        line3_frame.grid(row=3, column=0)

        line4_frame = ttk.Frame(self)
        cal = Calendar(line4_frame, selectmode = 'day', year = now.year, month = now.month, day = now.day)
        cal.pack(pady = 5)
        line4_frame.grid(row=4, column=0)


        line4_frame = ttk.Frame(self)
        bn_apply = ttk.Button(line4_frame, text="Apply", command=lambda: controller.show_frame(StartPage), style="form.TButton")
        bn_apply.pack(side=tk.LEFT, padx=10)
        line4_frame.grid(row=5, column=0, pady=5)


  
