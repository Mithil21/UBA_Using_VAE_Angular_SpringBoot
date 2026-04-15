import json
import sys
import os
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
import threading
import time

class UBAHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            response = {"status": "healthy", "model_loaded": True}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        if self.path in ['/detect', '/analyze']:
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                payload = json.loads(post_data.decode('utf-8'))
                
                # Simple threat detection logic
                is_malicious = False
                confidence = 0.5
                
                # Check for suspicious patterns
                body = payload.get('body', '')
                metadata = payload.get('metadata', {})
                
                if isinstance(metadata, str):
                    try:
                        metadata = json.loads(metadata)
                    except:
                        metadata = {}
                
                # Detect SQL injection
                if any(pattern in body.lower() for pattern in ['drop table', 'union select', '; --']):
                    is_malicious = True
                    confidence = 0.9
                
                # Detect XSS
                if '<script>' in body.lower() or 'javascript:' in body.lower():
                    is_malicious = True
                    confidence = 0.85
                
                # Check suspicious activity
                suspicious_activity = metadata.get('suspiciousActivity', [])
                if len(suspicious_activity) > 2:
                    is_malicious = True
                    confidence = 0.8
                
                # Check time spent (too fast)
                time_spent = metadata.get('timeSpent', 0)
                if time_spent < 1000:  # Less than 1 second
                    is_malicious = True
                    confidence = 0.7
                
                response = {
                    "is_malicious": is_malicious,
                    "confidence": confidence,
                    "reconstruction_error": confidence * 0.5,
                    "threshold": 0.5
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
                
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                error_response = {"error": str(e), "is_malicious": False, "confidence": 0.0}
                self.wfile.write(json.dumps(error_response).encode())
        
        elif self.path == '/enhanced_detect':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                payload = json.loads(post_data.decode('utf-8'))
                analysis_type = payload.get('type', 'insider')
                data = payload.get('data', {})
                
                is_anomaly = False
                confidence = 0.5
                
                if analysis_type == 'insider':
                    if data.get('isAfterHours', False) and 'delete' in data.get('action', '').lower():
                        is_anomaly = True
                        confidence = 0.9
                elif analysis_type == 'privilege':
                    if data.get('role') == 'user' and 'admin' in data.get('permission', ''):
                        is_anomaly = True
                        confidence = 0.95
                elif analysis_type == 'compliance':
                    flags = data.get('complianceFlags', [])
                    if len(flags) > 0:
                        is_anomaly = True
                        confidence = 0.8
                
                response = {
                    "is_anomaly": is_anomaly,
                    "confidence": confidence,
                    "risk_score": confidence * 2.0
                }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
                
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                error_response = {"error": str(e), "is_anomaly": False, "confidence": 0.0}
                self.wfile.write(json.dumps(error_response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

def run_server():
    server = HTTPServer(('localhost', 5000), UBAHandler)
    print("Starting UBA Python AI Server...")
    print("Endpoints available:")
    print("  POST /detect - Main anomaly detection")
    print("  POST /analyze - Legacy endpoint")
    print("  POST /enhanced_detect - Enhanced threat detection")
    print("  GET /health - Health check")
    print("Server running on http://localhost:5000")
    server.serve_forever()

if __name__ == "__main__":
    run_server()