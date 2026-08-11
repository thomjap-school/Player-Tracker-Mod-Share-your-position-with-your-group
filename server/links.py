"""Print the three links for a given tunnel host. Used by start.sh / start-ngrok.sh.

Usage:  python3 links.py <host> [wss|ws]
Outputs three lines (TRACKER, BEACON, MAP) that the shell script formats.
"""
import sys

import linkcrypto
from relaykeys import load


def main() -> None:
    host = sys.argv[1]
    scheme = sys.argv[2] if len(sys.argv) > 2 else "wss"
    http = "https" if scheme == "wss" else "http"
    tracker_key, beacon_key = load()

    tracker = f"{scheme}://{host}/r/{tracker_key}"
    beacon = linkcrypto.encrypt(f"{scheme}://{host}/r/{beacon_key}")
    web_map = f"{http}://{host}/map?room=mon-salon&k={tracker_key}"

    print("TRACKER " + tracker)
    print("BEACON " + beacon)
    print("MAP " + web_map)


if __name__ == "__main__":
    main()
