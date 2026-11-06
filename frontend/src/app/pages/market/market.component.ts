import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { apiError } from '../../core/format';
import { Farm, MarketBoard, MarketDemand, MarketOffer, MarketOrder, MarketProduct, MarketSeller } from '../../core/models';
import { inTanzania } from '../../shared/tanzania-map/tanzania-geo';
import { TANZANIA_REGION_NAMES } from '../../shared/tanzania-map/region-lookup';
import { TanzaniaMapComponent, TanzaniaPin } from '../../shared/tanzania-map/tanzania-map.component';

interface Photo {
  key: string;
  name: string;
  category: string;
  src: string;
}

interface SellerDraft {
  name: string;
  region: string;
  district: string;
  phone: string;
  latitude: number | null;
  longitude: number | null;
  summary: string;
}

interface ProductDraft {
  name: string;
  category: string;
  imageKey: string;
  description: string;
  quantity: string;
  unit: string;
  priceAmount: string;
  minOrder: string;
  grade: string;
  region: string;
  district: string;
  latitude: number | null;
  longitude: number | null;
  fulfillment: string;
}

interface DemandDraft {
  productId: string;
  productName: string;
  category: string;
  quantity: string;
  unit: string;
  grade: string;
  targetPrice: string;
  region: string;
  district: string;
  latitude: number | null;
  longitude: number | null;
  neededOn: string;
  note: string;
}

interface OfferDraft {
  requestId: string;
  productId: string;
  quantity: string;
  unit: string;
  priceAmount: string;
  terms: string;
}

const PHOTOS: Photo[] = [
  { key: 'MAIZE', name: 'Maize', category: 'Cereals', src: 'assets/market/maize.jpg' },
  { key: 'BEANS', name: 'Red beans', category: 'Legumes', src: 'assets/market/beans.jpg' },
  { key: 'TOMATOES', name: 'Tomatoes', category: 'Vegetables', src: 'assets/market/tomatoes.jpg' },
  { key: 'MILK', name: 'Fresh milk', category: 'Dairy', src: 'assets/market/milk.jpg' },
  { key: 'POTATOES', name: 'Irish potatoes', category: 'Vegetables', src: 'assets/market/potatoes.jpg' },
  { key: 'ONIONS', name: 'Red onions', category: 'Vegetables', src: 'assets/market/onions.jpg' },
  { key: 'CHICKENS', name: 'Broiler chickens', category: 'Poultry', src: 'assets/market/chickens.jpg' },
  { key: 'RICE', name: 'Rice', category: 'Cereals', src: 'assets/market/rice.jpg' }
];

const CATEGORIES = ['Cereals', 'Legumes', 'Vegetables', 'Dairy', 'Poultry', 'Fruits', 'Livestock', 'Other'];
const UNITS = [
  { id: 'KG', label: 'kg' },
  { id: 'TONNE', label: 'tonnes' },
  { id: 'BAG', label: 'bags' },
  { id: 'LITRE', label: 'litres' },
  { id: 'PIECE', label: 'pieces' }
];

@Component({
  selector: 'app-market',
  imports: [RouterLink, TanzaniaMapComponent],
  templateUrl: './market.component.html',
  styleUrl: './market.component.css'
})
export class MarketComponent {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  readonly photos = PHOTOS;
  readonly categories = CATEGORIES;
  readonly units = UNITS;
  readonly regions = TANZANIA_REGION_NAMES;
  readonly tabs = [
    { id: 'buy', label: 'Buy Products', fragment: 'buy' },
    { id: 'sell', label: 'Sell Products', fragment: 'sell' },
    { id: 'requests', label: 'Buying Requests', fragment: 'requests' },
    { id: 'orders', label: 'My Orders', fragment: 'orders' },
    { id: 'messages', label: 'Messages', fragment: 'messages' }
  ];

  board = signal<MarketBoard | null>(null);
  farms = signal<Farm[]>([]);
  loading = signal(true);
  error = signal('');
  saving = signal(false);
  formError = signal('');
  tab = signal<'buy' | 'sell' | 'requests' | 'orders' | 'messages'>('buy');
  sheet = signal<'' | 'request' | 'product' | 'seller' | 'detail' | 'offer'>('');
  zoom = signal(1);
  query = signal('');
  category = signal('');
  region = signal('');
  district = signal('');
  price = signal('');
  distance = signal('');
  applied = signal({ q: '', category: '', region: '', district: '' });
  detailProduct = signal<MarketProduct | null>(null);
  detailPhoto = signal<Photo | null>(null);
  editingId = signal('');
  thread = signal('');
  messageBody = signal('');
  reviewScore = signal('5');
  reviewNote = signal('');
  sellerForm = signal<SellerDraft>(this.blankSeller());
  productForm = signal<ProductDraft>(this.blankProduct());
  demandForm = signal<DemandDraft>(this.blankDemand());
  offerForm = signal<OfferDraft>(this.blankOffer());

  stats = computed(() => this.board()?.stats ?? { products: 0, buyers: 0, sellers: 0, openRequests: 0, pendingOffers: 0, completed: 0 });
  products = computed(() => this.board()?.products ?? []);
  listings = computed(() => this.board()?.listings ?? []);
  reviewListings = computed(() => this.board()?.reviewListings ?? []);
  requests = computed(() => this.board()?.requests ?? []);
  offers = computed(() => this.board()?.offers ?? []);
  orders = computed(() => this.board()?.orders ?? []);
  messages = computed(() => this.board()?.messages ?? []);
  notices = computed(() => this.board()?.notices ?? []);
  ownSeller = computed(() => this.board()?.ownSeller ?? null);
  pendingSellers = computed(() => this.board()?.pendingSellers ?? []);
  administrator = computed(() => this.board()?.administrator === true);
  recent = computed(() => this.requests().slice(0, 5));
  openDemand = computed(() => this.requests().filter(row => row.status === 'OPEN' || row.status === 'OFFER_RECEIVED'));
  districts = computed(() => {
    const names = new Set<string>();
    for (const row of [...this.products(), ...this.listings(), ...this.reviewListings()]) {
      if (row.district) {
        names.add(row.district);
      }
    }
    return [...names].sort((left, right) => left.localeCompare(right));
  });
  shown = computed(() => {
    const band = this.price();
    const limit = Number(this.distance());
    return this.products().filter(product => {
      if (band === 'low' && product.priceAmount >= 2000) {
        return false;
      }
      if (band === 'mid' && (product.priceAmount < 2000 || product.priceAmount > 10000)) {
        return false;
      }
      if (band === 'high' && product.priceAmount <= 10000) {
        return false;
      }
      if (limit > 0) {
        const km = this.nearestKm(product.latitude, product.longitude);
        if (km == null || km > limit) {
          return false;
        }
      }
      return true;
    });
  });
  showCategories = computed(() => !this.loading() && this.shown().length === 0 && !this.filtersOn());
  filtersOn = computed(() => {
    const applied = this.applied();
    return !!(applied.q || applied.category || applied.region || applied.district || this.price() || this.distance());
  });
  pins = computed<TanzaniaPin[]>(() => {
    const products = this.shown().map(product => ({
      id: product.id,
      label: product.region,
      title: `${product.name}. ${product.sellerName}. ${product.region}.`,
      latitude: product.latitude,
      longitude: product.longitude,
      tone: 'ok' as const
    }));
    const sellers = new Map<string, MarketProduct>();
    for (const product of this.shown()) {
      if (!sellers.has(product.sellerName)) {
        sellers.set(product.sellerName, product);
      }
    }
    const supplierPins = [...sellers.values()].map(product => ({
      id: `seller-${product.id}`,
      label: product.region,
      title: `${product.sellerName}. ${product.region}.`,
      latitude: product.latitude + 0.22,
      longitude: product.longitude + 0.22,
      tone: 'warn' as const
    }));
    const demand = this.openDemand()
      .filter(row => row.latitude != null && row.longitude != null)
      .map(row => ({
        id: `demand-${row.id}`,
        label: row.region,
        title: `Buying request for ${row.productName}. ${row.region}.`,
        latitude: Number(row.latitude),
        longitude: Number(row.longitude),
        tone: 'alert' as const
      }));
    return [...products, ...supplierPins, ...demand];
  });
  threads = computed(() => {
    const requests = this.requests()
      .filter(row => row.ownRequest || this.administrator() || this.offers().some(offer => offer.requestId === row.id && offer.ownOffer))
      .map(row => ({ id: `request:${row.id}`, label: `Request · ${row.productName}` }));
    const orders = this.orders().map(row => ({ id: `order:${row.id}`, label: `Order · ${row.productName}` }));
    return [...orders, ...requests];
  });
  threadMessages = computed(() => {
    const key = this.thread();
    if (!key) {
      return [];
    }
    const [kind, id] = key.split(':');
    return this.messages().filter(row => kind === 'order' ? row.orderId === id : row.requestId === id);
  });

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe(fragment => {
      if (fragment === 'sell' || fragment === 'requests' || fragment === 'orders' || fragment === 'messages' || fragment === 'buy') {
        this.tab.set(fragment);
      } else {
        this.tab.set('buy');
      }
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      board: this.api.market(this.applied()),
      farms: this.api.farms()
    }).subscribe({
      next: ({ board, farms }) => {
        this.board.set(board);
        this.farms.set(farms);
        this.loading.set(false);
        this.saving.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.saving.set(false);
        this.error.set(apiError(error, 'The marketplace could not be loaded.'));
      }
    });
  }

  search(): void {
    this.applied.set({
      q: this.query().trim(),
      category: this.category(),
      region: this.region(),
      district: this.district()
    });
    this.load();
  }

  clearFilters(): void {
    this.query.set('');
    this.category.set('');
    this.region.set('');
    this.district.set('');
    this.price.set('');
    this.distance.set('');
    this.applied.set({ q: '', category: '', region: '', district: '' });
    this.load();
  }

  searchArea(): void {
    const farm = this.farms().find(item => this.regions.includes(item.region));
    if (!farm) {
      this.formError.set('None of the visible farms uses an official Tanzania region, so the region filter was left unchanged.');
      return;
    }
    this.region.set(farm.region);
    this.search();
  }

  zoomIn(): void {
    this.zoom.update(value => Math.min(2.2, Math.round((value + 0.15) * 100) / 100));
  }

  zoomOut(): void {
    this.zoom.update(value => Math.max(0.85, Math.round((value - 0.15) * 100) / 100));
  }

  focusPin(id: string): void {
    const product = this.shown().find(row => row.id === id);
    if (product) {
      this.openProduct(product);
    }
  }

  openRequest(product?: MarketProduct, photo?: Photo): void {
    const draft = this.blankDemand();
    if (product) {
      draft.productId = product.id;
      draft.productName = product.name;
      draft.category = product.category;
      draft.unit = product.unit;
      draft.grade = product.grade;
      draft.region = product.region;
      draft.district = product.district;
    } else if (photo) {
      draft.productName = photo.name;
      draft.category = photo.category;
    }
    this.demandForm.set(draft);
    this.formError.set('');
    this.sheet.set('request');
  }

  openProductForm(product?: MarketProduct): void {
    const seller = this.ownSeller();
    if (!seller || seller.approval !== 'APPROVED') {
      this.openSeller();
      this.formError.set(seller ? 'An administrator still has to approve this seller profile before a listing can be added.' : 'Register as a seller first. Your farm role stays the same.');
      return;
    }
    const draft = this.blankProduct();
    if (product) {
      draft.name = product.name;
      draft.category = product.category;
      draft.imageKey = product.imageKey;
      draft.description = product.description;
      draft.quantity = String(product.quantity);
      draft.unit = product.unit;
      draft.priceAmount = String(product.priceAmount);
      draft.minOrder = String(product.minOrder);
      draft.grade = product.grade;
      draft.region = product.region;
      draft.district = product.district;
      draft.latitude = product.latitude;
      draft.longitude = product.longitude;
      draft.fulfillment = product.fulfillment;
      this.editingId.set(product.id);
    } else {
      draft.region = seller.region;
      draft.district = seller.district;
      draft.latitude = seller.latitude;
      draft.longitude = seller.longitude;
      this.editingId.set('');
    }
    this.productForm.set(draft);
    this.formError.set('');
    this.sheet.set('product');
  }

  openSeller(): void {
    const seller = this.ownSeller();
    const draft = this.blankSeller();
    if (seller) {
      draft.name = seller.name;
      draft.region = seller.region;
      draft.district = seller.district;
      draft.phone = seller.phone;
      draft.latitude = seller.latitude;
      draft.longitude = seller.longitude;
      draft.summary = seller.summary;
    }
    this.sellerForm.set(draft);
    this.formError.set('');
    this.sheet.set('seller');
  }

  openProduct(product: MarketProduct): void {
    this.detailProduct.set(product);
    this.detailPhoto.set(this.photos.find(photo => photo.key === product.imageKey) || null);
    this.sheet.set('detail');
  }

  openCategory(photo: Photo): void {
    this.detailProduct.set(null);
    this.detailPhoto.set(photo);
    this.sheet.set('detail');
  }

  openOffer(request: MarketDemand): void {
    const seller = this.ownSeller();
    if (!seller || seller.approval !== 'APPROVED') {
      this.openSeller();
      this.formError.set('An approved seller profile is required before sending an offer. Your farm role stays the same.');
      return;
    }
    this.offerForm.set({
      requestId: request.id,
      productId: '',
      quantity: String(request.quantity),
      unit: request.unit,
      priceAmount: String(request.targetPrice),
      terms: ''
    });
    this.formError.set('');
    this.sheet.set('offer');
  }

  closeSheet(): void {
    this.sheet.set('');
    this.formError.set('');
  }

  saveSeller(): void {
    const form = this.sellerForm();
    if (form.name.trim().length < 2 || !form.region || form.district.trim().length < 2 || form.phone.trim().length < 7 || form.summary.trim().length < 2) {
      this.formError.set('Fill in the seller name, region, district, phone, and description.');
      return;
    }
    if (form.latitude == null || form.longitude == null || !inTanzania(form.latitude, form.longitude)) {
      this.formError.set('Place the pin inside Tanzania.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    const body = { ...form };
    const seller = this.ownSeller();
    const request = seller ? this.api.updateMarketSeller(seller.id, body) : this.api.registerMarketSeller(body);
    request.subscribe({
      next: () => {
        this.closeSheet();
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The seller profile was not saved.'));
      }
    });
  }

  saveProduct(): void {
    const form = this.productForm();
    const quantity = Number(form.quantity);
    const price = Number(form.priceAmount);
    const minOrder = Number(form.minOrder);
    if (form.name.trim().length < 2 || !form.category || !form.imageKey || form.description.trim().length < 2 || form.grade.trim().length < 1 || !form.region || form.district.trim().length < 2) {
      this.formError.set('Fill in the product name, category, photo, description, grade, region, and district.');
      return;
    }
    if (!Number.isFinite(quantity) || quantity < 0 || !Number.isFinite(price) || price <= 0 || !Number.isFinite(minOrder) || minOrder <= 0) {
      this.formError.set('Enter the quantity, a TZS price, and a minimum order greater than zero.');
      return;
    }
    if (form.latitude == null || form.longitude == null || !inTanzania(form.latitude, form.longitude)) {
      this.formError.set('Place the pin inside Tanzania.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    const body = { ...form, quantity, priceAmount: price, minOrder };
    this.api.saveMarketProduct(body, this.editingId() || undefined).subscribe({
      next: () => {
        this.closeSheet();
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The listing was not saved.'));
      }
    });
  }

  saveRequest(): void {
    const form = this.demandForm();
    const quantity = Number(form.quantity);
    const targetPrice = Number(form.targetPrice);
    if (form.productName.trim().length < 2 || !form.category || form.grade.trim().length < 1 || !form.region || form.district.trim().length < 2 || !form.neededOn) {
      this.formError.set('Fill in the product, category, quantity, grade, target price, delivery location, and date.');
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(targetPrice) || targetPrice <= 0) {
      this.formError.set('Enter a quantity and a target price in TZS.');
      return;
    }
    if (form.neededOn < this.today()) {
      this.formError.set('The delivery date cannot be in the past.');
      return;
    }
    if ((form.latitude == null) !== (form.longitude == null) || (form.latitude != null && form.longitude != null && !inTanzania(form.latitude, form.longitude))) {
      this.formError.set('Place the delivery pin inside Tanzania, or leave the map unset.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    this.api.postBuyingRequest({
      ...form,
      productId: form.productId || null,
      quantity,
      targetPrice,
      latitude: form.latitude,
      longitude: form.longitude
    }).subscribe({
      next: () => {
        this.closeSheet();
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The buying request was not posted.'));
      }
    });
  }

  saveOffer(): void {
    const form = this.offerForm();
    const quantity = Number(form.quantity);
    const price = Number(form.priceAmount);
    if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(price) || price <= 0 || form.terms.trim().length < 2) {
      this.formError.set('Enter the quantity, the TZS price, and the delivery or collection terms.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    this.api.sendMarketOffer(form.requestId, {
      productId: form.productId || null,
      quantity,
      unit: form.unit,
      priceAmount: price,
      terms: form.terms.trim()
    }).subscribe({
      next: () => {
        this.closeSheet();
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.formError.set(apiError(error, 'The offer was not sent.'));
      }
    });
  }

  verifySeller(seller: MarketSeller, accept: boolean): void {
    this.saving.set(true);
    this.api.verifyMarketSeller(seller.id, accept).subscribe({
      next: () => this.load(),
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The seller decision was not saved.'));
      }
    });
  }

  moderate(product: MarketProduct, accept: boolean): void {
    this.saving.set(true);
    this.api.moderateMarketProduct(product.id, accept).subscribe({
      next: () => this.load(),
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The listing decision was not saved.'));
      }
    });
  }

  decide(offer: MarketOffer, accept: boolean): void {
    this.saving.set(true);
    this.api.decideMarketOffer(offer.id, accept).subscribe({
      next: () => this.load(),
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The offer decision was not saved.'));
      }
    });
  }

  advance(order: MarketOrder, status: string): void {
    this.saving.set(true);
    this.api.advanceMarketOrder(order.id, status).subscribe({
      next: () => this.load(),
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The order was not updated.'));
      }
    });
  }

  sendMessage(): void {
    const key = this.thread();
    const body = this.messageBody().trim();
    if (!key || !body) {
      this.error.set('Choose a request or order, then write the message.');
      return;
    }
    const [kind, id] = key.split(':');
    this.saving.set(true);
    this.api.sendMarketMessage({
      requestId: kind === 'request' ? id : null,
      orderId: kind === 'order' ? id : null,
      body
    }).subscribe({
      next: () => {
        this.messageBody.set('');
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The message was not sent.'));
      }
    });
  }

  saveReview(order: MarketOrder): void {
    const score = Number(this.reviewScore());
    if (!Number.isInteger(score) || score < 1 || score > 5) {
      this.error.set('Choose a rating from 1 to 5.');
      return;
    }
    this.saving.set(true);
    this.api.reviewMarketOrder(order.id, score, this.reviewNote().trim()).subscribe({
      next: () => {
        this.reviewNote.set('');
        this.load();
      },
      error: error => {
        this.saving.set(false);
        this.error.set(apiError(error, 'The review was not saved.'));
      }
    });
  }

  placeSeller(point: { latitude: number; longitude: number }): void {
    if (!inTanzania(point.latitude, point.longitude)) {
      this.formError.set('Place the pin inside Tanzania.');
      return;
    }
    this.formError.set('');
    this.sellerForm.update(form => ({ ...form, latitude: point.latitude, longitude: point.longitude }));
  }

  placeProduct(point: { latitude: number; longitude: number }): void {
    if (!inTanzania(point.latitude, point.longitude)) {
      this.formError.set('Place the pin inside Tanzania.');
      return;
    }
    this.formError.set('');
    this.productForm.update(form => ({ ...form, latitude: point.latitude, longitude: point.longitude }));
  }

  placeDemand(point: { latitude: number; longitude: number }): void {
    if (!inTanzania(point.latitude, point.longitude)) {
      this.formError.set('Place the pin inside Tanzania.');
      return;
    }
    this.formError.set('');
    this.demandForm.update(form => ({ ...form, latitude: point.latitude, longitude: point.longitude }));
  }

  offersFor(id: string): MarketOffer[] {
    return this.offers().filter(offer => offer.requestId === id);
  }

  photoFor(key: string): Photo {
    return this.photos.find(photo => photo.key === key) || this.photos[0];
  }

  unitWord(unit: string): string {
    return this.units.find(item => item.id === unit)?.label || unit.toLowerCase();
  }

  unitEach(unit: string): string {
    if (unit === 'LITRE') {
      return 'litre';
    }
    if (unit === 'PIECE') {
      return 'piece';
    }
    return this.unitWord(unit);
  }

  money(amount: number): string {
    const digits = Number.isInteger(Number(amount)) ? 0 : 2;
    return `TZS ${new Intl.NumberFormat('en-TZ', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(Number(amount))}`;
  }

  count(value: number): string {
    return new Intl.NumberFormat('en-TZ').format(value);
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      OPEN: 'Open',
      OFFER_RECEIVED: 'Offers received',
      CLOSED: 'Closed',
      SENT: 'Offer sent',
      ACCEPTED: 'Accepted',
      PROCESSING: 'Processing',
      READY: 'Ready for collection',
      IN_TRANSIT: 'In transit',
      COMPLETED: 'Completed',
      CANCELLED: 'Cancelled',
      REJECTED: 'Rejected',
      WITHDRAWN: 'Withdrawn',
      ACTIVE: 'Available',
      HIDDEN: 'Hidden',
      PENDING: 'Waiting for approval',
      APPROVED: 'Verified'
    };
    return labels[status] || status;
  }

  fulfillmentLabel(value: string): string {
    if (value === 'COLLECTION') {
      return 'Collection';
    }
    if (value === 'DELIVERY') {
      return 'Delivery';
    }
    return 'Collection or delivery';
  }

  day(value: string): string {
    const parsed = new Date(`${value}T00:00:00Z`);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(parsed);
  }

  ago(value: string): string {
    const then = new Date(value).getTime();
    if (!Number.isFinite(then)) {
      return '';
    }
    const minutes = Math.round((Date.now() - then) / 60000);
    if (minutes < 1) {
      return 'Just now';
    }
    if (minutes < 60) {
      return `${minutes} minutes ago`;
    }
    const hours = Math.round(minutes / 60);
    if (hours < 24) {
      return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
    }
    const days = Math.round(hours / 24);
    return days === 1 ? '1 day ago' : `${days} days ago`;
  }

  today(): string {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Dar_es_Salaam', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  }

  ratingLabel(rating: number | null, reviews: number): string {
    if (rating == null || reviews < 1) {
      return 'No reviews are stored.';
    }
    return `${rating.toFixed(1)} from ${reviews} review${reviews === 1 ? '' : 's'}`;
  }

  setSeller(key: 'name' | 'region' | 'district' | 'phone' | 'summary', event: Event): void {
    const value = this.text(event);
    this.sellerForm.update(form => ({ ...form, [key]: value }));
  }

  setProduct(key: 'name' | 'category' | 'description' | 'quantity' | 'unit' | 'priceAmount' | 'minOrder' | 'grade' | 'region' | 'district' | 'fulfillment', event: Event): void {
    const value = this.text(event);
    this.productForm.update(form => ({ ...form, [key]: value }));
  }

  choosePhoto(photo: Photo): void {
    this.productForm.update(form => ({
      ...form,
      imageKey: photo.key,
      name: form.name || photo.name,
      category: photo.category
    }));
  }

  setDemand(key: 'productName' | 'category' | 'quantity' | 'unit' | 'grade' | 'targetPrice' | 'region' | 'district' | 'neededOn' | 'note', event: Event): void {
    const value = this.text(event);
    this.demandForm.update(form => ({ ...form, [key]: value }));
  }

  setOffer(key: 'productId' | 'quantity' | 'unit' | 'priceAmount' | 'terms', event: Event): void {
    const value = this.text(event);
    this.offerForm.update(form => ({ ...form, [key]: value }));
  }

  categoryDetail(): Photo | null {
    return this.detailProduct() ? null : this.detailPhoto();
  }

  openCategoryRequest(photo: Photo): void {
    this.openRequest(undefined, photo);
  }

  averageLabel(rating: number | null): string {
    if (rating == null) {
      return 'No reviews are stored.';
    }
    return `Seller rating ${rating.toFixed(1)}`;
  }

  text(event: Event): string {
    const target = event.target;
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
      return target.value;
    }
    return '';
  }

  canOffer(request: MarketDemand): boolean {
    const seller = this.ownSeller();
    return !!seller && seller.approval === 'APPROVED' && !request.ownRequest && (request.status === 'OPEN' || request.status === 'OFFER_RECEIVED');
  }

  nextSteps(order: MarketOrder): string[] {
    if (order.status === 'COMPLETED' || order.status === 'CANCELLED') {
      return [];
    }
    const steps = ['CANCELLED'];
    const seller = order.ownSale || this.administrator();
    if (seller && order.status === 'ACCEPTED') {
      steps.unshift('PROCESSING');
    }
    if (seller && order.status === 'PROCESSING' && order.fulfillment !== 'DELIVERY') {
      steps.unshift('READY');
    }
    if (seller && order.status === 'PROCESSING' && order.fulfillment !== 'COLLECTION') {
      steps.unshift('IN_TRANSIT');
    }
    if ((order.ownPurchase || order.ownSale || this.administrator()) && (order.status === 'READY' || order.status === 'IN_TRANSIT')) {
      steps.unshift('COMPLETED');
    }
    return steps;
  }

  private nearestKm(latitude: number, longitude: number): number | null {
    const points = this.farms().filter(farm => Number.isFinite(Number(farm.latitude)) && Number.isFinite(Number(farm.longitude)));
    if (!points.length) {
      return null;
    }
    return Math.min(...points.map(farm => this.km(latitude, longitude, Number(farm.latitude), Number(farm.longitude))));
  }

  private km(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const earth = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
    return 2 * earth * Math.asin(Math.sqrt(a));
  }

  private blankSeller(): SellerDraft {
    return { name: '', region: '', district: '', phone: '', latitude: null, longitude: null, summary: '' };
  }

  private blankProduct(): ProductDraft {
    return {
      name: '', category: 'Cereals', imageKey: 'MAIZE', description: '', quantity: '', unit: 'KG',
      priceAmount: '', minOrder: '1', grade: '', region: '', district: '', latitude: null, longitude: null, fulfillment: 'BOTH'
    };
  }

  private blankDemand(): DemandDraft {
    return {
      productId: '', productName: '', category: 'Cereals', quantity: '', unit: 'KG', grade: '', targetPrice: '',
      region: '', district: '', latitude: null, longitude: null, neededOn: '', note: ''
    };
  }

  private blankOffer(): OfferDraft {
    return { requestId: '', productId: '', quantity: '', unit: 'KG', priceAmount: '', terms: '' };
  }
}
