import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UbaTrackerService } from '../../../core/services/uba-tracker.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form (ngSubmit)="onSubmit()">
      <h2>Register</h2>

      <input
        id="username"
        data-uba="register-username"
        type="text"
        placeholder="Username"
        [(ngModel)]="form.username"
        name="username"
        required
      />

      <input
        id="email"
        data-uba="register-email"
        type="email"
        placeholder="Email"
        [(ngModel)]="form.email"
        name="email"
        required
      />

      <input
        id="password"
        data-uba="register-password"
        type="password"
        placeholder="Password"
        [(ngModel)]="form.password"
        name="password"
        required
      />

      <p *ngIf="error" class="error">{{ error }}</p>
      <button type="submit" data-uba="register-submit" [disabled]="loading">
        {{ loading ? 'Registering…' : 'Register' }}
      </button>
    </form>
  `,
})
export class RegisterComponent {
  form = { username: '', email: '', password: '' };
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
      await this.authService.register(this.form);
      this.router.navigate(['/login']);
    } catch (err: unknown) {
      this.error = err instanceof Error ? err.message : 'Registration failed.';
    } finally {
      this.loading = false;
    }
  }
}
