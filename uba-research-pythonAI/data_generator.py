import json
import random
import numpy as np
import pandas as pd
from datetime import datetime

class UserBehaviorDataGenerator:
    def __init__(self, cmu_csv_path='DSL-StrongPasswordData.csv'):
        self.cmu_stats = self._load_cmu_stats(cmu_csv_path)
        
        self.malicious_patterns = {
            'dumb_bot': {'pause': (10, 15), 'hold': (5, 8)},
            'smart_bot': {'pause': (80, 120), 'hold': (40, 60)} # Tries to mimic humans
        }

    def _load_cmu_stats(self, csv_path):
        """Extracts real human typing distributions from the CMU dataset."""
        try:
            df = pd.read_csv(csv_path)
            # CMU records in seconds. We multiply by 1000 for milliseconds.
            h_cols = [c for c in df.columns if c.startswith('H.')]
            ud_cols = [c for c in df.columns if c.startswith('UD.')]
            
            # Calculate global human statistics from the 51 subjects
            return {
                'hold_mean': df[h_cols].values.mean() * 1000,
                'hold_std': df[h_cols].values.std() * 1000,
                'flight_mean': df[ud_cols].values.mean() * 1000,
                'flight_std': df[ud_cols].values.std() * 1000
            }
        except Exception as e:
            print(f"Warning: Could not load CMU dataset ({e}). Using fallback stats.")
            return {'hold_mean': 100, 'hold_std': 25, 'flight_mean': 200, 'flight_std': 50}

    def extract_features(self, payload):
        """Extracts old + new UBA features from the Angular JSON schema."""
        data = payload.get('ubaTelemetry', payload)

        # ---------- Raw keystroke fallback ----------
        keys = data.get('keystrokes', [])
        keydowns = [k for k in keys if k.get('type') == 'keydown']
        keyups = [k for k in keys if k.get('type') == 'keyup']

        keystroke_count = data.get('keystrokeCount', len(keydowns))

        typing_intervals = []
        if len(keydowns) > 1:
            typing_intervals = [
                keydowns[i]['timestamp'] - keydowns[i - 1]['timestamp']
                for i in range(1, len(keydowns))
            ]

        avg_flight_time = data.get(
            'avgFlightTime',
            np.mean(typing_intervals) if typing_intervals else 0
        )

        std_flight_time = data.get(
            'stdFlightTime',
            np.std(typing_intervals) if typing_intervals else 0
        )

        median_flight_time = data.get(
            'medianFlightTime',
            np.median(typing_intervals) if typing_intervals else 0
        )

        backspace_count = data.get(
            'backspaceCount',
            len([k for k in keydowns if k.get('key') == 'Backspace'])
        )

        backspace_ratio = data.get(
            'backspaceRatio',
            backspace_count / keystroke_count if keystroke_count > 0 else 0
        )

        special_keys = {
            'Tab', 'Enter', 'Shift', 'Control', 'Alt',
            'Meta', 'Escape', 'ArrowUp', 'ArrowDown',
            'ArrowLeft', 'ArrowRight', 'Backspace', 'Delete'
        }

        special_key_count = data.get(
            'specialKeyCount',
            len([k for k in keydowns if k.get('key') in special_keys])
        )

        # Estimate avg key hold time from keydown/keyup pairs
        hold_times = []
        pending_keydowns = {}

        for event in keys:
            key = event.get('key')
            event_type = event.get('type')
            timestamp = event.get('timestamp')

            if key is None or timestamp is None:
                continue

            if event_type == 'keydown':
                pending_keydowns[key] = timestamp
            elif event_type == 'keyup' and key in pending_keydowns:
                hold_times.append(timestamp - pending_keydowns[key])
                del pending_keydowns[key]

        avg_key_hold_time = data.get(
            'avgKeyHoldTime',
            np.mean(hold_times) if hold_times else 0
        )

        # ---------- Click fallback ----------
        clicks = data.get('clicks', [])
        click_count = data.get('clickCount', len(clicks))

        click_intervals = []
        if len(clicks) > 1:
            click_intervals = [
                clicks[i]['timestamp'] - clicks[i - 1]['timestamp']
                for i in range(1, len(clicks))
            ]

        mean_click_interval = np.mean(click_intervals) if click_intervals else 0

        click_frequency = data.get(
            'clickFrequency',
            click_count / (data.get('pageDwellTime', 1) / 1000)
            if data.get('pageDwellTime', 0) > 0 else 0
        )

        # ---------- Mouse fallback ----------
        all_mouse_events = data.get('mouseEvents', [])
        moves = [
            e for e in all_mouse_events
            if e.get('type') in ['mousemove', 'mouseover'] or 'x' in e
        ]

        distances = []
        speeds = []
        mouse_time_intervals = []

        if len(moves) > 1:
            for i in range(1, len(moves)):
                dx = moves[i].get('x', 0) - moves[i - 1].get('x', 0)
                dy = moves[i].get('y', 0) - moves[i - 1].get('y', 0)
                distance = np.sqrt(dx ** 2 + dy ** 2)

                dt = moves[i].get('timestamp', 0) - moves[i - 1].get('timestamp', 0)

                distances.append(distance)
                mouse_time_intervals.append(dt)

                if dt > 0:
                    speeds.append(distance / (dt / 1000))

        mouse_event_count = data.get('mouseEventCount', len(moves))
        mouse_distance = data.get('mouseDistance', np.sum(distances) if distances else 0)

        avg_mouse_speed = data.get(
            'avgMouseSpeed',
            np.mean(speeds) if speeds else 0
        )

        max_mouse_speed = data.get(
            'maxMouseSpeed',
            np.max(speeds) if speeds else 0
        )

        mean_mouse_distance = np.mean(distances) if distances else 0
        std_mouse_distance = np.std(distances) if distances else 0
        mean_mouse_interval = np.mean(mouse_time_intervals) if mouse_time_intervals else 0

        # ---------- Timing/session ----------
        page_dwell_time = data.get('pageDwellTime', 0)
        page_dwell_seconds = page_dwell_time / 1000 if page_dwell_time else 0

        form_completion_time = data.get('formCompletionTime', page_dwell_time)
        time_before_first_input = data.get('timeBeforeFirstInput', 0)

        typing_speed = data.get(
            'typingSpeed',
            keystroke_count / (form_completion_time / 1000)
            if form_completion_time and form_completion_time > 0 else 0
        )

        # idleTimeRatio: fraction of dwell time with no events (gaps > 3s)
        all_ts = sorted(
            [k['timestamp'] for k in keydowns] +
            [c['timestamp'] for c in clicks] +
            [m['timestamp'] for m in moves]
        )
        if len(all_ts) > 1 and page_dwell_time > 0:
            idle_ms = sum(
                all_ts[i] - all_ts[i-1]
                for i in range(1, len(all_ts))
                if all_ts[i] - all_ts[i-1] > 3000
            )
            idle_time_ratio = idle_ms / page_dwell_time
        else:
            idle_time_ratio = 0.0

        return np.array([
            avg_flight_time, std_flight_time, backspace_ratio, len(keys), median_flight_time,
            mean_click_interval, click_count,
            mean_mouse_distance, std_mouse_distance, mouse_event_count, mean_mouse_interval,
            page_dwell_seconds, data.get('tabSwitchCount', 0), data.get('windowBlurCount', 0),
            data.get('navigationCount', 0), time_before_first_input, form_completion_time,
            data.get('fieldSwitchCount', 0),
            keystroke_count, avg_key_hold_time, typing_speed,
            backspace_count, special_key_count,
            mouse_distance, avg_mouse_speed, max_mouse_speed,
            click_frequency, idle_time_ratio
        ], dtype=np.float32)
    def generate_normal_sample(self):
        current_time = int(datetime.now().timestamp() * 1000) - random.randint(10000, 50000)
        
        num_keys = random.randint(40, 100)
        keystrokes = []
        for _ in range(num_keys):
            # Sample from actual CMU statistical distributions
            flight_time = np.random.normal(self.cmu_stats['flight_mean'], self.cmu_stats['flight_std'])
            hold_time = np.random.normal(self.cmu_stats['hold_mean'], self.cmu_stats['hold_std'])
            
            # EDGE CASE: The "Distracted Human" (Prevents False Positives)
            # 5% chance the user stops typing to check their phone
            if random.random() < 0.05:  
                flight_time += random.uniform(2000, 8000)
                
            current_time += int(max(10, flight_time)) # Ensure time moves forward
            key = 'Backspace' if random.random() < 0.15 else random.choice('abcdefghijklmnopqrstuvwxyz')
            
            keystrokes.append({'key': key, 'type': 'keydown', 'target': 'register-email', 'timestamp': current_time})
            keystrokes.append({'key': key, 'type': 'keyup', 'target': 'register-email', 'timestamp': current_time + int(max(10, hold_time))})
        
        num_moves = random.randint(25, 60)
        mouse_events = []
        move_time = current_time - 15000
        for _ in range(num_moves):
            move_time += random.randint(100, 800)
            mouse_events.append({'type': 'mousemove', 'target': 'form', 'x': random.randint(200, 1000), 'y': random.randint(30, 600), 'timestamp': move_time})

        clicks = [{'target': 'register-email', 'x': random.randint(300, 900), 'y': random.randint(80, 500), 'timestamp': current_time + random.randint(1000, 5000)} for _ in range(random.randint(3, 8))]

        page_dwell_time = random.randint(20000, 120000)
        form_completion_time = keystrokes[-1]['timestamp'] - keystrokes[0]['timestamp'] if len(keystrokes) > 1 else 0
        return {
            'ubaTelemetry': {
                'sessionId': 'mock-session-id',
                'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}",
                'location': 'Manchester, United Kingdom',
                'pageDwellTime': page_dwell_time,
                'tabSwitchCount': random.randint(0, 2),
                'windowBlurCount': random.randint(0, 3),
                'navigationCount': random.randint(1, 5),
                'fieldSwitchCount': random.randint(1, 4),
                'formCompletionTime': form_completion_time,
                'keystrokes': keystrokes,
                'mouseEvents': mouse_events,
                'clicks': clicks,
                'pageNavigations': [{'url': '/register', 'timestamp': current_time - random.randint(1000, 5000)} for _ in range(random.randint(1, 4))]
            }
        }

    def generate_malicious_sample(self):
        current_time = int(datetime.now().timestamp() * 1000) - random.randint(1000, 5000)
        
        # EDGE CASE: The "Smart Bot" (Penetration Testing / Evasion)
        # 30% of bots will try to add jitter to evade basic detection
        bot_type = 'smart_bot' if random.random() < 0.3 else 'dumb_bot'
        pattern = self.malicious_patterns[bot_type]
        
        num_keys = random.randint(25, 45)
        keystrokes = []
        for _ in range(num_keys):
            interval = random.uniform(*pattern['pause'])
            hold = random.uniform(*pattern['hold'])
            current_time += int(interval)
            key = random.choice('abcdefghijklmnopqrstuvwxyz')
            
            keystrokes.append({'key': key, 'type': 'keydown', 'target': 'register-email', 'timestamp': current_time})
            keystrokes.append({'key': key, 'type': 'keyup', 'target': 'register-email', 'timestamp': current_time + int(hold)}) 
            
        mouse_events = [{'type': 'mousemove', 'target': 'register-submit', 'x': 500, 'y': 500, 'timestamp': current_time}] if random.random() > 0.8 else []
        clicks = [
            {'target': 'register-email', 'x': 400, 'y': 300, 'timestamp': current_time + 50},
            {'target': 'register-submit', 'x': 500, 'y': 500, 'timestamp': current_time + 100}
        ]

        form_completion_time = keystrokes[-1]['timestamp'] - keystrokes[0]['timestamp'] if len(keystrokes) > 1 else 0
        return {
            'ubaTelemetry': {
                'sessionId': 'bot-session-id',
                'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}",
                'location': 'Unknown Server',
                'pageDwellTime': random.randint(3000, 15000),
                'tabSwitchCount': 0,
                'windowBlurCount': 0,
                'navigationCount': 0,
                'fieldSwitchCount': 0,
                'formCompletionTime': form_completion_time,
                'keystrokes': keystrokes,
                'mouseEvents': mouse_events,
                'clicks': clicks,
                'pageNavigations': []
            }
        }

    def generate_dataset(self, num_normal=5000, num_malicious=1000):
        data, labels = [], []
        print(f"Generating {num_normal} normal samples (Anchored to CMU Dataset)...")
        for _ in range(num_normal):
            data.append(self.extract_features(self.generate_normal_sample()))
            labels.append(0)
            
        print(f"Generating {num_malicious} malicious (Dumb/Smart Bot) samples...")
        for _ in range(num_malicious):
            data.append(self.extract_features(self.generate_malicious_sample()))
            labels.append(1)
            
        return np.array(data), np.array(labels)
        
    def generate_normal_only_dataset(self, num_samples=5000):
        data = []
        print(f"Generating {num_samples} CMU-anchored normal samples for VAE training...")
        for _ in range(num_samples):
            data.append(self.extract_features(self.generate_normal_sample()))
        return np.array(data)

if __name__ == "__main__":
    generator = UserBehaviorDataGenerator('DSL-StrongPasswordData.csv')
    
    # 1. Generate normal-only dataset for unsupervised VAE training
    X_normal = generator.generate_normal_only_dataset(5000)
    np.save('normal_behavior_features.npy', X_normal)
    
    # 2. Generate labeled dataset for ROC Curve / Threshold Evaluation
    X, y = generator.generate_dataset(2000, 500)
    np.save('user_behavior_features.npy', X)
    np.save('user_behavior_labels.npy', y)
    
    print(f"\n[Success] Datasets saved!")
    print(f"Normal dataset shape (for VAE): {X_normal.shape}")
    print(f"Evaluation dataset shape: {X.shape}")