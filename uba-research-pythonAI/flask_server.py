from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import sys
import os

# Import existing detectors
from anomaly_detector import AnomalyDetector
from enhanced_anomaly_detector import EnhancedAnomalyDetector

app = Flask(__name__)
CORS(app)

# Initialize detectors
anomaly_detector = AnomalyDetector()
enhanced_detector = EnhancedAnomalyDetector()

@app.route('/detect', methods=['POST'])
def detect_endpoint():
    try:
        payload = request.json
        if not payload:
            return jsonify({"error": "No payload provided", "is_malicious": False, "confidence": 0.0}), 400
        
        # Use the existing anomaly detector
        result = anomaly_detector.detect_anomaly(payload)
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e), "is_malicious": False, "confidence": 0.0}), 500

@app.route('/analyze', methods=['POST'])
def analyze_endpoint():
    """Legacy endpoint for backward compatibility"""
    return detect_endpoint()

@app.route('/enhanced_detect', methods=['POST'])
def enhanced_detect_endpoint():
    try:
        payload = request.json
        analysis_type = payload.get('type', 'insider')
        data = payload.get('data', {})
        
        if analysis_type == 'insider':
            result = enhanced_detector.detect_insider_threat(data)
        elif analysis_type == 'privilege':
            result = enhanced_detector.detect_privilege_escalation(data)
        elif analysis_type == 'compliance':
            result = enhanced_detector.detect_compliance_violation(data)
        else:
            result = {'error': 'Invalid analysis type'}
        
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e), "is_anomaly": False, "confidence": 0.0}), 500

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy", 
        "model_loaded": anomaly_detector.vae is not None,
        "enhanced_detector": "available"
    })

if __name__ == "__main__":
    print("Starting UBA Python AI Flask Server...")
    print("Endpoints available:")
    print("  POST /detect - Main anomaly detection")
    print("  POST /analyze - Legacy endpoint")
    print("  POST /enhanced_detect - Enhanced threat detection")
    print("  GET /health - Health check")
    app.run(host='0.0.0.0', port=5000, debug=True)