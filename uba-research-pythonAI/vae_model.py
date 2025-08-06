import json
import sys
import numpy as np
from datetime import datetime
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
import pickle
import os

class UserBehaviourVAE(nn.Module):
    def __init__(self, input_dim=10, hidden_dim=5, latent_dim=2):
        super(UserBehaviourVAE, self).__init__()
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU()
        )
        self.mu = nn.Linear(hidden_dim, latent_dim)
        self.logvar = nn.Linear(hidden_dim, latent_dim)
        self.decoder = nn.Sequential(
            nn.Linear(latent_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, input_dim),
            nn.Sigmoid()
        )
    
    def encode(self, x):
        h = self.encoder(x)
        return self.mu(h), self.logvar(h)
    
    def reparameterize(self, mu, logvar):
        std = torch.exp(0.5 * logvar)
        eps = torch.randn_like(std)
        return mu + eps * std
    
    def decode(self, z):
        return self.decoder(z)
    
    def forward(self, x):
        mu, logvar = self.encode(x)
        z = self.reparameterize(mu, logvar)
        return self.decode(z), mu, logvar

class VAEAnomalyDetector:
    def __init__(self, model_path="user_behaviour_vae.pth", scaler_path="scaler.pkl", threshold=0.5):
        self.model_path = model_path
        self.scaler_path = scaler_path
        self.threshold = threshold
        self.model = None
        self.scaler = None
        self.device = torch.device('cpu')
        self.load_model()
    
    def load_model(self):
        """Load the trained VAE model and scaler"""
        try:
            if os.path.exists(self.model_path):
                self.model = UserBehaviourVAE()
                self.model.load_state_dict(torch.load(self.model_path, map_location=self.device))
                self.model.eval()
            else:
                self.model = UserBehaviourVAE()
            
            if os.path.exists(self.scaler_path):
                with open(self.scaler_path, 'rb') as f:
                    self.scaler = pickle.load(f)
            else:
                self.scaler = StandardScaler()
        except Exception as e:
            print(f"Error loading model: {e}")
            self.model = UserBehaviourVAE()
    
    def preprocess_data(self, json_data, timestamp):
        """Convert JSON data to feature vector"""
        features = []
        
        # Extract features from JSON data
        if isinstance(json_data, dict):
            # Add timestamp features
            dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            features.extend([
                dt.hour,
                dt.minute,
                dt.second,
                dt.weekday()
            ])
            
            # Extract numeric features from JSON
            for key, value in json_data.items():
                if isinstance(value, (int, float)):
                    features.append(value)
                elif isinstance(value, str):
                    features.append(len(value))  # String length as feature
                elif isinstance(value, bool):
                    features.append(1 if value else 0)
        
        # Pad or truncate to fixed size
        target_size = 10
        if len(features) < target_size:
            features.extend([0] * (target_size - len(features)))
        else:
            features = features[:target_size]
        
        return np.array(features).reshape(1, -1)
    
    def detect_anomaly(self, json_data, timestamp):
        """Detect if the input data is malicious/anomalous"""
        try:
            # Preprocess the data
            features = self.preprocess_data(json_data, timestamp)
            
            # Scale the features
            if hasattr(self.scaler, 'transform'):
                features = self.scaler.transform(features)
            
            # Convert to PyTorch tensor
            x = torch.FloatTensor(features).to(self.device)
            
            # Get reconstruction from VAE
            with torch.no_grad():
                reconstruction, mu, logvar = self.model(x)
            
            # Calculate reconstruction error
            mse = torch.mean((x - reconstruction) ** 2).item()
            
            # Determine if anomalous
            is_malicious = mse > self.threshold
            
            return {
                "is_malicious": bool(is_malicious),
                "anomaly_score": float(mse),
                "threshold": self.threshold,
                "timestamp": timestamp
            }
        
        except Exception as e:
            return {
                "is_malicious": True,  # Err on the side of caution
                "error": str(e),
                "timestamp": timestamp
            }

def main():
    if len(sys.argv) != 3:
        print(json.dumps({"error": "Invalid arguments. Expected: json_data timestamp"}))
        sys.exit(1)
    
    try:
        json_data = json.loads(sys.argv[1])
        timestamp = sys.argv[2]
        
        detector = VAEAnomalyDetector()
        result = detector.detect_anomaly(json_data, timestamp)
        
        print(json.dumps(result))
    
    except Exception as e:
        print(json.dumps({"error": str(e), "is_malicious": True}))
        sys.exit(1)

if __name__ == "__main__":
    main()