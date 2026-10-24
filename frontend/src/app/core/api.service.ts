import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccessCatalog, ActivityItem, AdviceNote, Adviser, AlertItem, Animal, Breed, Dashboard, Farm, Facility, FishCycle, HealthCase, HerdBatch, InventoryItem, Page, ProductionRecord, SearchHit, Settings, Species, StaffMember, UserAccess, WorkTask } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  dashboard() {
    return this.http.get<Dashboard>('/api/v1/dashboard');
  }

  activity() {
    return this.http.get<ActivityItem[]>('/api/v1/dashboard/activity');
  }

  farms() {
    return this.http.get<Farm[]>('/api/v1/farms');
  }

  farm(id: string) {
    return this.http.get<{ farm: Farm; facilities: Facility[] }>(`/api/v1/farms/${id}`);
  }

  createFarm(body: unknown) {
    return this.http.post<Farm>('/api/v1/farms', body);
  }

  updateFarm(id: string, body: unknown) {
    return this.http.put<Farm>(`/api/v1/farms/${id}`, body);
  }

  animals(params: { farmId?: string; speciesId?: string; q?: string; page?: number; size?: number }) {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<Page<Animal>>('/api/v1/animals', { params: httpParams });
  }

  createAnimal(body: unknown) {
    return this.http.post<Animal>('/api/v1/animals', body);
  }

  transferAnimal(id: string, body: unknown) {
    return this.http.post<Animal>(`/api/v1/animals/${id}/transfer`, body);
  }

  batches() {
    return this.http.get<HerdBatch[]>('/api/v1/batches');
  }

  updateBatchQuantity(id: string, quantity: number) {
    return this.http.patch<HerdBatch>(`/api/v1/batches/${id}`, { quantity });
  }

  accessUsers() {
    return this.http.get<UserAccess[]>('/api/v1/access/users');
  }

  assignAccess(userId: string, body: { roleCode: string; accessScope: string; farmIds: string[] }) {
    return this.http.put<UserAccess>(`/api/v1/access/users/${userId}/scope`, body);
  }

  species() {
    return this.http.get<Species[]>('/api/v1/species');
  }

  breeds(speciesId: string) {
    return this.http.get<Breed[]>('/api/v1/breeds', { params: { speciesId } });
  }

  production() {
    return this.http.get<ProductionRecord[]>('/api/v1/production');
  }

  recordProduction(body: unknown) {
    return this.http.post<ProductionRecord>('/api/v1/production', body);
  }

  inventory() {
    return this.http.get<InventoryItem[]>('/api/v1/inventory');
  }

  createInventory(body: unknown) {
    return this.http.post<InventoryItem>('/api/v1/inventory', body);
  }

  adjustInventory(id: string, body: unknown) {
    return this.http.post<InventoryItem>(`/api/v1/inventory/${id}/adjustments`, body);
  }

  staff() {
    return this.http.get<StaffMember[]>('/api/v1/staff');
  }

  createStaff(body: unknown) {
    return this.http.post<StaffMember>('/api/v1/staff', body);
  }

  alerts() {
    return this.http.get<AlertItem[]>('/api/v1/alerts');
  }

  tasks() {
    return this.http.get<WorkTask[]>('/api/v1/tasks');
  }

  fishCycles() {
    return this.http.get<FishCycle[]>('/api/v1/fish-cycles');
  }

  healthCases() {
    return this.http.get<HealthCase[]>('/api/v1/health-cases');
  }

  reportDisease(body: {
    farmId: string;
    diseaseName: string;
    status: string;
    animalsAffected: number;
    species: string;
    signs: string;
    noticedOn: string;
  }) {
    return this.http.post<{
      id: string;
      alertId: string;
      farmId: string;
      farmName: string;
      diseaseName: string;
      animalsAffected: number;
      severity: string;
      noticedOn: string;
      signs: string;
    }>('/api/v1/disease-reports', body);
  }

  reportAnimalHealth(body: {
    farmId: string;
    outcome: string;
    animals: number;
    condition: string;
    species: string;
    notes: string;
    occurredOn: string;
    batchId: string | null;
    animalId: string | null;
  }) {
    return this.http.post<{
      id: string;
      alertId: string | null;
      farmId: string;
      farmName: string;
      outcome: string;
      animals: number;
      herdQuantity: number | null;
    }>('/api/v1/health-reports', body);
  }

  advice() {
    return this.http.get<AdviceNote[]>('/api/v1/advice');
  }

  publishAdvice(body: { topic: string; advice: string; medication: string; medicationNote: string }) {
    return this.http.post<AdviceNote>('/api/v1/advice', body);
  }

  removeAdvice(id: string) {
    return this.http.delete<void>(`/api/v1/advice/${id}`);
  }

  advisers(params: { talent?: string; availability?: string; load?: string } = {}) {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) {
        httpParams = httpParams.set(key, value);
      }
    });
    return this.http.get<Adviser[]>('/api/v1/advisers', { params: httpParams });
  }

  updateAdviserProfile(body: { talents: string[]; availability: string; availabilityNote: string }) {
    return this.http.put<Adviser>('/api/v1/advisers/me', body);
  }

  rateAdviser(id: string, body: { score: number; note: string }) {
    return this.http.post<Adviser>(`/api/v1/advisers/${id}/rating`, body);
  }

  recommendAdviser(id: string, body: { note: string }) {
    return this.http.post<Adviser>(`/api/v1/advisers/${id}/recommendation`, body);
  }

  withdrawAdviserRecommendation(id: string) {
    return this.http.delete<Adviser>(`/api/v1/advisers/${id}/recommendation`);
  }

  settings() {
    return this.http.get<Settings>('/api/v1/settings');
  }

  accessCatalog() {
    return this.http.get<AccessCatalog>('/api/v1/access/roles');
  }

  search(q: string) {
    return this.http.get<SearchHit[]>('/api/v1/search', { params: { q } });
  }

  exportDashboard() {
    return this.http.get('/api/v1/reports/dashboard.csv', { responseType: 'blob' });
  }
}
