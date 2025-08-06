import json
import sys
import numpy as np
from sklearn.ensemble import IsolationForest
from datetime import datetime

class EnhancedAnomalyDetector:
    def __init__(self):
        self.insider_model = IsolationForest(contamination=0.15, random_state=42)
        self.privilege_model = IsolationForest(contamination=0.1, random_state=43)
        self.compliance_model = IsolationForest(contamination=0.05, random_state=44)
    
    def detect_insider_threat(self, activity_data):
        """Detect insider threats based on behavioral patterns"""
        features = []
        
        # Time-based features
        hour = activity_data.get('timeOfDay', 12)
        features.append(hour)
        features.append(1 if activity_data.get('isAfterHours', False) else 0)
        
        # Session features
        session_duration = activity_data.get('sessionDuration', 0) / 3600000  # Convert to hours
        features.append(min(session_duration, 24))  # Cap at 24 hours
        
        # Role-based features
        role = activity_data.get('role', 'user')
        features.append(1 if role == 'admin' else 0)
        features.append(1 if role == 'manager' else 0)
        
        # Activity pattern
        action = activity_data.get('action', '')
        features.append(1 if 'delete' in action.lower() else 0)
        features.append(1 if 'export' in action.lower() else 0)
        
        return self._analyze_anomaly(np.array(features).reshape(1, -1), 'insider')
    
    def detect_privilege_escalation(self, privilege_data):
        """Detect privilege escalation attempts"""
        features = []
        
        # Permission features
        permission = privilege_data.get('permission', '')
        features.append(1 if 'admin' in permission.lower() else 0)
        features.append(1 if 'delete' in permission.lower() else 0)
        features.append(1 if 'system' in permission.lower() else 0)
        
        # Scope mismatch
        expected_scope = privilege_data.get('expectedScope', 'limited')
        actual_scope = privilege_data.get('actualScope', 'limited')
        features.append(1 if expected_scope != actual_scope else 0)
        
        # Role vs permission mismatch
        role = privilege_data.get('role', 'user')
        features.append(1 if role == 'user' and 'admin' in permission else 0)
        
        return self._analyze_anomaly(np.array(features).reshape(1, -1), 'privilege')
    
    def detect_compliance_violation(self, access_data):
        """Detect compliance violations"""
        features = []
        
        # Data type sensitivity
        data_type = access_data.get('dataType', '')
        features.append(1 if 'personal' in data_type.lower() else 0)
        features.append(1 if 'medical' in data_type.lower() else 0)
        features.append(1 if 'financial' in data_type.lower() else 0)
        
        # Time-based compliance
        current_hour = datetime.now().hour
        features.append(1 if current_hour < 8 or current_hour > 18 else 0)
        
        # Purpose legitimacy
        purpose = access_data.get('purpose', '')
        features.append(1 if purpose == 'authorized' else 0)
        
        # Compliance flags count
        flags = access_data.get('complianceFlags', [])
        features.append(len(flags))
        
        return self._analyze_anomaly(np.array(features).reshape(1, -1), 'compliance')
    
    def _analyze_anomaly(self, features, model_type):
        """Generic anomaly analysis"""
        try:
            # Create synthetic training data for the model
            normal_data = np.random.normal(0, 0.5, (100, features.shape[1]))
            
            if model_type == 'insider':
                self.insider_model.fit(normal_data)
                prediction = self.insider_model.predict(features)
                score = self.insider_model.score_samples(features)[0]
            elif model_type == 'privilege':
                self.privilege_model.fit(normal_data)
                prediction = self.privilege_model.predict(features)
                score = self.privilege_model.score_samples(features)[0]
            else:  # compliance
                self.compliance_model.fit(normal_data)
                prediction = self.compliance_model.predict(features)
                score = self.compliance_model.score_samples(features)[0]
            
            is_anomaly = prediction[0] == -1
            confidence = abs(score) / 2.0  # Normalize confidence
            
            return {
                'is_anomaly': bool(is_anomaly),
                'confidence': float(min(confidence, 1.0)),
                'risk_score': float(abs(score))
            }
            
        except Exception as e:
            return {
                'is_anomaly': False,
                'confidence': 0.0,
                'risk_score': 0.0,
                'error': str(e)
            }

def main():
    if len(sys.argv) != 3:
        print(json.dumps({'error': 'Usage: python enhanced_anomaly_detector.py <analysis_type> <json_data>'}))
        sys.exit(1)
    
    analysis_type = sys.argv[1]
    data = json.loads(sys.argv[2])
    
    detector = EnhancedAnomalyDetector()
    
    if analysis_type == 'insider':
        result = detector.detect_insider_threat(data)
    elif analysis_type == 'privilege':
        result = detector.detect_privilege_escalation(data)
    elif analysis_type == 'compliance':
        result = detector.detect_compliance_violation(data)
    else:
        result = {'error': 'Invalid analysis type'}
    
    print(json.dumps(result))

if __name__ == "__main__":
    main()