import json
import subprocess
import sys

def test_vae_model():
    # Test data
    test_data = {
        "username": "testuser",
        "email": "test@example.com",
        "age": 25,
        "isActive": True
    }
    
    timestamp = "2024-01-01T12:00:00Z"
    
    try:
        # Call the VAE model
        result = subprocess.run([
            sys.executable, "vae_model.py", 
            json.dumps(test_data), 
            timestamp
        ], capture_output=True, text=True, cwd=".")
        
        if result.returncode == 0:
            response = json.loads(result.stdout)
            print("VAE Model Test Results:")
            print(f"Is Malicious: {response.get('is_malicious', 'N/A')}")
            print(f"Anomaly Score: {response.get('anomaly_score', 'N/A')}")
            print(f"Threshold: {response.get('threshold', 'N/A')}")
            print(f"Timestamp: {response.get('timestamp', 'N/A')}")
        else:
            print(f"Error: {result.stderr}")
            
    except Exception as e:
        print(f"Test failed: {e}")

if __name__ == "__main__":
    test_vae_model()