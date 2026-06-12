"""
UBA VAE Data Generator — Full Version (CMU + BALABIT + Fitts' Law + Curvature Index)
======================================================================================
Feature vector: 28 features (matches Java VAEAnalysis exactly)

Datasets used:
  - CMU Keystroke Dynamics (Killourhy & Maxion, 2009)
    Anchors hold/flight time distributions for keystrokes
    Download: https://www.cs.cmu.edu/~keystroke/

  - BALABIT Mouse Dynamics Challenge Dataset
    Anchors mouse speed, distance, interval distributions
    Download: https://github.com/balabit/Mouse-Dynamics-Challenge

Physics / biomechanics models:
  - Fitts Law (Fitts, 1954): MT = a + b * log2(2D/W)
    Ensures movement time is physiologically plausible for given distance
  - Curvature Index: actual_path_length / straight_line_distance
    Validates path shape is human-like (target: 1.05 - 3.0)
  - Velocity bell curve (ease-in/out via sin modulation)
    Humans accelerate then decelerate; bots are flat/constant

Human persona profiles (6):
  fast_typist, average_typist, slow_typist, hunt_and_peck, elderly, power_user
  Weighted to approximate real-world population distribution.

Bot profiles (4):
  dumb_bot, smart_bot, headless, human_mimicking

Sample counts (default):
  Normal:    15,000 (VAE unsupervised training)
  Malicious:  3,000 (ROC / threshold evaluation only)

Fixes from previous version:
  - len(keys) bug fixed: keystroke_count now uses keydowns only (index 3)
  - timeBeforeFirstInput properly generated per sample (was always 0)
  - idleTimeRatio added at index 27 (also add to Java VAEAnalysis + scaler)
  - BALABIT anchors mouse speed/interval (replaces hardcoded constants)
  - Fitts Law drives total movement duration per path segment
  - Curvature index retry loop rejects straight bot-like paths
  - Velocity profile rescaled to match Fitts Law predicted duration
"""

import random
import numpy as np
import pandas as pd
from datetime import datetime


SPECIAL_KEYS = {
    'Tab', 'Enter', 'Shift', 'Control', 'Alt',
    'Meta', 'Escape', 'ArrowUp', 'ArrowDown',
    'ArrowLeft', 'ArrowRight', 'Backspace', 'Delete'
}

HUMAN_PERSONAS = {
    'fast_typist':    {'flight_mean': 80,  'flight_std': 20,  'hold_mean': 70,  'hold_std': 12, 'backspace_prob': 0.06, 'distraction_prob': 0.02},
    'average_typist': {'flight_mean': 160, 'flight_std': 45,  'hold_mean': 100, 'hold_std': 22, 'backspace_prob': 0.12, 'distraction_prob': 0.05},
    'slow_typist':    {'flight_mean': 320, 'flight_std': 90,  'hold_mean': 140, 'hold_std': 35, 'backspace_prob': 0.20, 'distraction_prob': 0.08},
    'hunt_and_peck':  {'flight_mean': 550, 'flight_std': 160, 'hold_mean': 180, 'hold_std': 55, 'backspace_prob': 0.25, 'distraction_prob': 0.10},
    'elderly':        {'flight_mean': 700, 'flight_std': 200, 'hold_mean': 220, 'hold_std': 70, 'backspace_prob': 0.30, 'distraction_prob': 0.12},
    'power_user':     {'flight_mean': 60,  'flight_std': 15,  'hold_mean': 55,  'hold_std': 10, 'backspace_prob': 0.04, 'distraction_prob': 0.01},
}
PERSONA_NAMES   = list(HUMAN_PERSONAS.keys())
PERSONA_WEIGHTS = [0.15, 0.40, 0.20, 0.10, 0.08, 0.07]

BOT_PROFILES = {
    'dumb_bot':        {'pause': (8,   14),  'hold': (4,  7),   'mouse_moves': False, 'jitter': False},
    'smart_bot':       {'pause': (85,  130), 'hold': (38, 65),  'mouse_moves': True,  'jitter': True},
    'headless':        {'pause': (2,   6),   'hold': (1,  3),   'mouse_moves': False, 'jitter': False},
    'human_mimicking': {'pause': (150, 250), 'hold': (80, 120), 'mouse_moves': True,  'jitter': True},
}
BOT_NAMES   = list(BOT_PROFILES.keys())
BOT_WEIGHTS = [0.45, 0.30, 0.15, 0.10]

FITTS_A = 50
FITTS_B = 150


class UserBehaviorDataGenerator:

    def __init__(self, cmu_csv_path='DSL-StrongPasswordData.csv', balabit_csv_path='balabit_mouse.csv'):
        self.cmu_stats     = self._load_cmu_stats(cmu_csv_path)
        self.balabit_stats = self._load_balabit_stats(balabit_csv_path)

    def _load_cmu_stats(self, csv_path):
        try:
            df      = pd.read_csv(csv_path)
            h_cols  = [c for c in df.columns if c.startswith('H.')]
            ud_cols = [c for c in df.columns if c.startswith('UD.')]
            stats   = {
                'hold_mean':   df[h_cols].values.mean()  * 1000,
                'hold_std':    df[h_cols].values.std()   * 1000,
                'flight_mean': df[ud_cols].values.mean() * 1000,
                'flight_std':  df[ud_cols].values.std()  * 1000,
            }
            print(f"[CMU]     Loaded  hold_mean={stats['hold_mean']:.1f}ms  flight_mean={stats['flight_mean']:.1f}ms")
            return stats
        except Exception as e:
            print(f"[CMU]     Warning: {e}. Using fallback.")
            return {'hold_mean': 100, 'hold_std': 25, 'flight_mean': 200, 'flight_std': 50}

    def _load_balabit_stats(self, csv_path):
        """
        BALABIT columns: record_timestamp, client_timestamp, button, state, x, y
        Timestamps are in seconds (float).
        """
        try:
            df             = pd.read_csv(csv_path)
            df             = df.sort_values('record_timestamp').reset_index(drop=True)
            df['dx']       = df['x'].diff()
            df['dy']       = df['y'].diff()
            df['dt_sec']   = df['record_timestamp'].diff()
            df['distance'] = np.sqrt(df['dx']**2 + df['dy']**2)
            df             = df.dropna()
            df             = df[df['dt_sec'] > 0]
            df['speed']    = df['distance'] / df['dt_sec']
            df['dt_ms']    = df['dt_sec'] * 1000
            df             = df[df['dt_ms'] < 2000]   # remove pause outliers
            stats = {
                'speed_mean':    float(df['speed'].mean()),
                'speed_std':     float(df['speed'].std()),
                'distance_mean': float(df['distance'].mean()),
                'distance_std':  float(df['distance'].std()),
                'interval_mean': float(df['dt_ms'].mean()),
                'interval_std':  float(df['dt_ms'].std()),
            }
            print(f"[BALABIT] Loaded  speed_mean={stats['speed_mean']:.1f}px/s  interval_mean={stats['interval_mean']:.1f}ms")
            return stats
        except Exception as e:
            print(f"[BALABIT] Warning: {e}. Using fallback.")
            return {
                'speed_mean': 350,   'speed_std': 180,
                'distance_mean': 40, 'distance_std': 25,
                'interval_mean': 90, 'interval_std': 45,
            }

    def _fitts_movement_time(self, distance, target_size=20):
        """Fitts Law: MT = a + b * log2(2D/W). Returns ms."""
        if distance < 1:
            return float(FITTS_A)
        return float(FITTS_A + FITTS_B * np.log2((2.0 * distance) / target_size))

    def _curvature_index(self, points):
        """Actual path length / straight-line distance. Human range: 1.05 - 3.0"""
        if len(points) < 2:
            return 1.0
        actual   = sum(np.sqrt((points[i][0]-points[i-1][0])**2 + (points[i][1]-points[i-1][1])**2) for i in range(1, len(points)))
        sx, sy   = points[0]
        ex, ey   = points[-1]
        straight = max(np.sqrt((ex-sx)**2 + (ey-sy)**2), 1e-6)
        return actual / straight

    def _bezier_mouse_path(self, start, end):
        """
        Quadratic Bezier path anchored to BALABIT stats, duration from Fitts Law,
        shape validated by curvature index.
        Returns list of {x, y, time_offset} dicts (time_offset in ms from segment start).
        """
        sx, sy   = start
        ex, ey   = end
        distance = np.sqrt((ex-sx)**2 + (ey-sy)**2)

        mean_step  = max(self.balabit_stats['distance_mean'], 5.0)
        num_points = int(np.clip(distance / mean_step, 8, 60))

        fitts_mt = self._fitts_movement_time(distance)
        total_ms = fitts_mt * random.uniform(0.75, 1.25)

        best_points   = None
        best_ci_dist  = float('inf')

        for _ in range(8):
            spread_x = max(abs(ex-sx) * 0.35, 15.0)
            spread_y = max(abs(ey-sy) * 0.35, 15.0)
            cp_x     = (sx+ex)/2 + random.gauss(0, spread_x)
            cp_y     = (sy+ey)/2 + random.gauss(0, spread_y)

            pts = []
            for i in range(num_points):
                t      = i / max(num_points-1, 1)
                x      = (1-t)**2*sx + 2*(1-t)*t*cp_x + t**2*ex
                y      = (1-t)**2*sy + 2*(1-t)*t*cp_y + t**2*ey
                jitter = max(0.3, 1.5*(1.0-t))
                x     += random.gauss(0, jitter)
                y     += random.gauss(0, jitter)
                pts.append((int(x), int(y)))

            if random.random() < 0.55:
                for _ in range(random.randint(2, 4)):
                    pts.append((ex+random.randint(-4, 4), ey+random.randint(-4, 4)))
                pts.append((ex, ey))

            ci = self._curvature_index(pts)
            if 1.05 <= ci <= 3.0:
                best_points = pts
                break
            d = abs(ci - 1.5)
            if d < best_ci_dist:
                best_ci_dist = d
                best_points  = pts

        points = best_points

        raw_offsets = []
        running     = 0.0
        for i in range(len(points)):
            t            = i / max(len(points)-1, 1)
            speed_factor = np.sin(t * np.pi)
            base_iv      = max(5.0, np.random.normal(self.balabit_stats['interval_mean'], self.balabit_stats['interval_std']*0.3))
            running     += base_iv * (1.4 - 0.7*speed_factor)
            raw_offsets.append(running)

        scale   = total_ms / running if running > 0 else 1.0
        offsets = [int(o*scale) for o in raw_offsets]

        return [{'x': px, 'y': py, 'time_offset': off} for (px, py), off in zip(points, offsets)]

    def _generate_mouse_events(self, form_fields, start_time):
        events       = []
        clicks       = []
        current_time = start_time
        prev_pos     = (random.randint(300, 700), random.randint(150, 350))

        for field_name, tx, ty in form_fields:
            current_time += random.randint(200, 1400)
            path          = self._bezier_mouse_path(prev_pos, (tx, ty))
            seg_start     = current_time

            for pe in path:
                events.append({'type': 'mousemove', 'target': field_name,
                                'x': pe['x'], 'y': pe['y'],
                                'timestamp': seg_start + pe['time_offset']})

            if path:
                current_time = seg_start + path[-1]['time_offset']

            for _ in range(random.randint(1, 3)):
                current_time += random.randint(40, 120)
                events.append({'type': 'mousemove', 'target': field_name,
                                'x': tx+random.randint(-2,2), 'y': ty+random.randint(-2,2),
                                'timestamp': current_time})

            current_time += random.randint(60, 200)
            clicks.append({'target': field_name,
                           'x': tx+random.randint(-2,2), 'y': ty+random.randint(-2,2),
                           'timestamp': current_time})
            prev_pos = (tx, ty)

        return events, clicks, current_time

    def generate_normal_sample(self):
        persona_name = random.choices(PERSONA_NAMES, weights=PERSONA_WEIGHTS, k=1)[0]
        persona      = HUMAN_PERSONAS[persona_name]

        flight_mean = self.cmu_stats['flight_mean']*0.4 + persona['flight_mean']*0.6
        flight_std  = self.cmu_stats['flight_std'] *0.4 + persona['flight_std'] *0.6
        hold_mean   = self.cmu_stats['hold_mean']  *0.4 + persona['hold_mean']  *0.6
        hold_std    = self.cmu_stats['hold_std']   *0.4 + persona['hold_std']   *0.6

        page_start_time         = int(datetime.now().timestamp()*1000) - random.randint(30000, 180000)
        time_before_first_input = random.randint(1200, 9000)
        current_time            = page_start_time + time_before_first_input

        num_keys   = random.randint(35, 110)
        keystrokes = []

        for _ in range(num_keys):
            flight_time = np.random.normal(flight_mean, flight_std)
            hold_time   = np.random.normal(hold_mean,   hold_std)

            if random.random() < persona['distraction_prob']:
                flight_time += random.uniform(2000, 9000)
            if random.random() < 0.08:
                flight_time *= random.uniform(0.3, 0.6)

            current_time += int(max(10, flight_time))

            if random.random() < persona['backspace_prob']:
                key = 'Backspace'
            elif random.random() < 0.06:
                key = random.choice(list(SPECIAL_KEYS - {'Backspace'}))
            else:
                key = random.choice('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@._-+!')

            keystrokes.append({'key': key, 'type': 'keydown', 'target': 'register-email', 'timestamp': current_time})
            keystrokes.append({'key': key, 'type': 'keyup',   'target': 'register-email', 'timestamp': current_time + int(max(10, hold_time))})

        typing_end_time = current_time

        form_fields = [
            ('register-email',    random.randint(350, 650), random.randint(220, 280)),
            ('register-password', random.randint(350, 650), random.randint(320, 380)),
            ('register-submit',   random.randint(400, 600), random.randint(440, 500)),
        ]
        if random.random() < 0.35:
            form_fields.insert(2, ('register-email', form_fields[0][1], form_fields[0][2]))

        mouse_events, clicks, _ = self._generate_mouse_events(
            form_fields, page_start_time + random.randint(500, 3000)
        )

        page_dwell_time      = max(typing_end_time - page_start_time + random.randint(2000, 8000), random.randint(20000, 130000))
        form_completion_time = keystrokes[-1]['timestamp'] - keystrokes[0]['timestamp'] if len(keystrokes) > 1 else 0
        tab_switch_count     = random.choices([0,1,2,3],    weights=[0.55,0.28,0.12,0.05])[0]
        window_blur_count    = random.choices([0,1,2,3,4],  weights=[0.50,0.25,0.14,0.07,0.04])[0]
        navigation_count     = random.randint(1, 5)
        field_switch_count   = len(form_fields) - 1 + random.randint(0, 2)

        return {
            'ubaTelemetry': {
                'sessionId':            'mock-session-id',
                'ipAddress':            f"{random.randint(1,223)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}",
                'location':             'Manchester, United Kingdom',
                'pageDwellTime':        page_dwell_time,
                'tabSwitchCount':       tab_switch_count,
                'windowBlurCount':      window_blur_count,
                'navigationCount':      navigation_count,
                'fieldSwitchCount':     field_switch_count,
                'formCompletionTime':   form_completion_time,
                'timeBeforeFirstInput': time_before_first_input,
                'keystrokes':           keystrokes,
                'mouseEvents':          mouse_events,
                'clicks':               clicks,
                'pageNavigations':      [{'url': '/register', 'timestamp': page_start_time + random.randint(100, 3000)} for _ in range(random.randint(1, navigation_count))],
            }
        }

    def generate_malicious_sample(self):
        bot_name     = random.choices(BOT_NAMES, weights=BOT_WEIGHTS, k=1)[0]
        bot_profile  = BOT_PROFILES[bot_name]
        current_time = int(datetime.now().timestamp()*1000) - random.randint(500, 4000)

        num_keys   = random.randint(20, 50)
        keystrokes = []

        for _ in range(num_keys):
            interval = random.uniform(*bot_profile['pause'])
            hold     = random.uniform(*bot_profile['hold'])
            if bot_profile['jitter']:
                interval += random.gauss(0, interval*0.15)
                hold     += random.gauss(0, hold    *0.10)
            current_time += int(max(1, interval))
            key = random.choice('abcdefghijklmnopqrstuvwxyz0123456789')
            keystrokes.append({'key': key, 'type': 'keydown', 'target': 'register-email', 'timestamp': current_time})
            keystrokes.append({'key': key, 'type': 'keyup',   'target': 'register-email', 'timestamp': current_time + int(max(1, hold))})

        mouse_events = []
        if bot_profile['mouse_moves']:
            if bot_profile['jitter']:
                path = self._bezier_mouse_path((500, 500), (400, 300))
                t    = current_time
                for pe in path:
                    t += random.randint(5, 20)
                    mouse_events.append({'type': 'mousemove', 'target': 'register-email', 'x': pe['x'], 'y': pe['y'], 'timestamp': t})
            else:
                for i in range(5):
                    mouse_events.append({'type': 'mousemove', 'target': 'register-submit', 'x': 500, 'y': 500, 'timestamp': current_time + i*10})

        clicks = [
            {'target': 'register-email',  'x': 400, 'y': 300, 'timestamp': current_time + 20},
            {'target': 'register-submit', 'x': 500, 'y': 500, 'timestamp': current_time + 50},
        ]
        form_completion_time = keystrokes[-1]['timestamp'] - keystrokes[0]['timestamp'] if len(keystrokes) > 1 else 0

        return {
            'ubaTelemetry': {
                'sessionId':            'bot-session-id',
                'ipAddress':            f"{random.randint(1,223)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}",
                'location':             'Unknown Server',
                'pageDwellTime':        random.randint(2000, 12000),
                'tabSwitchCount':       0,
                'windowBlurCount':      0,
                'navigationCount':      0,
                'fieldSwitchCount':     0,
                'formCompletionTime':   form_completion_time,
                'timeBeforeFirstInput': random.randint(0, 150),
                'keystrokes':           keystrokes,
                'mouseEvents':          mouse_events,
                'clicks':               clicks,
                'pageNavigations':      [],
            }
        }

    def extract_features(self, payload):
        """Extracts 28-feature vector. Order must match Java VAEAnalysis.extractFeatures()."""
        data     = payload.get('ubaTelemetry', payload)
        keys     = data.get('keystrokes', [])
        keydowns = [k for k in keys if k.get('type') == 'keydown']

        keystroke_count = data.get('keystrokeCount', len(keydowns))  # keydowns only

        typing_intervals = []
        if len(keydowns) > 1:
            typing_intervals = [keydowns[i]['timestamp'] - keydowns[i-1]['timestamp'] for i in range(1, len(keydowns))]

        avg_flight_time    = data.get('avgFlightTime',    np.mean(typing_intervals)   if typing_intervals else 0.0)
        std_flight_time    = data.get('stdFlightTime',    np.std(typing_intervals)    if typing_intervals else 0.0)
        median_flight_time = data.get('medianFlightTime', np.median(typing_intervals) if typing_intervals else 0.0)
        backspace_count    = data.get('backspaceCount',   len([k for k in keydowns if k.get('key') == 'Backspace']))
        backspace_ratio    = data.get('backspaceRatio',   backspace_count / keystroke_count if keystroke_count > 0 else 0.0)
        special_key_count  = data.get('specialKeyCount',  len([k for k in keydowns if k.get('key') in SPECIAL_KEYS]))

        hold_times = []
        pending    = {}
        for event in keys:
            key, etype, ts = event.get('key'), event.get('type'), event.get('timestamp')
            if key is None or ts is None:
                continue
            if etype == 'keydown':
                pending[key] = ts
            elif etype == 'keyup' and key in pending:
                hold_times.append(ts - pending.pop(key))
        avg_key_hold_time = data.get('avgKeyHoldTime', np.mean(hold_times) if hold_times else 0.0)

        clicks              = data.get('clicks', [])
        click_count         = data.get('clickCount', len(clicks))
        click_intervals     = [clicks[i]['timestamp'] - clicks[i-1]['timestamp'] for i in range(1, len(clicks))] if len(clicks) > 1 else []
        mean_click_interval = np.mean(click_intervals) if click_intervals else 0.0
        page_dwell_time     = data.get('pageDwellTime', 0)
        click_frequency     = data.get('clickFrequency', click_count / (page_dwell_time/1000) if page_dwell_time > 0 else 0.0)

        all_mouse_events = data.get('mouseEvents', [])
        moves            = [e for e in all_mouse_events if e.get('type') in ['mousemove','mouseover'] or 'x' in e]

        distances, speeds, mouse_time_intervals = [], [], []
        if len(moves) > 1:
            for i in range(1, len(moves)):
                dx = moves[i].get('x',0) - moves[i-1].get('x',0)
                dy = moves[i].get('y',0) - moves[i-1].get('y',0)
                d  = float(np.sqrt(dx**2 + dy**2))
                dt = float(moves[i].get('timestamp',0) - moves[i-1].get('timestamp',0))
                distances.append(d)
                mouse_time_intervals.append(dt)
                if dt > 0:
                    speeds.append(d / (dt/1000.0))

        mouse_event_count   = data.get('mouseEventCount', len(moves))
        mouse_distance      = data.get('mouseDistance',   float(np.sum(distances)) if distances else 0.0)
        avg_mouse_speed     = data.get('avgMouseSpeed',   float(np.mean(speeds))   if speeds    else 0.0)
        max_mouse_speed     = data.get('maxMouseSpeed',   float(np.max(speeds))    if speeds    else 0.0)
        mean_mouse_distance = float(np.mean(distances)) if distances else 0.0
        std_mouse_distance  = float(np.std(distances))  if distances else 0.0
        mean_mouse_interval = float(np.mean(mouse_time_intervals)) if mouse_time_intervals else 0.0

        page_dwell_seconds      = page_dwell_time / 1000.0 if page_dwell_time else 0.0
        form_completion_time    = data.get('formCompletionTime', page_dwell_time)
        time_before_first_input = data.get('timeBeforeFirstInput', 0)
        typing_speed            = data.get('typingSpeed', keystroke_count / (form_completion_time/1000.0) if form_completion_time and form_completion_time > 0 else 0.0)

        all_ts = sorted([k['timestamp'] for k in keydowns] + [c['timestamp'] for c in clicks] + [m['timestamp'] for m in moves])
        if len(all_ts) > 1 and page_dwell_time > 0:
            idle_ms         = sum(all_ts[i]-all_ts[i-1] for i in range(1, len(all_ts)) if all_ts[i]-all_ts[i-1] > 3000)
            idle_time_ratio = min(idle_ms / page_dwell_time, 1.0)
        else:
            idle_time_ratio = 0.0

        return np.array([
            avg_flight_time,                        # 0
            std_flight_time,                        # 1
            backspace_ratio,                        # 2
            float(keystroke_count),                 # 3  FIX: keydowns only
            median_flight_time,                     # 4
            mean_click_interval,                    # 5
            float(click_count),                     # 6
            mean_mouse_distance,                    # 7
            std_mouse_distance,                     # 8
            float(mouse_event_count),               # 9
            mean_mouse_interval,                    # 10
            page_dwell_seconds,                     # 11
            float(data.get('tabSwitchCount',  0)),  # 12
            float(data.get('windowBlurCount', 0)),  # 13
            float(data.get('navigationCount', 0)),  # 14
            float(time_before_first_input),         # 15  FIX: now generated
            float(form_completion_time),            # 16
            float(data.get('fieldSwitchCount', 0)), # 17
            float(keystroke_count),                 # 18  intentional duplicate
            avg_key_hold_time,                      # 19
            typing_speed,                           # 20
            float(backspace_count),                 # 21
            float(special_key_count),               # 22
            mouse_distance,                         # 23
            avg_mouse_speed,                        # 24
            max_mouse_speed,                        # 25
            click_frequency,                        # 26
            idle_time_ratio,                        # 27  NEW - add to Java too
        ], dtype=np.float32)

    def generate_normal_only_dataset(self, num_samples=15000):
        data = []
        print(f"Generating {num_samples} normal samples (CMU + BALABIT + Fitts + Curvature)...")
        for i in range(num_samples):
            data.append(self.extract_features(self.generate_normal_sample()))
            if (i+1) % 1000 == 0:
                print(f"  {i+1}/{num_samples}")
        return np.array(data)

    def generate_dataset(self, num_normal=15000, num_malicious=3000):
        data, labels = [], []
        print(f"Generating {num_normal} normal samples...")
        for i in range(num_normal):
            data.append(self.extract_features(self.generate_normal_sample()))
            labels.append(0)
            if (i+1) % 2000 == 0:
                print(f"  Normal: {i+1}/{num_normal}")
        print(f"Generating {num_malicious} malicious samples...")
        for i in range(num_malicious):
            data.append(self.extract_features(self.generate_malicious_sample()))
            labels.append(1)
            if (i+1) % 500 == 0:
                print(f"  Malicious: {i+1}/{num_malicious}")
        return np.array(data), np.array(labels)


if __name__ == "__main__":

    generator = UserBehaviorDataGenerator(
        cmu_csv_path='DSL-StrongPasswordData.csv',
        balabit_csv_path='balabit_mouse.csv',  # set None if not downloaded yet
    )

    X_normal = generator.generate_normal_only_dataset(15000)
    np.save('normal_behavior_features.npy', X_normal)

    X, y = generator.generate_dataset(15000, 3000)
    np.save('user_behavior_features.npy', X)
    np.save('user_behavior_labels.npy',   y)

    print(f"\n[Done]")
    print(f"  Normal dataset  (VAE training): {X_normal.shape}")
    print(f"  Evaluation dataset:             {X.shape}")
    print(f"  Label distribution:             {np.bincount(y)}")

    feature_names = [
        'avgFlightTime','stdFlightTime','backspaceRatio','keystrokeCount',
        'medianFlightTime','meanClickInterval','clickCount',
        'meanMouseDistance','stdMouseDistance','mouseEventCount',
        'meanMouseInterval','pageDwellSeconds','tabSwitchCount',
        'windowBlurCount','navigationCount','timeBeforeFirstInput',
        'formCompletionTime','fieldSwitchCount','keystrokeCount2',
        'avgKeyHoldTime','typingSpeed','backspaceCount','specialKeyCount',
        'mouseDistance','avgMouseSpeed','maxMouseSpeed','clickFrequency',
        'idleTimeRatio',
    ]

    normal_mean    = X[y == 0].mean(axis=0)
    malicious_mean = X[y == 1].mean(axis=0)

    print(f"\n{'#':<4} {'Feature':<24} {'Normal':>12} {'Bot':>12} {'Sep%':>8}")
    print("-" * 64)
    for i, name in enumerate(feature_names):
        denom = max(normal_mean[i], malicious_mean[i], 1e-6)
        sep   = abs(normal_mean[i] - malicious_mean[i]) / denom * 100
        print(f"  {i:<3} {name:<24} {normal_mean[i]:>12.2f} {malicious_mean[i]:>12.2f} {sep:>7.1f}%")