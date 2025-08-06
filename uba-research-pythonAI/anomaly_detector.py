import json
import numpy as np
from flask import Flask, request, jsonify
from data_generator import UserBehaviorDataGenerator
from vae_model import UserBehaviorVAE
import sys
import os

class AnomalyDetector:
    def __init__(self, model_path='user_behavior_vae.pth'):
        self.feature_extractor = UserBehaviorDataGenerator()
        self.vae = None
        self.model_path = model_path
        self.load_model()
    
    def load_model(self):
        try:
            # Determine input dimension from a sample with new structure
            sample_features = self.feature_extractor.extract_features({
                'keysPressed': [{'key': 'a', 'time': 123}],
                'mouseClicks': [],
                'mouseHovers': [],
                'scrollEvents': [],
                'pasteEvents': [],
                'autofillDetected': [],
                'timeSpent': 1000,
                'screenResolution': '1920x1080'
            })
            
            self.vae = UserBehaviorVAE(input_dim=len(sample_features))
            
            if os.path.exists(self.model_path):
                self.vae.load_model(self.model_path)
                print("Model loaded successfully")
            else:
                print(f"Model file {self.model_path} not found. Train the model first.")
                
        except Exception as e:
            print(f"Error loading model: {e}")
            self.vae = None
    
    def detect_anomaly(self, payload):
        if self.vae is None:
            return {"error": "Model not loaded", "is_malicious": False, "confidence": 0.0}
        
        try:
            # Handle nested payload structure
            if 'metadata' in payload:
                data_to_analyze = payload['metadata']
            else:
                data_to_analyze = payload
            
            # Extract features from payload
            features = self.feature_extractor.extract_features(data_to_analyze)
            
            # Get prediction and reconstruction error
            prediction = self.vae.predict(features)[0]
            reconstruction_error = self.vae.get_reconstruction_error(features)
            
            # Calculate confidence based on how far from threshold
            if self.vae.threshold > 0:
                confidence = min(abs(reconstruction_error - self.vae.threshold) / self.vae.threshold, 1.0)
            else:
                confidence = 0.5
            
            return {
                "is_malicious": bool(prediction),
                "confidence": float(confidence),
                "reconstruction_error": float(reconstruction_error),
                "threshold": float(self.vae.threshold) if self.vae.threshold else 0.0
            }
            
        except Exception as e:
            return {"error": str(e), "is_malicious": False, "confidence": 0.0}

# Flask API for Spring integration
app = Flask(__name__)
detector = AnomalyDetector()

@app.route('/detect', methods=['POST'])
def detect_endpoint():
    try:
        payload = request.json
        result = detector.detect_anomaly(payload)
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e), "is_malicious": False, "confidence": 0.0}), 500

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({"status": "healthy", "model_loaded": detector.vae is not None})

# Command line interface for Spring to call directly
def main():
    if len(sys.argv) != 2:
        print("Usage: python anomaly_detector.py '<json_payload>'")
        sys.exit(1)
    
    try:
        payload = json.loads(sys.argv[1])
        result = detector.detect_anomaly(payload)
        
        # Return simple boolean for Spring
        if result.get("error"):
            print("false")  # Default to safe on error
        else:
            print("true" if result.get("is_malicious", False) else "false")
        
    except json.JSONDecodeError:
        print("false")  # Default to safe
    except Exception as e:
        print("false")  # Default to safe on any error

if __name__ == "__main__":
    if len(sys.argv) > 1:
        main()  # Command line mode
    else:
        app.run(host='0.0.0.0', port=5000, debug=False)  # API mode