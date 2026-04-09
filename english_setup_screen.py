from kivy.metrics import dp

from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.card import MDCard
from kivymd.uix.dialog import (
    MDDialog,
    MDDialogButtonContainer,
    MDDialogHeadlineText,
    MDDialogSupportingText,
)
from kivymd.uix.dialog.dialog import Widget
from kivymd.uix.floatlayout import MDFloatLayout
from kivymd.uix.label import MDLabel
from kivymd.uix.screen import MDScreen

from hd2020_helper import hd_register
from m1.m1_english import get_m1_english_xml
from m2.m2_english import get_m2_english_xml
from m3.m3_english import get_m3_english_xml
from m4.m4_english import get_m4_english_xml
from m5.m5_english import get_m5_english_xml

from app_state import state


class EnglishSetupScreen(MDScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

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
            MDCard(
                MDLabel(
                    text="Setup a large clock showing English date and time. Click Apply to proceed.",
                    halign="center",
                    valign="center",
                    adaptive_height=True,
                    theme_text_color="Primary",
                ),
                orientation="vertical",
                adaptive_height=True,
                padding=dp(24),
                spacing=dp(10),
                style="filled",
                radius=[dp(12)],
                size_hint_x=1,
            ),
            button_row,
            orientation="vertical",
            adaptive_height=True,
            spacing="24dp",
            padding="24dp",
            size_hint_x=0.85,
            pos_hint={"center_x": 0.5, "center_y": 0.5},
        )

        content.add_widget(form_box)
        layout.add_widget(content)
        self.add_widget(layout)

        self.update()

    def update(self):
        self.title_widget.text = f"{state.model.upper()} - English Setup"

    def on_back(self, *args):
        self.manager.current = "start"

    def on_apply(self, *args):
        model = state.model
        xml = None
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
            try:
                hd_register(xml)
                self.show_popup("Success", "English program written successfully")
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
