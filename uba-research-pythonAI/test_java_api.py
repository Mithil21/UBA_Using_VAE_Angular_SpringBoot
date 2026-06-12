"""
VAE Endpoint Test Suite
========================
Tests the /api/debug/vae-test endpoint with 6 distinct attack profiles
and 2 normal profiles, then prints a summary table.

Run after adding TestVAEController.java to your Spring Boot project.

Usage:
    python test_vae_endpoint.py

Requirements:
    pip install requests
"""

import requests
import json
import time
from datetime import datetime

BASE_URL = "http://localhost:8080/api/debug/vae-test"
HEADERS  = {"Content-Type": "application/json"}


# ---------------------------------------------------------------------------
# Helper — builds a minimal valid wrapper around ubaTelemetry
# ---------------------------------------------------------------------------

# def make_request_body(telemetry: dict) -> dict:
#     return {
#         "securityContext": {
#             "timestamp": int(time.time() * 1000),
#             "nonce":     f"test-{datetime.now().isoformat()}"
#         },
#         "payload": {
#             "username": "",
#             "email":    telemetry.get("_email", "test@test.com"),
#             "password": "Test@1234"
#         },
#         "ubaTelemetry": {k: v for k, v in telemetry.items() if not k.startswith("_")}
#     }

def make_request_body(telemetry: dict) -> dict:
    # Send ubaTelemetry fields directly as the root object
    # so Java Metadata<?> deserialises them directly
    return {k: v for k, v in telemetry.items() if not k.startswith("_")}


# ---------------------------------------------------------------------------
# Test profiles
# ---------------------------------------------------------------------------

PROFILES = [

    # ── Normal profiles ──────────────────────────────────────────────────────

    {
        "_label":       "Normal — Average typist",
        "_expect":      "ACCEPT",
        "_description": "Typical human filling a registration form. "
                        "Natural flight times, some variance, one backspace.",
        "sessionId":        "normal-average-001",
        "ipAddress":        "82.45.123.67",
        "location":         "Manchester, United Kingdom",
        "pageDwellTime":    45000,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   42,
        "avgKeyHoldTime":   98.0,
        "avgFlightTime":    175.0,
        "stdFlightTime":    85.0,
        "typingSpeed":      1.8,
        "backspaceCount":   3,
        "specialKeyCount":  6,
        "mouseEventCount":  28,
        "mouseDistance":    620.0,
        "avgMouseSpeed":    380.0,
        "maxMouseSpeed":    1200.0,
        "clickCount":       3,
        "clickFrequency":   0.067,
        "navigationCount":  1,
        "timeBeforeFirstInput": 2800,
        "formCompletionTime":   23000,
        "fieldSwitchCount": 2,
        "idleTimeRatio":    0.05,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Normal — Slow/careful typist",
        "_expect":      "ACCEPT",
        "_description": "Elderly or hunt-and-peck user. Slow but human — "
                        "high flight times, more backspaces, longer dwell.",
        "sessionId":        "normal-slow-001",
        "ipAddress":        "94.12.88.201",
        "location":         "London, United Kingdom",
        "pageDwellTime":    110000,
        "tabSwitchCount":   1,
        "windowBlurCount":  1,
        "keystrokeCount":   55,
        "avgKeyHoldTime":   210.0,
        "avgFlightTime":    620.0,
        "stdFlightTime":    240.0,
        "typingSpeed":      0.6,
        "backspaceCount":   10,
        "specialKeyCount":  8,
        "mouseEventCount":  35,
        "mouseDistance":    820.0,
        "avgMouseSpeed":    220.0,
        "maxMouseSpeed":    680.0,
        "clickCount":       4,
        "clickFrequency":   0.036,
        "navigationCount":  2,
        "timeBeforeFirstInput": 7500,
        "formCompletionTime":   91000,
        "fieldSwitchCount": 3,
        "idleTimeRatio":    0.12,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    # ── Bot / attack profiles ─────────────────────────────────────────────────

    {
        "_label":       "Attack — Dumb bot",
        "_expect":      "REJECT",
        "_description": "Basic scripted bot. Constant 10ms flight time, "
                        "no variance, zero mouse movement, instant submission.",
        "sessionId":        "bot-dumb-001",
        "ipAddress":        "185.220.101.45",
        "location":         "Unknown Server",
        "pageDwellTime":    3200,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   30,
        "avgKeyHoldTime":   5.0,
        "avgFlightTime":    10.0,       # impossibly fast
        "stdFlightTime":    0.8,        # zero variance — robotic
        "typingSpeed":      18.0,       # inhuman
        "backspaceCount":   0,          # bots never mistype
        "specialKeyCount":  0,
        "mouseEventCount":  0,          # no mouse at all
        "mouseDistance":    0.0,
        "avgMouseSpeed":    0.0,
        "maxMouseSpeed":    0.0,
        "clickCount":       2,
        "clickFrequency":   0.625,
        "navigationCount":  0,
        "timeBeforeFirstInput": 12,     # near-instant
        "formCompletionTime":   310,    # 0.3 seconds total
        "fieldSwitchCount": 0,
        "idleTimeRatio":    0.0,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Attack — Smart bot (jitter added)",
        "_expect":      "REJECT",
        "_description": "Bot that adds Gaussian jitter to flight/hold times "
                        "to evade basic threshold detection. Still no mouse.",
        "sessionId":        "bot-smart-001",
        "ipAddress":        "104.244.72.115",
        "location":         "Tor Exit Node",
        "pageDwellTime":    8500,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   35,
        "avgKeyHoldTime":   52.0,
        "avgFlightTime":    98.0,       # slightly more human-looking
        "stdFlightTime":    14.0,       # jitter added but too low vs humans
        "typingSpeed":      5.8,
        "backspaceCount":   0,
        "specialKeyCount":  0,
        "mouseEventCount":  2,          # minimal fake mouse
        "mouseDistance":    8.0,
        "avgMouseSpeed":    400.0,
        "maxMouseSpeed":    400.0,      # constant speed — no acceleration
        "clickCount":       2,
        "clickFrequency":   0.235,
        "navigationCount":  0,
        "timeBeforeFirstInput": 85,
        "formCompletionTime":   5200,
        "fieldSwitchCount": 0,
        "idleTimeRatio":    0.0,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Attack — Headless browser (Selenium/Playwright)",
        "_expect":      "REJECT",
        "_description": "Headless browser automation. Very fast, "
                        "programmatic field switching, no natural mouse curves.",
        "sessionId":        "bot-headless-001",
        "ipAddress":        "34.89.142.200",
        "location":         "GCP Cloud Instance",
        "pageDwellTime":    1800,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   28,
        "avgKeyHoldTime":   2.0,        # near-zero — key events fired programmatically
        "avgFlightTime":    3.5,
        "stdFlightTime":    0.3,
        "typingSpeed":      22.0,
        "backspaceCount":   0,
        "specialKeyCount":  1,
        "mouseEventCount":  0,
        "mouseDistance":    0.0,
        "avgMouseSpeed":    0.0,
        "maxMouseSpeed":    0.0,
        "clickCount":       2,
        "clickFrequency":   1.11,
        "navigationCount":  0,
        "timeBeforeFirstInput": 5,
        "formCompletionTime":   1260,
        "fieldSwitchCount": 1,
        "idleTimeRatio":    0.0,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Attack — Human-mimicking bot",
        "_expect":      "REJECT",
        "_description": "Sophisticated bot trained to mimic human timing. "
                        "Flight times look plausible but mouse is still unnatural "
                        "— straight line, constant velocity, no micro-corrections.",
        "sessionId":        "bot-mimic-001",
        "ipAddress":        "51.77.204.88",
        "location":         "OVH Server, France",
        "pageDwellTime":    32000,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   40,
        "avgKeyHoldTime":   95.0,       # plausible hold times
        "avgFlightTime":    185.0,      # plausible flight times
        "stdFlightTime":    22.0,       # but std too low — humans are messier
        "typingSpeed":      1.9,
        "backspaceCount":   0,          # still never mistypes
        "specialKeyCount":  4,
        "mouseEventCount":  6,          # very few mouse events
        "mouseDistance":    95.0,       # straight line — no curves
        "avgMouseSpeed":    420.0,      # constant velocity — no bell curve
        "maxMouseSpeed":    425.0,      # max ≈ avg — dead giveaway
        "clickCount":       2,
        "clickFrequency":   0.063,
        "navigationCount":  0,
        "timeBeforeFirstInput": 320,
        "formCompletionTime":   21000,
        "fieldSwitchCount": 1,
        "idleTimeRatio":    0.0,        # perfect — no idle gaps ever
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Attack — Credential stuffing (fast bulk attempt)",
        "_expect":      "REJECT",
        "_description": "Automated credential stuffing tool. "
                        "Identical timing across all fields, "
                        "batch-style submission pattern.",
        "sessionId":        "bot-stuffing-001",
        "ipAddress":        "92.63.197.43",
        "location":         "Russia",
        "pageDwellTime":    2100,
        "tabSwitchCount":   0,
        "windowBlurCount":  0,
        "keystrokeCount":   32,
        "avgKeyHoldTime":   8.0,
        "avgFlightTime":    15.0,
        "stdFlightTime":    1.1,
        "typingSpeed":      15.0,
        "backspaceCount":   0,
        "specialKeyCount":  0,
        "mouseEventCount":  0,
        "mouseDistance":    0.0,
        "avgMouseSpeed":    0.0,
        "maxMouseSpeed":    0.0,
        "clickCount":       1,
        "clickFrequency":   0.476,
        "navigationCount":  0,
        "timeBeforeFirstInput": 8,
        "formCompletionTime":   490,
        "fieldSwitchCount": 0,
        "idleTimeRatio":    0.0,
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },

    {
        "_label":       "Edge case — Distracted human (left tab open)",
        "_expect":      "ACCEPT",
        "_description": "Real user who opened the form, got distracted, "
                        "came back 3 minutes later and filled it in. "
                        "High dwell time but normal typing when active.",
        "sessionId":        "normal-distracted-001",
        "ipAddress":        "86.22.145.78",
        "location":         "Birmingham, United Kingdom",
        "pageDwellTime":    198000,     # 3.3 minutes
        "tabSwitchCount":   2,
        "windowBlurCount":  1,
        "keystrokeCount":   38,
        "avgKeyHoldTime":   105.0,
        "avgFlightTime":    195.0,
        "stdFlightTime":    145.0,
        "typingSpeed":      0.8,        # low because dwell is huge
        "backspaceCount":   4,
        "specialKeyCount":  5,
        "mouseEventCount":  22,
        "mouseDistance":    540.0,
        "avgMouseSpeed":    310.0,
        "maxMouseSpeed":    980.0,
        "clickCount":       3,
        "clickFrequency":   0.015,
        "navigationCount":  3,
        "timeBeforeFirstInput": 4200,
        "formCompletionTime":   28000,
        "fieldSwitchCount": 2,
        "idleTimeRatio":    0.82,       # most time was idle — tab was left open
        "keystrokes":       [],
        "mouseEvents":      [],
        "clicks":           [],
        "clipboardAttempts":[],
        "pageNavigations":  [],
    },
]


# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

def run_tests():
    print("\n" + "=" * 72)
    print("  UBA VAE — Endpoint Test Suite")
    print(f"  Target: {BASE_URL}")
    print(f"  Time:   {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 72)

    results = []

    for profile in PROFILES:
        label       = profile["_label"]
        expect      = profile["_expect"]
        description = profile["_description"]

        body = make_request_body(profile)

        try:
            resp = requests.post(BASE_URL, json=body, headers=HEADERS, timeout=10)

            if resp.status_code == 200:
                data        = resp.json()
                mse         = data.get("reconstructionError", -1)
                probability = data.get("normalProbability",   -1)
                accepted    = data.get("accepted", None)

                actual  = "ACCEPT" if accepted else "REJECT"
                correct = actual == expect
                status  = "✅ PASS" if correct else "❌ FAIL"

                results.append({
                    "label":       label,
                    "expect":      expect,
                    "actual":      actual,
                    "mse":         mse,
                    "probability": probability,
                    "correct":     correct,
                })

                print(f"\n{status}  {label}")
                print(f"         {description}")
                print(f"         Expected={expect}  Got={actual}  "
                      f"MSE={mse:.4f}  Prob={probability:.4f}")

            else:
                print(f"\n⚠️  HTTP {resp.status_code}  {label}")
                print(f"   Response: {resp.text[:200]}")
                results.append({
                    "label": label, "expect": expect,
                    "actual": "ERROR", "mse": -1,
                    "probability": -1, "correct": False
                })

        except requests.exceptions.ConnectionError:
            print(f"\n🔴  CONNECTION REFUSED  {label}")
            print(f"    Is Spring Boot running on {BASE_URL}?")
            results.append({
                "label": label, "expect": expect,
                "actual": "CONN_ERR", "mse": -1,
                "probability": -1, "correct": False
            })
            break

        except Exception as e:
            print(f"\n⚠️  ERROR  {label}: {e}")
            results.append({
                "label": label, "expect": expect,
                "actual": "ERROR", "mse": -1,
                "probability": -1, "correct": False
            })

    # ── Summary table ────────────────────────────────────────────────────────
    print("\n\n" + "=" * 72)
    print("  SUMMARY")
    print("=" * 72)
    print(f"  {'Label':<42} {'Expect':<8} {'Got':<8} {'MSE':>10} {'Prob':>8}  {'Result'}")
    print("  " + "-" * 68)

    passed = 0
    failed = 0
    for r in results:
        status = "✅" if r["correct"] else "❌"
        mse_str  = f"{r['mse']:.4f}"  if r["mse"]  >= 0 else "N/A"
        prob_str = f"{r['probability']:.4f}" if r["probability"] >= 0 else "N/A"
        print(f"  {r['label']:<42} {r['expect']:<8} {r['actual']:<8} "
              f"{mse_str:>10} {prob_str:>8}  {status}")
        if r["correct"]:
            passed += 1
        else:
            failed += 1

    total = passed + failed
    print("  " + "-" * 68)
    print(f"  Total: {total}   Passed: {passed}   Failed: {failed}")

    if total > 0:
        normal_results    = [r for r in results if r["expect"] == "ACCEPT" and r["probability"] >= 0]
        malicious_results = [r for r in results if r["expect"] == "REJECT" and r["probability"] >= 0]

        if normal_results:
            avg_normal_prob = sum(r["probability"] for r in normal_results) / len(normal_results)
            print(f"\n  Avg probability — Normal profiles:  {avg_normal_prob:.4f}")
        if malicious_results:
            avg_bot_prob = sum(r["probability"] for r in malicious_results) / len(malicious_results)
            print(f"  Avg probability — Bot profiles:     {avg_bot_prob:.4f}")

        tpr = sum(1 for r in malicious_results if r["actual"] == "REJECT") / max(len(malicious_results), 1)
        tnr = sum(1 for r in normal_results    if r["actual"] == "ACCEPT") / max(len(normal_results),    1)
        print(f"\n  True Positive Rate (bot detection):    {tpr:.1%}")
        print(f"  True Negative Rate (human acceptance): {tnr:.1%}")

    print("=" * 72 + "\n")


if __name__ == "__main__":
    run_tests()