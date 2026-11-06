import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { apiError } from '../../core/format';
import { Farm, MedicalBoard, MedicalSupplier, MedicineOrder, SupplierMedicine } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { inTanzania } from '../../shared/tanzania-map/tanzania-geo';
import { TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

interface ShopMark {
  id: string;
  src: string;
  label: string;
  caption: string;
}

const MARKS: ShopMark[] = [
  { id: 'LEAVES', src: 'assets/medical/leaves.png', label: 'Agrovet', caption: 'Leaves and a cross' },
  { id: 'PEAK', src: 'assets/medical/peak.png', label: 'Highland', caption: 'A mountain' },
  { id: 'CATTLE', src: 'assets/medical/cattle.png', label: 'Livestock', caption: 'A cow' },
  { id: 'CROSS', src: 'assets/medical/cross.png', label: 'Pharmacy', caption: 'A medical cross' },
  { id: 'PAW', src: 'assets/medical/paw.png', label: 'Animal health', caption: 'A paw' }
];

const CATEGORIES = ['Dewormers', 'Antibiotics', 'Vaccines', 'Vitamins and minerals', 'Supplements', 'Other'];

@Component({
  selector: 'app-suppliers',
  imports: [TanzaniaMapComponent],
  templateUrl: './suppliers.component.html',
  styleUrl: './suppliers.component.css'
})
export class SuppliersComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  readonly marks = MARKS;
  readonly categories = CATEGORIES;
  readonly regions = TANZANIA_REGION_NAMES;

  board = signal<MedicalBoard | null>(null);
  farms = signal<Farm[]>([]);
  species = signal<string[]>([]);
  loading = signal(true);
  error = signal('');
  saving = signal(false);
  formError = signal('');
  placeError = signal('');
  zoom = signal(1);
  pickedId = signal('');
  areaFarmId = signal('');
  tab = signal<'medicines' | 'about' | 'reviews'>('medicines');
  sheet = signal('');

  draftQ = signal('');
  draftAnimal = signal('');
  draftCategory = signal('');
  draftRegion = signal('');
  draftDistrict = signal('');
  applied = signal({ q: '', animal: '', category: '', region: '', district: '' });

  shopName = signal('');
  mark = signal('LEAVES');
  shopRegion = signal('');
  shopDistrict = signal('');
  address = signal('');
  phone = signal('');
  hours = signal('');
  draftLat = signal<number | null>(null);
  draftLon = signal<number | null>(null);
  license = signal('');
  licenseAuthority = signal('');
  licenseExpiresOn = signal('');
  productsOffered = signal('');
  summary = signal('');
  shopState = signal('OPEN');

  editingId = signal('');
  medName = signal('');
  medAnimals = signal('');
  medCategory = signal('Dewormers');
  medPrice = signal('');
  medCurrency = signal('TZS');
  medQty = signal('');
  medUnit = signal('');
  medStock = signal('IN_STOCK');
  medRx = signal(false);

  requestMedicine = signal<SupplierMedicine | null>(null);
  customRequest = signal(false);
  reqName = signal('');
  reqQty = signal('1');
  reqUnit = signal('');
  reqFulfillment = signal('COLLECTION');
  reqFarm = signal('');
  reqRx = signal(false);
  reqNote = signal('');
  decisionNote = signal('');

  suppliers = computed(() => {
    const rows = [...(this.board()?.suppliers ?? [])];
    return rows.sort((left, right) => {
      const leftKm = this.nearest(left)?.km ?? Number.POSITIVE_INFINITY;
      const rightKm = this.nearest(right)?.km ?? Number.POSITIVE_INFINITY;
      return leftKm - rightKm || left.name.localeCompare(right.name);
    });
  });
  districts = computed(() => this.board()?.districts ?? []);
  requests = computed(() => this.board()?.requests ?? []);
  ownShop = computed(() => this.board()?.ownShop ?? null);
  pending = computed(() => this.board()?.pendingReview ?? []);
  administrator = computed(() => this.board()?.administrator === true);
  adviser = computed(() => this.board()?.adviser === true);
  chosen = computed(() => this.suppliers().find(shop => shop.id === this.pickedId()) ?? null);

  pins = computed<TanzaniaPin[]>(() => {
    const farms = this.farms()
      .filter(farm => Number.isFinite(Number(farm.latitude)) && Number.isFinite(Number(farm.longitude)))
      .map(farm => ({
        id: `farm-${farm.id}`,
        label: farm.name,
        title: `${farm.name}, ${farm.region}. Your farm.`,
        latitude: Number(farm.latitude),
        longitude: Number(farm.longitude)
      }));
    const shops = this.suppliers().map(shop => ({
      id: shop.id,
      label: shop.name,
      title: `${shop.name}. ${shop.district}, ${shop.region}. ${this.distanceLabel(shop)}`.trim(),
      latitude: Number(shop.latitude),
      longitude: Number(shop.longitude),
      tone: 'alert' as const,
      notice: this.shortDistance(shop)
    }));
    return [...farms, ...shops];
  });

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe(fragment => {
      if (fragment === 'requests' || fragment === 'desk' || fragment === 'review') {
        this.sheet.set(fragment);
      }
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      board: this.api.medicalSuppliers(this.applied()),
      farms: this.api.farms(),
      species: this.api.species()
    }).subscribe({
      next: ({ board, farms, species }) => {
        this.board.set(board);
        this.farms.set(farms);
        this.species.set(species.map(item => item.name));
        if (!this.reqFarm() && farms[0]) {
          this.reqFarm.set(farms[0].id);
        }
        if (!this.pickedId() && board.suppliers.length) {
          const sample = board.suppliers.find(shop => shop.name === 'AgroVet Supplies') ?? board.suppliers[0];
          this.pickedId.set(sample.id);
        }
        if (this.pickedId() && !board.suppliers.some(shop => shop.id === this.pickedId())) {
          this.pickedId.set('');
        }
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiError(error, 'Suppliers could not be loaded.'));
      }
    });
  }

  reload(): void {
    this.api.medicalSuppliers(this.applied()).subscribe({
      next: board => {
        this.board.set(board);
        if (this.pickedId() && !board.suppliers.some(shop => shop.id === this.pickedId())) {
          this.pickedId.set('');
        }
        this.saving.set(false);
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'That could not be saved.'));
      }
    });
  }

  search(event: Event): void {
    event.preventDefault();
    this.applied.set({
      q: this.draftQ().trim(),
      animal: this.draftAnimal(),
      category: this.draftCategory(),
      region: this.draftRegion(),
      district: this.draftDistrict()
    });
    this.reload();
  }

  searchArea(): void {
    const selected = this.farms().find(farm => `farm-${farm.id}` === this.areaFarmId());
    const farm = [selected, ...this.farms()].find(item => !!item && this.regions.includes(item.region));
    if (!farm) {
      this.toast.show('Choose a farm whose region is on the Tanzania map.', 'err');
      return;
    }
    this.draftRegion.set(farm.region);
    this.applied.update(current => ({ ...current, region: farm.region }));
    this.reload();
    this.toast.show(`Showing shops in ${farm.region}.`);
  }

  zoomIn(): void {
    this.zoom.set(Math.min(2.2, Number((this.zoom() + 0.15).toFixed(2))));
  }

  zoomOut(): void {
    this.zoom.set(Math.max(0.85, Number((this.zoom() - 0.15).toFixed(2))));
  }

  pickPin(id: string): void {
    if (id.startsWith('farm-')) {
      this.areaFarmId.set(id);
      return;
    }
    this.pickedId.set(id);
    this.tab.set('medicines');
  }

  select(id: string): void {
    this.pickedId.set(id);
    this.tab.set('medicines');
  }

  openRegister(): void {
    const shop = this.ownShop();
    this.formError.set('');
    this.placeError.set('');
    if (shop) {
      this.shopName.set(shop.name);
      this.mark.set(shop.mark);
      this.shopRegion.set(shop.region);
      this.shopDistrict.set(shop.district);
      this.address.set(shop.address);
      this.phone.set(shop.phone);
      this.hours.set(shop.hours);
      this.draftLat.set(shop.latitude);
      this.draftLon.set(shop.longitude);
      this.license.set(shop.licenseNumber);
      this.licenseAuthority.set(shop.licenseAuthority || '');
      this.licenseExpiresOn.set(shop.licenseExpiresOn || '');
      this.productsOffered.set(shop.productsOffered || '');
      this.summary.set(shop.summary);
      this.shopState.set(shop.shopState);
    } else {
      this.shopName.set('');
      this.mark.set('LEAVES');
      this.shopRegion.set(this.farms()[0]?.region || '');
      this.shopDistrict.set('');
      this.address.set('');
      this.phone.set('');
      this.hours.set('');
      this.draftLat.set(null);
      this.draftLon.set(null);
      this.license.set('');
      this.licenseAuthority.set('');
      this.licenseExpiresOn.set('');
      this.productsOffered.set('');
      this.summary.set('');
      this.shopState.set('OPEN');
    }
    this.sheet.set('register');
  }

  placeShop(point: { latitude: number; longitude: number }): void {
    if (!inTanzania(point.latitude, point.longitude)) {
      this.placeError.set('These coordinates are outside Tanzania.');
      return;
    }
    this.placeError.set('');
    this.draftLat.set(point.latitude);
    this.draftLon.set(point.longitude);
  }

  saveShop(event: Event): void {
    event.preventDefault();
    const body = {
      name: this.shopName().trim(),
      mark: this.mark(),
      region: this.shopRegion(),
      district: this.shopDistrict().trim(),
      address: this.address().trim(),
      phone: this.phone().trim(),
      hours: this.hours().trim(),
      latitude: this.draftLat(),
      longitude: this.draftLon(),
      licenseNumber: this.license().trim(),
      licenseAuthority: this.licenseAuthority().trim() || null,
      licenseExpiresOn: this.licenseExpiresOn().trim() || null,
      productsOffered: this.productsOffered().trim() || null,
      summary: this.summary().trim(),
      shopState: this.shopState()
    };
    if (!body.name || !body.region || !body.district || !body.address || !body.phone || !body.hours || !body.licenseNumber || !body.summary) {
      this.formError.set('Fill in the shop name, region, district, address, phone, hours, licence, and description.');
      return;
    }
    if (body.latitude == null || body.longitude == null) {
      this.formError.set('Place the shop on the map inside Tanzania.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    const shop = this.ownShop();
    const request = shop ? this.api.updateMedicalSupplier(shop.id, body) : this.api.registerMedicalSupplier(body);
    request.subscribe({
      next: () => {
        this.sheet.set('');
        this.toast.show(shop ? 'Shop details saved.' : 'Registration sent. An administrator checks the licence before the shop appears in search.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The shop could not be saved.'));
      }
    });
  }

  openMedicine(medicine?: SupplierMedicine): void {
    this.formError.set('');
    this.editingId.set(medicine?.id || '');
    this.medName.set(medicine?.name || '');
    this.medAnimals.set(medicine?.animalTypes || '');
    this.medCategory.set(medicine?.category || 'Dewormers');
    this.medPrice.set(medicine ? String(medicine.priceAmount) : '');
    this.medCurrency.set(medicine?.currency || 'TZS');
    this.medQty.set(medicine ? String(medicine.quantity) : '');
    this.medUnit.set(medicine?.unit || '');
    this.medStock.set(medicine?.stock || 'IN_STOCK');
    this.medRx.set(medicine?.prescriptionRequired === true);
    this.sheet.set('medicine');
  }

  toggleAnimal(name: string): void {
    const current = new Set(this.medAnimals().split(',').map(item => item.trim()).filter(Boolean));
    if (name === 'Any') {
      this.medAnimals.set(current.has('Any') ? '' : 'Any');
      return;
    }
    current.delete('Any');
    if (current.has(name)) {
      current.delete(name);
    } else {
      current.add(name);
    }
    this.medAnimals.set([...current].join(', '));
  }

  animalOn(name: string): boolean {
    return this.medAnimals().split(',').map(item => item.trim()).includes(name);
  }

  saveMedicine(event: Event): void {
    event.preventDefault();
    const shop = this.ownShop();
    if (!shop) {
      this.formError.set('Register a shop before stocking medicine.');
      return;
    }
    const body = {
      name: this.medName().trim(),
      animalTypes: this.medAnimals(),
      category: this.medCategory(),
      priceAmount: Number(this.medPrice()),
      currency: this.medCurrency(),
      quantity: Number(this.medQty()),
      unit: this.medUnit().trim(),
      stock: this.medStock(),
      prescriptionRequired: this.medRx()
    };
    if (!body.name || !body.animalTypes || !body.unit || !Number.isFinite(body.priceAmount) || !Number.isFinite(body.quantity)) {
      this.formError.set('Enter the medicine, animal, price, quantity, and unit.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    this.api.saveSupplierMedicine(shop.id, body, this.editingId() || undefined).subscribe({
      next: () => {
        this.sheet.set('desk');
        this.toast.show('Medicine saved.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The medicine could not be saved.'));
      }
    });
  }

  openRequest(medicine: SupplierMedicine | null): void {
    const shop = this.chosen();
    if (!shop || shop.shopState === 'CLOSED') {
      return;
    }
    this.formError.set('');
    this.customRequest.set(medicine == null);
    this.requestMedicine.set(medicine);
    this.reqName.set(medicine?.name || '');
    this.reqQty.set('1');
    this.reqUnit.set(medicine?.unit || '');
    this.reqFulfillment.set('COLLECTION');
    this.reqRx.set(medicine?.prescriptionRequired === true);
    this.reqNote.set('');
    this.sheet.set('request');
  }

  sendRequest(event: Event): void {
    event.preventDefault();
    const shop = this.chosen();
    const medicine = this.requestMedicine();
    if (!shop) {
      return;
    }
    if (!this.reqFarm()) {
      this.formError.set('Choose the farm this request is for.');
      return;
    }
    const quantity = Number(this.reqQty());
    if (!Number.isFinite(quantity) || quantity <= 0) {
      this.formError.set('Enter a quantity greater than zero.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    this.api.requestMedicine({
      supplierId: shop.id,
      medicineId: medicine?.id || null,
      medicineName: this.reqName().trim(),
      quantity,
      unit: this.reqUnit().trim(),
      fulfillment: this.reqFulfillment(),
      farmId: this.reqFarm(),
      prescriptionRequired: this.reqRx(),
      note: this.reqNote().trim()
    }).subscribe({
      next: order => {
        this.sheet.set('requests');
        this.toast.show(order.status === 'AWAITING_PRESCRIPTION'
          ? 'Request sent. A Farm Adviser has to approve the prescription.'
          : 'Request sent to the shop.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The request could not be sent.'));
      }
    });
  }

  verify(id: string, approve: boolean): void {
    this.saving.set(true);
    this.api.verifyMedicalSupplier(id, approve).subscribe({
      next: () => {
        this.toast.show(approve ? 'Shop approved. It now appears in search.' : 'Shop rejected.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.toast.show(apiError(error, 'The shop could not be updated.'), 'err');
      }
    });
  }

  prescribe(id: string, approve: boolean): void {
    this.saving.set(true);
    this.api.prescribeMedicine(id, approve, this.decisionNote().trim()).subscribe({
      next: () => {
        this.decisionNote.set('');
        this.toast.show(approve ? 'Prescription approved.' : 'Prescription declined.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.toast.show(apiError(error, 'The prescription could not be saved.'), 'err');
      }
    });
  }

  decide(id: string, action: string): void {
    this.saving.set(true);
    this.api.decideMedicineRequest(id, action, this.decisionNote().trim()).subscribe({
      next: () => {
        this.decisionNote.set('');
        this.toast.show('Request updated.');
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.toast.show(apiError(error, 'The request could not be updated.'), 'err');
      }
    });
  }

  closeSheet(): void {
    if (this.saving()) {
      return;
    }
    this.sheet.set('');
    this.formError.set('');
  }

  text(event: Event): string {
    const target = event.target;
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
      return target.value;
    }
    return '';
  }

  checked(event: Event): boolean {
    return event.target instanceof HTMLInputElement && event.target.checked;
  }

  markFor(id: string): ShopMark {
    return this.marks.find(mark => mark.id === id) || this.marks[0];
  }

  distanceLabel(shop: MedicalSupplier): string {
    const near = this.nearest(shop);
    if (!near) {
      return '';
    }
    if (near.km < 1) {
      return `Under 1 km from ${near.farm}`;
    }
    return `${Math.round(near.km)} km from ${near.farm}`;
  }

  shortDistance(shop: MedicalSupplier): string {
    const near = this.nearest(shop);
    if (!near) {
      return '';
    }
    if (near.km < 1) {
      return 'Under 1 km';
    }
    return `${Math.round(near.km)} km away`;
  }

  money(amount: number, currency: string): string {
    const digits = Number.isInteger(Number(amount)) ? 0 : 2;
    const grouped = new Intl.NumberFormat('en-TZ', { maximumFractionDigits: digits }).format(Number(amount));
    if (currency === 'TZS') {
      return `TZS ${grouped}`;
    }
    try {
      return new Intl.NumberFormat('en-TZ', { style: 'currency', currency, maximumFractionDigits: digits }).format(Number(amount));
    } catch {
      return `${currency} ${grouped}`;
    }
  }

  medicineLine(item: SupplierMedicine): string {
    if (item.summary) {
      return item.summary;
    }
    return `${item.category} · ${item.animalTypes}`;
  }

  fromFarm(shop: MedicalSupplier): string {
    const near = this.nearest(shop);
    if (!near) {
      return '';
    }
    if (near.km < 1) {
      return 'Under 1 km from your farm';
    }
    return `${Math.round(near.km)} km from your farm`;
  }

  stockText(stock: string): string {
    if (stock === 'LIMITED') {
      return 'Limited stock';
    }
    if (stock === 'OUT') {
      return 'Out of stock';
    }
    return 'In stock';
  }

  shopText(state: string): string {
    if (state === 'LIMITED') {
      return 'Limited stock';
    }
    if (state === 'CLOSED') {
      return 'Closed';
    }
    return 'Open';
  }

  statusText(status: string): string {
    const labels: Record<string, string> = {
      AWAITING_PRESCRIPTION: 'Waiting for a Farm Adviser',
      PRESCRIPTION_APPROVED: 'Prescription approved',
      SUBMITTED: 'Submitted',
      CONFIRMED_COLLECTION: 'Confirmed for collection',
      CONFIRMED_DELIVERY: 'Confirmed for delivery',
      READY: 'Ready',
      COLLECTED: 'Collected',
      OUT_FOR_DELIVERY: 'Out for delivery',
      DELIVERED: 'Delivered',
      REJECTED: 'Rejected'
    };
    return labels[status] || status;
  }

  path(order: MedicineOrder): string[] {
    const head = order.prescriptionRequired
      ? ['Submitted', 'Waiting for a Farm Adviser', 'Prescription approved']
      : ['Submitted'];
    const tail = order.fulfillment === 'DELIVERY'
      ? ['Confirmed for delivery', 'Out for delivery', 'Delivered']
      : ['Confirmed for collection', 'Ready', 'Collected'];
    return [...head, ...tail];
  }

  reached(order: MedicineOrder, step: string): boolean {
    const steps = this.path(order);
    const current = steps.indexOf(this.statusText(order.status));
    return current >= steps.indexOf(step);
  }

  actions(order: MedicineOrder): { action: string; label: string }[] {
    if (!order.ownedShop) {
      return [];
    }
    if (order.status === 'SUBMITTED' || order.status === 'PRESCRIPTION_APPROVED') {
      return [
        { action: 'CONFIRM_COLLECTION', label: 'Confirm collection' },
        { action: 'CONFIRM_DELIVERY', label: 'Confirm delivery' },
        { action: 'REJECT', label: 'Reject' }
      ];
    }
    if (order.status === 'CONFIRMED_COLLECTION') {
      return [
        { action: 'MARK_READY', label: 'Mark ready' },
        { action: 'REJECT', label: 'Reject' }
      ];
    }
    if (order.status === 'READY') {
      return [{ action: 'MARK_COLLECTED', label: 'Mark collected' }];
    }
    if (order.status === 'CONFIRMED_DELIVERY') {
      return [
        { action: 'MARK_DISPATCHED', label: 'Mark out for delivery' },
        { action: 'REJECT', label: 'Reject' }
      ];
    }
    if (order.status === 'OUT_FOR_DELIVERY') {
      return [{ action: 'MARK_DELIVERED', label: 'Mark delivered' }];
    }
    return [];
  }

  when(value: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Africa/Dar_es_Salaam',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(new Date(value));
  }

  private nearest(shop: MedicalSupplier): { km: number; farm: string } | null {
    const points = this.farms().filter(farm => Number.isFinite(Number(farm.latitude)) && Number.isFinite(Number(farm.longitude)));
    if (!points.length) {
      return null;
    }
    let best = points[0];
    let bestKm = this.kilometres(shop, best);
    for (const farm of points.slice(1)) {
      const distance = this.kilometres(shop, farm);
      if (distance < bestKm) {
        best = farm;
        bestKm = distance;
      }
    }
    return { km: bestKm, farm: best.name };
  }

  private kilometres(shop: MedicalSupplier, farm: Farm): number {
    const earth = 6371;
    const lat1 = Number(shop.latitude) * Math.PI / 180;
    const lat2 = Number(farm.latitude) * Math.PI / 180;
    const dLat = lat2 - lat1;
    const dLon = (Number(farm.longitude) - Number(shop.longitude)) * Math.PI / 180;
    const arc = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return 2 * earth * Math.asin(Math.min(1, Math.sqrt(arc)));
  }
}
