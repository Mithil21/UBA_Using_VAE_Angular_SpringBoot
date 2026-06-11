import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SessionStore, LoginSnapshot } from '../../core/services/session-store.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dash-shell" *ngIf="snap; else noSession">

      <!-- ── Top bar ── -->
      <header class="topbar">
        <div class="topbar__brand">
          <div class="topbar__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="url(#dg)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <defs>
                <linearGradient id="dg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%"   stop-color="#38bdf8"/>
                  <stop offset="100%" stop-color="#818cf8"/>
                </linearGradient>
              </defs>
              <path d="M12 2L3 7v5c0 5.25 3.75 10.15 9 11.35C17.25 22.15 21 17.25 21 12V7L12 2z"/>
              <polyline points="9 12 11 14 15 10"/>
            </svg>
          </div>
          <span>ZeroTrust Forensics</span>
        </div>
        <div class="topbar__meta">
          <span class="pill pill--green">● Authenticated</span>
          <span class="topbar__session">Session&nbsp;{{ shortId(snap.telemetry.sessionId) }}</span>
        </div>
      </header>

      <!-- ── Welcome hero ── -->
      <section class="hero">
        <div class="hero__glow"></div>
        <div class="hero__content">
          <div class="hero__avatar">{{ initial(snap.username) }}</div>
          <div>
            <h1 class="hero__title">Welcome back, <span class="grad">{{ snap.username }}</span></h1>
            <p class="hero__sub">{{ snap.serverMessage }} &nbsp;·&nbsp; {{ snap.loginTime | date:'medium' }}</p>
          </div>
        </div>
      </section>

      <!-- ── Stat cards ── -->
      <section class="stats">
        <div class="stat-card">
          <div class="stat-card__icon cyan">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ (snap.telemetry.pageDwellTime / 1000).toFixed(1) }}s</div>
            <div class="stat-card__label">Time on login page</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon purple">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="17 10 12 5 7 10"/><line x1="12" y1="5" x2="12" y2="19"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ snap.telemetry.keystrokes.length }}</div>
            <div class="stat-card__label">Keystrokes recorded</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon blue">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M4.93 4.93a10 10 0 0 0 0 14.14"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ snap.telemetry.mouseEvents.length }}</div>
            <div class="stat-card__label">Mouse events</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon amber">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 8h1a4 4 0 0 1 0 8h-1"/><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"/><line x1="6" y1="1" x2="6" y2="4"/><line x1="10" y1="1" x2="10" y2="4"/><line x1="14" y1="1" x2="14" y2="4"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ snap.telemetry.clicks.length }}</div>
            <div class="stat-card__label">Click events</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon red">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ snap.telemetry.tabSwitchCount }}</div>
            <div class="stat-card__label">Tab switches</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon green">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">AES-256</div>
            <div class="stat-card__label">Encryption used</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-card__icon red">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></svg>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__val">{{ snap.telemetry.clipboardAttempts.length }}</div>
            <div class="stat-card__label">Clipboard attempts</div>
          </div>
        </div>
      </section>

      <!-- ── Timeline ── -->
      <section class="panel">
        <h2 class="panel__title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          Session Timeline
        </h2>
        <div class="timeline">
          <div class="tl-item tl-item--info">
            <div class="tl-dot"></div>
            <div class="tl-body">
              <div class="tl-title">Page loaded</div>
              <div class="tl-meta">Login form opened &nbsp;·&nbsp; {{ snap.telemetry.location || 'Location resolving…' }}</div>
              <div class="tl-time">{{ (snap.loginTime - snap.telemetry.pageDwellTime) | date:'HH:mm:ss' }}</div>
            </div>
          </div>

          <div class="tl-item tl-item--cyan" *ngIf="snap.telemetry.keystrokes.length">
            <div class="tl-dot"></div>
            <div class="tl-body">
              <div class="tl-title">Typing started</div>
              <div class="tl-meta">First keystroke on <code>{{ snap.telemetry.keystrokes[0].target }}</code></div>
              <div class="tl-time">{{ snap.telemetry.keystrokes[0].timestamp | date:'HH:mm:ss' }}</div>
            </div>
          </div>

          <div class="tl-item tl-item--amber" *ngFor="let nav of snap.telemetry.pageNavigations">
            <div class="tl-dot"></div>
            <div class="tl-body">
              <div class="tl-title">{{ navLabel(nav.type) }}</div>
              <div class="tl-meta">While on <code>{{ nav.fromPage }}</code> page</div>
              <div class="tl-time">{{ nav.timestamp | date:'HH:mm:ss' }}</div>
            </div>
          </div>

          <div class="tl-item tl-item--purple">
            <div class="tl-dot"></div>
            <div class="tl-body">
              <div class="tl-title">UBA payload encrypted</div>
              <div class="tl-meta">AES-GCM 256-bit key wrapped with RSA-OAEP · nonce injected</div>
              <div class="tl-time">{{ snap.loginTime | date:'HH:mm:ss' }}</div>
            </div>
          </div>

          <div class="tl-item tl-item--green">
            <div class="tl-dot"></div>
            <div class="tl-body">
              <div class="tl-title">Authentication successful</div>
              <div class="tl-meta">{{ snap.serverMessage }}</div>
              <div class="tl-time">{{ snap.loginTime | date:'HH:mm:ss' }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- ── Two column: keystroke log + encrypted envelope ── -->
      <div class="two-col">

        <!-- Keystroke log -->
        <section class="panel">
          <h2 class="panel__title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            Keystroke Log
            <span class="badge-count">{{ snap.telemetry.keystrokes.length }}</span>
          </h2>
          <div class="scroll-table">
            <table>
              <thead><tr><th>Type</th><th>Key</th><th>Target</th><th>Time</th></tr></thead>
              <tbody>
                <tr *ngFor="let k of snap.telemetry.keystrokes">
                  <td><span class="pill" [class]="k.type === 'keydown' ? 'pill--cyan' : 'pill--purple'">{{ k.type }}</span></td>
                  <td><code>{{ k.key }}</code></td>
                  <td class="muted">{{ k.target }}</td>
                  <td class="muted">{{ k.timestamp | date:'HH:mm:ss.SSS' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="empty" *ngIf="!snap.telemetry.keystrokes.length">No keystrokes recorded</div>
          </div>
        </section>

        <!-- Encrypted envelope -->
        <section class="panel">
          <h2 class="panel__title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            Encrypted Envelope Sent
          </h2>
          <div class="env-block">
            <div class="env-label">encryptedData</div>
            <div class="env-val">{{ snap.encryptedEnvelope.encryptedData | slice:0:80 }}…</div>
            <div class="env-label mt">encryptedAesKey</div>
            <div class="env-val">{{ snap.encryptedEnvelope.encryptedAesKey | slice:0:80 }}…</div>
            <div class="env-label mt">iv</div>
            <div class="env-val">{{ snap.encryptedEnvelope.iv }}</div>
          </div>
        </section>

      </div>

      <!-- ── Mouse events ── -->
      <section class="panel">
        <h2 class="panel__title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M4.93 4.93a10 10 0 0 0 0 14.14"/></svg>
          Mouse Events
          <span class="badge-count">{{ snap.telemetry.mouseEvents.length }}</span>
        </h2>
        <div class="scroll-table">
          <table>
            <thead><tr><th>Type</th><th>Target</th><th>X</th><th>Y</th><th>Time</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of snap.telemetry.mouseEvents">
                <td><span class="pill pill--blue">{{ m.type }}</span></td>
                <td class="muted">{{ m.target }}</td>
                <td class="muted">{{ m.x }}</td>
                <td class="muted">{{ m.y }}</td>
                <td class="muted">{{ m.timestamp | date:'HH:mm:ss.SSS' }}</td>
              </tr>
            </tbody>
          </table>
          <div class="empty" *ngIf="!snap.telemetry.mouseEvents.length">No mouse events recorded</div>
        </div>
      </section>

      <!-- ── Clipboard attempts ── -->
      <section class="panel">
        <h2 class="panel__title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></svg>
          Clipboard Attempts
          <span class="badge-count" [style.background]="snap.telemetry.clipboardAttempts.length ? 'rgba(248,113,113,0.15)' : ''" [style.color]="snap.telemetry.clipboardAttempts.length ? '#f87171' : ''">{{ snap.telemetry.clipboardAttempts.length }}</span>
        </h2>
        <div class="scroll-table">
          <table>
            <thead><tr><th>Type</th><th>Target</th><th>Attempted Text</th><th>Status</th><th>Time</th></tr></thead>
            <tbody>
              <tr *ngFor="let c of snap.telemetry.clipboardAttempts">
                <td><span class="pill" [class]="c.type === 'copy' ? 'pill--amber' : 'pill--purple'">{{ c.type }}</span></td>
                <td class="muted">{{ c.target }}</td>
                <td><code>{{ c.attemptedText || '(empty)' }}</code></td>
                <td><span class="pill pill--red">blocked</span></td>
                <td class="muted">{{ c.timestamp | date:'HH:mm:ss.SSS' }}</td>
              </tr>
            </tbody>
          </table>
          <div class="empty" *ngIf="!snap.telemetry.clipboardAttempts.length">No clipboard attempts recorded</div>
        </div>
      </section>

      <!-- ── Footer ── -->
      <footer class="dash-footer">
        <span>IP: <strong>{{ snap.telemetry.ipAddress || '—' }}</strong></span>
        <span>Location: <strong>{{ snap.telemetry.location || '—' }}</strong></span>
        <span>Session: <code>{{ snap.telemetry.sessionId }}</code></span>
      </footer>

    </div>

    <ng-template #noSession>
      <div class="auth-shell">
        <div class="auth-card" style="text-align:center">
          <p style="color:var(--text-muted);margin-bottom:24px">No active session found.</p>
          <button class="btn-submit" style="width:auto;padding:0 32px" (click)="router.navigate(['/login'])">
            <span>Go to Login</span>
          </button>
        </div>
      </div>
    </ng-template>
  `,
  styles: [`
    .dash-shell {
      min-height: 100vh;
      padding-bottom: 48px;
      position: relative;
      z-index: 1;
    }

    /* ── Topbar ── */
    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 32px;
      border-bottom: 1px solid rgba(56,189,248,0.1);
      background: rgba(5,10,20,0.8);
      backdrop-filter: blur(20px);
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .topbar__brand { display:flex; align-items:center; gap:12px; font-weight:700; font-size:15px; letter-spacing:-0.2px; color:#e2e8f0; }
    .topbar__icon  { width:32px; height:32px; }
    .topbar__icon svg { width:100%; height:100%; }
    .topbar__meta  { display:flex; align-items:center; gap:16px; }
    .topbar__session { font-family:'JetBrains Mono',monospace; font-size:12px; color:#475569; }

    /* ── Pills ── */
    .pill { display:inline-flex; align-items:center; padding:3px 10px; border-radius:99px; font-size:11px; font-weight:600; letter-spacing:0.3px; }
    .pill--green  { background:rgba(52,211,153,0.12); color:#34d399; border:1px solid rgba(52,211,153,0.25); }
    .pill--cyan   { background:rgba(56,189,248,0.12); color:#38bdf8; border:1px solid rgba(56,189,248,0.25); }
    .pill--purple { background:rgba(129,140,248,0.12); color:#818cf8; border:1px solid rgba(129,140,248,0.25); }
    .pill--blue   { background:rgba(99,102,241,0.12); color:#a5b4fc; border:1px solid rgba(99,102,241,0.25); }
    .pill--amber  { background:rgba(251,191,36,0.12); color:#fbbf24; border:1px solid rgba(251,191,36,0.25); }
    .pill--red    { background:rgba(248,113,113,0.12); color:#f87171; border:1px solid rgba(248,113,113,0.25); }

    /* ── Hero ── */
    .hero { position:relative; overflow:hidden; padding:48px 32px 40px; margin-bottom:0; }
    .hero__glow { position:absolute; top:-60px; left:50%; transform:translateX(-50%); width:600px; height:300px; background:radial-gradient(ellipse at center, rgba(56,189,248,0.08) 0%, transparent 70%); pointer-events:none; }
    .hero__content { display:flex; align-items:center; gap:24px; max-width:1100px; margin:0 auto; position:relative; }
    .hero__avatar  { width:64px; height:64px; border-radius:50%; background:linear-gradient(135deg,#0ea5e9,#6366f1); display:flex; align-items:center; justify-content:center; font-size:26px; font-weight:700; color:#fff; flex-shrink:0; box-shadow:0 0 32px rgba(56,189,248,0.3); }
    .hero__title   { font-size:28px; font-weight:700; letter-spacing:-0.5px; color:#e2e8f0; }
    .hero__sub     { font-size:14px; color:#64748b; margin-top:6px; }
    .grad { background:linear-gradient(135deg,#38bdf8,#818cf8); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }

    /* ── Stat cards ── */
    .stats { display:grid; grid-template-columns:repeat(auto-fill,minmax(200px,1fr)); gap:16px; padding:0 32px 32px; max-width:1100px; margin:0 auto; }
    .stat-card { background:rgba(10,20,40,0.6); border:1px solid rgba(56,189,248,0.1); border-radius:16px; padding:20px; display:flex; align-items:center; gap:16px; backdrop-filter:blur(12px); transition:border-color 0.25s; }
    .stat-card:hover { border-color:rgba(56,189,248,0.28); }
    .stat-card__icon { width:44px; height:44px; border-radius:12px; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
    .stat-card__icon svg { width:22px; height:22px; }
    .stat-card__icon.cyan   { background:rgba(56,189,248,0.12); color:#38bdf8; }
    .stat-card__icon.purple { background:rgba(129,140,248,0.12); color:#818cf8; }
    .stat-card__icon.blue   { background:rgba(99,102,241,0.12);  color:#a5b4fc; }
    .stat-card__icon.amber  { background:rgba(251,191,36,0.12);  color:#fbbf24; }
    .stat-card__icon.red    { background:rgba(248,113,113,0.12); color:#f87171; }
    .stat-card__icon.green  { background:rgba(52,211,153,0.12);  color:#34d399; }
    .stat-card__val   { font-size:22px; font-weight:700; color:#e2e8f0; line-height:1; }
    .stat-card__label { font-size:12px; color:#64748b; margin-top:4px; }

    /* ── Panels ── */
    .panel { background:rgba(10,20,40,0.6); border:1px solid rgba(56,189,248,0.1); border-radius:16px; padding:24px; backdrop-filter:blur(12px); max-width:1100px; margin:0 auto 24px; }
    .panel { margin-left:32px; margin-right:32px; }
    .panel__title { font-size:15px; font-weight:600; color:#94a3b8; display:flex; align-items:center; gap:10px; margin-bottom:20px; }
    .panel__title svg { width:18px; height:18px; color:#38bdf8; }
    .badge-count { margin-left:auto; background:rgba(56,189,248,0.12); color:#38bdf8; border-radius:99px; padding:2px 10px; font-size:12px; font-weight:600; }

    /* ── Timeline ── */
    .timeline { display:flex; flex-direction:column; gap:0; }
    .tl-item { display:flex; gap:16px; padding:12px 0; border-left:2px solid rgba(56,189,248,0.1); margin-left:8px; padding-left:20px; position:relative; }
    .tl-dot { position:absolute; left:-7px; top:16px; width:12px; height:12px; border-radius:50%; border:2px solid; }
    .tl-item--info   .tl-dot { background:#1e3a5f; border-color:#38bdf8; }
    .tl-item--cyan   .tl-dot { background:#0f3040; border-color:#06b6d4; }
    .tl-item--amber  .tl-dot { background:#3d2d00; border-color:#fbbf24; }
    .tl-item--purple .tl-dot { background:#2d1f5e; border-color:#818cf8; }
    .tl-item--green  .tl-dot { background:#0f2d1e; border-color:#34d399; }
    .tl-body { flex:1; }
    .tl-title { font-size:14px; font-weight:600; color:#e2e8f0; }
    .tl-meta  { font-size:13px; color:#64748b; margin-top:2px; }
    .tl-meta code { font-family:'JetBrains Mono',monospace; color:#38bdf8; background:rgba(56,189,248,0.08); padding:1px 6px; border-radius:4px; font-size:12px; }
    .tl-time  { font-size:11px; font-family:'JetBrains Mono',monospace; color:#334155; margin-top:4px; }

    /* ── Two col ── */
    .two-col { display:grid; grid-template-columns:1fr 1fr; gap:24px; max-width:1100px; margin:0 32px 24px; }
    .two-col .panel { margin:0; }

    /* ── Tables ── */
    .scroll-table { overflow-x:auto; max-height:280px; overflow-y:auto; }
    table { width:100%; border-collapse:collapse; font-size:13px; }
    thead th { text-align:left; padding:8px 12px; color:#475569; font-weight:600; font-size:11px; text-transform:uppercase; letter-spacing:0.5px; border-bottom:1px solid rgba(56,189,248,0.08); position:sticky; top:0; background:rgba(10,20,40,0.95); }
    tbody tr:hover { background:rgba(56,189,248,0.03); }
    tbody td { padding:8px 12px; border-bottom:1px solid rgba(255,255,255,0.03); color:#e2e8f0; }
    td.muted { color:#64748b; }
    td code { font-family:'JetBrains Mono',monospace; color:#38bdf8; font-size:12px; }
    .empty { text-align:center; color:#334155; padding:24px; font-size:13px; }

    /* ── Encrypted envelope block ── */
    .env-block { background:rgba(0,0,0,0.3); border-radius:10px; padding:16px; }
    .env-label { font-size:11px; font-weight:600; text-transform:uppercase; letter-spacing:1px; color:#475569; }
    .env-label.mt { margin-top:14px; }
    .env-val { font-family:'JetBrains Mono',monospace; font-size:11px; color:#38bdf8; word-break:break-all; margin-top:4px; line-height:1.6; opacity:0.8; }

    /* ── Footer ── */
    .dash-footer { display:flex; justify-content:center; gap:32px; flex-wrap:wrap; padding:24px 32px 0; border-top:1px solid rgba(56,189,248,0.08); font-size:12px; color:#334155; max-width:1100px; margin:0 auto; }
    .dash-footer strong { color:#64748b; }
    .dash-footer code { font-family:'JetBrains Mono',monospace; color:#38bdf8; font-size:11px; }

    /* ── Responsive ── */
    @media (max-width: 768px) {
      .two-col { grid-template-columns:1fr; }
      .stats { grid-template-columns:repeat(2,1fr); }
      .hero__title { font-size:20px; }
      .panel, .two-col { margin-left:16px; margin-right:16px; }
      .stats { padding:0 16px 24px; }
    }
  `],
})
export class DashboardComponent implements OnInit {
  snap: LoginSnapshot | null = null;

  constructor(public router: Router, private sessionStore: SessionStore) {}

  ngOnInit(): void {
    this.snap = this.sessionStore.snapshot;
    if (!this.snap) this.router.navigate(['/login']);
  }

  initial(name: string): string {
    return (name?.[0] ?? '?').toUpperCase();
  }

  shortId(id: string): string {
    return id ? id.slice(0, 8).toUpperCase() : '';
  }

  navLabel(type: string): string {
    const map: Record<string, string> = {
      'tab-hidden':   'Switched away (tab hidden)',
      'tab-visible':  'Returned to tab',
      'window-blur':  'Window lost focus',
      'window-focus': 'Window regained focus',
    };
    return map[type] ?? type;
  }
}
