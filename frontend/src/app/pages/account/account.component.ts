import { Component, HostListener, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-account',
  imports: [FormsModule],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent {
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  session = this.auth.session;
  phone = signal('');
  currentPassword = signal('');
  newPassword = signal('');
  confirmPassword = signal('');
  showCurrent = signal(false);
  showNew = signal(false);
  showConfirm = signal(false);
  savingPhone = signal(false);
  savingPassword = signal(false);
  savingPhoto = signal(false);
  photoOpen = signal(false);
  preview = signal<string | null>(null);

  private phoneReady = false;

  constructor() {
    effect(() => {
      const session = this.session();
      if (!session || this.phoneReady) {
        return;
      }
      this.phone.set(session.phone ?? '');
      this.phoneReady = true;
      if (session.hasPhoto) {
        this.loadStoredPhoto();
      }
    });
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.photoOpen.set(false);
  }

  savePhone(): void {
    this.savingPhone.set(true);
    this.auth.updatePhone(this.phone().trim()).subscribe({
      next: session => {
        this.phone.set(session.phone ?? '');
        this.toast.show(session.phone ? 'Phone number saved.' : 'Phone number cleared.');
        this.savingPhone.set(false);
      },
      error: error => {
        this.toast.show(apiError(error, 'The phone number was not saved.'), 'err');
        this.savingPhone.set(false);
      }
    });
  }

  savePassword(): void {
    if (this.newPassword() !== this.confirmPassword()) {
      this.toast.show('The new password and confirmation do not match.', 'err');
      return;
    }
    this.savingPassword.set(true);
    this.auth.changePassword(this.currentPassword(), this.newPassword()).subscribe({
      next: () => {
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.toast.show('Password changed.');
        this.savingPassword.set(false);
      },
      error: error => {
        this.toast.show(apiError(error, 'The password was not changed.'), 'err');
        this.savingPassword.set(false);
      }
    });
  }

  onPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.toast.show('Use a JPEG, PNG, or WebP image.', 'err');
      return;
    }
    if (file.size > 2_000_000) {
      this.toast.show('Use an image of 2 MB or smaller.', 'err');
      return;
    }
    this.savingPhoto.set(true);
    this.auth.uploadPhoto(file).subscribe({
      next: () => {
        this.replacePreview(URL.createObjectURL(file));
        this.toast.show('Profile image saved.');
        this.savingPhoto.set(false);
      },
      error: error => {
        this.toast.show(apiError(error, 'The image was not saved.'), 'err');
        this.savingPhoto.set(false);
      }
    });
  }

  private loadStoredPhoto(): void {
    this.auth.loadPhoto().subscribe({
      next: blob => this.replacePreview(URL.createObjectURL(blob)),
      error: () => this.toast.show('The profile image could not be loaded.', 'err')
    });
  }

  private replacePreview(next: string): void {
    const previous = this.preview();
    if (previous) {
      URL.revokeObjectURL(previous);
    }
    this.preview.set(next);
  }
}
