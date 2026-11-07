import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/format';
import { TanzaniaMapComponent } from '../../shared/tanzania-map/tanzania-map.component';
import { locationIssue, roundCoord } from '../../shared/tanzania-map/boundary';
import { regionContaining, TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TanzaniaMapComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  regions = TANZANIA_REGION_NAMES;
  readonly kinds: AccountKindOption[] = [
    { id: 'FARM', title: 'Farmer / Farm Manager', detail: 'Run and manage your own farm.', image: 'assets/register/tractor.png', tone: 'farm' },
    { id: 'MEDICAL_EXPERT', title: 'Veterinary Professional', detail: 'Advise on animal health and provide veterinary care.', image: 'assets/register/cow.png', tone: 'vet' },
    { id: 'MEDICAL_SELLER', title: 'Veterinary Medicine Supplier', detail: 'Register a veterinary medicine shop and sell animal health products.', image: 'assets/register/medicine.png', tone: 'pharmacy' },
    { id: 'MARKET_BUYER', title: 'Agricultural Buyer', detail: 'Request and purchase farm produce and livestock.', image: 'assets/register/vegetables.png', tone: 'buyer' },
    { id: 'MARKET_SELLER', title: 'Agricultural Seller', detail: 'Offer and sell farm produce, livestock, and other agricultural products.', image: 'assets/register/grain.png', tone: 'seller' },
    { id: 'AGRICULTURAL_TRAINER', title: 'Agricultural Trainer', detail: 'Offer agricultural training, practical skills, and advisory services.', image: 'assets/register/seedling.png', tone: 'trainer' }
  ];
  readonly marks = [
    { id: 'LEAVES', label: 'Leaves' },
    { id: 'PEAK', label: 'Peak' },
    { id: 'CATTLE', label: 'Cattle' },
    { id: 'CROSS', label: 'Cross' },
    { id: 'PAW', label: 'Paw' }
  ];
  kind = signal<AccountKind>('FARM');
  busy = signal(false);
  locating = signal(false);
  error = signal('');
  notice = signal('');
  showPassword = signal(false);
  showConfirm = signal(false);
  agreed = signal(false);
  private stageIndex = signal(0);
  accuracy = signal<number | null>(null);
  query = signal('');
  searchOpen = signal(false);
  zoom = signal(1);

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(80)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(80)]],
    farmName: ['', [Validators.required, Validators.maxLength(160)]],
    farmCode: ['', [Validators.required, Validators.maxLength(40)]],
    region: ['', Validators.required],
    address: ['', [Validators.required, Validators.maxLength(300)]],
    contactPhone: ['', Validators.maxLength(40)],
    latitude: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.required, Validators.min(-180), Validators.max(180)]],
    district: ['', Validators.maxLength(80)],
    organizationName: ['', Validators.maxLength(160)],
    licenseNumber: ['', Validators.maxLength(80)],
    hours: ['', Validators.maxLength(120)],
    summary: ['', Validators.maxLength(400)],
    mark: ['LEAVES']
  });

  coordinate(name: 'latitude' | 'longitude'): number | null {
    const raw = this.form.controls[name].value;
    if (raw === '') {
      return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : null;
  }

  accountReady(): boolean {
    const controls = this.form.controls;
    return controls.fullName.valid && controls.email.valid && controls.password.valid
      && controls.confirmPassword.valid && this.passwordsMatch();
  }

  farmReady(): boolean {
    const controls = this.form.controls;
    return controls.farmName.valid && controls.farmCode.valid && controls.region.valid && controls.address.valid;
  }

  needsLocation(): boolean {
    const kind = this.kind();
    return kind === 'FARM' || kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER';
  }

  detailsReady(): boolean {
    const controls = this.form.controls;
    const kind = this.kind();
    if (kind === 'FARM') {
      return this.farmReady();
    }
    if (kind === 'MARKET_BUYER') {
      return controls.region.valid;
    }
    if (kind === 'MEDICAL_EXPERT' || kind === 'AGRICULTURAL_TRAINER') {
      return controls.region.valid && controls.contactPhone.valid && controls.summary.valid
        && (kind === 'MEDICAL_EXPERT' || controls.district.valid);
    }
    return controls.organizationName.valid && controls.region.valid && controls.district.valid
      && controls.address.valid && controls.contactPhone.valid && controls.summary.valid
      && (kind !== 'MEDICAL_SELLER' || (controls.licenseNumber.valid && controls.hours.valid && controls.mark.valid));
  }

  openDetails(): boolean {
    return this.accountReady();
  }

  openLocation(): boolean {
    return this.detailsReady();
  }

  openCreate(): boolean {
    return this.needsLocation() ? this.locationReady() : this.detailsReady();
  }

  stepNumber(name: 'type' | 'account' | 'details' | 'location' | 'create'): number {
    if (name === 'type') {
      return 1;
    }
    if (name === 'account') {
      return 2;
    }
    if (name === 'details') {
      return 3;
    }
    if (name === 'location') {
      return 4;
    }
    return this.needsLocation() ? 5 : 4;
  }

  locationReady(): boolean {
    const latitude = this.coordinate('latitude');
    const longitude = this.coordinate('longitude');
    return latitude !== null && longitude !== null && !locationIssue(latitude, longitude);
  }

  currentStep(): 1 | 2 | 3 {
    if (!this.accountReady()) {
      return 1;
    }
    if (!this.detailsReady()) {
      return 2;
    }
    return 3;
  }

  stage(): number {
    return Math.min(this.stageIndex(), this.stepKeys().length - 1);
  }

  stepKeys(): StepKey[] {
    const keys: StepKey[] = ['type', 'account', 'details'];
    if (this.needsLocation()) {
      keys.push('location');
    }
    keys.push('review');
    return keys;
  }

  activeKey(): StepKey {
    return this.stepKeys()[this.stage()];
  }

  showing(name: StepKey): boolean {
    return this.activeKey() === name;
  }

  canOpen(index: number): boolean {
    const target = index - 1;
    const keys = this.stepKeys();
    if (target < 0 || target >= keys.length) {
      return false;
    }
    for (let position = 0; position < target; position++) {
      if (!this.stepFilled(keys[position])) {
        return false;
      }
    }
    return true;
  }

  openStep(index: number): void {
    if (!this.canOpen(index)) {
      return;
    }
    this.error.set('');
    this.stageIndex.set(index - 1);
  }

  advance(): void {
    const key = this.activeKey();
    if (!this.stepFilled(key)) {
      this.touch(key);
      this.error.set(key === 'account' && !this.passwordsMatch()
        ? 'The passwords do not match.'
        : 'Complete this step before continuing.');
      return;
    }
    this.error.set('');
    this.stageIndex.set(this.stage() + 1);
  }

  retreat(): void {
    this.error.set('');
    this.stageIndex.set(Math.max(0, this.stage() - 1));
  }

  timeline(): TimelineStep[] {
    const stage = this.stage();
    return this.stepKeys().map((key, index) => ({
      index: index + 1,
      title: this.stepTitle(key),
      detail: this.stepDetail(key),
      done: index !== stage && (key === 'review' ? this.openCreate() && this.agreed() : this.stepFilled(key)),
      current: index === stage
    }));
  }

  reviewRows(): ReviewRow[] {
    const raw = this.form.getRawValue();
    const kind = this.kind();
    const rows: ReviewRow[] = [
      { label: 'Account type', value: this.heading() },
      { label: 'Full name', value: raw.fullName.trim() },
      { label: 'Email', value: raw.email.trim() }
    ];
    if (kind === 'FARM') {
      rows.push(
        { label: 'Farm name', value: raw.farmName.trim() },
        { label: 'Farm code', value: raw.farmCode.trim() },
        { label: 'Region', value: raw.region },
        { label: 'Address', value: raw.address.trim(), wide: true }
      );
      if (raw.contactPhone.trim()) {
        rows.push({ label: 'Phone', value: raw.contactPhone.trim() });
      }
    } else {
      if (kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER') {
        rows.push(
          { label: kind === 'MEDICAL_SELLER' ? 'Supplier name' : 'Seller name', value: raw.organizationName.trim() },
          { label: 'District', value: raw.district.trim() }
        );
      }
      rows.push({ label: 'Region', value: raw.region });
      if (kind === 'AGRICULTURAL_TRAINER') {
        rows.push({ label: 'District', value: raw.district.trim() });
      }
      if (kind === 'MEDICAL_SELLER') {
        rows.push(
          { label: 'Address', value: raw.address.trim(), wide: true },
          { label: 'Licence number', value: raw.licenseNumber.trim() },
          { label: 'Opening hours', value: raw.hours.trim() },
          { label: 'Shop mark', value: this.marks.find(item => item.id === raw.mark)?.label ?? raw.mark }
        );
      }
      if (raw.contactPhone.trim()) {
        rows.push({ label: 'Phone', value: raw.contactPhone.trim() });
      }
      if (kind !== 'MARKET_BUYER' && raw.summary.trim()) {
        rows.push({ label: 'Description', value: raw.summary.trim(), wide: true });
      }
    }
    if (this.needsLocation()) {
      rows.push(
        { label: 'Latitude', value: raw.latitude },
        { label: 'Longitude', value: raw.longitude }
      );
    }
    return rows.filter(row => row.value !== '');
  }

  private stepFilled(key: StepKey): boolean {
    switch (key) {
      case 'type': return true;
      case 'account': return this.accountReady();
      case 'details': return this.detailsReady();
      case 'location': return this.locationReady();
      case 'review': return this.openCreate();
    }
  }

  private stepTitle(key: StepKey): string {
    switch (key) {
      case 'type': return 'Account type';
      case 'account': return 'Account';
      case 'details': return this.detailTitle();
      case 'location': return 'Location';
      case 'review': return 'Create account';
    }
  }

  private stepDetail(key: StepKey): string {
    switch (key) {
      case 'type': return this.heading();
      case 'account': return 'Name, email and password';
      case 'details': return this.detailHint();
      case 'location': return this.kind() === 'FARM' ? 'Place the farm on the map' : 'Place the shop or stall on the map';
      case 'review': return 'Agree to the terms, then submit';
    }
  }

  private touch(key: StepKey): void {
    const kind = this.kind();
    const names: (keyof RegisterComponent['form']['controls'])[] = [];
    if (key === 'account') {
      names.push('fullName', 'email', 'password', 'confirmPassword');
    }
    if (key === 'details') {
      names.push('region');
      if (kind === 'FARM') {
        names.push('farmName', 'farmCode', 'address');
      }
      if (kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER') {
        names.push('organizationName', 'district');
      }
      if (kind === 'MEDICAL_SELLER') {
        names.push('address', 'licenseNumber', 'hours', 'mark');
      }
      if (kind === 'AGRICULTURAL_TRAINER') {
        names.push('district');
      }
      if (kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER' || kind === 'MEDICAL_EXPERT' || kind === 'AGRICULTURAL_TRAINER') {
        names.push('contactPhone');
      }
      if (kind !== 'FARM' && kind !== 'MARKET_BUYER') {
        names.push('summary');
      }
    }
    if (key === 'location') {
      names.push('latitude', 'longitude');
    }
    names.forEach(name => this.form.controls[name].markAsTouched());
  }

  detailStepTitle(): string {
    return this.detailTitle();
  }

  private detailTitle(): string {
    switch (this.kind()) {
      case 'FARM': return 'Farm details';
      case 'MEDICAL_SELLER': return 'Supplier details';
      case 'MARKET_BUYER': return 'Buyer details';
      case 'MARKET_SELLER': return 'Seller details';
      case 'AGRICULTURAL_TRAINER': return 'Trainer details';
      default: return 'Professional details';
    }
  }

  detailHint(): string {
    switch (this.kind()) {
      case 'FARM': return 'Farm name, code, region and address';
      case 'MEDICAL_EXPERT': return 'Region, phone and description';
      case 'MEDICAL_SELLER': return 'Shop, licence, hours and description';
      case 'MARKET_BUYER': return 'Region for buying requests';
      case 'MARKET_SELLER': return 'Seller name, district and description';
      default: return 'District, phone and description';
    }
  }

  chooseKind(kind: AccountKind): void {
    this.kind.set(kind);
    this.error.set('');
    const controls = this.form.controls;
    const text = (max: number, required = false) => required
      ? [Validators.required, Validators.maxLength(max)]
      : [Validators.maxLength(max)];
    const point = this.needsLocation()
      ? [Validators.required, Validators.min(kind === 'FARM' ? -90 : -12.2), Validators.max(kind === 'FARM' ? 90 : -0.5)]
      : [Validators.min(-90), Validators.max(90)];
    const pointLon = this.needsLocation()
      ? [Validators.required, Validators.min(kind === 'FARM' ? -180 : 28.9), Validators.max(kind === 'FARM' ? 180 : 40.9)]
      : [Validators.min(-180), Validators.max(180)];
    controls.farmName.setValidators(text(160, kind === 'FARM'));
    controls.farmCode.setValidators(text(40, kind === 'FARM'));
    controls.region.setValidators(text(80, true));
    controls.address.setValidators(text(300, kind === 'FARM' || kind === 'MEDICAL_SELLER'));
    controls.district.setValidators(text(80, kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER' || kind === 'AGRICULTURAL_TRAINER'));
    controls.organizationName.setValidators(text(160, kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER'));
    controls.licenseNumber.setValidators(text(80, kind === 'MEDICAL_SELLER'));
    controls.hours.setValidators(text(120, kind === 'MEDICAL_SELLER'));
    controls.summary.setValidators(text(400, kind !== 'FARM' && kind !== 'MARKET_BUYER'));
    controls.contactPhone.setValidators(text(40, kind === 'MEDICAL_SELLER' || kind === 'MARKET_SELLER' || kind === 'MEDICAL_EXPERT' || kind === 'AGRICULTURAL_TRAINER'));
    controls.mark.setValidators(kind === 'MEDICAL_SELLER' ? [Validators.required] : []);
    controls.latitude.setValidators(point);
    controls.longitude.setValidators(pointLon);
    Object.values(controls).forEach(control => control.updateValueAndValidity());
    const last = this.stepKeys().length - 1;
    if (this.stageIndex() > last) {
      this.stageIndex.set(last);
    }
  }

  matches(): Place[] {
    const query = this.query().trim().toLowerCase();
    if (query.length < 2) {
      return [];
    }
    return PLACES.filter(place => place.name.toLowerCase().includes(query)).slice(0, 6);
  }

  search(value: string): void {
    this.query.set(value);
    this.searchOpen.set(true);
  }

  searchFrom(event: Event): void {
    this.search((event.target as HTMLInputElement).value);
  }

  setAgreed(event: Event): void {
    this.agreed.set((event.target as HTMLInputElement).checked);
  }

  closeSearch(): void {
    setTimeout(() => this.searchOpen.set(false), 160);
  }

  choose(place: Place): void {
    this.query.set(place.name);
    this.searchOpen.set(false);
    this.place(place, place.region);
  }

  zoomBy(step: number): void {
    const next = Math.round((this.zoom() + step) * 100) / 100;
    this.zoom.set(Math.min(2.2, Math.max(1, next)));
  }

  place(point: { latitude: number; longitude: number }, regionName?: string): void {
    const latitude = roundCoord(point.latitude);
    const longitude = roundCoord(point.longitude);
    const detected = regionContaining(latitude, longitude);
    const named = regionName && this.regions.includes(regionName) ? regionName : '';
    const region = detected || named;
    this.accuracy.set(null);
    this.form.patchValue({
      latitude: latitude.toFixed(6),
      longitude: longitude.toFixed(6),
      ...(region && !this.form.controls.region.value ? { region } : {})
    });
    this.form.controls.latitude.markAsTouched();
    this.form.controls.longitude.markAsTouched();
    this.error.set(locationIssue(latitude, longitude));
  }

  clearLocation(): void {
    this.accuracy.set(null);
    this.query.set('');
    this.searchOpen.set(false);
    this.zoom.set(1);
    this.form.controls.latitude.reset('');
    this.form.controls.longitude.reset('');
    this.error.set('');
  }

  locate(): void {
    if (!navigator.geolocation) {
      this.error.set('This browser cannot read a GPS position. Click the map or type the coordinates.');
      return;
    }
    this.locating.set(true);
    this.error.set('');
    navigator.geolocation.getCurrentPosition(
      position => {
        this.locating.set(false);
        this.place({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        const metres = Math.round(position.coords.accuracy);
        this.accuracy.set(Number.isFinite(metres) ? metres : null);
      },
      () => {
        this.locating.set(false);
        this.error.set('The GPS position could not be read. Click the map or type the latitude and longitude.');
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
    );
  }

  passwordsMatch(): boolean {
    return this.form.controls.password.value === this.form.controls.confirmPassword.value;
  }

  mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return (confirm.touched || this.busy()) && confirm.value !== '' && !this.passwordsMatch();
  }

  invalid(name: 'fullName' | 'email' | 'password' | 'confirmPassword' | 'farmName' | 'farmCode' | 'region' | 'address' | 'latitude' | 'longitude' | 'district' | 'organizationName' | 'licenseNumber' | 'hours' | 'summary' | 'contactPhone' | 'mark'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.busy());
  }

  explainPrivacy(): void {
    this.notice.set('Registration creates your tenant account and this farm. The session stays on this browser until you sign out.');
  }

  explainTerms(): void {
    const kind = this.kind();
    if (kind === 'MEDICAL_SELLER') {
      this.notice.set('The veterinary medicine shop stays hidden until an administrator approves the licence. This account does not replace a Farmer / Farm Manager or Farm Adviser.');
      return;
    }
    if (kind === 'MARKET_SELLER') {
      this.notice.set('The agricultural seller profile stays hidden until an administrator approves it. This account does not replace a Farmer / Farm Manager or Farm Adviser.');
      return;
    }
    if (kind === 'FARM') {
      this.notice.set('A new farm account starts with Own Farm Only access. You see this farm until an administrator changes the grant.');
      return;
    }
    this.notice.set('This account is separate from Farmer / Farm Manager and Farm Adviser. It does not change livestock, stock, or staff records.');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (!this.openCreate()) {
      this.error.set('Complete each step in order before creating the account.');
      return;
    }
    if (!this.passwordsMatch()) {
      this.error.set('The password and confirmation do not match.');
      return;
    }
    if (!this.agreed()) {
      this.error.set('Agree to the access terms and privacy note before creating the account.');
      return;
    }
    const latitude = this.coordinate('latitude');
    const longitude = this.coordinate('longitude');
    const issue = !this.needsLocation()
      ? ''
      : latitude == null || longitude == null
        ? 'Place the location on the map.'
        : locationIssue(latitude, longitude);
    if (issue) {
      this.error.set(issue);
    }
    if (this.form.invalid || issue || this.busy()) {
      return;
    }
    const raw = this.form.getRawValue();
    const kind = this.kind();
    this.busy.set(true);
    this.error.set('');
    this.auth.register({
      accountKind: kind,
      fullName: raw.fullName.trim(),
      email: raw.email.trim(),
      password: raw.password,
      region: raw.region,
      address: raw.address.trim(),
      latitude: raw.latitude === '' ? null : Number(raw.latitude),
      longitude: raw.longitude === '' ? null : Number(raw.longitude),
      farmName: raw.farmName.trim(),
      farmCode: raw.farmCode.trim(),
      contactPhone: raw.contactPhone.trim(),
      district: raw.district.trim(),
      organizationName: raw.organizationName.trim(),
      licenseNumber: raw.licenseNumber.trim(),
      hours: raw.hours.trim(),
      summary: raw.summary.trim(),
      mark: raw.mark
    }).subscribe({
      next: () => {
        if (kind === 'MARKET_BUYER') {
          this.router.navigate(['/market'], { fragment: 'buy' });
        } else if (kind === 'MARKET_SELLER') {
          this.router.navigate(['/market'], { fragment: 'sell' });
        } else if (kind === 'MEDICAL_SELLER') {
          this.router.navigate(['/suppliers']);
        } else if (kind === 'MEDICAL_EXPERT') {
          this.router.navigate(['/health']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: err => {
        this.busy.set(false);
        this.error.set(apiError(err, 'The account could not be created'));
      }
    });
  }

  heading(): string {
    return this.kinds.find(item => item.id === this.kind())?.title ?? 'Farmer / Farm Manager';
  }
}

type AccountKind = 'FARM' | 'MEDICAL_EXPERT' | 'MEDICAL_SELLER' | 'MARKET_BUYER' | 'MARKET_SELLER' | 'AGRICULTURAL_TRAINER';

interface AccountKindOption {
  id: AccountKind;
  title: string;
  detail: string;
  image: string;
  tone: string;
}

type StepKey = 'type' | 'account' | 'details' | 'location' | 'review';

interface TimelineStep {
  index: number;
  title: string;
  detail: string;
  done: boolean;
  current: boolean;
}

interface ReviewRow {
  label: string;
  value: string;
  wide?: boolean;
}

interface Place {
  name: string;
  kind: string;
  latitude: number;
  longitude: number;
  region?: string;
}

const PLACES: Place[] = [
  { name: 'Arusha', kind: 'Town', latitude: -3.3869, longitude: 36.683, region: 'Arusha' },
  { name: 'Babati', kind: 'Town', latitude: -4.2117, longitude: 35.7477, region: 'Manyara' },
  { name: 'Bukoba', kind: 'Town', latitude: -1.3317, longitude: 31.8122, region: 'Kagera' },
  { name: 'Dar es Salaam', kind: 'City', latitude: -6.7924, longitude: 39.2083, region: 'Dar es Salaam' },
  { name: 'Dodoma', kind: 'City', latitude: -6.163, longitude: 35.7516, region: 'Dodoma' },
  { name: 'Geita', kind: 'Town', latitude: -2.8724, longitude: 32.232, region: 'Geita' },
  { name: 'Iringa', kind: 'Town', latitude: -7.77, longitude: 35.69, region: 'Iringa' },
  { name: 'Kigoma', kind: 'Town', latitude: -4.8769, longitude: 29.6267, region: 'Kigoma' },
  { name: 'Kilimanjaro', kind: 'Region', latitude: -3.35, longitude: 37.34, region: 'Kilimanjaro' },
  { name: 'Lindi', kind: 'Town', latitude: -9.9971, longitude: 39.7145, region: 'Lindi' },
  { name: 'Mbeya', kind: 'City', latitude: -8.9094, longitude: 33.4608, region: 'Mbeya' },
  { name: 'Morogoro', kind: 'Town', latitude: -6.8278, longitude: 37.6591, region: 'Morogoro' },
  { name: 'Moshi', kind: 'Town', latitude: -3.35, longitude: 37.34, region: 'Kilimanjaro' },
  { name: 'Mpanda', kind: 'Town', latitude: -6.3438, longitude: 31.0694, region: 'Katavi' },
  { name: 'Mtwara', kind: 'Town', latitude: -10.2667, longitude: 40.1833, region: 'Mtwara' },
  { name: 'Musoma', kind: 'Town', latitude: -1.5, longitude: 33.8, region: 'Mara' },
  { name: 'Mwanza', kind: 'City', latitude: -2.5164, longitude: 32.9175, region: 'Mwanza' },
  { name: 'Njombe', kind: 'Town', latitude: -9.3333, longitude: 34.7667, region: 'Njombe' },
  { name: 'Rombo', kind: 'Town', latitude: -3.15, longitude: 37.52, region: 'Kilimanjaro' },
  { name: 'Shinyanga', kind: 'Town', latitude: -3.6639, longitude: 33.4211, region: 'Shinyanga' },
  { name: 'Singida', kind: 'Town', latitude: -4.8163, longitude: 34.7436, region: 'Singida' },
  { name: 'Songea', kind: 'Town', latitude: -10.6833, longitude: 35.65, region: 'Ruvuma' },
  { name: 'Sumbawanga', kind: 'Town', latitude: -7.9667, longitude: 31.6167, region: 'Rukwa' },
  { name: 'Tabora', kind: 'Town', latitude: -5.0167, longitude: 32.8, region: 'Tabora' },
  { name: 'Tanga', kind: 'Town', latitude: -5.0689, longitude: 39.0988, region: 'Tanga' },
  { name: 'Zanzibar', kind: 'City', latitude: -6.1659, longitude: 39.2026 }
];
