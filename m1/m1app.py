import tkinter as tk
from tkinter import ttk
from tkinter.font import BOLD
from datetime import date
from tkcalendar import Calendar
  
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

        frame = ttk.Frame(self)

        lbl_year = ttk.Label(frame, text="Bangla Year", style="form.TLabel")
        lbl_year.pack(side=tk.LEFT, padx=0)

        current_year = date.today().year
        cmb_bn_year = ttk.Combobox(frame, font=(None, 11), values=list(range(current_year-2, current_year+3)))
        cmb_bn_year.current(2)
        cmb_bn_year.pack(side=tk.LEFT, padx=10)

        bn_apply = ttk.Button(frame, text="Apply", command=lambda: controller.show_frame(StartPage), style="form.TButton")
        bn_apply.pack(side=tk.LEFT, padx=10)

        frame.grid(row=1, column=0)

class HijriSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=18)
        self.rowconfigure(1, weight=1)
        self.rowconfigure(2, weight=1)
        self.rowconfigure(3, weight=18)

        lbl_heading = ttk.Label(self, text="Hijri Setup", style="heading.TLabel")
        lbl_heading.grid(row=0, column=0)

        frame = ttk.Frame(self)

        cal = Calendar(frame, selectmode = 'day', year = 2020, month = 5, day = 22)
        cal.pack(pady = 20)

        frame.grid(row=1, column=0)
  
