import kivy

kivy.require("2.3.0")

from datetime import datetime

from kivymd.app import MDApp
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText, MDIconButton
from kivymd.uix.dialog import MDDialog
from kivymd.uix.gridlayout import MDGridLayout
from kivymd.uix.label import MDLabel
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.pickers import MDDockedDatePicker
from kivymd.uix.screen import MDScreen
from kivymd.uix.screenmanager import MDScreenManager
from kivymd.uix.stacklayout import MDStackLayout
from kivymd.uix.textfield import (
    MDTextField,
    MDTextFieldHintText,
)

from bangla_setup_screen import BanglaSetupScreen

from hd2020_helper import hd_register, load_waqt_data, save_waqt_data
from hijri_utils import (
    get_hijri_months,
    get_min_margib_time,
    get_next_hijri_month,
    get_salah_times,
    str_to_date,
)
from m1.m1_bangla import get_m1_bangla_xml
from m1.m1_english import get_m1_english_xml
from m1.m1_hijri import get_m1_hijri_xml
from m2.m2_bangla import get_m2_bangla_xml
from m2.m2_english import get_m2_english_xml
from m2.m2_hijri import get_m2_hijri_xml
from m2.m2_waqt import get_m2_waqt_xml
from m3.m3_bangla import get_m3_bangla_xml
from m3.m3_english import get_m3_english_xml
from m3.m3_hijri import get_m3_hijri_xml
from m3.m3_waqt import get_m3_waqt_xml
from m4.m4_bangla import get_m4_bangla_xml
from m4.m4_english import get_m4_english_xml
from m4.m4_hijri import get_m4_hijri_xml
from m4.m4_waqt import get_m4_waqt_xml
from m5.m5_bangla import get_m5_bangla_xml
from m5.m5_english import get_m5_english_xml
from m5.m5_hijri import get_m5_hijri_xml
from m5.m5_waqt import get_m5_waqt_xml
from waqt_utils import get_apply_date, join_time, split_time


class AppUI(MDApp):
    def __init__(self, model=None, **kwargs):
        super().__init__(**kwargs)
        self.model = model

    def build(self):
        sm = MDScreenManager()

        sm.add_widget(SelectModelScreen(name="select_model", app=self))
        sm.add_widget(StartScreen(name="start", app=self))
        sm.add_widget(EnglishSetupScreen(name="english_setup", app=self))
        sm.add_widget(BanglaSetupScreen(name="bangla_setup"))
        sm.add_widget(HijriSetupScreen(name="hijri_setup", app=self))
        sm.add_widget(WaqtSetupScreen(name="waqt_setup", app=self))
        sm.add_widget(WaqtScheduleScreen(name="waqt_schedule", app=self))

        if self.model:
            sm.current = "start"
        else:
            sm.current = "select_model"

        return sm


class SelectModelScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=20)

        title = MDLabel(
            text="Matrix Clock",
            halign="center",
            theme_text_color="Primary",
        )
        subtitle = MDLabel(
            text="Select clock model",
            halign="center",
            theme_text_color="Secondary",
        )

        self.model_spinner = MDDropdownMenu(
            caller=self,
            items=[
                {
                    "text": "m1",
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x="m1": self.set_model(x),
                },
                {
                    "text": "m2",
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x="m2": self.set_model(x),
                },
                {
                    "text": "m3",
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x="m3": self.set_model(x),
                },
                {
                    "text": "m4",
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x="m4": self.set_model(x),
                },
                {
                    "text": "m5",
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x="m5": self.set_model(x),
                },
            ],
            width_mult=4,
        )
        self._model_btn_text = MDButtonText(text="m1")
        self.model_spinner_button = MDButton(
            self._model_btn_text,
            size_hint_y=None,
            height=50,
            pos_hint={"center_x": 0.5},
            style="filled",
        )
        self.model_spinner_button.bind(on_release=self.open_model_menu)
        self.selected_model = "m1"

        btn = MDButton(
            MDButtonText(text="Select"), size_hint_y=None, height=50, style="filled"
        )
        btn.bind(on_release=self.select)

        layout.add_widget(title)
        layout.add_widget(subtitle)
        layout.add_widget(self.model_spinner_button)
        layout.add_widget(btn)

        self.add_widget(layout)

    def open_model_menu(self, *args):
        self.model_spinner.open()

    def set_model(self, model):
        self.selected_model = model
        self._model_btn_text.text = model
        self.model_spinner.dismiss()

    def select(self, *args):
        model = self.selected_model.strip().lower()
        if model in ["m1", "m2", "m3", "m4", "m5"]:
            self.app.model = model
            # Rebuild start screen with new model
            self.manager.remove_widget(self.manager.get_screen("start"))
            self.manager.add_widget(StartScreen(name="start", app=self.app))
            self.manager.current = "start"
        else:
            self.show_popup("Error", "Please select m1, m2, m3, m4, or m5")

    def show_popup(self, title, text):
        ok_btn = MDButton(MDButtonText(text="OK"), style="filled")
        self.dialog = MDDialog(
            title=title,
            text=text,
            size_hint=(0.8, 0.3),
            buttons=[ok_btn],
        )
        ok_btn.bind(on_release=lambda x: self.dialog.dismiss())
        self.dialog.open()


class StartScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=15)

        model_name = self.app.model.upper() if self.app.model else ""
        title = MDLabel(
            text=f"Matrix Clock - {model_name}",
            halign="center",
            theme_text_color="Primary",
            font_style="Headline",
        )
        layout.add_widget(title)

        btns = [
            ("English Setup", "english_setup"),
            ("Bangla Setup", "bangla_setup"),
            ("Hijri Setup", "hijri_setup"),
        ]

        if self.app.model and self.app.model != "m1":
            btns.append(("Waqt Setup", "waqt_setup"))

        for text, screen in btns:
            btn = MDButton(
                MDButtonText(text=text), size_hint_y=None, height=50, style="filled"
            )
            btn.bind(on_release=self.goto(screen))
            layout.add_widget(btn)

        self.add_widget(layout)

    def goto(self, screen_name):
        def callback(*args):
            self.manager.current = screen_name

        return callback


class EnglishSetupScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=20)
        title = MDLabel(
            text="English Setup",
            halign="center",
            theme_text_color="Primary",
        )
        layout.add_widget(title)

        btn_layout = MDBoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = MDButton(MDButtonText(text="Back"), style="text")
        apply_btn = MDButton(MDButtonText(text="Apply"), style="filled")
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
        ok_btn = MDButton(MDButtonText(text="OK"), style="filled")
        self.dialog = MDDialog(
            title=title,
            text=text,
            size_hint=(0.8, 0.3),
            buttons=[ok_btn],
        )
        ok_btn.bind(on_release=lambda x: self.dialog.dismiss())
        self.dialog.open()


class HijriSetupScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=20)
        title = MDLabel(
            text="Hijri Setup",
            halign="center",
            theme_text_color="Primary",
        )
        layout.add_widget(title)

        now = datetime.today()
        hz_year, hz_month = get_next_hijri_month()
        self.hijri_months = get_hijri_months()

        self.year_spinner = MDDropdownMenu(
            caller=self,
            items=[
                {
                    "text": str(y),
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x=str(y): self.set_year(x),
                }
                for y in range(hz_year - 2, hz_year + 3)
            ],
            width_mult=4,
        )
        self._year_btn_text = MDButtonText(text=str(hz_year))
        self.year_spinner_button = MDButton(
            self._year_btn_text,
            style="filled",
            size_hint_y=None,
            height=40,
            pos_hint={"center_x": 0.5},
        )
        self.year_spinner_button.bind(on_release=self.open_year_menu)
        self.selected_year = str(hz_year)

        layout.add_widget(MDLabel(text="Hijri Year:", halign="left"))
        layout.add_widget(self.year_spinner_button)

        self.month_spinner = MDDropdownMenu(
            caller=self,
            items=[
                {
                    "text": month,
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x=month: self.set_month(x),
                }
                for month in self.hijri_months
            ],
            width_mult=4,
        )
        self._month_btn_text = MDButtonText(text=self.hijri_months[hz_month - 1])
        self.month_spinner_button = MDButton(
            self._month_btn_text,
            style="filled",
            size_hint_y=None,
            height=40,
            pos_hint={"center_x": 0.5},
        )
        self.month_spinner_button.bind(on_release=self.open_month_menu)
        self.selected_month = self.hijri_months[hz_month - 1]

        layout.add_widget(MDLabel(text="Hijri Month:", halign="left"))
        layout.add_widget(self.month_spinner_button)

        layout.add_widget(MDLabel(text="Start Date:", halign="left"))
        self._date_btn_text = MDButtonText(text=now.strftime("%Y-%m-%d"))
        self.date_btn = MDButton(
            self._date_btn_text,
            style="filled",
            size_hint_y=None,
            height=50,
            pos_hint={"center_x": 0.5},
        )
        self.date_btn.bind(on_release=self.show_date_picker)
        layout.add_widget(self.date_btn)
        self.selected_date = now

        btn_layout = MDBoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = MDButton(MDButtonText(text="Back"), style="text")
        apply_btn = MDButton(MDButtonText(text="Apply"), style="filled")
        btn_layout.add_widget(back_btn)
        btn_layout.add_widget(apply_btn)
        back_btn.bind(on_release=self.back)
        apply_btn.bind(on_release=self.apply)
        layout.add_widget(btn_layout)

        self.add_widget(layout)

    def back(self, *args):
        self.manager.current = "start"

    def open_year_menu(self, *args):
        self.year_spinner.open()

    def set_year(self, year):
        self.selected_year = year
        self._year_btn_text.text = year
        self.year_spinner.dismiss()

    def open_month_menu(self, *args):
        self.month_spinner.open()

    def set_month(self, month):
        self.selected_month = month
        self._month_btn_text.text = month
        self.month_spinner.dismiss()

    def show_date_picker(self, *args):
        date_picker = MDDockedDatePicker()
        date_picker.bind(on_save=self.on_date_save, on_cancel=self.on_date_cancel)
        date_picker.open()

    def on_date_save(self, instance, value, *args):
        self.selected_date = value
        self._date_btn_text.text = value.strftime("%Y-%m-%d")

    def on_date_cancel(self, instance, *args):
        # Handle cancel event - just close the dialog
        pass

    def apply(self, *args):
        year = int(self.selected_year)
        month = self.hijri_months.index(self.selected_month) + 1
        start_date = self.selected_date

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
        ok_btn = MDButton(MDButtonText(text="OK"), style="filled")
        self.dialog = MDDialog(
            title=title,
            text=text,
            size_hint=(0.8, 0.3),
            buttons=[ok_btn],
        )
        ok_btn.bind(on_release=lambda x: self.dialog.dismiss())
        self.dialog.open()


class WaqtSetupScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=15)
        title = MDLabel(
            text="Waqt Setup",
            halign="center",
            theme_text_color="Primary",
        )
        layout.add_widget(title)

        self.waqt_data = load_waqt_data()
        times = self.waqt_data["times"]

        waqt_names = ["Fazr", "Zuhr", "Asr", "Magrib", "Isha", "Jumu'ah"]
        self.time_fields = []

        for i, name in enumerate(waqt_names):
            row = MDBoxLayout(spacing=10, size_hint_y=None, height=40)
            row.add_widget(MDLabel(text=f"{name}:", size_hint_x=0.3))

            hour_input = MDTextField(
                text=str(times[i][0]),
                input_filter="int",
                size_hint_x=0.3,
                multiline=False,
            )
            min_input = MDTextField(
                text=str(times[i][1]),
                input_filter="int",
                size_hint_x=0.3,
                multiline=False,
            )

            row.add_widget(hour_input)
            row.add_widget(min_input)
            self.time_fields.append((hour_input, min_input))
            layout.add_widget(row)

        btn_layout = MDBoxLayout(spacing=10, size_hint_y=None, height=50)
        back_btn = MDButton(MDButtonText(text="Back"), style="text")
        sched_btn = MDButton(MDButtonText(text="Schedule"), style="filled")
        apply_btn = MDButton(MDButtonText(text="Apply"), style="filled")
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
        ok_btn = MDButton(MDButtonText(text="OK"), style="filled")
        self.dialog = MDDialog(
            title=title,
            text=text,
            size_hint=(0.8, 0.3),
            buttons=[ok_btn],
        )
        ok_btn.bind(on_release=lambda x: self.dialog.dismiss())
        self.dialog.open()


class WaqtScheduleScreen(MDScreen):
    def __init__(self, name, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app
        self.changes = []
        self.selected_idx = None
        self.waqt_names = ["Fazr", "Duhr", "Asr", "Magrib", "Isha", "Jumu'ah"]

        layout = MDBoxLayout(orientation="vertical", padding=40, spacing=20)
        title = MDLabel(
            text="Schedule Waqt Change",
            halign="center",
            theme_text_color="Primary",
        )
        layout.add_widget(title)

        self.date_input = MDTextField(
            hint_text="Date (YYYY-MM-DD)",
            text=datetime.today().strftime("%Y-%m-%d"),
            multiline=False,
            size_hint_y=None,
            height=40,
        )
        layout.add_widget(self.date_input)

        row = MDBoxLayout(spacing=10, size_hint_y=None, height=40)
        self.waqt_spinner = MDDropdownMenu(
            caller=self,
            items=[
                {
                    "text": name,
                    "viewclass": "MDDropdownTextItem",
                    "on_release": lambda x=name: self.set_waqt(x),
                }
                for name in self.waqt_names
            ],
            width_mult=4,
        )
        self._waqt_btn_text = MDButtonText(text="Magrib")
        self.waqt_spinner_button = MDButton(
            self._waqt_btn_text,
            style="filled",
            size_hint_x=0.4,
            size_hint_y=None,
            height=40,
        )
        self.waqt_spinner_button.bind(on_release=self.open_waqt_menu)
        self.selected_waqt = "Magrib"
        self.hour_input = MDTextField(
            hint_text="Hour",
            text="12",
            size_hint_x=0.3,
            input_filter="int",
            multiline=False,
        )
        self.min_input = MDTextField(
            hint_text="Min",
            text="0",
            size_hint_x=0.3,
            input_filter="int",
            multiline=False,
        )
        row.add_widget(self.waqt_spinner_button)
        row.add_widget(self.hour_input)
        row.add_widget(self.min_input)
        layout.add_widget(row)

        btn_row = MDBoxLayout(spacing=10, size_hint_y=None, height=50)
        add_btn = MDButton(MDButtonText(text="Add"), style="filled")
        self.save_btn = MDButton(MDButtonText(text="Save"), style="filled")
        self.delete_btn = MDButton(MDButtonText(text="Delete"), style="filled")
        self.save_btn.disabled = True
        self.delete_btn.disabled = True
        btn_row.add_widget(add_btn)
        btn_row.add_widget(self.save_btn)
        btn_row.add_widget(self.delete_btn)
        add_btn.bind(on_release=self.add_change)
        self.save_btn.bind(on_release=self.edit_change)
        self.delete_btn.bind(on_release=self.delete_change)
        layout.add_widget(btn_row)

        self.changes_layout = MDStackLayout(spacing=5, padding=10)
        layout.add_widget(self.changes_layout)

        btn_layout = MDBoxLayout(spacing=10, size_hint_y=None, height=50)
        done_btn = MDButton(MDButtonText(text="Done"), style="filled")
        done_btn.bind(on_release=self.done)
        btn_layout.add_widget(done_btn)
        layout.add_widget(btn_layout)

        self.add_widget(layout)
        self.enable_add_mode()

    def open_waqt_menu(self, *args):
        self.waqt_spinner.open()

    def set_waqt(self, waqt):
        self.selected_waqt = waqt
        self._waqt_btn_text.text = waqt
        self.waqt_spinner.dismiss()

    def add_change(self, *args):
        try:
            selected_date = datetime.strptime(self.date_input.text, "%Y-%m-%d")
        except ValueError:
            self.show_popup("Error", "Invalid date format. Use YYYY-MM-DD")
            return

        time = join_time((int(self.hour_input.text), int(self.min_input.text)))
        waqt = self.selected_waqt
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
            self.changes[self.selected_idx]["waqt"] = self.selected_waqt
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
            btn = MDButton(
                MDButtonText(
                    text=f"{change['waqt']} - {change['date']} - {change['time']}"
                ),
                style="filled",
                size_hint_y=None,
                height=40,
            )
            btn.bind(on_release=lambda x, i=idx: self.load_change(i))
            self.changes_layout.add_widget(btn)

    def load_change(self, idx):
        self.selected_idx = idx
        change = self.changes[idx]
        self.selected_waqt = change["waqt"]
        self._waqt_btn_text.text = change["waqt"]
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
        ok_btn = MDButton(MDButtonText(text="OK"), style="filled")
        self.dialog = MDDialog(
            title=title,
            text=text,
            size_hint=(0.8, 0.3),
            buttons=[ok_btn],
        )
        ok_btn.bind(on_release=lambda x: self.dialog.dismiss())
        self.dialog.open()
