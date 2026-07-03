import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { forkJoin, interval, Subscription } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';

interface Stats {
  totalRequests: number;
  totalAccepted: number;
  totalReview: number;
  totalRejected: number;
  deadLetterCount: number;
  fabricCommitted: number;
  fabricCoverage: number;
  stateDistribution: Record<string, number>;
  retryDistribution: Record<string, number>;
}

interface VaeRecord {
  recordId: string;
  email: string;
  decision: string;
  createdAt: string;
  vaeScore: number;
  mseScore: number;
  onLedger: boolean;
  fabricHash?: string;
  reviewLabel?: string;
  rejectionReason?: string;
}

interface VaeScores {
  accepted: Array<{decision: string; vaeScore: number; mseScore: number; email: string}>;
  review:   Array<{decision: string; vaeScore: number; mseScore: number; email: string}>;
  rejected: Array<{decision: string; vaeScore: number; mseScore: number; email: string}>;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
<div class="shell">

  <!-- ── Header ──────────────────────────────────────────────── -->
  <header class="topbar">
    <div class="brand">
      <svg class="shield" viewBox="0 0 24 24" fill="none" stroke="url(#sg)" stroke-width="1.6">
        <defs>
          <linearGradient id="sg" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#38bdf8"/>
            <stop offset="100%" stop-color="#818cf8"/>
          </linearGradient>
        </defs>
        <path d="M12 2L3 7v5c0 5.25 3.75 10.15 9 11.35C17.25 22.15 21 17.25 21 12V7L12 2z"/>
        <polyline points="9 12 11 14 15 10"/>
      </svg>
      <div>
        <div class="brand-name">ZeroTrust Forensics</div>
        <div class="brand-sub">Audit Dashboard</div>
      </div>
    </div>
    <div class="topbar-right">
      <div class="live-badge">
        <span class="pulse"></span> Live
      </div>
      <button class="logout-btn" (click)="logout()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="15" height="15">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
          <polyline points="16 17 21 12 16 7"/>
          <line x1="21" y1="12" x2="9" y2="12"/>
        </svg>
        Logout
      </button>
    </div>
  </header>

  <!-- ── Stat cards ───────────────────────────────────────────── -->
  <section class="cards" *ngIf="stats">
    <div class="card card--green">
      <div class="card-value">{{ stats.totalAccepted }}</div>
      <div class="card-label">Accepted</div>
      <div class="card-sub">Human users verified</div>
    </div>
    <div class="card card--amber">
      <div class="card-value">{{ stats.totalReview }}</div>
      <div class="card-label">Under Review</div>
      <div class="card-sub">Borderline — awaiting label</div>
    </div>
    <div class="card card--red">
      <div class="card-value">{{ stats.totalRejected }}</div>
      <div class="card-label">Rejected</div>
      <div class="card-sub">Bot attempts blocked</div>
    </div>
    <div class="card card--purple">
      <div class="card-value">{{ stats.deadLetterCount }}</div>
      <div class="card-label">Dead Letter</div>
      <div class="card-sub">Infrastructure failures</div>
    </div>
    <div class="card card--blue">
      <div class="card-value">{{ stats.fabricCommitted }}</div>
      <div class="card-label">On Ledger</div>
      <div class="card-sub">{{ stats.fabricCoverage }}% Fabric coverage</div>
    </div>
  </section>

  <!-- ── Charts row ───────────────────────────────────────────── -->
  <section class="charts" *ngIf="stats">

    <!-- Decision donut -->
    <div class="chart-card">
      <div class="chart-title">Decision Distribution</div>
      <div class="donut-wrap">
        <svg viewBox="0 0 120 120" class="donut">
          <ng-container *ngIf="donutSegments.length > 0">
            <circle *ngFor="let seg of donutSegments"
              cx="60" cy="60" r="45"
              fill="none"
              [attr.stroke]="seg.color"
              stroke-width="18"
              [attr.stroke-dasharray]="seg.dash"
              [attr.stroke-dashoffset]="seg.offset"
              stroke-linecap="butt"
            />
          </ng-container>
          <text x="60" y="55" text-anchor="middle" class="donut-num">{{ stats.totalRequests }}</text>
          <text x="60" y="70" text-anchor="middle" class="donut-label">total</text>
        </svg>
        <div class="donut-legend">
          <div class="legend-item"><span class="dot dot--green"></span> Accepted ({{ stats.totalAccepted }})</div>
          <div class="legend-item"><span class="dot dot--amber"></span> Review ({{ stats.totalReview }})</div>
          <div class="legend-item"><span class="dot dot--red"></span> Rejected ({{ stats.totalRejected }})</div>
          <div class="legend-item"><span class="dot dot--purple"></span> Dead letter ({{ stats.deadLetterCount }})</div>
        </div>
      </div>
    </div>

    <!-- Retry bar chart -->
    <div class="chart-card">
      <div class="chart-title">Retry Distribution</div>
      <div class="bar-chart">
        <div class="bar-row" *ngFor="let r of retryRows">
          <div class="bar-key">{{ r.retries }} {{ r.retries === 1 ? 'retry' : 'retries' }}</div>
          <div class="bar-track">
            <div class="bar-fill" [style.width.%]="r.pct" [style.background]="r.color"></div>
          </div>
          <div class="bar-val">{{ r.count }}</div>
        </div>
        <div class="bar-empty" *ngIf="retryRows.length === 0">No retry data yet</div>
      </div>
    </div>

    <!-- VAE score scatter -->
    <div class="chart-card chart-card--wide">
      <div class="chart-title">VAE Probability Scores</div>
      <div class="scatter-wrap">
        <div class="scatter-chart">
          <!-- threshold lines -->
          <div class="threshold-line" [style.left.%]="65" style="border-color:#34d399">
            <span class="threshold-label">0.65 Accept</span>
          </div>
          <div class="threshold-line" [style.left.%]="40" style="border-color:#f59e0b">
            <span class="threshold-label">0.40 Review</span>
          </div>
          <!-- dots -->
          <div *ngFor="let pt of scatterPoints"
               class="scatter-dot"
               [class.dot-accepted]="pt.decision === 'ACCEPTED'"
               [class.dot-review]="pt.decision === 'REVIEW'"
               [class.dot-rejected]="pt.decision === 'REJECTED'"
               [style.left.%]="pt.x"
               [style.top.%]="pt.y"
               [title]="pt.email + ' — ' + pt.decision + ' (' + fmt(pt.score, 4) + ')'">
          </div>
        </div>
        <div class="scatter-axis">
          <span>0.0</span>
          <span>0.25</span>
          <span>0.50</span>
          <span>0.75</span>
          <span>1.0</span>
        </div>
        <div class="scatter-legend">
          <span class="dot-accepted-leg">● Accepted</span>
          <span class="dot-review-leg">● Review</span>
          <span class="dot-rejected-leg">● Rejected</span>
        </div>
      </div>
    </div>

  </section>

  <!-- ── Tables ───────────────────────────────────────────────── -->
  <section class="tables">

    <!-- Accepted -->
    <div class="table-card">
      <div class="table-header">
        <span class="table-title">Accepted Registrations</span>
        <span class="badge badge--green">{{ accepted.length }}</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>VAE Score</th>
              <th>MSE</th>
              <th>Ledger</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of accepted">
              <td class="email-cell">{{ r.email }}</td>
              <td><span class="score score--green">{{ fmt(r.vaeScore, 4) }}</span></td>
              <td class="muted">{{ fmt(r.mseScore, 3) }}</td>
              <td>
                <span *ngIf="r.onLedger" class="ledger-badge ledger-badge--on" title="Committed to Fabric">✓ On chain</span>
                <span *ngIf="!r.onLedger" class="ledger-badge ledger-badge--off">⏳ Pending</span>
              </td>
              <td class="muted">{{ r.createdAt | slice:0:16 }}</td>
            </tr>
            <tr *ngIf="accepted.length === 0">
              <td colspan="5" class="empty">No accepted registrations yet</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Review -->
    <div class="table-card">
      <div class="table-header">
        <span class="table-title">Under Review</span>
        <span class="badge badge--amber">{{ review.length }}</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>VAE Score</th>
              <th>MSE</th>
              <th>Label</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of review">
              <td class="email-cell">{{ r.email }}</td>
              <td><span class="score score--amber">{{ fmt(r.vaeScore, 4) }}</span></td>
              <td class="muted">{{ fmt(r.mseScore, 3) }}</td>
              <td>
                <span class="review-label"
                  [class.label--pending]="r.reviewLabel === 'PENDING'"
                  [class.label--legit]="r.reviewLabel === 'LEGITIMATE'"
                  [class.label--bot]="r.reviewLabel === 'BOT'">
                  {{ r.reviewLabel }}
                </span>
              </td>
              <td class="muted">{{ r.createdAt | slice:0:16 }}</td>
            </tr>
            <tr *ngIf="review.length === 0">
              <td colspan="5" class="empty">No borderline cases yet</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Rejected -->
    <div class="table-card">
      <div class="table-header">
        <span class="table-title">Rejected Attempts</span>
        <span class="badge badge--red">{{ rejected.length }}</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>VAE Score</th>
              <th>MSE</th>
              <th>Reason</th>
              <th>Ledger</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of rejected">
              <td class="email-cell">{{ r.email }}</td>
              <td><span class="score score--red">{{ fmt(r.vaeScore, 4) }}</span></td>
              <td class="muted">{{ fmt(r.mseScore, 3) }}</td>
              <td class="muted">{{ r.rejectionReason }}</td>
              <td>
                <span *ngIf="r.onLedger" class="ledger-badge ledger-badge--on">✓ On chain</span>
                <span *ngIf="!r.onLedger" class="ledger-badge ledger-badge--off">⏳ Pending</span>
              </td>
            </tr>
            <tr *ngIf="rejected.length === 0">
              <td colspan="5" class="empty">No rejected attempts yet</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

  </section>

  <div class="loading" *ngIf="!stats">Loading audit data…</div>

</div>
  `,
  styles: [`
    :host { display:block; }

    .shell {
      min-height: 100vh;
      background: #0f172a;
      color: #e2e8f0;
      font-family: 'Segoe UI', system-ui, sans-serif;
      padding: 0 0 48px;
    }

    /* ── Topbar ── */
    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 32px;
      border-bottom: 1px solid #1e293b;
      background: rgba(15,23,42,0.95);
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(8px);
    }
    .brand { display:flex; align-items:center; gap:14px; }
    .shield { width:36px; height:36px; }
    .brand-name { font-size:16px; font-weight:700; color:#f1f5f9; }
    .brand-sub  { font-size:11px; color:#64748b; }
    .topbar-right { display:flex; align-items:center; gap:16px; }
    .live-badge {
      display:flex; align-items:center; gap:8px;
      font-size:12px; color:#34d399; font-weight:600;
    }
    .logout-btn {
      display:flex; align-items:center; gap:6px;
      padding:6px 14px; border-radius:8px; border:1px solid #334155;
      background:transparent; color:#94a3b8; font-size:12px; font-weight:600;
      cursor:pointer; transition:all 0.2s;
    }
    .logout-btn:hover { border-color:#f87171; color:#f87171; background:rgba(248,113,113,0.08); }
    .pulse {
      width:8px; height:8px; border-radius:50%;
      background:#34d399;
      animation: pulse 2s ease-in-out infinite;
    }
    @keyframes pulse {
      0%,100% { opacity:1; transform:scale(1); }
      50%      { opacity:0.4; transform:scale(0.8); }
    }

    /* ── Stat cards ── */
    .cards {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 16px;
      padding: 28px 32px 0;
    }
    .card {
      background: #1e293b;
      border-radius: 12px;
      padding: 20px;
      border-top: 3px solid transparent;
    }
    .card--green  { border-color: #34d399; }
    .card--amber  { border-color: #f59e0b; }
    .card--red    { border-color: #f87171; }
    .card--purple { border-color: #818cf8; }
    .card--blue   { border-color: #38bdf8; }
    .card-value { font-size:32px; font-weight:700; color:#f1f5f9; line-height:1; }
    .card-label { font-size:13px; font-weight:600; color:#94a3b8; margin-top:6px; }
    .card-sub   { font-size:11px; color:#475569; margin-top:3px; }

    /* ── Charts ── */
    .charts {
      display: grid;
      grid-template-columns: 1fr 1fr 2fr;
      gap: 16px;
      padding: 20px 32px 0;
    }
    .chart-card {
      background: #1e293b;
      border-radius: 12px;
      padding: 20px;
    }
    .chart-card--wide { grid-column: span 1; }
    .chart-title {
      font-size:13px; font-weight:600; color:#94a3b8;
      text-transform:uppercase; letter-spacing:0.05em;
      margin-bottom:16px;
    }

    /* Donut */
    .donut-wrap { display:flex; align-items:center; gap:20px; }
    .donut { width:120px; height:120px; transform:rotate(-90deg); flex-shrink:0; }
    .donut-num   { font-size:18px; font-weight:700; fill:#f1f5f9; transform:rotate(90deg); transform-origin:60px 60px; }
    .donut-label { font-size:9px;  fill:#64748b;   transform:rotate(90deg); transform-origin:60px 60px; }
    .donut-legend { display:flex; flex-direction:column; gap:8px; }
    .legend-item { display:flex; align-items:center; gap:8px; font-size:12px; color:#94a3b8; }
    .dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
    .dot--green  { background:#34d399; }
    .dot--amber  { background:#f59e0b; }
    .dot--red    { background:#f87171; }
    .dot--purple { background:#818cf8; }

    /* Bar chart */
    .bar-chart { display:flex; flex-direction:column; gap:12px; }
    .bar-row { display:flex; align-items:center; gap:10px; }
    .bar-key  { font-size:11px; color:#64748b; width:60px; flex-shrink:0; }
    .bar-track { flex:1; height:8px; background:#0f172a; border-radius:4px; overflow:hidden; }
    .bar-fill  { height:100%; border-radius:4px; transition:width 0.6s ease; }
    .bar-val   { font-size:12px; color:#94a3b8; width:20px; text-align:right; }
    .bar-empty { font-size:12px; color:#475569; text-align:center; padding:20px 0; }

    /* Scatter */
    .scatter-wrap { display:flex; flex-direction:column; gap:8px; }
    .scatter-chart {
      position:relative; height:100px;
      background:#0f172a; border-radius:8px;
      border: 1px solid #1e293b;
    }
    .threshold-line {
      position:absolute; top:0; bottom:0;
      border-left:1px dashed;
      opacity:0.5;
    }
    .threshold-label {
      position:absolute; top:4px; left:4px;
      font-size:9px; color:#94a3b8; white-space:nowrap;
    }
    .scatter-dot {
      position:absolute; width:10px; height:10px;
      border-radius:50%; transform:translate(-50%,-50%);
      cursor:pointer; transition:transform 0.2s;
    }
    .scatter-dot:hover { transform:translate(-50%,-50%) scale(1.5); }
    .dot-accepted { background:#34d399; }
    .dot-review   { background:#f59e0b; }
    .dot-rejected { background:#f87171; }
    .scatter-axis {
      display:flex; justify-content:space-between;
      font-size:10px; color:#475569; padding:0 4px;
    }
    .scatter-legend { display:flex; gap:16px; font-size:11px; }
    .dot-accepted-leg { color:#34d399; }
    .dot-review-leg   { color:#f59e0b; }
    .dot-rejected-leg { color:#f87171; }

    /* ── Tables ── */
    .tables {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px 32px 0;
    }
    .table-card {
      background: #1e293b;
      border-radius: 12px;
      overflow: hidden;
    }
    .table-header {
      display:flex; align-items:center; gap:10px;
      padding:16px 20px;
      border-bottom:1px solid #0f172a;
    }
    .table-title { font-size:13px; font-weight:600; color:#94a3b8; text-transform:uppercase; letter-spacing:0.05em; }
    .badge { padding:2px 8px; border-radius:999px; font-size:11px; font-weight:700; }
    .badge--green  { background:#064e3b; color:#34d399; }
    .badge--amber  { background:#451a03; color:#f59e0b; }
    .badge--red    { background:#4c1d1d; color:#f87171; }
    .table-scroll { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; font-size:13px; }
    th {
      text-align:left; padding:10px 20px;
      color:#64748b; font-size:11px; font-weight:600;
      text-transform:uppercase; letter-spacing:0.05em;
      border-bottom:1px solid #0f172a;
    }
    td { padding:12px 20px; border-bottom:1px solid rgba(255,255,255,0.03); }
    tr:last-child td { border-bottom:none; }
    tr:hover td { background:rgba(255,255,255,0.02); }
    .email-cell { color:#f1f5f9; font-weight:500; }
    .muted { color:#64748b; }
    .empty { text-align:center; color:#334155; padding:24px; }

    .score { padding:2px 8px; border-radius:6px; font-weight:600; font-size:12px; font-family:monospace; }
    .score--green  { background:#064e3b; color:#34d399; }
    .score--amber  { background:#451a03; color:#f59e0b; }
    .score--red    { background:#4c1d1d; color:#f87171; }

    .ledger-badge { font-size:11px; font-weight:600; padding:2px 8px; border-radius:6px; }
    .ledger-badge--on  { background:#0c2d1e; color:#34d399; }
    .ledger-badge--off { background:#1e293b; color:#64748b; border:1px solid #334155; }

    .review-label { font-size:11px; font-weight:600; padding:2px 8px; border-radius:6px; }
    .label--pending { background:#1e293b; color:#64748b; border:1px solid #334155; }
    .label--legit   { background:#064e3b; color:#34d399; }
    .label--bot     { background:#4c1d1d; color:#f87171; }

    .loading { text-align:center; color:#475569; padding:80px; font-size:14px; }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {

  stats:    Stats | null = null;
  accepted: VaeRecord[]  = [];
  review:   VaeRecord[]  = [];
  rejected: VaeRecord[]  = [];
  scores:   VaeScores | null = null;

  donutSegments: any[] = [];
  retryRows:     any[] = [];
  scatterPoints: any[] = [];

  private sub: Subscription | null = null;
  private readonly API = '/api/dashboard';

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() {
    this.sub = new Subscription();
    this.sub.add(this.loadAll().subscribe());
    this.sub.add(
      interval(10000).pipe(
        switchMap(() => this.loadAll())
      ).subscribe()
    );
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  private loadAll() {
    return forkJoin({
      stats:    this.http.get<Stats>(`${this.API}/stats`),
      accepted: this.http.get<VaeRecord[]>(`${this.API}/accepted`),
      review:   this.http.get<VaeRecord[]>(`${this.API}/review`),
      rejected: this.http.get<VaeRecord[]>(`${this.API}/rejected`),
      scores:   this.http.get<VaeScores>(`${this.API}/vae-scores`),
    }).pipe(
      tap(data => {
        this.stats    = data.stats;
        this.accepted = data.accepted;
        this.review   = data.review;
        this.rejected = data.rejected;
        this.scores   = data.scores;
        this.buildCharts();
        this.buildScatter();
      })
    );
  }

  logout() {
    this.router.navigate(['/login']);
  }

  fmt(v: unknown, digits: number): string {
    return (+(v ?? 0)).toFixed(digits);
  }

  private buildCharts() {
    if (!this.stats) return;
    this.buildDonut();
    this.buildRetryBars();
  }

  private buildDonut() {
    const s = this.stats!;
    const total = s.totalAccepted + s.totalReview + s.totalRejected + s.deadLetterCount;
    if (total === 0) { this.donutSegments = []; return; }

    const circ = 2 * Math.PI * 45;
    const segments = [
      { value: s.totalAccepted,   color: '#34d399' },
      { value: s.totalReview,     color: '#f59e0b' },
      { value: s.totalRejected,   color: '#f87171' },
      { value: s.deadLetterCount, color: '#818cf8' },
    ];

    let offset = 0;
    this.donutSegments = segments.map(seg => {
      const pct  = seg.value / total;
      const dash = `${pct * circ} ${circ}`;
      const result = { color: seg.color, dash, offset: -offset };
      offset += pct * circ;
      return result;
    });
  }

  private buildRetryBars() {
    if (!this.stats) return;
    const dist = this.stats.retryDistribution;
    const max  = Math.max(...Object.values(dist), 1);
    const colors = ['#34d399', '#f59e0b', '#f87171', '#818cf8'];

    this.retryRows = Object.entries(dist).map(([key, count], i) => ({
      retries: parseInt(key),
      count,
      pct:   (count / max) * 100,
      color: colors[Math.min(i, colors.length - 1)]
    })).sort((a, b) => a.retries - b.retries);
  }

  private buildScatter() {
    if (!this.scores) return;
    const scores = this.scores;
    type ScoreEntry = { decision: string; vaeScore: number; mseScore: number; email: string };
    const all: ScoreEntry[] = [
      ...scores.accepted.map(p => ({ ...p, decision: 'ACCEPTED' as string })),
      ...scores.review.map(p =>   ({ ...p, decision: 'REVIEW'   as string })),
      ...scores.rejected.map(p => ({ ...p, decision: 'REJECTED' as string })),
    ];
    this.scatterPoints = all.map((p, i) => ({
      decision: p.decision,
      email:    p.email,
      score:    p.vaeScore,
      x:        p.vaeScore * 100,
      y:        20 + (i % 5) * 15,
    }));
  }
}
