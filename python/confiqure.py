"""confiqure — class decorator marking a configuration target.

Usage:

    from confiqure import Confiqure

    @Confiqure(
        end="/notifications",
        type="single",
        tools=["Amazon_Connect", "Ebay_Connect"],
        callback="https://myapp.com/webhooks/confiqure",
    )
    class Notifications:
        ...

The confiqure CLI scans source files for ``@Confiqure``. The backend AI
parses the decorated class and generates the chat playbook.
"""

from __future__ import annotations

from typing import Iterable, Optional


class Confiqure:
    """Parameterized class decorator. Attaches metadata to the decorated class.

    The metadata is exposed as ``cls.__confiqure__``; the confiqure CLI
    does not rely on this attribute at upload time (it scans source text),
    but host applications can inspect it at runtime if useful.
    """

    def __init__(
        self,
        end: str = "",
        type: str = "single",
        tools: Optional[Iterable[str]] = None,
        callback: str = "",
    ) -> None:
        self.end = end
        self.type = type
        self.tools = list(tools) if tools is not None else []
        self.callback = callback

    def __call__(self, cls):
        cls.__confiqure__ = {
            "end": self.end,
            "type": self.type,
            "tools": list(self.tools),
            "callback": self.callback,
        }
        return cls


__all__ = ["Confiqure"]
