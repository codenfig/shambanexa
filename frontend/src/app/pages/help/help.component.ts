import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Settings } from '../../core/models';

@Component({
  selector: 'app-help',
  imports: [RouterLink],
  templateUrl: './help.component.html',
  styleUrl: './help.component.css'
})
export class HelpComponent {
  private api = inject(ApiService);
  status = signal<Settings | null>(null);
  statusError = signal(false);

  constructor() {
    this.api.settings().subscribe({
      next: settings => this.status.set(settings),
      error: () => this.statusError.set(true)
    });
  }
}
