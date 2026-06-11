import { Injectable, OnDestroy } from '@angular/core';

export interface KeystrokeEvent {
  type: 'keydown' | 'keyup';
  target: string;
  key: string;
  timestamp: number;
}

export interface ClickEvent {
  target: string;
  timestamp: number;
}

export interface ClipboardEvent_ {
  type: 'copy' | 'paste';
  target: string;
  timestamp: number;
}

export interface UbaTelemetry {
  ipAddress: string;
  location: { city: string; region: string; country: string };
  timeSpent: number;
  page: string;
  keystrokes: KeystrokeEvent[];
  clicks: ClickEvent[];
  clipboard: ClipboardEvent_[];
}

@Injectable({ providedIn: 'root' })
export class UbaTrackerService implements OnDestroy {
  private keystrokes: KeystrokeEvent[] = [];
  private clicks: ClickEvent[] = [];
  private clipboard: ClipboardEvent_[] = [];
  private pageEntryTime = Date.now();
  private ipAddress = '';
  private location = { city: '', region: '', country: '' };

  private boundKeydown = this.onKeydown.bind(this);
  private boundKeyup = this.onKeyup.bind(this);
  private boundClick = this.onClick.bind(this);
  private boundCopy = this.onClipboard.bind(this, 'copy');
  private boundPaste = this.onClipboard.bind(this, 'paste');

  constructor() {
    this.initListeners();
    this.fetchGeoInfo();
  }

  private initListeners(): void {
    document.addEventListener('keydown', this.boundKeydown, true);
    document.addEventListener('keyup', this.boundKeyup, true);
    document.addEventListener('click', this.boundClick, true);
    document.addEventListener('copy', this.boundCopy, true);
    document.addEventListener('paste', this.boundPaste, true);
  }

  private resolveTarget(event: Event): string {
    const el = event.target as HTMLElement;
    return el.getAttribute('data-uba') || el.id || el.tagName.toLowerCase();
  }

  private isPasswordField(event: Event): boolean {
    return (event.target as HTMLInputElement).type === 'password';
  }

  private onKeydown(event: KeyboardEvent): void {
    this.keystrokes.push({
      type: 'keydown',
      target: this.resolveTarget(event),
      key: this.isPasswordField(event) ? 'MASKED' : event.key,
      timestamp: Date.now(),
    });
  }

  private onKeyup(event: KeyboardEvent): void {
    this.keystrokes.push({
      type: 'keyup',
      target: this.resolveTarget(event),
      key: this.isPasswordField(event) ? 'MASKED' : event.key,
      timestamp: Date.now(),
    });
  }

  private onClick(event: MouseEvent): void {
    this.clicks.push({ target: this.resolveTarget(event), timestamp: Date.now() });
  }

  private onClipboard(type: 'copy' | 'paste', event: Event): void {
    this.clipboard.push({ type, target: this.resolveTarget(event), timestamp: Date.now() });
  }

  private async fetchGeoInfo(): Promise<void> {
    try {
      const res = await fetch('https://ipapi.co/json/');
      const data = await res.json();
      this.ipAddress = data.ip ?? '';
      this.location = { city: data.city ?? '', region: data.region ?? '', country: data.country_name ?? '' };
    } catch {
      // silently fail — geo info remains empty defaults
    }
  }

  resetPageTimer(): void {
    this.pageEntryTime = Date.now();
  }

  flushBatch(currentPage: string): UbaTelemetry {
    const telemetry: UbaTelemetry = {
      ipAddress: this.ipAddress,
      location: { ...this.location },
      timeSpent: Date.now() - this.pageEntryTime,
      page: currentPage,
      keystrokes: [...this.keystrokes],
      clicks: [...this.clicks],
      clipboard: [...this.clipboard],
    };
    this.keystrokes = [];
    this.clicks = [];
    this.clipboard = [];
    this.pageEntryTime = Date.now();
    return telemetry;
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown', this.boundKeydown, true);
    document.removeEventListener('keyup', this.boundKeyup, true);
    document.removeEventListener('click', this.boundClick, true);
    document.removeEventListener('copy', this.boundCopy, true);
    document.removeEventListener('paste', this.boundPaste, true);
  }
}
