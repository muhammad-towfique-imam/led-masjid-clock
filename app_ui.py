import tkinter as tk
from tkinter import ttk
from tkinter.font import BOLD
from datetime import date
from tkcalendar import Calendar
from hijri_utils import get_next_hijri_month, get_hijri_months, get_min_margib_time, str_to_date
import datetime
from m1.m1_bangla import get_m1_bangla_xml
from m1.m1_hijri import get_m1_hijri_xml
from m1.m1_english import get_m1_english_xml
from m2.m2_bangla import get_m2_bangla_xml
from m2.m2_hijri import get_m2_hijri_xml
from m2.m2_english import get_m2_english_xml
from hd2020_helper import hd_register
import bangladatetime
import tkinter.messagebox as msg
  
class AppUI(tk.Tk):
     
    # __init__ function for class tkinterApp
    def __init__(self, model, *args, **kwargs):
        # __init__ function for class Tk
        tk.Tk.__init__(self, *args, **kwargs)

        style = ttk.Style(self)
        style.configure('heading.TLabel', font=(None, 16, BOLD))
        style.configure('subheading.TLabel', font=(None, 11))
        style.configure('form.TLabel', font=(None, 11))
        style.configure('form.TButton', font=(None, 11))

        self.model = model
        self.title("Matrix Clock")
        self.geometry("350x450")
        self.resizable(0, 0)
         
        # creating a container
        container = tk.Frame(self) 
        container.pack(side = "top", fill = "both", expand = True)
  
        container.grid_columnconfigure(0, weight = 1)
        container.grid_rowconfigure(0, weight = 1)
        container.grid_rowconfigure(1, weight = 1)
        container.grid_rowconfigure(2, weight = 8)
  
        # initializing frames to an empty array
        self.frames = {} 
  
        # iterating through a tuple consisting
        # of the different page layouts

        lbl_heading = ttk.Label(container, text="Matrix Clock " + model.upper() , style="heading.TLabel")
        lbl_heading.grid(row=0, column=0, pady=5)

        self.sub_heading = tk.StringVar()
        lbl_sub_heading = ttk.Label(container, style="subheading.TLabel", textvariable=self.sub_heading)
        lbl_sub_heading.grid(row=1, column=0, pady=(0, 20))

        for F in (StartPage, EnglishSetupPage, BanglaSetupPage, HijriSetupPage):
  
            frame = F(container, self)
  
            # initializing frame of that object from
            # startpage, page1, page2 respectively with
            # for loop
            self.frames[F] = frame
  
            frame.grid(row = 2, column = 0, sticky ="nsew")
  
        self.show_frame(StartPage)
  
    # to display the current frame passed as
    # parameter
    def show_frame(self, cont):
        frame = self.frames[cont]
        self.sub_heading.set(frame.title)
        frame.tkraise()
  
class StartPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.title = ""

        frame = ttk.Frame(self)

        en_btn = ttk.Button(frame, text="English Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(EnglishSetupPage))
        en_btn.grid(row=0, column=0, padx=20, pady=5)

        bn_btn = ttk.Button(frame, text="Bangla Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(BanglaSetupPage))
        bn_btn.grid(row=1, column=0, padx=20, pady=5)

        hz_btn = ttk.Button(frame, text="Hijri Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(HijriSetupPage))
        hz_btn.grid(row=2, column=0, padx=20, pady=5)
        
        frame.pack()
  
class EnglishSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller
        self.title = "English Setup"

        frame = ttk.Frame(self)

        btn_frame = ttk.Frame(frame)
        btn_back = ttk.Button(btn_frame, text="Back", command=lambda: self.controller.show_frame(StartPage), style="form.TButton")
        btn_back.pack(side = "left", padx=5)
        btn_apply = ttk.Button(btn_frame, text="Apply", command=lambda: self.apply(), style="form.TButton")
        btn_apply.pack(side = "left", padx=5)
        btn_frame.grid(row=0, column=0, sticky = tk.E, pady=5, columnspan=2)

        frame.pack()

    def apply(self):
        if self.controller.model == 'm1':
            xml = get_m1_english_xml()
        elif self.controller.model == 'm2':
            xml = get_m2_english_xml()
        if xml:
            hd_register(xml)
            msg.showinfo("Success", "English program written successfully")
        self.controller.show_frame(StartPage)


class BanglaSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller
        self.title = "Bangla Setup"

        frame = ttk.Frame(self)
        lbl_year = ttk.Label(frame, text="Bangla Year:", style="form.TLabel")
        lbl_year.grid(row=1, column=0, sticky = tk.W, pady=5)
        
        now = datetime.datetime.today()
        now_bn = bangladatetime.date.fromgregorian(now.year, now.month, now.day)

        cmb_bn_year = ttk.Combobox(frame, font=(None, 11), values=list(range(now_bn.year-1, now_bn.year+4)))
        cmb_bn_year.current(2)
        cmb_bn_year.grid(row=1, column=1, padx=10, sticky = tk.W, pady=5)

        btn_frame = ttk.Frame(frame)
        bn_back = ttk.Button(btn_frame, text="Back", command=lambda: self.controller.show_frame(StartPage), style="form.TButton")
        bn_back.pack(side = "left", padx=5)
        bn_apply = ttk.Button(btn_frame, text="Apply", command=lambda: self.apply(cmb_bn_year.get()), style="form.TButton")
        bn_apply.pack(side = "left", padx=5)
        btn_frame.grid(row=2, column=0, sticky = tk.E, pady=5, columnspan=2)

        frame.pack()

    def apply(self, bn_year):
        if self.controller.model == 'm1':
            xml = get_m1_bangla_xml(int(bn_year))
        elif self.controller.model == 'm2':
            xml = get_m2_bangla_xml(int(bn_year))
        if xml:
            hd_register(xml)
            msg.showinfo("Success", "Bangla program written successfully")
        self.controller.show_frame(StartPage)


class HijriSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller
        self.title = "Hijri Setup"

        now = datetime.datetime.today()
        hz_year, hz_month = get_next_hijri_month()

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

        btn_frame = ttk.Frame(frame)
        bn_back = ttk.Button(btn_frame, text="Back", command=lambda: self.controller.show_frame(StartPage), style="form.TButton")
        bn_back.pack(side = "left", padx=5)
        bn_apply = ttk.Button(btn_frame, text="Apply", command=lambda: self.apply(cmb_hz_year.get(), cmb_hz_month.current() + 1, cal.get_date()), style="form.TButton")
        bn_apply.pack(side = "left", padx=5)
        btn_frame.grid(row=4, column=0, sticky = tk.E, pady=5, columnspan=2)

        frame.pack()

    def apply(self, year, month, start_date_txt):
        start_date = str_to_date(start_date_txt)
        h, m = get_min_margib_time(start_date)
        if self.controller.model == 'm1':
            xml = get_m1_hijri_xml(int(year), month, h, m, start_date)
        elif self.controller.model == 'm2':
            xml = get_m2_hijri_xml(int(year), month, h, m, start_date)
        if xml:
            hd_register(xml)
            msg.showinfo("Success", "Hijri program written successfully")
        self.controller.show_frame(StartPage)

  
