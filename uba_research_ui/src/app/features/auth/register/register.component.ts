import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UbaTrackerService } from '../../../core/services/uba-tracker.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="auth-shell">
      <div class="auth-card">

        <!-- Brand -->
        <div class="brand">
          <div class="brand-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="url(#sg)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <defs>
                <linearGradient id="sg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%"   stop-color="#38bdf8"/>
                  <stop offset="100%" stop-color="#818cf8"/>
                </linearGradient>
              </defs>
              <path d="M12 2L3 7v5c0 5.25 3.75 10.15 9 11.35C17.25 22.15 21 17.25 21 12V7L12 2z"/>
              <polyline points="9 12 11 14 15 10"/>
            </svg>
          </div>
          <div>
            <div class="brand-title">ZeroTrust Forensics</div>
            <div class="brand-subtitle">Blockchain Audit System</div>
          </div>
        </div>

        <!-- Heading -->
        <div class="form-heading">
          <span class="dot"></span> Create your account
        </div>

        <!-- Form -->
        <form (ngSubmit)="onSubmit()" autocomplete="off">

          <div class="field">
            <input
              id="email"
              data-uba="register-email"
              type="text"
              placeholder="Email"
              [(ngModel)]="form.email"
              name="email"
              autocomplete="off"
              required
            />
            <label for="email">Email address</label>
          </div>

          <div class="field">
            <input
              id="password"
              data-uba="register-password"
              [type]="showPassword ? 'text' : 'password'"
              placeholder="Password"
              [(ngModel)]="form.password"
              name="password"
              autocomplete="new-password"
              (input)="calcStrength()"
              required
            />
            <label for="password">Password</label>
            <button type="button" class="toggle-pw" (click)="showPassword = !showPassword" tabindex="-1">
              <svg *ngIf="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
              </svg>
              <svg *ngIf="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
              </svg>
            </button>
            <!-- Strength bar -->
            <div class="strength-bar" *ngIf="form.password.length > 0">
              <span [class]="strengthClass(0)"></span>
              <span [class]="strengthClass(1)"></span>
              <span [class]="strengthClass(2)"></span>
              <span [class]="strengthClass(3)"></span>
            </div>
          </div>

          <!-- Error -->

          <button class="btn-submit" type="submit" data-uba="register-submit" [disabled]="loading">
            <span>
              <span class="spinner" *ngIf="loading"></span>
              {{ loading ? 'Creating account…' : 'Create account' }}
            </span>
          </button>
        </form>

        <div class="divider">OR</div>

        <div class="auth-footer">
          Already have an account? <a (click)="router.navigate(['/login'])">Sign in</a>
        </div>

        <div class="security-badges">
          <div class="badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            AES-256
          </div>
          <div class="badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            RSA-OAEP
          </div>
          <div class="badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            Zero-Trust
          </div>
        </div>

      </div>
    </div>
  `,
})
export class RegisterComponent {
  form = { username: '', email: '', password: '' };
  loading = false;
  showPassword = false;
  private strength = 0;

  constructor(
    public router: Router,
    private authService: AuthService,
    private ubaTracker: UbaTrackerService,
    private notify: NotificationService
  ) {
    this.ubaTracker.resetPageTimer('register');
  }

  calcStrength(): void {
    const p = this.form.password;
    let score = 0;
    if (p.length >= 8) score++;
    if (/[A-Z]/.test(p)) score++;
    if (/[0-9]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;
    this.strength = score;
  }

  strengthClass(index: number): string {
    if (index >= this.strength) return '';
    if (this.strength <= 1) return 'weak';
    if (this.strength <= 2) return 'medium';
    return 'strong';
  }

  async onSubmit(): Promise<void> {
    this.loading = true;
    try {
      await this.authService.register(this.form);
      this.notify.success('Registration submitted! Please check your email.');
      this.form = { username: '', email: '', password: '' };
      this.strength = 0;
    } catch (err: unknown) {
      const httpErr = err as { error?: string; status?: number; message?: string };
      this.form = { username: '', email: '', password: '' };
      this.strength = 0;
      this.notify.error(httpErr.error ?? httpErr.message ?? 'Registration failed.');
    } finally {
      this.loading = false;
    }
  }
}
