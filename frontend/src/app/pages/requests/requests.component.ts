import { Component } from '@angular/core';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-requests',
  imports: [PageHeaderComponent],
  template: `
    <app-page-header title="Farmer requests" description="Messages from farmers on the farms in your scope." />
    <article class="card">
      <p class="empty-state">No farmer has sent a request.</p>
    </article>
  `
})
export class RequestsComponent {}
