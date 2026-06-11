import { Injectable } from '@angular/core';
import { UbaTelemetry } from './uba-tracker.service';
import { EncryptedEnvelope } from './crypto.service';

export interface LoginSnapshot {
  username: string;
  serverMessage: string;
  loginTime: number;
  telemetry: UbaTelemetry;
  encryptedEnvelope: EncryptedEnvelope;
}

@Injectable({ providedIn: 'root' })
export class SessionStore {
  snapshot: LoginSnapshot | null = null;
}
