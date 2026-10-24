import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';

const EMAIL_KEY = 'shambanexa.email';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  error = signal('');
  notice = signal('');
  busy = signal(false);
  showPassword = signal(false);
  remember = signal(true);
  year = new Date().getFullYear();
  form = this.fb.nonNullable.group({
    email: [localStorage.getItem(EMAIL_KEY) || 'admin@shambanexa.com', [Validators.required, Validators.email]],
    password: ['Admin@123', Validators.required]
  });

  constructor() {
    if (this.auth.token()) {
      this.router.navigate(['/dashboard']);
    }
  }

  toggleRemember(event: Event): void {
    this.remember.set((event.target as HTMLInputElement).checked);
  }

  explainReset(): void {
    this.notice.set('Password reset is handled by your system administrator. This workspace does not email a reset link.');
  }

  explainPrivacy(): void {
    this.notice.set('Sign-in checks your tenant account on the server. A remembered session stays on this browser until you sign out.');
  }

  explainTerms(): void {
    this.notice.set('Access is limited to the farms on your role grant. A farm manager sees only those farms.');
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    if (this.remember()) {
      localStorage.setItem(EMAIL_KEY, email);
    } else {
      localStorage.removeItem(EMAIL_KEY);
    }
    this.auth.login(email, password, this.remember()).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        this.error.set(apiError(err, 'Could not sign in'));
        this.busy.set(false);
      }
    });
  }
}
