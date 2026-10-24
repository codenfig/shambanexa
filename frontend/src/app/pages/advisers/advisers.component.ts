import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Adviser } from '../../core/models';
import { apiError, relativeTime } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { MapCallout, TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

@Component({
  selector: 'app-advisers',
  imports: [PageHeaderComponent, TanzaniaMapComponent],
  templateUrl: './advisers.component.html',
  styleUrl: './advisers.component.css'
})
export class AdvisersComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  people = signal<Adviser[]>([]);
  loading = signal(true);
  error = signal('');
  talent = signal('');
  availability = signal('');
  loadFilter = signal('');
  pickedId = signal('');
  pickedPin = signal('');
  talentDraft = signal('');
  availabilityDraft = signal('');
  noteDraft = signal('');
  score = signal(0);
  ratingNote = signal('');
  recommendNote = signal('');
  savingProfile = signal(false);
  savingRating = signal(false);
  savingRecommendation = signal(false);
  profileError = signal('');
  actionError = signal('');
  when = relativeTime;
  canAdvise = computed(() => this.auth.session()?.canAdvise === true);

  visible = computed(() => {
    const talent = this.talent().trim().toLowerCase();
    return this.people().filter(person => {
      const talentMatch = !talent || person.talents.some(item => item.toLowerCase().includes(talent));
      const availabilityMatch = !this.availability() || person.availability === this.availability();
      const loadMatch = !this.loadFilter() || person.load === this.loadFilter();
      return talentMatch && availabilityMatch && loadMatch;
    });
  });

  chosen = computed(() => {
    const rows = this.visible();
    return rows.find(person => person.id === this.pickedId()) || rows[0] || null;
  });

  pins = computed<TanzaniaPin[]>(() => this.visible().flatMap(person => person.places
    .filter(place => Number.isFinite(Number(place.latitude)) && Number.isFinite(Number(place.longitude)))
    .map(place => ({
      id: `${person.id}::${place.farmId}`,
      label: person.fullName,
      title: `${person.fullName}. ${this.availabilityLabel(person.availability)}. ${person.load} load. ${place.farmName}.`,
      latitude: Number(place.latitude),
      longitude: Number(place.longitude),
      tone: person.availability === 'LIMITED' ? 'warn' as const : person.availability === 'UNAVAILABLE' ? 'alert' as const : undefined
    }))));

  selectedPin = computed(() => {
    const person = this.chosen();
    if (!person) {
      return null;
    }
    const current = this.pickedPin();
    if (current.startsWith(`${person.id}::`) && this.pins().some(pin => pin.id === current)) {
      return current;
    }
    return this.pins().find(pin => pin.id.startsWith(`${person.id}::`))?.id || null;
  });

  callout = computed<MapCallout | null>(() => {
    const person = this.chosen();
    const pinId = this.selectedPin();
    if (!person || !pinId) {
      return null;
    }
    const place = person.places.find(item => pinId.endsWith(`::${item.farmId}`));
    return {
      id: pinId,
      lines: [
        person.fullName,
        this.availabilityLabel(person.availability),
        `${person.load} load · ${person.openAlerts} alerts · ${person.openTasks} tasks`,
        place ? place.farmName : 'Assigned farm'
      ]
    };
  });

  availabilityCounts = computed(() => this.countBy(person => this.availabilityLabel(person.availability), ['Available', 'Limited', 'Unavailable', 'Not set']));
  loadCounts = computed(() => this.countBy(person => person.load, ['Light', 'Steady', 'Heavy']));

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.advisers().subscribe({
      next: rows => {
        this.people.set(rows);
        this.loading.set(false);
        const mine = rows.find(person => person.self);
        if (mine) {
          this.talentDraft.set(mine.talents.join(', '));
          this.availabilityDraft.set(mine.availability === 'UNSET' ? '' : mine.availability);
          this.noteDraft.set(mine.availabilityNote || '');
        }
        this.syncDraft(this.chosen());
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load advisers.');
      }
    });
  }

  setTalent(event: Event): void {
    this.talent.set((event.target as HTMLInputElement).value);
  }

  setAvailability(event: Event): void {
    this.availability.set((event.target as HTMLSelectElement).value);
  }

  setLoad(event: Event): void {
    this.loadFilter.set((event.target as HTMLSelectElement).value);
  }

  setTalentDraft(event: Event): void {
    this.talentDraft.set((event.target as HTMLInputElement).value);
  }

  setAvailabilityDraft(event: Event): void {
    this.availabilityDraft.set((event.target as HTMLSelectElement).value);
  }

  setNoteDraft(event: Event): void {
    this.noteDraft.set((event.target as HTMLInputElement).value);
  }

  setRatingNote(event: Event): void {
    this.ratingNote.set((event.target as HTMLInputElement).value);
  }

  setRecommendNote(event: Event): void {
    this.recommendNote.set((event.target as HTMLTextAreaElement).value);
  }

  pick(id: string, pinId = ''): void {
    this.pickedId.set(id);
    if (pinId) {
      this.pickedPin.set(pinId);
    }
    this.actionError.set('');
    this.syncDraft(this.people().find(person => person.id === id) || null);
  }

  pickPin(pinId: string): void {
    const adviserId = pinId.split('::')[0];
    if (adviserId) {
      this.pick(adviserId, pinId);
    }
  }

  saveProfile(): void {
    if (this.savingProfile()) {
      return;
    }
    const talents = this.talentDraft().split(',').map(item => item.trim()).filter(Boolean);
    if (!this.availabilityDraft()) {
      this.profileError.set('Choose available, limited, or unavailable.');
      return;
    }
    this.savingProfile.set(true);
    this.profileError.set('');
    this.api.updateAdviserProfile({
      talents,
      availability: this.availabilityDraft(),
      availabilityNote: this.noteDraft().trim()
    }).subscribe({
      next: saved => {
        this.savingProfile.set(false);
        this.replace(saved);
        this.toast.show('Your adviser listing is updated.');
      },
      error: error => {
        this.savingProfile.set(false);
        this.profileError.set(apiError(error, 'The listing could not be saved.'));
      }
    });
  }

  saveRating(): void {
    const person = this.chosen();
    if (!person || person.self || this.savingRating() || this.score() < 1) {
      return;
    }
    this.savingRating.set(true);
    this.actionError.set('');
    this.api.rateAdviser(person.id, { score: this.score(), note: this.ratingNote().trim() }).subscribe({
      next: saved => {
        this.savingRating.set(false);
        this.replace(saved);
        this.toast.show(`Rating saved for ${saved.fullName}.`);
      },
      error: error => {
        this.savingRating.set(false);
        this.actionError.set(apiError(error, 'The rating could not be saved.'));
      }
    });
  }

  recommend(): void {
    const person = this.chosen();
    if (!person || person.self || this.savingRecommendation()) {
      return;
    }
    this.savingRecommendation.set(true);
    this.actionError.set('');
    this.api.recommendAdviser(person.id, { note: this.recommendNote().trim() }).subscribe({
      next: saved => {
        this.savingRecommendation.set(false);
        this.replace(saved);
        this.toast.show(`${saved.fullName} is recommended.`);
      },
      error: error => {
        this.savingRecommendation.set(false);
        this.actionError.set(apiError(error, 'The recommendation could not be saved.'));
      }
    });
  }

  withdraw(): void {
    const person = this.chosen();
    if (!person || this.savingRecommendation()) {
      return;
    }
    this.savingRecommendation.set(true);
    this.actionError.set('');
    this.api.withdrawAdviserRecommendation(person.id).subscribe({
      next: saved => {
        this.savingRecommendation.set(false);
        this.replace(saved);
        this.recommendNote.set('');
        this.toast.show('Recommendation withdrawn.');
      },
      error: error => {
        this.savingRecommendation.set(false);
        this.actionError.set(apiError(error, 'The recommendation could not be withdrawn.'));
      }
    });
  }

  isChosen(id: string): boolean {
    const current = this.chosen();
    return current !== null && current.id === id;
  }

  farmNames(person: Adviser): string {
    return person.places.map(place => place.farmName).join(', ');
  }

  availabilityLabel(value: string): string {
    switch (value) {
      case 'AVAILABLE': return 'Available';
      case 'LIMITED': return 'Limited';
      case 'UNAVAILABLE': return 'Unavailable';
      default: return 'Not set';
    }
  }

  ratingLabel(person: Adviser): string {
    if (!person.ratingCount || person.ratingAverage == null) {
      return 'No ratings yet';
    }
    const average = Number(person.ratingAverage).toFixed(1);
    const noun = person.ratingCount === 1 ? 'rating' : 'ratings';
    return `${average} from ${person.ratingCount} ${noun}`;
  }

  private replace(saved: Adviser): void {
    this.people.update(rows => rows.map(row => row.id === saved.id ? saved : row));
    this.pickedId.set(saved.id);
    this.syncDraft(saved);
  }

  private syncDraft(person: Adviser | null): void {
    if (!person || person.self) {
      this.score.set(0);
      this.ratingNote.set('');
      this.recommendNote.set('');
      return;
    }
    this.score.set(person.myScore || 0);
    this.ratingNote.set(person.myRatingNote || '');
    this.recommendNote.set(person.myRecommendationNote || '');
  }

  private countBy(pick: (person: Adviser) => string, labels: string[]): { label: string; count: number }[] {
    return labels.map(label => ({
      label,
      count: this.visible().filter(person => pick(person) === label).length
    }));
  }
}
