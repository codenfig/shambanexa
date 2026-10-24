import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { Settings } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-help',
  imports: [PageHeaderComponent],
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
