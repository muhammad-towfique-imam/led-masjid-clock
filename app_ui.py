import kivy

kivy.require("2.3.0")

from kivy.app import App
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.stacklayout import StackLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.popup import Popup

from datetime import datetime
import bangladatetime

from hijri_utils import (
    get_next_hijri_month,
    get_hijri_months,
    get_min_margib_time,
    str_to_date,
    get_salah_times,
)
from m1.m1_bangla import get_m1_bangla_xml
from m1.m1_hijri import get_m1_hijri_xml
from m1.m1_english import get_m1_english_xml
from m2.m2_bangla import get_m2_bangla_xml
from m2.m2_hijri import get_m2_hijri_xml
from m2.m2_english import get_m2_english_xml
from m2.m2_waqt import get_m2_waqt_xml
from hd2020_helper import hd_register, load_waqt_data, save_waqt_data
from m3.m3_bangla import get_m3_bangla_xml
from m3.m3_english import get_m3_english_xml
from m3.m3_hijri import get_m3_hijri_xml
from m3.m3_waqt import get_m3_waqt_xml
from m4.m4_bangla import get_m4_bangla_xml
from m5.m5_bangla import get_m5_bangla_xml
from m4.m4_english import get_m4_english_xml
from m5.m5_english import get_m5_english_xml
from m4.m4_hijri import get_m4_hijri_xml
from m5.m5_hijri import get_m5_hijri_xml
from m4.m4_waqt import get_m4_waqt_xml
from m5.m5_waqt import get_m5_waqt_xml
from waqt_utils import get_apply_date, join_time, split_time


class AppUI(App):
    def __init__(self, model=None, **kwargs):
        super().__init__(**kwargs)
        self.model = model

    def build(self):
        sm = ScreenManager()

        sm.add_widget(SelectModelScreen(name="select_model", app=self))
        sm.add_widget(StartScreen(name="start", app=self))
        sm.add_widget(EnglishSetupScreen(name="english_setup", app=self))
        sm.add_widget(BanglaSetupScreen(name="bangla_setup", app=self))
        sm.add_widget(HijriSetupScreen(name="hijri_setup", app=self))
        sm.add_widget(WaqtSetupScreen(name="waqt_setup", app=self))
        sm.add_widget(WaqtScheduleScreen(name="waqt_schedule", app=self))

        if self.model:
            sm.current = "start"
        else:
            sm.current = "select_model"

        return sm


class SelectModelScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=20)

        title = Label(text="Matrix Clock", font_size=32, halign="center")
        subtitle = Label(text="Select clock model", font_size=18, halign="center")

        self.model_input = TextInput(
            hint_text="Model (m1-m5)",
            text="m1",
            multiline=False,
            size_hint_y=None,
            height=40,
        )

        btn = Button(text="Select", size_hint_y=None, height=50)
        btn.bind(on_release=self.select)

        layout.add_widget(title)
        layout.add_widget(subtitle)
        layout.add_widget(self.model_input)
        layout.add_widget(btn)

        self.add_widget(layout)

    def select(self, *args):
        model = self.model_input.text.strip().lower()
        if model in ["m1", "m2", "m3", "m4", "m5"]:
            self.app.model = model
            self.manager.current = "start"
        else:
            self.show_popup("Error", "Please enter m1, m2, m3, m4, or m5")

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.open()


class StartScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=15)

        model_name = self.app.model.upper() if self.app.model else ""
        title = Label(
            text=f"Matrix Clock - {model_name}", font_size=24, halign="center"
        )
        layout.add_widget(title)

        btns = [
            ("English Setup", "english_setup"),
            ("Bangla Setup", "bangla_setup"),
            ("Hijri Setup", "hijri_setup"),
        ]

        if self.app.model and self.app.model not in ("m1",):
            btns.append(("Waqt Setup", "waqt_setup"))

        for text, screen in btns:
            btn = Button(text=text, size_hint_y=None, height=50)
            btn.bind(on_release=self.goto(screen))
            layout.add_widget(btn)

        self.add_widget(layout)

    def goto(self, screen_name):
        def callback(*args):
            self.manager.current = screen_name

        return callback


class EnglishSetupScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=20)
        title = Label(text="English Setup", font_size=24, halign="center")
        layout.add_widget(title)

        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = Button(text="Back")
        apply_btn = Button(text="Apply")
        btn_layout.add_widget(back_btn)
        btn_layout.add_widget(apply_btn)
        back_btn.bind(on_release=self.back)
        apply_btn.bind(on_release=self.apply)
        layout.add_widget(btn_layout)

        self.add_widget(layout)

    def back(self, *args):
        self.manager.current = "start"

    def apply(self, *args):
        model = self.app.model
        if model == "m1":
            xml = get_m1_english_xml()
        elif model == "m2":
            xml = get_m2_english_xml()
        elif model == "m3":
            xml = get_m3_english_xml()
        elif model == "m4":
            xml = get_m4_english_xml()
        elif model == "m5":
            xml = get_m5_english_xml()

        if xml:
            hd_register(xml)
            self.show_popup("Success", "English program written successfully")

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.bind(on_dismiss=lambda *a: setattr(self.manager, "current", "start"))
        popup.open()


class BanglaSetupScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=20)
        title = Label(text="Bangla Setup", font_size=24, halign="center")
        layout.add_widget(title)

        now = datetime.today()
        now_bn = bangladatetime.date.fromgregorian(now.year, now.month, now.day)

        self.year_input = TextInput(
            hint_text="Bangla Year",
            text=str(now_bn.year + 2),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.year_input)

        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = Button(text="Back")
        apply_btn = Button(text="Apply")
        btn_layout.add_widget(back_btn)
        btn_layout.add_widget(apply_btn)
        back_btn.bind(on_release=self.back)
        apply_btn.bind(on_release=self.apply)
        layout.add_widget(btn_layout)

        self.add_widget(layout)

    def back(self, *args):
        self.manager.current = "start"

    def apply(self, *args):
        bn_year = int(self.year_input.text)
        model = self.app.model

        if model == "m1":
            xml = get_m1_bangla_xml(bn_year)
        elif model == "m2":
            xml = get_m2_bangla_xml(bn_year)
        elif model == "m3":
            xml = get_m3_bangla_xml(bn_year)
        elif model == "m4":
            xml = get_m4_bangla_xml(bn_year)
        elif model == "m5":
            xml = get_m5_bangla_xml(bn_year)

        if xml:
            hd_register(xml)
            self.show_popup("Success", "Bangla program written successfully")

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.bind(on_dismiss=lambda *a: setattr(self.manager, "current", "start"))
        popup.open()


class HijriSetupScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=20)
        title = Label(text="Hijri Setup", font_size=24, halign="center")
        layout.add_widget(title)

        now = datetime.today()
        hz_year, hz_month = get_next_hijri_month()

        self.year_input = TextInput(
            hint_text="Hijri Year",
            text=str(hz_year),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.year_input)

        self.month_input = TextInput(
            hint_text="Hijri Month (1-12)",
            text=str(hz_month),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.month_input)

        self.date_input = TextInput(
            hint_text="Start Date (YYYY-MM-DD)",
            text=now.strftime("%Y-%m-%d"),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.date_input)

        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = Button(text="Back")
        apply_btn = Button(text="Apply")
        btn_layout.add_widget(back_btn)
        btn_layout.add_widget(apply_btn)
        back_btn.bind(on_release=self.back)
        apply_btn.bind(on_release=self.apply)
        layout.add_widget(btn_layout)

        self.add_widget(layout)

    def back(self, *args):
        self.manager.current = "start"

    def apply(self, *args):
        year = int(self.year_input.text)
        month = int(self.month_input.text)
        try:
            start_date = datetime.strptime(self.date_input.text, "%Y-%m-%d")
        except ValueError:
            self.show_popup("Error", "Invalid date format. Use YYYY-MM-DD")
            return
        h, m = get_min_margib_time(start_date)
        model = self.app.model

        if model == "m1":
            xml = get_m1_hijri_xml(year, month, h, m, start_date)
        elif model == "m2":
            xml = get_m2_hijri_xml(year, month, h, m, start_date)
        elif model == "m3":
            xml = get_m3_hijri_xml(year, month, h, m, start_date)
        elif model == "m4":
            xml = get_m4_hijri_xml(year, month, h, m, start_date)
        elif model == "m5":
            xml = get_m5_hijri_xml(year, month, h, m, start_date)

        if xml:
            hd_register(xml)
            self.show_popup("Success", "Hijri program written successfully")

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.bind(on_dismiss=lambda *a: setattr(self.manager, "current", "start"))
        popup.open()


class WaqtSetupScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = BoxLayout(orientation="vertical", padding=40, spacing=15)
        title = Label(text="Waqt Setup", font_size=24, halign="center")
        layout.add_widget(title)

        self.waqt_data = load_waqt_data()
        times = self.waqt_data["times"]

        waqt_names = ["Fazr", "Zuhr", "Asr", "Magrib", "Isha", "Jumu'ah"]
        self.time_fields = []

        for i, name in enumerate(waqt_names):
            row = BoxLayout(spacing=10, size_hint_y=None, height=40)
            row.add_widget(Label(text=f"{name}:", size_hint_x=0.3))

            hour_input = TextInput(
                text=str(times[i][0]),
                input_filter="int",
                size_hint_x=0.3,
                multiline=False,
            )
            min_input = TextInput(
                text=str(times[i][1]),
                input_filter="int",
                size_hint_x=0.3,
                multiline=False,
            )

            row.add_widget(hour_input)
            row.add_widget(min_input)
            self.time_fields.append((hour_input, min_input))
            layout.add_widget(row)

        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = Button(text="Back")
        sched_btn = Button(text="Schedule")
        apply_btn = Button(text="Apply")
        btn_layout.add_widget(back_btn)
        btn_layout.add_widget(sched_btn)
        btn_layout.add_widget(apply_btn)
        back_btn.bind(on_release=self.back)
        sched_btn.bind(on_release=self.goto_schedule)
        apply_btn.bind(on_release=self.apply)
        layout.add_widget(btn_layout)

        self.add_widget(layout)

    def back(self, *args):
        self.manager.current = "start"

    def get_times_data(self):
        times = []
        for hour_field, min_field in self.time_fields:
            h = int(hour_field.text) if hour_field.text else 0
            m = int(min_field.text) if min_field.text else 0
            times.append((h, m))
        times.extend([(0, 0), (0, 0)])
        return times

    def goto_schedule(self, *args):
        self.manager.current = "waqt_schedule"

    def apply(self, *args):
        model = self.app.model
        if model in ("m2", "m3", "m4", "m5"):
            times = self.get_times_data()
            schedule_screen = self.manager.get_screen("waqt_schedule")
            changes = schedule_screen.get_changes()

            waqt_data = {"times": times, "changes": changes}

            if model == "m2":
                xml = get_m2_waqt_xml(waqt_data)
            elif model == "m3":
                xml = get_m3_waqt_xml(waqt_data)
            elif model == "m4":
                xml = get_m4_waqt_xml(waqt_data)
            elif model == "m5":
                xml = get_m5_waqt_xml(waqt_data)

            if xml:
                hd_register(xml)
                save_waqt_data(waqt_data)
                self.show_popup("Success", "Waqt program written successfully")

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.bind(on_dismiss=lambda *a: setattr(self.manager, "current", "start"))
        popup.open()


class WaqtScheduleScreen(Screen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app
        self.changes = []
        self.selected_idx = None
        self.waqt_names = ["Fazr", "Zuhr", "Asr", "Magrib", "Isha", "Jumu'ah"]

        layout = BoxLayout(orientation="vertical", padding=40, spacing=20)
        title = Label(text="Schedule Waqt Change", font_size=24, halign="center")
        layout.add_widget(title)

        self.date_input = TextInput(
            hint_text="Date (YYYY-MM-DD)",
            text=datetime.today().strftime("%Y-%m-%d"),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.date_input)

        row = BoxLayout(spacing=10, size_hint_y=None, height=40)
        self.waqt_input = TextInput(
            hint_text="Waqt", text="Magrib", size_hint_x=0.3, multiline=False
        )
        self.hour_input = TextInput(
            hint_text="Hour",
            text="12",
            size_hint_x=0.3,
            input_filter="int",
            multiline=False,
        )
        self.min_input = TextInput(
            hint_text="Min",
            text="0",
            size_hint_x=0.3,
            input_filter="int",
            multiline=False,
        )
        row.add_widget(self.waqt_input)
        row.add_widget(self.hour_input)
        row.add_widget(self.min_input)
        layout.add_widget(row)

        btn_row = BoxLayout(spacing=10, size_hint_y=None, height=50)
        add_btn = Button(text="Add")
        self.save_btn = Button(text="Save")
        self.delete_btn = Button(text="Delete")
        self.save_btn.disabled = True
        self.delete_btn.disabled = True
        btn_row.add_widget(add_btn)
        btn_row.add_widget(self.save_btn)
        btn_row.add_widget(self.delete_btn)
        add_btn.bind(on_release=self.add_change)
        self.save_btn.bind(on_release=self.edit_change)
        self.delete_btn.bind(on_release=self.delete_change)
        layout.add_widget(btn_row)

        self.changes_layout = StackLayout(spacing=5, padding=10)
        layout.add_widget(self.changes_layout)

        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height=50)
        done_btn = Button(text="Done")
        done_btn.bind(on_release=self.done)
        btn_layout.add_widget(done_btn)
        layout.add_widget(btn_layout)

        self.add_widget(layout)
        self.enable_add_mode()

    def add_change(self, *args):
        try:
            selected_date = datetime.strptime(self.date_input.text, "%Y-%m-%d")
        except ValueError:
            self.show_popup("Error", "Invalid date format. Use YYYY-MM-DD")
            return

        time = join_time((int(self.hour_input.text), int(self.min_input.text)))
        waqt = self.waqt_input.text
        dt = selected_date.strftime("%d/%m/%y")

        for change in self.changes:
            if change["waqt"] == waqt and change["date"] == dt:
                self.show_popup("Error", "Duplicate entry")
                return

        self.changes.append({"waqt": waqt, "date": dt, "time": time})
        self.refresh_changes()

    def edit_change(self, *args):
        if self.selected_idx is not None:
            time = join_time((int(self.hour_input.text), int(self.min_input.text)))
            self.changes[self.selected_idx]["waqt"] = self.waqt_input.text
            self.changes[self.selected_idx]["date"] = datetime.strptime(
                self.date_input.text, "%Y-%m-%d"
            ).strftime("%d/%m/%y")
            self.changes[self.selected_idx]["time"] = time
            self.refresh_changes()
            self.enable_add_mode()

    def delete_change(self, *args):
        if self.selected_idx is not None:
            del self.changes[self.selected_idx]
            self.refresh_changes()
            self.enable_add_mode()

    def refresh_changes(self):
        self.changes_layout.clear_widgets()
        for idx, change in enumerate(self.changes):
            btn = Button(
                text=f"{change['waqt']} - {change['date']} - {change['time']}",
                size_hint_y=None,
                height=40,
            )
            btn.bind(on_release=lambda x, i=idx: self.load_change(i))
            self.changes_layout.add_widget(btn)

    def load_change(self, idx):
        self.selected_idx = idx
        change = self.changes[idx]
        self.waqt_input.text = change["waqt"]
        self.date_input.text = change["date"]
        time = split_time(change["time"])
        self.hour_input.text = str(time[0])
        self.min_input.text = str(time[1])
        self.enable_edit_mode()

    def enable_add_mode(self):
        self.selected_idx = None
        self.save_btn.disabled = True
        self.delete_btn.disabled = True

    def enable_edit_mode(self):
        self.save_btn.disabled = False
        self.delete_btn.disabled = False

    def get_changes(self):
        return [
            [
                c["date"],
                self.waqt_names.index(c["waqt"]) if c["waqt"] in self.waqt_names else 0,
                split_time(c["time"]),
            ]
            for c in self.changes
        ]

    def done(self, *args):
        self.manager.current = "waqt_setup"

    def show_popup(self, title, text):
        popup = Popup(
            title=title,
            content=Label(text=text),
            size_hint=(None, None),
            size=(300, 150),
        )
        popup.open()
