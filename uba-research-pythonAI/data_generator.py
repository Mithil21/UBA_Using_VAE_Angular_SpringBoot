import json
import random
import numpy as np
from datetime import datetime, timedelta
import pandas as pd

class UserBehaviorDataGenerator:
    def __init__(self):
        self.normal_patterns = {
            'typing_speed': (50, 150),  # chars per minute
            'pause_between_keys': (50, 300),  # milliseconds
            'mouse_movement_speed': (100, 500),  # pixels per second
            'form_completion_time': (10, 60),  # seconds
            'backspace_ratio': (0.05, 0.15),  # ratio of backspaces to total keys
        }
        
        self.malicious_patterns = {
            'typing_speed': (200, 1000),  # unusually fast (bot-like)
            'pause_between_keys': (5, 50),  # very short pauses
            'mouse_movement_speed': (1000, 5000),  # very fast movements
            'form_completion_time': (1, 5),  # suspiciously fast
            'backspace_ratio': (0, 0.02),  # very few corrections
        }

    def extract_features(self, payload):
        features = []
        
        # Keystroke dynamics
        keys = payload.get('keysPressed', [])
        if len(keys) > 1:
            typing_intervals = [keys[i]['time'] - keys[i-1]['time'] for i in range(1, len(keys))]
            features.extend([
                np.mean(typing_intervals) if typing_intervals else 0,
                np.std(typing_intervals) if typing_intervals else 0,
                len([k for k in keys if k['key'] == 'Backspace']) / len(keys) if keys else 0,
                len(keys)
            ])
        else:
            features.extend([0, 0, 0, 0])
        
        # Mouse behavior
        clicks = payload.get('mouseClicks', [])
        hovers = payload.get('mouseHovers', [])
        
        if len(clicks) > 1:
            click_intervals = [clicks[i]['time'] - clicks[i-1]['time'] for i in range(1, len(clicks))]
            features.extend([
                np.mean(click_intervals) if click_intervals else 0,
                len(clicks)
            ])
        else:
            features.extend([0, len(clicks)])
        
        # Mouse movement patterns
        if len(hovers) > 1:
            distances = []
            for i in range(1, len(hovers)):
                dx = hovers[i]['x'] - hovers[i-1]['x']
                dy = hovers[i]['y'] - hovers[i-1]['y']
                distances.append(np.sqrt(dx**2 + dy**2))
            
            features.extend([
                np.mean(distances) if distances else 0,
                np.std(distances) if distances else 0,
                len(hovers)
            ])
        else:
            features.extend([0, 0, len(hovers)])
        
        # Time-based features
        features.extend([
            payload.get('timeSpent', 0),
            len(payload.get('scrollEvents', []))
        ])
        
        # Screen and environment
        screen_res = payload.get('screenResolution', '1920x1080').split('x')
        features.extend([
            int(screen_res[0]) if len(screen_res) > 0 else 1920,
            int(screen_res[1]) if len(screen_res) > 1 else 1080
        ])
        
        return np.array(features, dtype=np.float32)

    def generate_normal_sample(self):
        # Generate realistic normal user behavior
        typing_speed = random.uniform(*self.normal_patterns['typing_speed'])
        num_keys = random.randint(20, 100)
        
        keys_pressed = []
        current_time = int(datetime.now().timestamp() * 1000)
        
        for i in range(num_keys):
            interval = random.uniform(*self.normal_patterns['pause_between_keys'])
            current_time += int(interval)
            
            if random.random() < self.normal_patterns['backspace_ratio'][1]:
                key = 'Backspace'
            else:
                key = random.choice('abcdefghijklmnopqrstuvwxyz0123456789')
            
            keys_pressed.append({
                'key': key,
                'time': current_time
            })
        
        # Generate mouse events
        num_clicks = random.randint(2, 8)
        mouse_clicks = []
        for i in range(num_clicks):
            mouse_clicks.append({
                'x': random.randint(100, 1400),
                'y': random.randint(100, 800),
                'time': current_time + random.randint(1000, 5000),
                'element': f"INPUT#{random.choice(['email', 'firstName', 'lastName', 'password'])}"
            })
        
        num_hovers = random.randint(10, 50)
        mouse_hovers = []
        for i in range(num_hovers):
            mouse_hovers.append({
                'x': random.randint(100, 1400),
                'y': random.randint(100, 800),
                'time': current_time + random.randint(100, 1000),
                'element': "DIV.form-container"
            })
        
        return {
            'keysPressed': keys_pressed,
            'mouseClicks': mouse_clicks,
            'mouseHovers': mouse_hovers,
            'scrollEvents': [],
            'timeSpent': random.randint(int(self.normal_patterns['form_completion_time'][0] * 1000), 
                                      int(self.normal_patterns['form_completion_time'][1] * 1000)),
            'currentPage': '/register',
            'timestamp': datetime.now().isoformat(),
            'screenResolution': f"{random.choice([1920, 1536, 1366])}x{random.choice([1080, 864, 768])}",
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}"
        }

    def generate_malicious_sample(self):
        # Generate bot-like or malicious behavior
        num_keys = random.randint(10, 30)  # Fewer keys, more efficient
        
        keys_pressed = []
        current_time = int(datetime.now().timestamp() * 1000)
        
        for i in range(num_keys):
            interval = random.uniform(*self.malicious_patterns['pause_between_keys'])
            current_time += int(interval)
            
            # Very few backspaces (perfect typing)
            if random.random() < self.malicious_patterns['backspace_ratio'][1]:
                key = 'Backspace'
            else:
                key = random.choice('abcdefghijklmnopqrstuvwxyz0123456789')
            
            keys_pressed.append({
                'key': key,
                'time': current_time
            })
        
        # Minimal mouse interaction
        mouse_clicks = [{
            'x': random.randint(400, 600),
            'y': random.randint(300, 500),
            'time': current_time + 100,
            'element': "INPUT#email"
        }]
        
        mouse_hovers = []  # Bots typically don't hover
        
        return {
            'keysPressed': keys_pressed,
            'mouseClicks': mouse_clicks,
            'mouseHovers': mouse_hovers,
            'scrollEvents': [],
            'timeSpent': random.randint(int(self.malicious_patterns['form_completion_time'][0] * 1000), 
                                      int(self.malicious_patterns['form_completion_time'][1] * 1000)),
            'currentPage': '/register',
            'timestamp': datetime.now().isoformat(),
            'screenResolution': "1920x1080",  # Consistent resolution
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}"
        }

    def generate_dataset(self, num_normal=8000, num_malicious=2000):
        data = []
        labels = []
        
        # Generate normal samples
        for _ in range(num_normal):
            sample = self.generate_normal_sample()
            features = self.extract_features(sample)
            data.append(features)
            labels.append(0)  # 0 for normal
        
        # Generate malicious samples
        for _ in range(num_malicious):
            sample = self.generate_malicious_sample()
            features = self.extract_features(sample)
            data.append(features)
            labels.append(1)  # 1 for malicious
        
        return np.array(data), np.array(labels)

if __name__ == "__main__":
    generator = UserBehaviorDataGenerator()
    
    # Test feature extraction with your sample
    sample_payload = {
        "keysPressed": [
            {"key": "Control", "time": 1754123479881},
            {"key": "Shift", "time": 1754123480025},
            {"key": "I", "time": 1754123480121}
        ],
        "mouseClicks": [
            {"x": 1469, "y": 435, "time": 1754123479618, "element": "DIV.registration-container"}
        ],
        "mouseHovers": [
            {"x": 964, "y": 380, "time": 1754123478823, "element": "INPUT#lastName"}
        ],
        "scrollEvents": [],
        "timeSpent": 20322,
        "screenResolution": "1536x864"
    }
    
    features = generator.extract_features(sample_payload)
    print(f"Extracted features shape: {features.shape}")
    print(f"Features: {features}")
    
    # Generate dataset
    print("Generating dataset...")
    X, y = generator.generate_dataset(1000, 200)
    
    # Save dataset
    np.save('user_behavior_features.npy', X)
    np.save('user_behavior_labels.npy', y)
    
    print(f"Dataset generated: {X.shape[0]} samples, {X.shape[1]} features")
    print(f"Normal samples: {np.sum(y == 0)}, Malicious samples: {np.sum(y == 1)}")