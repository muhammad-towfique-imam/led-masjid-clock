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
    """
    A single row containing a Date picker, a Time picker, and a delete button.
    Used within a PrayerCard to manage specific timing changes.
    """

    def __init__(self, initial_time=None, initial_date=None, is_first=False, **kwargs):
        super().__init__(**kwargs)
        self.adaptive_height = True
        self.spacing = dp(16)
        self.padding = [0, dp(8)]

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

        # Disable remove button for the primary (first) schedule row
        self.remove_btn = MDIconButton(
            icon="close", pos_hint={"center_y": 0.5}, disabled=is_first
        )
        if not is_first:
            self.remove_btn.bind(on_release=self.on_remove)

        self.add_widget(self.date_field)
        self.add_widget(self.time_field)
        self.add_widget(self.remove_btn)

    def _format_initial_date(self, date_val):
        """Standardizes input date string to 'MM / DD / YYYY' format."""
        if not date_val:
            return ""
        try:
            # Attempt to parse common format used in logic
            dt = datetime.datetime.strptime(date_val, "%d/%m/%y")
            return dt.strftime("%m / %d / %Y")
        except (ValueError, TypeError):
            return str(date_val)

    def _format_initial_time(self, time_val):
        """Converts (hour, min) tuple to AM/PM string."""
        if not time_val or not isinstance(time_val, (list, tuple)):
            return str(time_val) if time_val else ""

        hour, minute = time_val
        period = "AM" if hour < 12 else "PM"
        adj_hour = hour if 0 < hour <= 12 else abs(hour - 12)
        if adj_hour == 0:
            adj_hour = 12

        return f"{adj_hour}:{int(minute):02d} {period}"

    def on_remove(self, *args):
        """Removes this row from the parent layout."""
        if self.parent:
            self.parent.remove_widget(self)

    def get_values(self):
        """Returns the current text of the date and time fields."""
        return self.date_field.text, self.time_field.text

    # Picker Logic
    def open_date_picker(self, instance, focus):
        if focus:
            picker = MDModalDatePicker()
            picker.bind(
                on_ok=self.set_date,
                on_cancel=lambda x: self.clear_focus(self.date_field, x),
            )
            picker.open()

    def set_date(self, picker):
        # Result of get_date() is a list of date objects
        self.date_field.text = picker.get_date()[0].strftime("%m / %d / %Y")
        self.clear_focus(self.date_field, picker)

    def open_time_picker(self, instance, focus):
        if focus:
            picker = MDTimePickerDialVertical()
            picker.bind(
                on_ok=self.set_time,
                on_cancel=lambda x: self.clear_focus(self.time_field, x),
            )
            picker.open()

    def set_time(self, picker):
        self.time_field.text = f"{picker.hour}:{int(picker.minute):02d} {picker.am_pm}"
        self.clear_focus(self.time_field, picker)

    def clear_focus(self, field, picker):
        picker.dismiss()
        field.focus = False


class PrayerCard(MDCard):
    """
    A card representing a specific Prayer (e.g., FAZR).
    Contains a list of ScheduleRows and functionality to add more.
    """

    def __init__(
        self,
        title="PRAYER",
        initial_time=None,
        initial_date=None,
        scheduled_changes=None,
        **kwargs,
    ):
        super().__init__(**kwargs)
        self.orientation = "vertical"
        self.adaptive_height = True
        self.padding = dp(16)
        self.spacing = dp(10)
        self.style = "filled"
        self.radius = [dp(12)]

        # UI: Title
        self.add_widget(
            MDLabel(text=title, bold=True, font_style="Title", adaptive_height=True)
        )

        # UI: Row Container
        self.rows_box = MDBoxLayout(orientation="vertical", adaptive_height=True)

        # Add the 'Current' timing row
        base_date = initial_date or datetime.date.today().strftime("%m / %d / %Y")
        self.rows_box.add_widget(
            ScheduleRow(
                initial_time=initial_time, initial_date=base_date, is_first=True
            )
        )

        # Add any pre-existing future changes
        if scheduled_changes:
            for change in scheduled_changes:
                # change[0] = date string, change[2] = time tuple
                self.rows_box.add_widget(
                    ScheduleRow(
                        initial_time=change[2], initial_date=change[0], is_first=False
                    )
                )

        self.add_widget(self.rows_box)

        # UI: Add Button
        self.add_widget(
            MDButton(
                MDButtonText(text="+ Add Schedule"),
                style="text",
                on_release=self.add_new_schedule_row,
            )
        )

    def add_new_schedule_row(self, *args):
        """Calculates the next logical date and appends a new row."""
        # Kivy children are ordered from last added to first; index 0 is the newest
        last_row = self.rows_box.children[0]
        last_date_str, last_time_str = last_row.get_values()

        try:
            last_dt = datetime.datetime.strptime(last_date_str, "%m / %d / %Y")
            next_date_raw = (last_dt + datetime.timedelta(days=1)).strftime("%d/%m/%y")
        except (ValueError, TypeError):
            next_date_raw = datetime.date.today().strftime("%d/%m/%y")

        new_row = ScheduleRow(initial_date=next_date_raw, is_first=False)
        new_row.time_field.text = last_time_str  # Carry over the time from previous row
        self.rows_box.add_widget(new_row)


class PrayerTimingsScreen(MDScreen):
    """
    Main screen for viewing and editing all prayer timings.
    """

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

        # Dependencies: Import locally to avoid circular dependencies if any
        try:
            from hd2020_helper import load_waqt_data
            from waqt_utils import get_apply_date
        except ImportError:
            print("Warning: Helper modules not found.")
            return

        # Data initialization
        self.waqt_data = load_waqt_data()
        today = datetime.date.today()

        # Build Main Layout
        layout = MDBoxLayout(orientation="vertical")

        # 1. App Bar
        self.title_widget = MDTopAppBarTitle(halign="center")
        layout.add_widget(
            MDTopAppBar(
                self.title_widget,
                type="small",
                size_hint_x=0.9,
                pos_hint={"center_x": 0.5},
            )
        )

        # 2. Scrollable Content
        scroll = MDScrollView(bar_width=dp(8))
        self.main_list = MDBoxLayout(
            orientation="vertical", adaptive_height=True, padding=dp(20), spacing=dp(20)
        )

        # 3. Process and Add Cards
        prayer_names = ["FAZR", "ZUHR", "ASR", "MAGRIB", "ISHA"]
        for idx, name in enumerate(prayer_names):
            card_data = self._get_prayer_schedule_data(idx, today, get_apply_date)
            self.main_list.add_widget(
                PrayerCard(
                    title=name,
                    initial_time=card_data["current_time"],
                    initial_date=card_data["current_date"],
                    scheduled_changes=card_data["future_changes"],
                )
            )

        scroll.add_widget(self.main_list)

        # 4. Action Buttons
        btn_container = self._build_action_buttons()

        # Assemble Final Layout
        content_layout = MDBoxLayout(
            orientation="vertical", spacing=dp(10), padding=dp(20)
        )
        content_layout.add_widget(scroll)
        content_layout.add_widget(btn_container)

        layout.add_widget(content_layout)
        self.add_widget(layout)

        self.update()

    def update(self):
        self.title_widget.text = f"{state.model.upper()} - Prayer Timings"

    def _get_prayer_schedule_data(self, waqt_idx, today, date_helper):
        """Filters waqt_data to find current active time and future scheduled changes."""
        times = self.waqt_data.get("times", [])
        changes = self.waqt_data.get("changes", [])

        # Filter changes for this specific prayer
        relevant = sorted([c for c in changes if c[1] == waqt_idx], key=lambda x: x[0])

        current_time = times[waqt_idx] if waqt_idx < len(times) else None
        current_date = today.strftime("%d/%m/%y")
        future_changes = []

        for change in relevant:
            apply_dt = date_helper(change[0], change[1], change[2]).date()
            if apply_dt <= today:
                # This is the most recent past/present change
                current_date = apply_dt.strftime("%d/%m/%y")
                current_time = change[2]
            else:
                # This is a future change
                future_changes.append(change)

        return {
            "current_time": current_time,
            "current_date": current_date,
            "future_changes": future_changes,
        }

    def _build_action_buttons(self):
        """Creates the Back and Apply button row."""
        container = MDRelativeLayout(size_hint_y=None, height=dp(50))
        btn_row = MDBoxLayout(
            MDButton(
                MDButtonText(text="Back"), style="outlined", on_release=self.on_back
            ),
            MDButton(
                MDButtonText(text="Apply"), style="filled", on_release=self.on_apply
            ),
            orientation="horizontal",
            spacing=dp(16),
            adaptive_size=True,
            pos_hint={"right": 1, "y": 0},
        )
        container.add_widget(btn_row)
        return container

    def on_back(self, *args):
        if self.manager:
            self.manager.current = "start"

    def get_times_data(self):
        return self.waqt_data.get("times", [])

    def get_changes(self):
        changes = self.waqt_data.get("changes", [])
        return changes

    def on_apply(self, *args):
        from hd2020_helper import hd_register

        model = state.model
        if model in ("m2", "m3", "m4", "m5"):
            times = self.get_times_data()
            changes = self.get_changes()

            waqt_data = {"times": times, "changes": changes}

            xml = None

            if model == "m2":
                xml = get_m2_waqt_xml(waqt_data)
            elif model == "m3":
                xml = get_m3_waqt_xml(waqt_data)
            elif model == "m4":
                xml = get_m4_waqt_xml(waqt_data)
            elif model == "m5":
                xml = get_m5_waqt_xml(waqt_data)

            if xml:
                try:
                    hd_register(xml)
                    from hd2020_helper import save_waqt_data

                    save_waqt_data(waqt_data)
                    self.show_popup("Success", "Waqt program written successfully")
                except Exception as e:
                    self.show_popup("Error", str(e))

    def show_popup(self, title, text):
        dialog = MDDialog(
            MDDialogHeadlineText(
                text=title,
                halign="left",
            ),
            MDDialogSupportingText(
                text=text,
                halign="left",
            ),
            MDDialogButtonContainer(
                Widget(),
                MDButton(
                    MDButtonText(
                        text="OK",
                        theme_text_color="Custom",
                        text_color=self.theme_cls.primaryColor,
                    ),
                    style="text",
                    on_release=lambda x: dialog.dismiss(),
                ),
            ),
        )
        dialog.open()
