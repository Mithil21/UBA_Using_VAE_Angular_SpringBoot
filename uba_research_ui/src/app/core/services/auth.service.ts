import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CryptoService, EncryptedEnvelope } from './crypto.service';
import { UbaTrackerService } from './uba-tracker.service';

export interface SecureRequestEnvelope<T> {
  payload: T;
  metadata: EncryptedEnvelope;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = 'http://localhost:8080/api/auth';
  private readonly cryptoUrl = 'http://localhost:8080/api/crypto';
  private cachedPublicKey: string | null = null;

  constructor(
    private http: HttpClient,
    private cryptoService: CryptoService,
    private ubaTracker: UbaTrackerService
  ) {}

  async fetchServerPublicKey(): Promise<string> {
    if (this.cachedPublicKey) return this.cachedPublicKey;
    this.cachedPublicKey = await firstValueFrom(
      this.http.get(`${this.cryptoUrl}/public-key`, { responseType: 'text' })
    );
    return this.cachedPublicKey!;
  }

  private async buildEnvelope<T extends object>(
    payload: T,
    page: string
  ): Promise<SecureRequestEnvelope<T>> {
    const publicKey = await this.fetchServerPublicKey();
    const ubaTelemetry = this.ubaTracker.flushBatch(page);
    const metadata = await this.cryptoService.encrypt(ubaTelemetry, publicKey);
    return { payload, metadata };
  }

  async register(data: RegisterRequest): Promise<unknown> {
    const envelope = await this.buildEnvelope(data, 'register');
    return firstValueFrom(this.http.post(`${this.baseUrl}/register`, envelope));
  }

  async login(data: LoginRequest): Promise<unknown> {
    const envelope = await this.buildEnvelope(data, 'login');
    return firstValueFrom(this.http.post(`${this.baseUrl}/login`, envelope));
  }
}
