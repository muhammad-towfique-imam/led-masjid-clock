from dataclasses import dataclass
from typing import Optional

@dataclass
class AppState:
    # Essential properties
    model: str = ""
    selected_screen: str = ""

    # You can add default values or optional types
    is_authenticated: bool = False
    user_role: Optional[str] = None


# Global instance
state = AppState()
