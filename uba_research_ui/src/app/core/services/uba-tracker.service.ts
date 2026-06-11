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
  button?: number;
  timestamp: number;
}

export interface ClipboardEvent_ {
  type: 'copy' | 'paste';
  target: string;
  attemptedText: string;
  blocked: true;
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
  clipboardAttempts: ClipboardEvent_[];
  pageNavigations: PageSwitchEvent[];
}

@Injectable({ providedIn: 'root' })
export class UbaTrackerService implements OnDestroy {
  private sessionId         = crypto.randomUUID();
  private currentPage       = '';
  private keystrokes:        KeystrokeEvent[]  = [];
  private mouseEvents:       MouseEvent_[]     = [];
  private clicks:            ClickEvent[]      = [];
  private clipboardAttempts: ClipboardEvent_[] = [];
  private pageNavigations:   PageSwitchEvent[] = [];
  private tabSwitchCount    = 0;
  private windowBlurCount   = 0;
  private pageEntryTime     = Date.now();
  private ipAddress         = '';
  private location          = '';
  private moveThrottle      = 0;
  private overThrottle      = 0;

  private boundKeydown       = this.onKeydown.bind(this);
  private boundKeyup         = this.onKeyup.bind(this);
  private boundMousedown     = this.onMousedown.bind(this);
  private boundMouseup       = this.onMouseup.bind(this);
  private boundMousemove     = this.onMousemove.bind(this);
  private boundMouseover     = this.onMouseover.bind(this);
  private boundClick         = this.onClick.bind(this);
  private boundCopy          = this.onCopy.bind(this);
  private boundPaste         = this.onPaste.bind(this);
  private boundVisibility    = this.onVisibilityChange.bind(this);
  private boundBlur          = this.onWindowBlur.bind(this);
  private boundFocus         = this.onWindowFocus.bind(this);
  private boundContextMenu   = this.onContextMenu.bind(this);
  private boundKeydownSecure = this.onKeydownSecure.bind(this);
  private boundSelectStart   = this.onSelectStart.bind(this);

  constructor() {
    // ── UBA tracking ─────────────────────────────────────────────────────
    document.addEventListener('keydown',          this.boundKeydown,       true);
    document.addEventListener('keyup',            this.boundKeyup,         true);
    document.addEventListener('mousedown',        this.boundMousedown,     true);
    document.addEventListener('mouseup',          this.boundMouseup,       true);
    document.addEventListener('mousemove',        this.boundMousemove,     true);
    document.addEventListener('mouseover',        this.boundMouseover,     true);
    document.addEventListener('click',            this.boundClick,         true);
    document.addEventListener('copy',             this.boundCopy,          true);
    document.addEventListener('paste',            this.boundPaste,         true);
    document.addEventListener('visibilitychange', this.boundVisibility);
    window.addEventListener('blur',  this.boundBlur);
    window.addEventListener('focus', this.boundFocus);

    // ── Security lockdown ─────────────────────────────────────────────────
    document.addEventListener('contextmenu',  this.boundContextMenu);
    document.addEventListener('keydown',      this.boundKeydownSecure);
    document.addEventListener('selectstart',  this.boundSelectStart);

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

  // ── Security lockdown handlers ────────────────────────────────────────────

  private onContextMenu(event: Event): void {
    event.preventDefault();
  }

  private onSelectStart(event: Event): void {
    const el = event.target as HTMLElement;
    // allow selection inside input/textarea, block everywhere else
    if (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA') {
      event.preventDefault();
    }
  }

  private onKeydownSecure(event: KeyboardEvent): void {
    const k = event.key.toLowerCase();
    const ctrl = event.ctrlKey || event.metaKey;

    // Block F12, Ctrl+Shift+I/J/C/U, Ctrl+U, Ctrl+S, Ctrl+A (outside inputs)
    if (event.key === 'F12') { event.preventDefault(); return; }
    if (ctrl && event.shiftKey && ['i','j','c'].includes(k)) { event.preventDefault(); return; }
    if (ctrl && k === 'u') { event.preventDefault(); return; }
    if (ctrl && k === 's') { event.preventDefault(); return; }

    const el = event.target as HTMLElement;
    const isInput = el.tagName === 'INPUT' || el.tagName === 'TEXTAREA';

    // Block Ctrl+A outside inputs
    if (ctrl && k === 'a' && !isInput) { event.preventDefault(); return; }
    // Block Ctrl+C / Ctrl+X outside inputs
    if (ctrl && (k === 'c' || k === 'x') && !isInput) { event.preventDefault(); return; }
    // Block Ctrl+V outside inputs
    if (ctrl && k === 'v' && !isInput) { event.preventDefault(); return; }
  }

  // ── Clipboard capture (before blocking) ──────────────────────────────────

  private onCopy(event: ClipboardEvent): void {
    const selected = window.getSelection()?.toString() ?? '';
    this.clipboardAttempts.push({
      type:          'copy',
      target:        this.resolveTarget(event),
      attemptedText: this.isPasswordField(event) ? 'MASKED' : selected,
      blocked:       true,
      timestamp:     Date.now(),
    });
    event.preventDefault();
  }

  private onPaste(event: ClipboardEvent): void {
    const text = event.clipboardData?.getData('text') ?? '';
    const el   = event.target as HTMLInputElement;
    this.clipboardAttempts.push({
      type:          'paste',
      target:        this.resolveTarget(event),
      attemptedText: el.type === 'password' ? 'MASKED' : text,
      blocked:       true,
      timestamp:     Date.now(),
    });
    event.preventDefault();
  }

  // ── Keyboard ──────────────────────────────────────────────────────────────

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
    this.mouseEvents.push({ type: 'mousedown', target: this.resolveTarget(event), x: event.clientX, y: event.clientY, button: event.button, timestamp: Date.now() });
  }

  private onMouseup(event: MouseEvent): void {
    this.mouseEvents.push({ type: 'mouseup', target: this.resolveTarget(event), x: event.clientX, y: event.clientY, button: event.button, timestamp: Date.now() });
  }

  private onMousemove(event: MouseEvent): void {
    const now = Date.now();
    if (now - this.moveThrottle < 200) return;
    this.moveThrottle = now;
    this.mouseEvents.push({ type: 'mousemove', target: this.resolveTarget(event), x: event.clientX, y: event.clientY, timestamp: now });
  }

  private onMouseover(event: MouseEvent): void {
    const now = Date.now();
    if (now - this.overThrottle < 300) return;
    this.overThrottle = now;
    this.mouseEvents.push({ type: 'mouseover', target: this.resolveTarget(event), x: event.clientX, y: event.clientY, timestamp: now });
  }

  private onClick(event: MouseEvent): void {
    this.clicks.push({ target: this.resolveTarget(event), x: event.clientX, y: event.clientY, timestamp: Date.now() });
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
    this.pageNavigations.push({ type: 'window-blur', fromPage: this.currentPage, timestamp: Date.now() });
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
    this.currentPage       = page;
    this.pageEntryTime     = Date.now();
    this.keystrokes        = [];
    this.mouseEvents       = [];
    this.clicks            = [];
    this.clipboardAttempts = [];
    this.pageNavigations   = [];
    this.tabSwitchCount    = 0;
    this.windowBlurCount   = 0;
  }

  peekTelemetry(): UbaTelemetry {
    return {
      sessionId:         this.sessionId,
      ipAddress:         this.ipAddress,
      location:          this.location,
      pageDwellTime:     Date.now() - this.pageEntryTime,
      tabSwitchCount:    this.tabSwitchCount,
      windowBlurCount:   this.windowBlurCount,
      keystrokes:        [...this.keystrokes],
      mouseEvents:       [...this.mouseEvents],
      clicks:            [...this.clicks],
      clipboardAttempts: [...this.clipboardAttempts],
      pageNavigations:   [...this.pageNavigations],
    };
  }

  flushBatch(page: string): UbaTelemetry {
    const telemetry: UbaTelemetry = {
      sessionId:         this.sessionId,
      ipAddress:         this.ipAddress,
      location:          this.location,
      pageDwellTime:     Date.now() - this.pageEntryTime,
      tabSwitchCount:    this.tabSwitchCount,
      windowBlurCount:   this.windowBlurCount,
      keystrokes:        [...this.keystrokes],
      mouseEvents:       [...this.mouseEvents],
      clicks:            [...this.clicks],
      clipboardAttempts: [...this.clipboardAttempts],
      pageNavigations:   [...this.pageNavigations],
    };
    this.resetPageTimer(page);
    return telemetry;
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown',          this.boundKeydown,       true);
    document.removeEventListener('keyup',            this.boundKeyup,         true);
    document.removeEventListener('mousedown',        this.boundMousedown,     true);
    document.removeEventListener('mouseup',          this.boundMouseup,       true);
    document.removeEventListener('mousemove',        this.boundMousemove,     true);
    document.removeEventListener('mouseover',        this.boundMouseover,     true);
    document.removeEventListener('click',            this.boundClick,         true);
    document.removeEventListener('copy',             this.boundCopy,          true);
    document.removeEventListener('paste',            this.boundPaste,         true);
    document.removeEventListener('visibilitychange', this.boundVisibility);
    document.removeEventListener('contextmenu',      this.boundContextMenu);
    document.removeEventListener('keydown',          this.boundKeydownSecure);
    document.removeEventListener('selectstart',      this.boundSelectStart);
    window.removeEventListener('blur',  this.boundBlur);
    window.removeEventListener('focus', this.boundFocus);
  }
}
