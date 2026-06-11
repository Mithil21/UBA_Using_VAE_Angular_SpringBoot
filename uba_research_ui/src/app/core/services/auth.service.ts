import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CryptoService, EncryptedEnvelope } from './crypto.service';
import { UbaTrackerService } from './uba-tracker.service';
import { SessionStore } from './session-store.service';

export interface SecureRequestEnvelope<T> {
  payload: T;
  metadata: EncryptedEnvelope;
}

export interface RegisterRequest { username: string; email: string; password: string; }
export interface LoginRequest    { username: string; password: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl   = '/api/auth';
  private readonly cryptoUrl = '/api/crypto';
  private cachedPublicKey: string | null = null;

  constructor(
    private http: HttpClient,
    private cryptoService: CryptoService,
    private ubaTracker: UbaTrackerService,
    private sessionStore: SessionStore
  ) {}

  async fetchServerPublicKey(): Promise<string> {
    if (this.cachedPublicKey) return this.cachedPublicKey;
    this.cachedPublicKey = await firstValueFrom(
      this.http.get(`${this.cryptoUrl}/public-key`, { responseType: 'text' })
    );
    return this.cachedPublicKey!;
  }

  private async buildAndSend<T extends object>(
    payload: T,
    page: string,
    endpoint: string
  ): Promise<string> {
    const ubaTelemetry = this.ubaTracker.flushBatch(page);
    const publicKey    = await this.fetchServerPublicKey();
    const encrypted    = await this.cryptoService.encrypt(ubaTelemetry, payload, publicKey);
    const envelope: SecureRequestEnvelope<T> = { payload, metadata: encrypted };

    console.group(`%c🚀 ENVELOPE SENT TO BACKEND — ${page.toUpperCase()}`, 'color:#38bdf8;font-weight:700;font-size:14px');
    console.log('%c📋 payload  (plaintext)', 'color:#818cf8;font-weight:600');
    console.log(JSON.stringify(envelope.payload, null, 2));
    console.log('%c🔒 metadata  (encrypted)', 'color:#f59e0b;font-weight:600');
    console.log(JSON.stringify(envelope.metadata, null, 2));
    console.log('%c📦 full envelope (copy-pasteable)', 'color:#38bdf8;font-weight:600');
    console.log(JSON.stringify(envelope, null, 2));
    console.groupEnd();

    return firstValueFrom(this.http.post(endpoint, envelope, { responseType: 'text' }));
  }

  async register(data: RegisterRequest): Promise<string> {
    return this.buildAndSend(data, 'register', `${this.baseUrl}/register`);
  }

  async login(data: LoginRequest): Promise<string> {
    // Peek telemetry BEFORE flush so dashboard can show it
    const telemetrySnapshot = this.ubaTracker.peekTelemetry();

    const ubaTelemetry = this.ubaTracker.flushBatch('login');
    const publicKey    = await this.fetchServerPublicKey();
    const encrypted    = await this.cryptoService.encrypt(ubaTelemetry, data, publicKey);
    const envelope: SecureRequestEnvelope<LoginRequest> = { payload: data, metadata: encrypted };

    console.group('%c🚀 ENVELOPE SENT TO BACKEND — LOGIN', 'color:#38bdf8;font-weight:700;font-size:14px');
    console.log('%c📋 payload  (plaintext)', 'color:#818cf8;font-weight:600');
    console.log(JSON.stringify(envelope.payload, null, 2));
    console.log('%c🔒 metadata  (encrypted)', 'color:#f59e0b;font-weight:600');
    console.log(JSON.stringify(envelope.metadata, null, 2));
    console.log('%c📦 full envelope (copy-pasteable)', 'color:#38bdf8;font-weight:600');
    console.log(JSON.stringify(envelope, null, 2));
    console.groupEnd();

    const serverMessage = await firstValueFrom(
      this.http.post(`${this.baseUrl}/login`, envelope, { responseType: 'text' })
    );

    // Store snapshot for dashboard
    this.sessionStore.snapshot = {
      username:          data.username,
      serverMessage,
      loginTime:         Date.now(),
      telemetry:         telemetrySnapshot,
      encryptedEnvelope: encrypted,
    };

    return serverMessage;
  }
}
