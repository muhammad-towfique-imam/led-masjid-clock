from kivymd.app import MDApp
from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.floatlayout import MDFloatLayout
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.screen import MDScreen
from kivymd.uix.screenmanager import MDScreenManager
from kivymd.uix.textfield import (
    MDTextField,
    MDTextFieldHintText,
    MDTextFieldTrailingIcon,
)

from bangla_setup_screen import BanglaSetupScreen
from english_setup_screen import EnglishSetupScreen
from hijri_setup_screen import HijriSetupScreen
from start_screen import StartScreen
from prayer_timings_screen import PrayerTimingsScreen
from app_state import state


class MainScreen(MDScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

        self.dropdown_field = MDTextField(
            MDTextFieldHintText(text="Select Clock Model"),
            MDTextFieldTrailingIcon(icon="chevron-down"),
            mode="outlined",
            readonly=True,
        )
        self.dropdown_field.bind(focus=self.show_dropdown)

        menu_items = [
            {
                "text": f"m{i}",
                "on_release": lambda x=f"m{i}": self.set_dropdown_item(x),
            }
            for i in range(1, 6)
        ]
        self.menu = MDDropdownMenu(
            caller=self.dropdown_field,
            items=menu_items,
            width_mult=4,
        )

        layout = MDBoxLayout(orientation="vertical")
        layout.add_widget(
            MDTopAppBar(
                MDTopAppBarTitle(
                    text="Masjid Clock",
                    halign="center",
                ),
                type="small",
                size_hint_x=0.8,
                pos_hint={"center_x": 0.5, "center_y": 0.5},
            ),
        )

        content = MDFloatLayout()
        form_box = MDBoxLayout(
            self.dropdown_field,
            MDButton(
                MDButtonText(text="Next"),
                style="filled",
                pos_hint={"right": 1},
                on_release=self.on_next,
            ),
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

    def show_dropdown(self, instance, focus):
        if focus:
            self.menu.open()

    def set_dropdown_item(self, text_item):
        self.dropdown_field.text = text_item
        self.menu.dismiss()
        self.dropdown_field.focus = False

    def on_next(self, *args):
        state.model = self.dropdown_field.text
        if state.model:
            if self.manager.has_screen("start") is True:
                start_screen = self.manager.get_screen("start")
            else:
                start_screen = StartScreen(name="start")
                self.manager.add_widget(start_screen)
            start_screen.update()
            self.manager.current = "start"


class MainApp(MDApp):
    def build(self):
        self.theme_cls.theme_style = "Dark"
        self.theme_cls.primary_palette = "Green"
        self.title = "Masjid Clock"
        sm = MDScreenManager()
        sm.add_widget(MainScreen(name="main"))
        sm.add_widget(StartScreen(name="start"))
        sm.add_widget(BanglaSetupScreen(name="bangla_setup"))
        sm.add_widget(EnglishSetupScreen(name="english_setup"))
        sm.add_widget(HijriSetupScreen(name="hijri_setup"))
        sm.add_widget(PrayerTimingsScreen(name="prayer_timings"))
        return sm


if __name__ == "__main__":
    MainApp().run()
