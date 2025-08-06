/**
 * Backend Decoder for Stealth UBA Data
 * Node.js/Express middleware to decode hidden metadata
 */

const crypto = require('crypto');

class UBADecoder {
  static xorDecrypt(encryptedText, key) {
    let result = '';
    for (let i = 0; i < encryptedText.length; i++) {
      result += String.fromCharCode(encryptedText.charCodeAt(i) ^ key.charCodeAt(i % key.length));
    }
    return result;
  }

  static decodeFromHeaders(headers) {
    try {
      // Reconstruct encrypted metadata from headers
      const encrypted = (headers['x-request-id'] || '') + 
                       (headers['x-session-token'] || '') + 
                       (headers['x-client-version'] || '') + 
                       (headers['x-trace-id'] || '');
      
      if (!encrypted) return null;
      
      // Decode base64
      const decoded = Buffer.from(encrypted, 'base64').toString();
      
      // Note: In production, you'd need to share the encryption key securely
      // For demo purposes, using a known key
      const decrypted = this.xorDecrypt(decoded, 'your-shared-secret-key');
      
      return JSON.parse(decrypted);
    } catch (error) {
      console.error('Failed to decode metadata from headers:', error);
      return null;
    }
  }

  static decodeFromPayload(body) {
    try {
      if (!body.clientVersion || !body.requestId || !body.sessionToken) {
        return null;
      }
      
      // Reconstruct encoded metadata
      const encoded = body.clientVersion + body.requestId + body.sessionToken + (body.checksum || '');
      
      // Decode base64
      const decoded = Buffer.from(encoded, 'base64').toString();
      
      return JSON.parse(decoded);
    } catch (error) {
      console.error('Failed to decode metadata from payload:', error);
      return null;
    }
  }

  static extractCleanPayload(body) {
    // Remove stealth fields to get original payload
    const { clientVersion, requestId, sessionToken, checksum, timestamp, locale, timezone, ...cleanPayload } = body;
    return cleanPayload;
  }
}

// Express middleware
function ubaStealthMiddleware(req, res, next) {
  // Try to decode metadata from headers first
  let metadata = UBADecoder.decodeFromHeaders(req.headers);
  
  // Fallback to payload decoding
  if (!metadata && req.body) {
    metadata = UBADecoder.decodeFromPayload(req.body);
  }
  
  if (metadata) {
    // Attach decoded metadata to request
    req.ubaBehaviorData = metadata;
    
    // Clean the payload
    req.body = UBADecoder.extractCleanPayload(req.body || {});
    
    console.log('🔍 UBA Metadata decoded:', {
      keysPressed: metadata.keysPressed?.length || 0,
      mouseClicks: metadata.mouseClicks?.length || 0,
      timeSpent: metadata.timeSpent,
      location: metadata.location?.locationName
    });
  }
  
  next();
}

// Usage example
const express = require('express');
const app = express();

app.use(express.json());
app.use(ubaStealthMiddleware);

app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body; // Clean payload
  const behaviorData = req.ubaBehaviorData; // Hidden metadata
  
  console.log('Login attempt:', { email });
  console.log('Behavior analysis:', behaviorData);
  
  // Fraud detection
  if (behaviorData) {
    const suspiciousActivity = analyzeBehavior(behaviorData);
    if (suspiciousActivity) {
      return res.status(403).json({ error: 'Suspicious activity detected' });
    }
  }
  
  // Normal login process
  res.json({ success: true, message: 'Login successful' });
});

function analyzeBehavior(metadata) {
  if (!metadata) return false;
  
  // Bot detection logic
  if (metadata.timeSpent < 2000) return true; // Too fast
  if (!metadata.keysPressed || metadata.keysPressed.length === 0) return true; // No typing
  if (!metadata.mouseClicks || metadata.mouseClicks.length === 0) return true; // No clicks
  
  return false;
}

module.exports = { UBADecoder, ubaStealthMiddleware };