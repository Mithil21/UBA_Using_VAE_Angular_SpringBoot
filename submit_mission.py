import hmac
import hashlib
import time
import struct
import json
import urllib.request
import urllib.error
import ssl  # <--- Added this

# --- CONFIGURATION ---
EMAIL = "mithil.baria@postgrad.manchester.ac.uk"  
GIST_URL = "https://gist.github.com/Mithil21/65cd6fec63b74a3c5b29c7cac4106e7a" # <--- PASTE YOUR GIST URL HERE AGAIN
LANGUAGE = "python"
# ---------------------

def get_totp_password(userid):
    # 1. Construct the Secret
    secret_str = userid + "HENNGECHALLENGE004"
    secret_bytes = secret_str.encode('ascii')

    # 2. Calculate Time Steps
    time_step = 30
    counter = int(time.time() / time_step)
    counter_bytes = struct.pack(">Q", counter)

    # 3. Calculate HMAC-SHA-512
    digest = hmac.new(secret_bytes, counter_bytes, hashlib.sha512).digest()

    # 4. Dynamic Truncation
    offset = digest[-1] & 0x0F
    code_bytes = digest[offset : offset + 4]
    code_int = struct.unpack(">I", code_bytes)[0]
    code_int &= 0x7FFFFFFF

    # 5. Modulo for 10 digits
    totp = code_int % 10_000_000_000
    return f"{totp:010d}"

def send_request():
    url = "https://api.challenge.hennge.com/challenges/backend-recursion/004"
    
    data = {
        "github_url": GIST_URL,
        "contact_email": EMAIL,
        "solution_language": LANGUAGE
    }
    json_data = json.dumps(data).encode('utf-8')

    password = get_totp_password(EMAIL)
    
    req = urllib.request.Request(url, data=json_data, method="POST")
    req.add_header("Content-Type", "application/json")
    
    import base64
    auth_str = f"{EMAIL}:{password}"
    auth_b64 = base64.b64encode(auth_str.encode('ascii')).decode('ascii')
    req.add_header("Authorization", f"Basic {auth_b64}")

    print(f"Sending request for: {EMAIL}")
    print(f"TOTP Password used: {password}")
    
    # --- FIX FOR SSL ERROR ---
    # Create an unverified SSL context to bypass the certificate check
    ctx = ssl._create_unverified_context()
    # -------------------------

    try:
        # Pass the context here
        with urllib.request.urlopen(req, context=ctx) as response:
            print(f"Status: {response.status}")
            print("Response Body:")
            print(response.read().decode('utf-8'))
            print("\nSUCCESS! Check your email.")
    except urllib.error.HTTPError as e:
        print(f"Failed with HTTP {e.code}")
        print(e.read().decode('utf-8'))
    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    send_request()