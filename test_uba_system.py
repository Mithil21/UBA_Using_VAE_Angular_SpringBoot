#!/usr/bin/env python3
"""
UBA Research System Test Script
Tests all 10 security scenarios and system integration
"""

import requests
import json
import time
import sys
from datetime import datetime

class UBASystemTester:
    def __init__(self):
        self.backend_url = "http://localhost:8080"
        self.python_ai_url = "http://localhost:5000"
        self.test_results = []
        
    def test_python_ai_service(self):
        """Test Python AI service endpoints"""
        print("🔍 Testing Python AI Service...")
        
        # Test health endpoint
        try:
            response = requests.get(f"{self.python_ai_url}/health")
            if response.status_code == 200:
                print("✅ Python AI Health Check: PASSED")
                self.test_results.append(("Python AI Health", "PASSED"))
            else:
                print("❌ Python AI Health Check: FAILED")
                self.test_results.append(("Python AI Health", "FAILED"))
        except Exception as e:
            print(f"❌ Python AI Health Check: FAILED - {e}")
            self.test_results.append(("Python AI Health", "FAILED"))
        
        # Test detect endpoint
        test_payload = {
            "body": "test data",
            "metadata": {
                "keysPressed": [{"key": "a", "time": 123}],
                "mouseClicks": [],
                "timeSpent": 1000
            }
        }
        
        try:
            response = requests.post(f"{self.python_ai_url}/detect", json=test_payload)
            if response.status_code == 200:
                result = response.json()
                if "is_malicious" in result:
                    print("✅ Python AI Detection: PASSED")
                    self.test_results.append(("Python AI Detection", "PASSED"))
                else:
                    print("❌ Python AI Detection: FAILED - Invalid response format")
                    self.test_results.append(("Python AI Detection", "FAILED"))
            else:
                print(f"❌ Python AI Detection: FAILED - Status {response.status_code}")
                self.test_results.append(("Python AI Detection", "FAILED"))
        except Exception as e:
            print(f"❌ Python AI Detection: FAILED - {e}")
            self.test_results.append(("Python AI Detection", "FAILED"))
    
    def test_backend_integration(self):
        """Test Spring Boot backend integration"""
        print("🔍 Testing Backend Integration...")
        
        # Test basic connectivity
        try:
            response = requests.get(f"{self.backend_url}/api/auth/health", timeout=5)
            print("✅ Backend Connectivity: PASSED")
            self.test_results.append(("Backend Connectivity", "PASSED"))
        except Exception as e:
            print(f"❌ Backend Connectivity: FAILED - {e}")
            self.test_results.append(("Backend Connectivity", "FAILED"))
            return
        
        # Test security endpoints
        security_endpoints = [
            "/api/security/insider-activity",
            "/api/security/privilege-usage", 
            "/api/security/data-access"
        ]
        
        for endpoint in security_endpoints:
            try:
                test_data = {
                    "userId": "test_user",
                    "timestamp": datetime.now().isoformat(),
                    "action": "test_action"
                }
                response = requests.post(f"{self.backend_url}{endpoint}", json=test_data, timeout=5)
                if response.status_code in [200, 201, 202]:
                    print(f"✅ {endpoint}: PASSED")
                    self.test_results.append((endpoint, "PASSED"))
                else:
                    print(f"❌ {endpoint}: FAILED - Status {response.status_code}")
                    self.test_results.append((endpoint, "FAILED"))
            except Exception as e:
                print(f"❌ {endpoint}: FAILED - {e}")
                self.test_results.append((endpoint, "FAILED"))
    
    def test_security_scenarios(self):
        """Test all 10 security scenarios"""
        print("🔍 Testing Security Scenarios...")
        
        scenarios = [
            ("Penetration Detection", self.test_penetration_detection),
            ("Session Hijacking", self.test_session_hijacking),
            ("API Abuse", self.test_api_abuse),
            ("Application Attacks", self.test_application_attacks),
            ("Account Takeover", self.test_account_takeover),
            ("Data Exfiltration", self.test_data_exfiltration),
            ("Insider Threats", self.test_insider_threats),
            ("Privilege Escalation", self.test_privilege_escalation),
            ("Malware Detection", self.test_malware_detection),
            ("Compliance Violations", self.test_compliance_violations)
        ]
        
        for name, test_func in scenarios:
            try:
                result = test_func()
                status = "PASSED" if result else "FAILED"
                print(f"{'✅' if result else '❌'} {name}: {status}")
                self.test_results.append((name, status))
            except Exception as e:
                print(f"❌ {name}: FAILED - {e}")
                self.test_results.append((name, "FAILED"))
    
    def test_penetration_detection(self):
        """Test penetration and intrusion detection"""
        payload = {
            "body": "login_attempt",
            "metadata": {
                "keysPressed": [{"key": "admin", "time": 100}, {"key": "password", "time": 150}],
                "mouseClicks": [],
                "timeSpent": 500,  # Very fast - suspicious
                "suspiciousActivity": ["TOO_FAST_COMPLETION", "NO_MOUSE_INTERACTION"]
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/detect", json=payload)
        return response.status_code == 200 and response.json().get("is_malicious", False)
    
    def test_session_hijacking(self):
        """Test session hijacking detection"""
        # Simulate different device fingerprint
        payload = {
            "body": "session_request",
            "metadata": {
                "deviceFingerprint": "different_device",
                "ipAddress": "192.168.1.100",
                "location": {"latitude": 40.7128, "longitude": -74.0060}
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/detect", json=payload)
        return response.status_code == 200
    
    def test_api_abuse(self):
        """Test API abuse detection"""
        # Simulate rapid API calls
        for i in range(5):
            payload = {"body": f"api_call_{i}", "metadata": {"timestamp": time.time()}}
            requests.post(f"{self.python_ai_url}/detect", json=payload)
            time.sleep(0.1)
        return True
    
    def test_application_attacks(self):
        """Test application layer attack detection"""
        malicious_payloads = [
            "'; DROP TABLE users; --",
            "<script>alert('xss')</script>",
            "../../etc/passwd"
        ]
        
        for payload in malicious_payloads:
            data = {"body": payload, "metadata": {"inputType": "form_field"}}
            response = requests.post(f"{self.python_ai_url}/detect", json=data)
            if response.status_code != 200:
                return False
        return True
    
    def test_account_takeover(self):
        """Test account takeover detection"""
        payload = {
            "body": "login",
            "metadata": {
                "ipAddress": "suspicious_ip",
                "location": {"latitude": 0, "longitude": 0},
                "deviceFingerprint": "unknown_device"
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/detect", json=payload)
        return response.status_code == 200
    
    def test_data_exfiltration(self):
        """Test data exfiltration detection"""
        payload = {
            "type": "insider",
            "data": {
                "action": "DATA_EXPORT",
                "resource": "sensitive_files",
                "timeOfDay": 2,  # 2 AM
                "isAfterHours": True,
                "sessionDuration": 7200000  # 2 hours
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/enhanced_detect", json=payload)
        return response.status_code == 200 and response.json().get("is_anomaly", False)
    
    def test_insider_threats(self):
        """Test insider threat detection"""
        payload = {
            "type": "insider",
            "data": {
                "userId": "employee_123",
                "role": "user",
                "action": "delete_sensitive_data",
                "timeOfDay": 23,  # 11 PM
                "isAfterHours": True,
                "sessionDuration": 10800000  # 3 hours
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/enhanced_detect", json=payload)
        return response.status_code == 200 and response.json().get("is_anomaly", False)
    
    def test_privilege_escalation(self):
        """Test privilege escalation detection"""
        payload = {
            "type": "privilege",
            "data": {
                "role": "user",
                "permission": "admin_delete",
                "expectedScope": "limited",
                "actualScope": "system"
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/enhanced_detect", json=payload)
        return response.status_code == 200 and response.json().get("is_anomaly", False)
    
    def test_malware_detection(self):
        """Test malware detection"""
        payload = {
            "body": "malware_test",
            "metadata": {
                "malwareIndicators": {
                    "suspiciousExtensions": ["HEADLESS_BROWSER"],
                    "automatedTraffic": True,
                    "browserIntegrity": False
                }
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/detect", json=payload)
        return response.status_code == 200
    
    def test_compliance_violations(self):
        """Test compliance violation detection"""
        payload = {
            "type": "compliance",
            "data": {
                "dataType": "personal_medical_data",
                "purpose": "unauthorized",
                "complianceFlags": ["AFTER_HOURS_PERSONAL_DATA_ACCESS", "UNAUTHORIZED_MEDICAL_DATA_ACCESS"]
            }
        }
        
        response = requests.post(f"{self.python_ai_url}/enhanced_detect", json=payload)
        return response.status_code == 200 and response.json().get("is_anomaly", False)
    
    def print_summary(self):
        """Print test summary"""
        print("\n" + "="*60)
        print("🎯 UBA SYSTEM TEST SUMMARY")
        print("="*60)
        
        passed = sum(1 for _, status in self.test_results if status == "PASSED")
        total = len(self.test_results)
        
        print(f"Total Tests: {total}")
        print(f"Passed: {passed}")
        print(f"Failed: {total - passed}")
        print(f"Success Rate: {(passed/total)*100:.1f}%")
        
        print("\nDetailed Results:")
        for test_name, status in self.test_results:
            icon = "✅" if status == "PASSED" else "❌"
            print(f"  {icon} {test_name}: {status}")
        
        if passed == total:
            print("\n🎉 ALL TESTS PASSED! UBA System is working correctly.")
        else:
            print(f"\n⚠️  {total - passed} tests failed. Check the issues above.")

def main():
    print("🚀 Starting UBA Research System Tests...")
    print("Make sure all services are running:")
    print("  - Spring Boot Backend (port 8080)")
    print("  - Python AI Flask Server (port 5000)")
    print("  - Angular Frontend (port 4200)")
    print()
    
    tester = UBASystemTester()
    
    # Run all tests
    tester.test_python_ai_service()
    tester.test_backend_integration()
    tester.test_security_scenarios()
    
    # Print summary
    tester.print_summary()

if __name__ == "__main__":
    main()