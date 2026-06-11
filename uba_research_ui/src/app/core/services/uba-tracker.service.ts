import { Injectable, OnDestroy } from '@angular/core';

export interface KeystrokeEvent {
  key: string;
  type: 'keydown' | 'keyup';
  target: string;
  timestamp: number;
}

export interface MouseEvent_ {
  type: 'mousedown' | 'mouseup' | 'mousemove' | 'mouseover';
  target: string;
  x: number;
  y: number;
  button?: number;   // 0=left, 1=middle, 2=right — present on mousedown/mouseup
  timestamp: number;
}

export interface ClickEvent {
  target: string;
  x: number;
  y: number;
  timestamp: number;
}

export interface PageSwitchEvent {
  type: 'tab-hidden' | 'tab-visible' | 'window-blur' | 'window-focus';
  fromPage: string;
  timestamp: number;
}

export interface UbaTelemetry {
  sessionId: string;
  ipAddress: string;
  location: string;
  pageDwellTime: number;
  tabSwitchCount: number;
  windowBlurCount: number;
  keystrokes: KeystrokeEvent[];
  mouseEvents: MouseEvent_[];
  clicks: ClickEvent[];
  pageNavigations: PageSwitchEvent[];
}

@Injectable({ providedIn: 'root' })
export class UbaTrackerService implements OnDestroy {
  private sessionId       = crypto.randomUUID();
  private currentPage     = '';
  private keystrokes:      KeystrokeEvent[]  = [];
  private mouseEvents:     MouseEvent_[]     = [];
  private clicks:          ClickEvent[]      = [];
  private pageNavigations: PageSwitchEvent[] = [];
  private tabSwitchCount  = 0;
  private windowBlurCount = 0;
  private pageEntryTime   = Date.now();
  private ipAddress       = '';
  private location        = '';
  private moveThrottle    = 0;
  private overThrottle    = 0;

  private boundKeydown    = this.onKeydown.bind(this);
  private boundKeyup      = this.onKeyup.bind(this);
  private boundMousedown  = this.onMousedown.bind(this);
  private boundMouseup    = this.onMouseup.bind(this);
  private boundMousemove  = this.onMousemove.bind(this);
  private boundMouseover  = this.onMouseover.bind(this);
  private boundClick      = this.onClick.bind(this);
  private boundVisibility = this.onVisibilityChange.bind(this);
  private boundBlur       = this.onWindowBlur.bind(this);
  private boundFocus      = this.onWindowFocus.bind(this);

  constructor() {
    document.addEventListener('keydown',    this.boundKeydown,   true);
    document.addEventListener('keyup',      this.boundKeyup,     true);
    document.addEventListener('mousedown',  this.boundMousedown, true);
    document.addEventListener('mouseup',    this.boundMouseup,   true);
    document.addEventListener('mousemove',  this.boundMousemove, true);
    document.addEventListener('mouseover',  this.boundMouseover, true);
    document.addEventListener('click',      this.boundClick,     true);
    document.addEventListener('visibilitychange', this.boundVisibility);
    window.addEventListener('blur',  this.boundBlur);
    window.addEventListener('focus', this.boundFocus);
    this.fetchGeoInfo();
  }

  // ── Target resolution ────────────────────────────────────────────────────

  private resolveTarget(event: Event): string {
    const el = event.target as HTMLElement;
    return el.getAttribute('data-uba') || el.id || el.tagName.toLowerCase();
  }

  private isPasswordField(event: Event): boolean {
    return (event.target as HTMLInputElement).type === 'password';
  }

  // ── Keyboard ─────────────────────────────────────────────────────────────

  private onKeydown(event: KeyboardEvent): void {
    this.keystrokes.push({
      key:       this.isPasswordField(event) ? 'MASKED' : event.key,
      type:      'keydown',
      target:    this.resolveTarget(event),
      timestamp: Date.now(),
    });
  }

  private onKeyup(event: KeyboardEvent): void {
    this.keystrokes.push({
      key:       this.isPasswordField(event) ? 'MASKED' : event.key,
      type:      'keyup',
      target:    this.resolveTarget(event),
      timestamp: Date.now(),
    });
  }

  // ── Mouse ─────────────────────────────────────────────────────────────────

  private onMousedown(event: MouseEvent): void {
    this.mouseEvents.push({
      type:      'mousedown',
      target:    this.resolveTarget(event),
      x:         event.clientX,
      y:         event.clientY,
      button:    event.button,
      timestamp: Date.now(),
    });
  }

  private onMouseup(event: MouseEvent): void {
    this.mouseEvents.push({
      type:      'mouseup',
      target:    this.resolveTarget(event),
      x:         event.clientX,
      y:         event.clientY,
      button:    event.button,
      timestamp: Date.now(),
    });
  }

  private onMousemove(event: MouseEvent): void {
    const now = Date.now();
    if (now - this.moveThrottle < 200) return;
    this.moveThrottle = now;
    this.mouseEvents.push({
      type:      'mousemove',
      target:    this.resolveTarget(event),
      x:         event.clientX,
      y:         event.clientY,
      timestamp: now,
    });
  }

  private onMouseover(event: MouseEvent): void {
    const now = Date.now();
    if (now - this.overThrottle < 300) return;
    this.overThrottle = now;
    this.mouseEvents.push({
      type:      'mouseover',
      target:    this.resolveTarget(event),
      x:         event.clientX,
      y:         event.clientY,
      timestamp: now,
    });
  }

  private onClick(event: MouseEvent): void {
    this.clicks.push({
      target:    this.resolveTarget(event),
      x:         event.clientX,
      y:         event.clientY,
      timestamp: Date.now(),
    });
  }

  // ── Page / Tab switching ──────────────────────────────────────────────────

  private onVisibilityChange(): void {
    if (document.visibilityState === 'hidden') {
      this.tabSwitchCount++;
      this.pageNavigations.push({ type: 'tab-hidden',  fromPage: this.currentPage, timestamp: Date.now() });
    } else {
      this.pageNavigations.push({ type: 'tab-visible', fromPage: this.currentPage, timestamp: Date.now() });
    }
  }

  private onWindowBlur(): void {
    this.windowBlurCount++;
    this.pageNavigations.push({ type: 'window-blur',  fromPage: this.currentPage, timestamp: Date.now() });
  }

  private onWindowFocus(): void {
    this.pageNavigations.push({ type: 'window-focus', fromPage: this.currentPage, timestamp: Date.now() });
  }

  // ── Geo ───────────────────────────────────────────────────────────────────

  private async fetchGeoInfo(): Promise<void> {
    try {
      const res  = await fetch('https://ipapi.co/json/');
      const data = await res.json();
      this.ipAddress = data.ip ?? '';
      this.location  = [data.city, data.country_name].filter(Boolean).join(', ');
    } catch { /* silently fail */ }
  }

  // ── Public API ────────────────────────────────────────────────────────────

  resetPageTimer(page: string): void {
    this.currentPage    = page;
    this.pageEntryTime  = Date.now();
    this.keystrokes     = [];
    this.mouseEvents    = [];
    this.clicks         = [];
    this.pageNavigations = [];
    this.tabSwitchCount  = 0;
    this.windowBlurCount = 0;
  }

  peekTelemetry(): UbaTelemetry {
    return {
      sessionId:       this.sessionId,
      ipAddress:       this.ipAddress,
      location:        this.location,
      pageDwellTime:   Date.now() - this.pageEntryTime,
      tabSwitchCount:  this.tabSwitchCount,
      windowBlurCount: this.windowBlurCount,
      keystrokes:      [...this.keystrokes],
      mouseEvents:     [...this.mouseEvents],
      clicks:          [...this.clicks],
      pageNavigations: [...this.pageNavigations],
    };
  }

  flushBatch(page: string): UbaTelemetry {
    const telemetry: UbaTelemetry = {
      sessionId:       this.sessionId,
      ipAddress:       this.ipAddress,
      location:        this.location,
      pageDwellTime:   Date.now() - this.pageEntryTime,
      tabSwitchCount:  this.tabSwitchCount,
      windowBlurCount: this.windowBlurCount,
      keystrokes:      [...this.keystrokes],
      mouseEvents:     [...this.mouseEvents],
      clicks:          [...this.clicks],
      pageNavigations: [...this.pageNavigations],
    };
    this.resetPageTimer(page);
    return telemetry;
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown',           this.boundKeydown,    true);
    document.removeEventListener('keyup',             this.boundKeyup,      true);
    document.removeEventListener('mousedown',         this.boundMousedown,  true);
    document.removeEventListener('mouseup',           this.boundMouseup,    true);
    document.removeEventListener('mousemove',         this.boundMousemove,  true);
    document.removeEventListener('mouseover',         this.boundMouseover,  true);
    document.removeEventListener('click',             this.boundClick,      true);
    document.removeEventListener('visibilitychange',  this.boundVisibility);
    window.removeEventListener('blur',  this.boundBlur);
    window.removeEventListener('focus', this.boundFocus);
  }
}
