import json
import random
import numpy as np
from datetime import datetime, timedelta
import pandas as pd

class UserBehaviorDataGenerator:
    def __init__(self):
        self.normal_patterns = {
            'pause_between_keys': (80, 400),  # More realistic human pauses
            'form_completion_time': (20, 120),  # Longer realistic time
            'backspace_ratio': (0.08, 0.20),  # Higher correction rate
            'hover_ratio': (0.6, 0.9),  # Humans hover frequently
        }
        
        self.malicious_patterns = {
            'pause_between_keys': (10, 60),  # Consistent fast typing
            'form_completion_time': (3, 12),  # Very fast completion
            'backspace_ratio': (0, 0.03),  # Almost no corrections
            'hover_ratio': (0, 0.2),  # Minimal hovering
        }

    def extract_features(self, payload):
        # Handle nested structure - extract metadata if present
        if 'metadata' in payload:
            data = payload['metadata']
        else:
            data = payload
            
        features = []
        
        # Keystroke dynamics
        keys = data.get('keysPressed', [])
        if len(keys) > 1:
            typing_intervals = [keys[i]['time'] - keys[i-1]['time'] for i in range(1, len(keys))]
            backspace_count = len([k for k in keys if k['key'] == 'Backspace'])
            features.extend([
                np.mean(typing_intervals),
                np.std(typing_intervals),
                backspace_count / len(keys),
                len(keys),
                np.median(typing_intervals)
            ])
        else:
            features.extend([0, 0, 0, len(keys), 0])
        
        # Mouse behavior
        clicks = data.get('mouseClicks', [])
        hovers = data.get('mouseHovers', [])
        
        if len(clicks) > 1:
            click_intervals = [clicks[i]['time'] - clicks[i-1]['time'] for i in range(1, len(clicks))]
            features.extend([
                np.mean(click_intervals),
                len(clicks)
            ])
        else:
            features.extend([0, len(clicks)])
        
        # Mouse movement patterns
        if len(hovers) > 1:
            distances = []
            time_intervals = []
            for i in range(1, len(hovers)):
                dx = hovers[i]['x'] - hovers[i-1]['x']
                dy = hovers[i]['y'] - hovers[i-1]['y']
                distances.append(np.sqrt(dx**2 + dy**2))
                time_intervals.append(hovers[i]['time'] - hovers[i-1]['time'])
            
            features.extend([
                np.mean(distances),
                np.std(distances),
                len(hovers),
                np.mean(time_intervals) if time_intervals else 0
            ])
        else:
            features.extend([0, 0, len(hovers), 0])
        
        # Scroll behavior
        scrolls = data.get('scrollEvents', [])
        if len(scrolls) > 1:
            scroll_speeds = []
            for i in range(1, len(scrolls)):
                time_diff = scrolls[i]['time'] - scrolls[i-1]['time']
                scroll_diff = abs(scrolls[i]['scrollY'] - scrolls[i-1]['scrollY'])
                if time_diff > 0:
                    scroll_speeds.append(scroll_diff / time_diff)
            
            features.extend([
                np.mean(scroll_speeds) if scroll_speeds else 0,
                len(scrolls)
            ])
        else:
            features.extend([0, len(scrolls)])
        
        # Time-based features
        features.extend([
            data.get('timeSpent', 0) / 1000,  # Convert to seconds
            len(data.get('pasteEvents', [])),
            len(data.get('autofillDetected', []))
        ])
        
        # Screen resolution
        screen_res = data.get('screenResolution', '1920x1080').split('x')
        features.extend([
            int(screen_res[0]),
            int(screen_res[1])
        ])
        
        return np.array(features, dtype=np.float32)

    def generate_normal_sample(self):
        num_keys = random.randint(40, 100)
        keys_pressed = []
        current_time = random.randint(1754000000000, 1754999999999)
        
        # Human-like typing with pauses, corrections, variations
        for i in range(num_keys):
            # Variable intervals with occasional long pauses (thinking)
            if random.random() < 0.1:  # 10% chance of long pause
                interval = random.uniform(800, 2000)
            else:
                interval = random.uniform(*self.normal_patterns['pause_between_keys'])
            current_time += int(interval)
            
            if random.random() < self.normal_patterns['backspace_ratio'][1]:
                key = 'Backspace'
            else:
                key = random.choice('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@.Tab')
            
            keys_pressed.append({'key': key, 'time': current_time})
        
        # Natural mouse behavior with more variation
        num_clicks = random.randint(3, 8)
        mouse_clicks = []
        for i in range(num_clicks):
            mouse_clicks.append({
                'x': random.randint(300, 900) + random.randint(-50, 50),
                'y': random.randint(80, 500) + random.randint(-30, 30),
                'time': current_time + random.randint(1000, 5000),
                'element': f"INPUT#{random.choice(['email', 'firstName', 'lastName', 'password'])}"
            })
        
        # Lots of natural hovering
        num_hovers = random.randint(25, 60)
        mouse_hovers = []
        hover_time = current_time - 15000
        for i in range(num_hovers):
            hover_time += random.randint(100, 800)
            mouse_hovers.append({
                'x': random.randint(200, 1000),
                'y': random.randint(30, 600),
                'time': hover_time,
                'element': random.choice(["DIV.form-container", "INPUT#email", "LABEL", "DIV.registration-card"])
            })
        
        # Natural scrolling behavior
        num_scrolls = random.randint(8, 25)
        scroll_events = []
        scroll_time = current_time - 8000
        scroll_y = random.uniform(100, 400)
        for i in range(num_scrolls):
            scroll_time += random.randint(200, 1500)
            scroll_y += random.uniform(-80, 80)
            scroll_events.append({
                'scrollY': max(0, scroll_y),
                'time': scroll_time
            })
        
        return {
            'keysPressed': keys_pressed,
            'mouseClicks': mouse_clicks,
            'mouseHovers': mouse_hovers,
            'scrollEvents': scroll_events,
            'pasteEvents': [],
            'autofillDetected': [],
            'timeSpent': random.randint(int(self.normal_patterns['form_completion_time'][0] * 1000), 
                                      int(self.normal_patterns['form_completion_time'][1] * 1000)),
            'currentPage': '/register',
            'timestamp': datetime.now().isoformat(),
            'screenResolution': f"{random.choice([1920, 1536, 1366])}x{random.choice([1080, 864, 768])}",
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}"
        }

    def generate_malicious_sample(self):
        # Bot-like: consistent timing, minimal interaction, no natural variations
        num_keys = random.randint(25, 45)
        keys_pressed = []
        current_time = random.randint(1754000000000, 1754999999999)
        
        # Consistent, robotic typing
        for i in range(num_keys):
            interval = random.uniform(*self.malicious_patterns['pause_between_keys'])
            current_time += int(interval)
            
            # Almost perfect typing, very rare backspaces
            if random.random() < self.malicious_patterns['backspace_ratio'][1]:
                key = 'Backspace'
            else:
                key = random.choice('abcdefghijklmnopqrstuvwxyz0123456789@.')
            keys_pressed.append({'key': key, 'time': current_time})
        
        # Minimal, precise clicks - exactly what's needed
        mouse_clicks = [
            {'x': 612, 'y': 138, 'time': current_time + 50, 'element': "INPUT#email"},
            {'x': 708, 'y': 211, 'time': current_time + 100, 'element': "INPUT#firstName"},
            {'x': 0, 'y': 0, 'time': current_time + 150, 'element': "BUTTON.register-btn"}
        ]
        
        # Minimal or no hovering (major bot indicator)
        if random.random() < 0.8:  # 80% chance of no hovers
            mouse_hovers = []
        else:
            mouse_hovers = [
                {'x': 600, 'y': 200, 'time': current_time - 500, 'element': "INPUT#email"}
            ]
        
        return {
            'keysPressed': keys_pressed,
            'mouseClicks': mouse_clicks,
            'mouseHovers': mouse_hovers,
            'scrollEvents': [],  # Bots rarely scroll
            'pasteEvents': [],
            'autofillDetected': [],
            'timeSpent': random.randint(int(self.malicious_patterns['form_completion_time'][0] * 1000), 
                                      int(self.malicious_patterns['form_completion_time'][1] * 1000)),
            'currentPage': '/register',
            'timestamp': datetime.now().isoformat(),
            'screenResolution': "1920x1080",  # Bots use consistent resolution
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}"
        }

    def generate_dataset(self, num_normal=5000, num_malicious=1000):
        data = []
        labels = []
        
        print(f"Generating {num_normal} normal samples...")
        for i in range(num_normal):
            if i % 1000 == 0:
                print(f"Generated {i} normal samples")
            sample = self.generate_normal_sample()
            features = self.extract_features(sample)
            data.append(features)
            labels.append(0)
        
        print(f"Generating {num_malicious} malicious samples...")
        for i in range(num_malicious):
            if i % 200 == 0:
                print(f"Generated {i} malicious samples")
            sample = self.generate_malicious_sample()
            features = self.extract_features(sample)
            data.append(features)
            labels.append(1)
        
        return np.array(data), np.array(labels)
    
    def generate_normal_only_dataset(self, num_samples=5000):
        """Generate only normal samples for unsupervised training"""
        data = []
        
        print(f"Generating {num_samples} normal samples for unsupervised training...")
        for i in range(num_samples):
            if i % 1000 == 0:
                print(f"Generated {i} samples")
            sample = self.generate_normal_sample()
            features = self.extract_features(sample)
            data.append(features)
        
        return np.array(data)

if __name__ == "__main__":
    generator = UserBehaviorDataGenerator()
    
    # Test with your actual payload structure
    test_payload = {
        "payload": {
            "email": "test@example.com",
            "firstName": "Test",
            "lastName": "User",
            "password": "password123"
        },
        "metadata": {
            "keysPressed": [
                {"key": "m", "time": 1754328807699},
                {"key": "i", "time": 1754328807913},
                {"key": "t", "time": 1754328808119}
            ],
            "mouseClicks": [
                {"x": 612, "y": 138, "time": 1754328806139, "element": "INPUT#email"}
            ],
            "mouseHovers": [
                {"x": 726, "y": 324, "time": 1754298074155, "element": "DIV.registration-card"}
            ],
            "scrollEvents": [],
            "pasteEvents": [],
            "autofillDetected": [],
            "timeSpent": 32471350,
            "screenResolution": "1536x864"
        }
    }
    
    features = generator.extract_features(test_payload)
    print(f"Extracted features shape: {features.shape}")
    print(f"Features: {features}")
    
    # Generate normal-only dataset for unsupervised training
    print("\nGenerating normal dataset for unsupervised training...")
    X_normal = generator.generate_normal_only_dataset(3000)
    np.save('normal_behavior_features.npy', X_normal)
    
    # Also generate labeled dataset for evaluation
    print("\nGenerating labeled dataset for evaluation...")
    X, y = generator.generate_dataset(2000, 400)
    np.save('user_behavior_features.npy', X)
    np.save('user_behavior_labels.npy', y)
    
    print(f"\nDatasets saved:")
    print(f"Normal dataset: {X_normal.shape[0]} samples, {X_normal.shape[1]} features")
    print(f"Labeled dataset: {X.shape[0]} samples, {X.shape[1]} features")
    print(f"Normal samples: {np.sum(y == 0)}, Malicious samples: {np.sum(y == 1)}")