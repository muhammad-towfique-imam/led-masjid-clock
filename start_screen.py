from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.floatlayout import MDFloatLayout
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import MDTextFieldHintText, MDTextFieldTrailingIcon
from kivymd.uix.textfield.textfield import MDTextField

from app_state import state


class StartScreen(MDScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.md_bg_color = self.theme_cls.surfaceColor

        self.dropdown_field = MDTextField(
            MDTextFieldHintText(text="Select Setup"),
            MDTextFieldTrailingIcon(icon="chevron-down"),
            mode="outlined",
            readonly=True,
        )
        self.dropdown_field.bind(focus=self.show_dropdown)

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
                MDButtonText(text="Next"),
                style="filled",
                on_release=self.on_next,
            ),
            orientation="horizontal",
            spacing="8dp",
            adaptive_size=True,
            pos_hint={"right": 1},
        )
        form_box = MDBoxLayout(
            self.dropdown_field,
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

        self.selected_screen = None

        self.update()

    def _build_options(self):
        options = [
            ("English Setup", "english_setup"),
            ("Bangla Setup", "bangla_setup"),
            ("Hijri Setup", "hijri_setup"),
        ]
        if state.model and state.model != "m1":
            options.append(("Prayer Timings Setup", "prayer_timings"))
        return options

    def update(self):
        self.title_widget.text = f"{state.model.upper()} - Masjid Clock"

        options = self._build_options()

        self.menu = MDDropdownMenu(
            caller=self.dropdown_field,
            items=[
                {
                    "text": text,
                    "on_release": lambda x=screen, y=text: self.set_dropdown_item(x, y),
                }
                for text, screen in options
            ],
            width_mult=4,
        )

    def on_back(self, *args):
        self.manager.current = "main"

    def show_dropdown(self, instance, focus):
        if focus:
            self.menu.open()

    def set_dropdown_item(self, screen, text):
        self.dropdown_field.text = text
        self.selected_screen = screen
        self.menu.dismiss()
        self.dropdown_field.focus = False

    def on_next(self, *args):
        if self.selected_screen:
            state.selected_screen = self.selected_screen
            self.manager.current = self.selected_screen
            self.manager.get_screen(self.selected_screen).update()
