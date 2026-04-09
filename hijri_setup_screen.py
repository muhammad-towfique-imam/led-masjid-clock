from datetime import datetime

from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.dialog import (
    MDDialog,
    MDDialogButtonContainer,
    MDDialogHeadlineText,
    MDDialogSupportingText,
)
from kivymd.uix.dialog.dialog import Widget
from kivymd.uix.floatlayout import MDFloatLayout
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.pickers import MDModalDatePicker
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import (
    MDTextField,
    MDTextFieldHintText,
    MDTextFieldTrailingIcon,
)

from hd2020_helper import hd_register
from hijri_utils import (
    get_hijri_months,
    get_min_margib_time,
    get_next_hijri_month,
)
from m1.m1_hijri import get_m1_hijri_xml
from m2.m2_hijri import get_m2_hijri_xml
from m3.m3_hijri import get_m3_hijri_xml
from m4.m4_hijri import get_m4_hijri_xml
from m5.m5_hijri import get_m5_hijri_xml

from app_state import state


class HijriSetupScreen(MDScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

        now = datetime.today()
        hz_year, hz_month = get_next_hijri_month()
        self.hijri_months = get_hijri_months()

        self.year_field = MDTextField(
            MDTextFieldHintText(text="Hijri Year"),
            MDTextFieldTrailingIcon(icon="chevron-down"),
            mode="outlined",
            readonly=True,
            text=str(hz_year),
        )
        self.year_field.bind(focus=self.open_year_menu)
        self.selected_year = str(hz_year)

        self.month_field = MDTextField(
            MDTextFieldHintText(text="Hijri Month"),
            MDTextFieldTrailingIcon(icon="chevron-down"),
            mode="outlined",
            readonly=True,
            text=self.hijri_months[hz_month - 1],
        )
        self.month_field.bind(focus=self.open_month_menu)
        self.selected_month = self.hijri_months[hz_month - 1]

        self.date_field = MDTextField(
            MDTextFieldHintText(text="Start Date"),
            MDTextFieldTrailingIcon(icon="calendar"),
            mode="outlined",
            readonly=True,
            text=now.strftime("%m / %d / %Y"),
        )
        self.date_field.bind(focus=self.open_date_picker)
        self.selected_date = now

        self.year_spinner = MDDropdownMenu(
            caller=self.year_field,
            items=[
                {
                    "text": str(y),
                    "on_release": lambda x=str(y): self.set_year(x),
                }
                for y in range(hz_year - 2, hz_year + 3)
            ],
            width_mult=4,
        )

        self.month_spinner = MDDropdownMenu(
            caller=self.month_field,
            items=[
                {
                    "text": month,
                    "on_release": lambda x=month: self.set_month(x),
                }
                for month in self.hijri_months
            ],
            width_mult=4,
        )

        layout = MDBoxLayout(orientation="vertical")
        self.title_widget = MDTopAppBarTitle(
            text="",
            halign="center",
        )
        layout.add_widget(
            MDTopAppBar(
                self.title_widget,
                type="small",
                size_hint_x=0.8,
                pos_hint={"center_x": 0.5, "center_y": 0.5},
            ),
        )

        content = MDFloatLayout()
        button_row = MDBoxLayout(
            MDButton(
                MDButtonText(text="Back"),
                style="outlined",
                on_release=self.on_back,
            ),
            MDButton(
                MDButtonText(text="Apply"),
                style="filled",
                on_release=self.on_apply,
            ),
            orientation="horizontal",
            spacing="8dp",
            adaptive_size=True,
            pos_hint={"right": 1},
        )
        form_box = MDBoxLayout(
            self.year_field,
            self.month_field,
            self.date_field,
            button_row,
            orientation="vertical",
            adaptive_height=True,
            spacing="16dp",
            padding="24dp",
            size_hint_x=0.85,
            pos_hint={"center_x": 0.5, "center_y": 0.5},
        )

        content.add_widget(form_box)
        layout.add_widget(content)
        self.add_widget(layout)

        self.update()

    def update(self):
        self.title_widget.text = f"{state.model.upper()} - Hijri Setup"

    def open_year_menu(self, instance, focus):
        if focus:
            self.year_spinner.open()

    def set_year(self, year):
        self.selected_year = year
        self.year_field.text = year
        self.year_spinner.dismiss()
        self.year_field.focus = False

    def open_month_menu(self, instance, focus):
        if focus:
            self.month_spinner.open()

    def set_month(self, month):
        self.selected_month = month
        self.month_field.text = month
        self.month_spinner.dismiss()
        self.month_field.focus = False

    def open_date_picker(self, instance, focus):
        if focus:
            picker = MDModalDatePicker()
            picker.bind(on_ok=self.set_date, on_cancel=self.cancel_date_picker)
            picker.open()

    def set_date(self, picker):
        self.selected_date = picker.get_date()[0]
        self.date_field.text = self.selected_date.strftime("%m / %d / %Y")
        self.date_field.focus = False
        picker.dismiss()

    def cancel_date_picker(self, picker):
        self.date_field.focus = False
        picker.dismiss()

    def on_back(self, *args):
        self.manager.current = "start"

    def on_apply(self, *args):
        year = int(self.selected_year)
        month = self.hijri_months.index(self.selected_month) + 1
        start_date = self.selected_date

        h, m = get_min_margib_time(start_date)
        model = state.model

        xml = None
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
            try:
                hd_register(xml)
                self.show_popup("Success", "Hijri program written successfully")
            except Exception as e:
                self.show_popup("Error", str(e))

    def show_popup(self, title, text):
        dialog = MDDialog(
            MDDialogHeadlineText(text=title, halign="left"),
            MDDialogSupportingText(text=text, halign="left"),
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
