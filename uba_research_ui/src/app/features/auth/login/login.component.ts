import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UbaTrackerService } from '../../../core/services/uba-tracker.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form (ngSubmit)="onSubmit()">
      <h2>Login</h2>

      <input
        id="username"
        data-uba="login-username"
        type="text"
        placeholder="Username"
        [(ngModel)]="form.username"
        name="username"
        required
      />

      <input
        id="password"
        data-uba="login-password"
        type="password"
        placeholder="Password"
        [(ngModel)]="form.password"
        name="password"
        required
      />

      <p *ngIf="error" class="error">{{ error }}</p>
      <button type="submit" data-uba="login-submit" [disabled]="loading">
        {{ loading ? 'Logging in…' : 'Login' }}
      </button>
    </form>
  `,
})
export class LoginComponent {
  form = { username: '', password: '' };
  loading = false;
  error = '';

  constructor(
    private authService: AuthService,
    private ubaTracker: UbaTrackerService,
    private router: Router
  ) {
    this.ubaTracker.resetPageTimer();
  }

  async onSubmit(): Promise<void> {
    this.loading = true;
    this.error = '';
    try {
      await this.authService.login(this.form);
      this.router.navigate(['/dashboard']);
    } catch (err: unknown) {
      this.error = err instanceof Error ? err.message : 'Login failed.';
    } finally {
      this.loading = false;
    }
  }
}
