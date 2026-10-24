import { Injectable, signal } from '@angular/core';

export interface ToastMessage {
  text: string;
  kind: 'ok' | 'err';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly current = signal<ToastMessage | null>(null);
  private timer = 0;

  show(text: string, kind: ToastMessage['kind'] = 'ok'): void {
    this.current.set({ text, kind });
    window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => this.current.set(null), 3200);
  }

  dismiss(): void {
    window.clearTimeout(this.timer);
    this.current.set(null);
  }
}
