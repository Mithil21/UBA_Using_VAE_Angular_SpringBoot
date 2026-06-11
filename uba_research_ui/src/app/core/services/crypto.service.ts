import { Injectable } from '@angular/core';

export interface EncryptedEnvelope {
  encryptedData: string;
  encryptedAesKey: string;
  iv: string;
}

@Injectable({ providedIn: 'root' })
export class CryptoService {
  private toBase64(buffer: ArrayBuffer): string {
    return btoa(String.fromCharCode(...new Uint8Array(buffer)));
  }

  private pemToBuffer(pem: string): ArrayBuffer {
    const b64 = pem
      .replace(/-----BEGIN PUBLIC KEY-----/, '')
      .replace(/-----END PUBLIC KEY-----/, '')
      .replace(/\s/g, '');
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  async encrypt(payload: object, rsaPublicKeyPem: string): Promise<EncryptedEnvelope> {
    const enrichedPayload = {
      ...payload,
      timestamp: Date.now(),
      nonce: crypto.randomUUID(),
    };

    const plaintext = new TextEncoder().encode(JSON.stringify(enrichedPayload));

    // Generate ephemeral AES-GCM 256-bit key + 12-byte IV
    const aesKey = await crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );
    const iv = crypto.getRandomValues(new Uint8Array(12));

    // Encrypt payload with AES-GCM
    const encryptedData = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      plaintext
    );

    // Import RSA public key
    const rsaCryptoKey = await crypto.subtle.importKey(
      'spki',
      this.pemToBuffer(rsaPublicKeyPem),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['wrapKey']
    );

    // Wrap AES key with RSA-OAEP
    const encryptedAesKey = await crypto.subtle.wrapKey('raw', aesKey, rsaCryptoKey, {
      name: 'RSA-OAEP',
    });

    return {
      encryptedData: this.toBase64(encryptedData),
      encryptedAesKey: this.toBase64(encryptedAesKey),
      iv: this.toBase64(iv.buffer),
    };
  }
}
