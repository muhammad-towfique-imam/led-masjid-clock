import datetime
from kivy.metrics import dp
from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.dialog import (
    MDDialog,
    MDDialogButtonContainer,
    MDDialogHeadlineText,
    MDDialogSupportingText,
)
from kivymd.uix.dialog.dialog import Widget
from kivymd.uix.screen import MDScreen
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.scrollview import MDScrollView
from kivymd.uix.card import MDCard
from kivymd.uix.label import MDLabel
from kivymd.uix.button import MDButton, MDButtonText, MDIconButton
from kivymd.uix.textfield import (
    MDTextField,
    MDTextFieldHintText,
    MDTextFieldTrailingIcon,
)
from kivymd.uix.pickers import MDModalDatePicker, MDTimePickerDialVertical
from kivymd.uix.relativelayout import MDRelativeLayout
from app_state import state
from m2.m2_waqt import get_m2_waqt_xml
from m3.m3_waqt import get_m3_waqt_xml
from m4.m4_waqt import get_m4_waqt_xml
from m5.m5_waqt import get_m5_waqt_xml

class ScheduleRow(MDBoxLayout):
    def __init__(self, initial_time=None, initial_date=None, is_first=False, is_am=False, **kwargs):
        super().__init__(**kwargs)
        self.adaptive_height = True
        self.spacing = dp(16)
        self.padding = [0, dp(8)]
        self.is_am = is_am  # Force am/pm based on prayer index logic

        # Initialize default values
        formatted_date = self._format_initial_date(initial_date)
        formatted_time = self._format_initial_time(initial_time)

        # UI Components
        self.date_field = MDTextField(
            MDTextFieldHintText(text="Date"),
            MDTextFieldTrailingIcon(icon="calendar"),
            mode="outlined",
            readonly=True,
            size_hint_x=0.5,
            text=formatted_date,
        )
        self.date_field.bind(focus=self.open_date_picker)

        self.time_field = MDTextField(
            MDTextFieldHintText(text="Time"),
            MDTextFieldTrailingIcon(icon="clock-outline"),
            mode="outlined",
            readonly=True,
            size_hint_x=0.4,
            text=formatted_time,
        )
        self.time_field.bind(focus=self.open_time_picker)

        self.remove_btn = MDIconButton(
            icon="close", pos_hint={"center_y": 0.5}, disabled=is_first
        )
        if not is_first:
            self.remove_btn.bind(on_release=self.on_remove)

        self.add_widget(self.date_field)
        self.add_widget(self.time_field)
        self.add_widget(self.remove_btn)

    def _format_initial_date(self, date_val):
        if not date_val:
            return ""
        try:
            # Handle DD/MM/YY input from XML/Storage
            dt = datetime.datetime.strptime(date_val, "%d/%m/%y")
            return dt.strftime("%m / %d / %Y")
        except (ValueError, TypeError):
            return str(date_val)

    def _format_initial_time(self, time_val):
        """Converts (hour, min) tuple where hour is < 12 into UI string."""
        if not time_val or not isinstance(time_val, (list, tuple)):
            return ""

        hour, minute = time_val
        period = "am" if self.is_am else "pm"

        # UI usually shows 12:00 instead of 0:00
        display_hour = 12 if hour == 0 else hour
        return f"{display_hour}:{int(minute):02d} {period}"

    def on_remove(self, *args):
        if self.parent:
            self.parent.remove_widget(self)

    def get_values(self):
        return self.date_field.text, self.time_field.text

    def get_parsed_values(self):
        """Returns (date_str_short, time_tuple) ensuring hour < 12."""
        date_str, time_str = self.get_values()

        try:
            dt_obj = datetime.datetime.strptime(date_str, "%m / %d / %Y")
            formatted_date = dt_obj.strftime("%d/%m/%y")
        except ValueError:
            formatted_date = date_str

        try:
            # Split "1:30 pm"
            time_part, _ = time_str.split(" ")
            h, m = map(int, time_part.split(":"))

            # Reverse logic: always store hour < 12
            # If UI says 12, store as 0.
            if h == 12:
                h = 0
            time_tuple = (h, m)
        except (ValueError, IndexError):
            time_tuple = (0, 0)

        return formatted_date, time_tuple

    def open_date_picker(self, instance, focus):
        if focus:
            picker = MDModalDatePicker()
            picker.bind(
                on_ok=self.set_date,
                on_cancel=lambda x: self.clear_focus(self.date_field, x),
            )
            picker.open()

    def set_date(self, picker):
        self.date_field.text = picker.get_date()[0].strftime("%m / %d / %Y")
        self.clear_focus(self.date_field, picker)

    def open_time_picker(self, instance, focus):
        if focus:
            picker = MDTimePickerDialVertical()
            current_time = self.time_field.text
            if current_time:
                try:
                    time_part, period = current_time.split(" ")
                    h, m = map(int, time_part.split(":"))
                    # Map 12 to 0 for the picker's internal 12-hour logic
                    picker_h = 0 if h == 12 else h
                    picker.set_time(datetime.time(picker_h, m))
                    picker.am_pm = period.lower()
                except (ValueError, IndexError):
                    pass

            picker.bind(
                on_ok=self.set_time,
                on_cancel=lambda x: self.clear_focus(self.time_field, x),
            )
            picker.open()

    def set_time(self, picker):
        # We force the period based on prayer type regardless of picker selection
        period = "am" if self.is_am else "pm"
        self.time_field.text = f"{picker.hour}:{int(picker.minute):02d} {period}"
        self.clear_focus(self.time_field, picker)

    def clear_focus(self, field, picker):
        picker.dismiss()
        field.focus = False

class PrayerCard(MDCard):
    def __init__(self, title="PRAYER", initial_time=None, initial_date=None,
                 scheduled_changes=None, is_am=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = "vertical"
        self.adaptive_height = True
        self.padding = dp(16)
        self.spacing = dp(10)
        self.style = "filled"
        self.radius = [dp(12)]
        self.is_am = is_am

        self.add_widget(MDLabel(text=title, bold=True, font_style="Title", adaptive_height=True))
        self.rows_box = MDBoxLayout(orientation="vertical", adaptive_height=True)

        base_date = initial_date or datetime.date.today().strftime("%d/%m/%y")
        self.rows_box.add_widget(
            ScheduleRow(initial_time=initial_time, initial_date=base_date, is_first=True, is_am=self.is_am)
        )

        if scheduled_changes:
            for change in scheduled_changes:
                self.rows_box.add_widget(
                    ScheduleRow(initial_time=change[2], initial_date=change[0], is_first=False, is_am=self.is_am)
                )

        self.add_widget(self.rows_box)
        self.add_widget(
            MDButton(
                MDButtonText(text="+ Add Schedule"),
                style="text",
                on_release=self.add_new_schedule_row,
            )
        )

    def add_new_schedule_row(self, *args):
        last_row = self.rows_box.children[0]
        last_date_str, last_time_str = last_row.get_values()

        try:
            last_dt = datetime.datetime.strptime(last_date_str, "%m / %d / %Y")
            next_date_raw = (last_dt + datetime.timedelta(days=1)).strftime("%d/%m/%y")
        except (ValueError, TypeError):
            next_date_raw = datetime.date.today().strftime("%d/%m/%y")

        new_row = ScheduleRow(initial_date=next_date_raw, is_first=False, is_am=self.is_am)
        new_row.time_field.text = last_time_str
        self.rows_box.add_widget(new_row)

class PrayerTimingsScreen(MDScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

        try:
            from hd2020_helper import load_waqt_data
            from waqt_utils import get_apply_date
        except ImportError:
            return

        self.waqt_data = load_waqt_data()
        today = datetime.date.today()

        layout = MDBoxLayout(orientation="vertical")
        self.title_widget = MDTopAppBarTitle(halign="center")
        layout.add_widget(MDTopAppBar(self.title_widget, type="small", size_hint_x=0.9, pos_hint={"center_x": 0.5}))

        scroll = MDScrollView(bar_width=dp(8))
        self.main_list = MDBoxLayout(orientation="vertical", adaptive_height=True, padding=dp(20), spacing=dp(20))

        prayer_names = ["FAZR", "ZUHR", "ASR", "MAGRIB", "ISHA", "JUMMA"]
        for idx, name in enumerate(prayer_names):
            card_data = self._get_prayer_schedule_data(idx, today, get_apply_date)
            self.main_list.add_widget(
                PrayerCard(
                    title=name,
                    initial_time=card_data["current_time"],
                    initial_date=card_data["current_date"],
                    scheduled_changes=card_data["future_changes"],
                    is_am=(idx == 0) # Only Fazr is AM
                )
            )

        scroll.add_widget(self.main_list)
        content_layout = MDBoxLayout(orientation="vertical", spacing=dp(10), padding=dp(20))
        content_layout.add_widget(scroll)
        content_layout.add_widget(self._build_action_buttons())

        layout.add_widget(content_layout)
        self.add_widget(layout)
        self.update()

    def update(self):
        self.title_widget.text = f"{state.model.upper()} - Prayer Timings"

    def _get_prayer_schedule_data(self, waqt_idx, today, date_helper):
        times = self.waqt_data.get("times", [])
        changes = self.waqt_data.get("changes", [])
        relevant = sorted([c for c in changes if c[1] == waqt_idx], key=lambda x: x[0])

        # Times are already stored as hour < 12 from _extract_ui_data
        current_time = times[waqt_idx] if waqt_idx < len(times) else (0, 0)
        current_date = today.strftime("%d/%m/%y")
        future_changes = []

        for change in relevant:
            apply_dt = date_helper(change[0], change[1], change[2]).date()
            if apply_dt <= today:
                current_date = apply_dt.strftime("%d/%m/%y")
                current_time = change[2]
            else:
                future_changes.append(change)

        return {"current_time": current_time, "current_date": current_date, "future_changes": future_changes}

    def _build_action_buttons(self):
        container = MDRelativeLayout(size_hint_y=None, height=dp(50))
        btn_row = MDBoxLayout(
            MDButton(MDButtonText(text="Back"), style="outlined", on_release=self.on_back),
            MDButton(MDButtonText(text="Apply"), style="filled", on_release=self.on_apply),
            orientation="horizontal", spacing=dp(16), adaptive_size=True, pos_hint={"right": 1, "y": 0},
        )
        container.add_widget(btn_row)
        return container

    def on_back(self, *args):
        if self.manager: self.manager.current = "start"

    def _extract_ui_data(self):
        new_times = []
        new_changes = []
        prayer_cards = list(reversed(self.main_list.children))

        for idx, card in enumerate(prayer_cards):
            if not isinstance(card, PrayerCard): continue
            rows = list(reversed(card.rows_box.children))
            for i, row in enumerate(rows):
                date_str, time_tuple = row.get_parsed_values()
                if i == 0:
                    new_times.append(time_tuple)
                else:
                    new_changes.append([date_str, idx, time_tuple])

        return new_times, new_changes

    def on_apply(self, *args):
        from hd2020_helper import hd_register, save_waqt_data
        model = state.model
        if model in ("m2", "m3", "m4", "m5"):
            times, changes = self._extract_ui_data()
            times.extend([(0, 0), (0, 0)]) # Dummy for sunrise/sunset
            waqt_data = {"times": times, "changes": changes}

            xml = None
            if model == "m2": xml = get_m2_waqt_xml(waqt_data)
            elif model == "m3": xml = get_m3_waqt_xml(waqt_data)
            elif model == "m4": xml = get_m4_waqt_xml(waqt_data)
            elif model == "m5": xml = get_m5_waqt_xml(waqt_data)

            if xml:
                try:
                    hd_register(xml)
                    save_waqt_data(waqt_data)
                    self.show_popup("Success", "Waqt program written successfully")
                except Exception as e:
                    self.show_popup("Error", str(e))

    def show_popup(self, title, text):
        dialog = MDDialog(
            MDDialogHeadlineText(text=title),
            MDDialogSupportingText(text=text),
            MDDialogButtonContainer(
                Widget(),
                MDButton(MDButtonText(text="OK"), style="text", on_release=lambda x: dialog.dismiss()),
            ),
        )
        dialog.open()
