import tkinter as tk
from tkinter import ttk
from tkinter.font import BOLD
from datetime import date
from tkcalendar import Calendar
import babel.numbers    # Do not remove, required to generate .exe as Calendar dependency
from hijri_utils import get_next_hijri_month, get_hijri_months, get_min_margib_time, str_to_date, get_salah_times
import datetime
from m1.m1_bangla import get_m1_bangla_xml
from m1.m1_hijri import get_m1_hijri_xml
from m1.m1_english import get_m1_english_xml
from m2.m2_bangla import get_m2_bangla_xml
from m2.m2_hijri import get_m2_hijri_xml
from m2.m2_english import get_m2_english_xml
from m2.m2_waqt import get_m2_waqt_xml
from hd2020_helper import hd_register, load_waqt_times, save_waqt_times
import bangladatetime
import tkinter.messagebox as msg

from waqt_utils import join_time, split_time
  
class AppUI(tk.Tk):
     
    # __init__ function for class tkinterApp
    def __init__(self, model = None, *args, **kwargs):
        # __init__ function for class Tk
        tk.Tk.__init__(self, *args, **kwargs)

        style = ttk.Style(self)
        style.configure('heading.TLabel', font=(None, 16, BOLD))
        style.configure('subheading.TLabel', font=(None, 11))
        style.configure('form.TLabel', font=(None, 11))
        style.configure('form.TButton', font=(None, 11))

        self.title("Matrix Clock")
        self.geometry("350x650")
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

        self.heading = tk.StringVar()
        lbl_heading = ttk.Label(container, style="heading.TLabel", textvariable=self.heading)
        lbl_heading.grid(row=0, column=0, pady=5)

        self.sub_heading = tk.StringVar()
        lbl_sub_heading = ttk.Label(container, style="subheading.TLabel", textvariable=self.sub_heading)
        lbl_sub_heading.grid(row=1, column=0, pady=(0, 20))

        self.set_heading(model)

        for F in (SelectModelPage, StartPage, EnglishSetupPage, BanglaSetupPage, HijriSetupPage, WaqtSetupPage, WaqtSchedulePage):
  
            frame = F(container, self)
  
            # initializing frame of that object from
            # startpage, page1, page2 respectively with
            # for loop
            self.frames[F] = frame
  
            frame.grid(row = 2, column = 0, sticky ="nsew")
        if model:
            self.show_frame(StartPage)
        else:
            self.show_frame(SelectModelPage)
  
    # to display the current frame passed as
    # parameter
    def show_frame(self, cont):
        frame = self.frames[cont]
        self.sub_heading.set(frame.title)
        frame.tkraise()

    def set_heading(self, model):
        self.model = model
        if model:
            self.heading.set("Matrix Clock - " + model.upper())
        else:
            self.heading.set("Matrix Clock")

class SelectModelPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.title = "Select clock model"
        self.controller = controller

        frame = ttk.Frame(self)

        lbl_year = ttk.Label(frame, text="Clock model :", style="form.TLabel")
        lbl_year.grid(row=0, column=0, sticky = tk.W, pady=5)

        cmb_model = ttk.Combobox(frame, font=(None, 11), state = "readonly", values=list(["m1", "m2"]))
        cmb_model.current(0)
        cmb_model.grid(row=0, column=1, padx=10, sticky = tk.W, pady=5)
        
        btn_apply = ttk.Button(frame, text="Select", command=lambda: self.select(cmb_model.get()), style="form.TButton")
        btn_apply.grid(row=1, column=0, columnspan=2, padx=10, sticky = tk.E, pady=5)

        frame.pack()

    def select(self, model):
        self.controller.set_heading(model)
        if model == "m1":
            self.controller.frames[StartPage].wqt_btn.grid_forget()
        self.controller.show_frame(StartPage)

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

        self.wqt_btn = ttk.Button(frame, text="Waqt Setup", style="form.TButton", width=25, command=lambda: controller.show_frame(WaqtSetupPage))
        self.wqt_btn.grid(row=3, column=0, padx=20, pady=5)

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

        frame.pack(pady=(10, 0))

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

        frame.pack(pady=(10, 0))

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
        cmb_hz_month = ttk.Combobox(frame, font=(None, 11),  state = "readonly", values=get_hijri_months())
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

        frame.pack(pady=(10, 0))

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

class WaqtSetupPage(tk.Frame):
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller
        self.title = "Waqt Setup"

        times = load_waqt_times()

        frame = ttk.Frame(self)

        (cmb1_hour, cmb1_min) = self.build_waqt_row(frame, "Fazr", times[0], 0)
        (cmb2_hour, cmb2_min) = self.build_waqt_row(frame, "Duhr", times[1], 1)
        (cmb3_hour, cmb3_min) = self.build_waqt_row(frame, "Asr", times[2], 2)
        (cmb4_hour, cmb4_min) = self.build_waqt_row(frame, "Magrib", times[3], 3)
        (cmb5_hour, cmb5_min) = self.build_waqt_row(frame, "Isha", times[4], 4)

        btn_frame = ttk.Frame(frame)
        bn_back = ttk.Button(btn_frame, text="Back", command=lambda: self.controller.show_frame(StartPage), style="form.TButton")
        bn_back.pack(side = "left", padx=5)
        bn_apply = ttk.Button(btn_frame, text="Apply", style="form.TButton", command=lambda: self.apply([
            (int(cmb1_hour.get()), int(cmb1_min.get())),
            (int(cmb2_hour.get()), int(cmb2_min.get())),
            (int(cmb3_hour.get()), int(cmb3_min.get())),
            (int(cmb4_hour.get()), int(cmb4_min.get())),
            (int(cmb5_hour.get()), int(cmb5_min.get())),
        ]))
        bn_apply.pack(side = "left", padx=5)
        btn_frame.grid(row=5, column=0, sticky = tk.E, pady=5, columnspan=2)

        frame.pack(pady=(10, 0))

    def build_waqt_row(self, frame, name, time, row):
        waqt_frame = ttk.Frame(frame)
        lbl_waqt = ttk.Label(waqt_frame, text=name+":", style="form.TLabel")
        lbl_waqt.pack(pady=5, side=tk.LEFT)
        cmb_waqt_hour = ttk.Combobox(waqt_frame, width=2, font=(None, 11), values=list(range(1, 13)))
        cmb_waqt_hour.current(time[0] -1)
        cmb_waqt_hour.pack(padx=5, pady=5, side=tk.LEFT)
        cmb_waqt_min = ttk.Combobox(waqt_frame, width=2, font=(None, 11), values=list(range(60)))
        cmb_waqt_min.current(time[1])
        cmb_waqt_min.pack(padx=5, pady=5, side=tk.LEFT)

        schedule_icon = tk.PhotoImage(file="schedule.png")
        bn_schedule = ttk.Button(waqt_frame, image=schedule_icon, command=lambda: self.goto_schedule_page(name, time))
        bn_schedule.image = schedule_icon
        bn_schedule.pack(side = "left", padx=5)

        waqt_frame.grid(row=row, column=0, sticky=tk.E)
        return (cmb_waqt_hour, cmb_waqt_min)

    def goto_schedule_page(self, name, time):
        now = datetime.datetime.today()
        page = self.controller.frames[WaqtSchedulePage]
        page.title = "Schedule Waqt Change: " + name
        page.waqt = name
        page.load(now, time)
        self.controller.show_frame(WaqtSchedulePage)

    def apply(self, times):
        str_times = [
            f'{times[0][0]}:{times[0][1]:02}',
            f'{times[1][0]}:{times[1][1]:02}',
            f'{times[2][0]}:{times[2][1]:02}',
            f'{times[3][0]}:{times[3][1]:02}',
            f'{times[4][0]}:{times[4][1]:02}',
        ]
        if self.controller.model == 'm2':
            waqt = {
                "times": [
                    [5,20],
                    [1,15],
                    [5,0],
                    [6,46],
                    [8,15]
                ],
                "changes": [
                    ["28/09/23", 0, [5, 15]],
                    ["05/10/23", 0, [5, 10]],
                    ["10/10/23", 0, [5, 0]],
                    ["01/10/23", 2, [4, 30]],
                    ["01/10/23", 3, [6, 35]],
                    ["02/10/23", 4, [8, 0]],
                    ["04/10/23", 3, [6, 30]]        
                ]
            }            
            xml = get_m2_waqt_xml(waqt)
        if xml:
            hd_register(xml)
            save_waqt_times(times)
            msg.showinfo("Success", "Waqt program written successfully")
        self.controller.show_frame(StartPage)

  
class WaqtSchedulePage(tk.Frame):
    
    def __init__(self, parent, controller):
        tk.Frame.__init__(self, parent)
        self.controller = controller
        self.DT_FMT = '%d/%m/%y'
        self.selected_id = None

        frame = ttk.Frame(self)

        self.cal = Calendar(frame, selectmode = 'day')
        self.cal.grid(row=0, column=0, columnspan=2, sticky = tk.E, pady=5)

        waqt_frame = ttk.Frame(frame)
        lbl_waqt = ttk.Label(waqt_frame, text="Time :", style="form.TLabel")
        lbl_waqt.pack(pady=5, side=tk.LEFT)
        self.cmb_waqt_hour = ttk.Combobox(waqt_frame, width=2, font=(None, 11), values=list(range(1, 13)))
        self.cmb_waqt_hour.pack(padx=5, pady=5, side=tk.LEFT)
        self.cmb_waqt_min = ttk.Combobox(waqt_frame, width=2, font=(None, 11), values=list(range(60)))
        self.cmb_waqt_min.pack(padx=5, pady=5, side=tk.LEFT)
        save_icon = tk.PhotoImage(file="save.png")
        delete_icon = tk.PhotoImage(file="delete.png")


        self.bn_add = ttk.Button(waqt_frame, image=save_icon, command=lambda: self.add_row(), style="form.TButton")
        self.bn_add.image = save_icon
        self.bn_save = ttk.Button(waqt_frame, image=save_icon, command=lambda: self.edit_row(), style="form.TButton")
        self.bn_save.image = save_icon
        self.bn_delete = ttk.Button(waqt_frame, image=delete_icon, command=lambda: self.delete_row(), style="form.TButton")
        self.bn_delete.image = delete_icon
        self.enable_add_mode()

        waqt_frame.grid(row=1, column=0, sticky=tk.E)

        tree_view_frame = ttk.Frame(frame)

        self.tree_view = ttk.Treeview(tree_view_frame, height = 10, selectmode='browse')

        treeScroll = ttk.Scrollbar(tree_view_frame)
        treeScroll.configure(command=self.tree_view.yview)
        self.tree_view.configure(yscrollcommand=treeScroll.set)
        treeScroll.pack(side= tk.LEFT, fill = tk.BOTH)
        self.tree_view.pack()

        self.tree_view['columns'] = ('waqt', 'date', 'time')
        self.tree_view.heading('#0', text='')
        self.tree_view.column('#0', width=0)
        self.tree_view.heading('waqt', text='Waqt')
        self.tree_view.column('waqt', anchor='center', width=75)
        self.tree_view.heading('date', text='Date')
        self.tree_view.column('date', anchor='center', width=75)
        self.tree_view.heading('time', text='Time')
        self.tree_view.column('time', anchor='center', width=75)
        self.tree_view.bind("<Double-1>", self.load_edit_row)

        tree_view_frame.grid(sticky = tk.E)

        btn_frame = ttk.Frame(frame)
        bn_back = ttk.Button(btn_frame, text="Back", command=lambda: self.controller.show_frame(WaqtSetupPage), style="form.TButton")
        bn_back.pack(side = "left", padx=5)
        bn_apply = ttk.Button(btn_frame, text="Apply", command=lambda: self.apply(cal.get_date()), style="form.TButton")
        bn_apply.pack(side = "left", padx=5)
        btn_frame.grid(row=3, column=0, sticky = tk.E, pady=5, columnspan=2)

        frame.pack(pady=(10, 0))

    def delete_row(self):
        self.tree_view.delete(self.selected_id)
        self.enable_add_mode()

    def add_row(self):
        time = join_time((self.cmb_waqt_hour.current() + 1, self.cmb_waqt_min.current()))
        values = (self.waqt, self.cal.selection_get().strftime(self.DT_FMT), time)
        self.tree_view.insert("", tk.END, text="", values=values)

    def edit_row(self):
        time = join_time((self.cmb_waqt_hour.current() + 1, self.cmb_waqt_min.current()))
        values = (self.waqt, self.cal.selection_get().strftime(self.DT_FMT), time)
        self.tree_view.item(self.selected_id, text=self.selected_id, values=values)
        self.enable_add_mode()

    def enable_add_mode(self):
        self.selected_id = None
        self.bn_add.pack(side = "right", padx=5)
        self.bn_save.pack_forget()
        self.bn_delete.pack_forget()

    def enable_edit_mode(self, selected_id):
        self.selected_id = selected_id
        self.bn_add.pack_forget()
        self.bn_save.pack(side = "right", padx=5)
        self.bn_delete.pack(side = "right", padx=5)

    def load_edit_row(self, event):
        id = self.tree_view.identify('item', event.x, event.y)
        row = self.tree_view.item(id)
        values = row['values']
        dt = datetime.datetime.strptime(values[1], self.DT_FMT)
        time = split_time(values[2])
        self.load(dt, time)
        self.bn_add['text'] = "Save"
        self.enable_edit_mode(id)

    def load(self, dt, time):
        self.cal.selection_set(dt)
        self.cmb_waqt_hour.current(time[0] - 1)
        self.cmb_waqt_min.current(time[1])

    def apply(self, date_txt):
        self.controller.frames[WaqtSetupPage].data["date"] = date_txt
        self.controller.show_frame(WaqtSetupPage)


