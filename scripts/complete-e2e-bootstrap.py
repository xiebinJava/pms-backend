"""Complete the normal first-login flow for the isolated CI fixture."""
import json
import os
import sys
import time
import urllib.error
import urllib.request


def post(path, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base + path, json.dumps(payload).encode(), headers=headers)
    with urllib.request.urlopen(request, timeout=5) as response:
        body = json.load(response)
    if body["code"] != 200:
        raise RuntimeError("fixture setup failed at " + path)
    return body["data"]


base = sys.argv[1]
email = os.environ["PMS_E2E_ADMIN_EMAIL"]
password = os.environ["PMS_E2E_ADMIN_PASSWORD"]
initial = os.environ["PMS_BOOTSTRAP_ADMIN_PASSWORD"]
for attempt in range(90):
    try:
        with urllib.request.urlopen(base + "/health/ready", timeout=3) as response:
            if json.load(response)["status"] == "UP":
                break
    except (OSError, ValueError):
        pass
    time.sleep(1)
else:
    raise SystemExit("fixture backend did not become ready")

try:
    session = post("/auth/login", {"email": email, "password": initial})
except urllib.error.HTTPError as error:
    if error.code != 401:
        raise
    # A restarted fixture has already consumed the initial password.
    session = post("/auth/login", {"email": email, "password": password})
    if session["user"].get("mustChangePassword"):
        raise SystemExit("fixture still requires initial password change")
else:
    if session["user"].get("mustChangePassword"):
        post("/auth/password/change", {"currentPassword": initial, "newPassword": password}, session["accessToken"])
    elif initial != password:
        raise SystemExit("fixture has unexpected administrator credentials")
