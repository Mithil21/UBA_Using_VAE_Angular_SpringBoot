import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, Notification } from '../../core/services/notification.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      <div
        *ngFor="let n of notificationService.notifications$ | async; trackBy: trackById"
        class="toast"
        [class]="'toast toast--' + n.type"
      >
        <div class="toast__icon">
          <!-- success -->
          <svg *ngIf="n.type === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><polyline points="9 12 11 14 15 10"/>
          </svg>
          <!-- error -->
          <svg *ngIf="n.type === 'error'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><circle cx="12" cy="16" r="0.5" fill="currentColor"/>
          </svg>
          <!-- info -->
          <svg *ngIf="n.type === 'info'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><circle cx="12" cy="8" r="0.5" fill="currentColor"/>
          </svg>
        </div>
        <span class="toast__msg">{{ n.message }}</span>
        <button class="toast__close" (click)="notificationService.dismiss(n.id)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
        <div class="toast__bar"></div>
      </div>
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      top: 24px;
      right: 24px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 12px;
      pointer-events: none;
    }

    .toast {
      pointer-events: all;
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 300px;
      max-width: 400px;
      padding: 14px 16px;
      border-radius: 12px;
      border: 1px solid transparent;
      backdrop-filter: blur(20px);
      font-family: 'Inter', sans-serif;
      font-size: 14px;
      font-weight: 500;
      position: relative;
      overflow: hidden;
      animation: slideIn 0.35s cubic-bezier(0.4, 0, 0.2, 1) both;
    }

    @keyframes slideIn {
      from { opacity: 0; transform: translateX(110%); }
      to   { opacity: 1; transform: translateX(0); }
    }

    .toast--success {
      background: rgba(16, 40, 30, 0.85);
      border-color: rgba(52, 211, 153, 0.35);
      color: #34d399;
      box-shadow: 0 8px 32px rgba(52, 211, 153, 0.15), 0 0 0 1px rgba(52,211,153,0.08);
    }

    .toast--error {
      background: rgba(40, 16, 16, 0.88);
      border-color: rgba(248, 113, 113, 0.35);
      color: #f87171;
      box-shadow: 0 8px 32px rgba(248, 113, 113, 0.15), 0 0 0 1px rgba(248,113,113,0.08);
    }

    .toast--info {
      background: rgba(10, 25, 45, 0.88);
      border-color: rgba(56, 189, 248, 0.35);
      color: #38bdf8;
      box-shadow: 0 8px 32px rgba(56, 189, 248, 0.15), 0 0 0 1px rgba(56,189,248,0.08);
    }

    .toast__icon {
      flex-shrink: 0;
      width: 20px;
      height: 20px;
    }

    .toast__icon svg { width: 100%; height: 100%; }

    .toast__msg {
      flex: 1;
      line-height: 1.4;
      color: #e2e8f0;
    }

    .toast__close {
      flex-shrink: 0;
      width: 20px;
      height: 20px;
      background: none;
      border: none;
      cursor: pointer;
      color: #475569;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: color 0.2s;
    }

    .toast__close:hover { color: #e2e8f0; }
    .toast__close svg { width: 14px; height: 14px; }

    .toast__bar {
      position: absolute;
      bottom: 0;
      left: 0;
      height: 2px;
      width: 100%;
      animation: shrink 4s linear forwards;
      border-radius: 0 0 12px 12px;
    }

    .toast--success .toast__bar { background: #34d399; }
    .toast--error   .toast__bar { background: #f87171; }
    .toast--info    .toast__bar { background: #38bdf8; }

    @keyframes shrink {
      from { width: 100%; }
      to   { width: 0%; }
    }
  `],
})
export class NotificationsComponent {
  constructor(public notificationService: NotificationService) {}
  trackById(_: number, n: Notification): string { return n.id; }
}
