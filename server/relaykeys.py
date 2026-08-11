"""Role tokens for the relay, shared by the server and the link encoder.

TRACKER_KEY grants the full-duplex role, BEACON_KEY the write-only role. They are
independent random strings so one can never be derived from the other. Order of
resolution: environment variables > persisted file (.relaykeys) > generate+persist.
"""
import json
import os
import secrets
from typing import Tuple

KEYS_FILE = os.path.join(os.path.dirname(__file__), ".relaykeys")


def load() -> Tuple[str, str]:
    tk = os.environ.get("TRACKER_KEY")
    bk = os.environ.get("BEACON_KEY")
    if tk and bk:
        return tk, bk
    try:
        with open(KEYS_FILE) as f:
            d = json.load(f)
            return d["tracker"], d["beacon"]
    except Exception:
        pass
    tk = tk or secrets.token_urlsafe(16)
    bk = bk or secrets.token_urlsafe(16)
    try:
        with open(KEYS_FILE, "w") as f:
            json.dump({"tracker": tk, "beacon": bk}, f)
    except Exception:
        pass
    return tk, bk
