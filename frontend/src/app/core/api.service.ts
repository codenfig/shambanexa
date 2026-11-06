import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AccessCatalog, ActivityItem, AdviceNote, Adviser, AlertItem, Animal, AttendanceMark, Breed, Dashboard, Farm, FarmClimate, FarmForecast, Facility, FarmerRequest, FishCycle, HealthBoard, HealthCase, HealthProtocol, HealthTreatment, HealthVaccination, HerdBatch, InventoryAdjustment, InventoryItem, InventoryMovement, InventoryReport, MarketBoard, MarketDemand, MarketMessage, MarketOffer, MarketOrder, MarketProduct, MarketSeller, MasterCategory, MasterDetail, MasterImport, MasterPage, MasterRecord, MedicalBoard, MedicalSupplier, MedicineOrder, MovementRecord, Page, ProductionRecord, SearchHit, Settings, Species, StaffCompetency, StaffGoal, StaffMember, StaffReview, StaffShift, StaffTimesheet, StaffTraining, StockAlert, StockBatch, SupplierMedicine, SupplierRecord, UserAccess, WorkforceBundle, WorkTask } from './models';

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

  farmClimate(id: string) {
    return this.http.get<FarmClimate>(`/api/v1/farms/${id}/climate`);
  }

  farmForecast(id: string) {
    return this.http.get<FarmForecast>(`/api/v1/farms/${id}/forecast`);
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

  movements() {
    return this.http.get<MovementRecord[]>('/api/v1/animals/movements');
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

  masterCategories() {
    return this.http.get<MasterCategory[]>('/api/v1/master');
  }

  masterPage(category: string, params: { q?: string; status?: string; parentId?: string; sort?: string; direction?: string; page?: number; size?: number }) {
    return this.http.get<MasterPage>(`/api/v1/master/${category}`, { params });
  }

  masterDetail(category: string, id: string) {
    return this.http.get<MasterDetail>(`/api/v1/master/${category}/${id}`);
  }

  createMasterSpecies(body: unknown) {
    return this.http.post<MasterRecord>('/api/v1/master/species', body);
  }

  updateMasterSpecies(id: string, body: unknown) {
    return this.http.put<MasterRecord>(`/api/v1/master/species/${id}`, body);
  }

  masterSpeciesStatus(id: string, active: boolean) {
    return this.http.post<MasterRecord>(`/api/v1/master/species/${id}/status`, { active });
  }

  deleteMasterSpecies(id: string) {
    return this.http.delete<void>(`/api/v1/master/species/${id}`);
  }

  createMasterBreed(body: unknown) {
    return this.http.post<MasterRecord>('/api/v1/master/breeds', body);
  }

  updateMasterBreed(id: string, body: unknown) {
    return this.http.put<MasterRecord>(`/api/v1/master/breeds/${id}`, body);
  }

  masterBreedStatus(id: string, active: boolean) {
    return this.http.post<MasterRecord>(`/api/v1/master/breeds/${id}/status`, { active });
  }

  deleteMasterBreed(id: string) {
    return this.http.delete<void>(`/api/v1/master/breeds/${id}`);
  }

  exportMaster(category: string, format: 'csv' | 'xlsx') {
    return this.http.get(`/api/v1/master/${category}/export`, { params: { format }, responseType: 'blob', observe: 'response' });
  }

  importMaster(category: 'species' | 'breeds', file: File, apply: boolean, updateExisting: boolean) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<MasterImport>(`/api/v1/master/${category}/import`, body, {
      params: { apply: String(apply), updateExisting: String(updateExisting) }
    });
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

  receiveInventory(id: string, body: unknown) {
    return this.http.post<InventoryItem>(`/api/v1/inventory/${id}/receipts`, body);
  }

  issueInventory(id: string, body: unknown) {
    return this.http.post<InventoryItem>(`/api/v1/inventory/${id}/issues`, body);
  }

  transferInventory(id: string, body: unknown) {
    return this.http.post<InventoryItem>(`/api/v1/inventory/${id}/transfers`, body);
  }

  adjustInventory(id: string, body: unknown) {
    return this.http.post<InventoryAdjustment>(`/api/v1/inventory/${id}/adjustments`, body);
  }

  decideAdjustment(id: string, approve: boolean) {
    return this.http.post<InventoryAdjustment>(`/api/v1/inventory/adjustments/${id}/decision`, { approve });
  }

  inventoryLedger() {
    return this.http.get<InventoryMovement[]>('/api/v1/inventory/ledger');
  }

  inventoryAdjustments() {
    return this.http.get<InventoryAdjustment[]>('/api/v1/inventory/adjustments');
  }

  inventoryBatches() {
    return this.http.get<StockBatch[]>('/api/v1/inventory/batches');
  }

  inventoryAlerts() {
    return this.http.get<StockAlert[]>('/api/v1/inventory/alerts');
  }

  acknowledgeInventoryAlert(body: { batchId: string; alertKind: string; assigneeName?: string; note?: string }) {
    return this.http.post<StockAlert>('/api/v1/inventory/alerts', body);
  }

  supplierRecords() {
    return this.http.get<SupplierRecord[]>('/api/v1/inventory/supplier-records');
  }

  saveSupplierRecord(body: unknown) {
    return this.http.post<SupplierRecord>('/api/v1/inventory/supplier-records', body);
  }

  inventoryReport() {
    return this.http.get<InventoryReport>('/api/v1/inventory/reports');
  }

  createMasterReference(kind: string, body: unknown) {
    return this.http.post<MasterRecord>(`/api/v1/master/references/${kind}`, body);
  }

  updateMasterReference(id: string, body: unknown) {
    return this.http.put<MasterRecord>(`/api/v1/master/references/${id}`, body);
  }

  masterReferenceStatus(id: string, active: boolean) {
    return this.http.post<MasterRecord>(`/api/v1/master/references/${id}/status`, { active });
  }

  deleteMasterReference(id: string) {
    return this.http.delete<void>(`/api/v1/master/references/${id}`);
  }

  staff() {
    return this.http.get<StaffMember[]>('/api/v1/staff');
  }

  createStaff(body: unknown) {
    return this.http.post<StaffMember>('/api/v1/staff', body);
  }

  workforce() {
    return this.http.get<WorkforceBundle>('/api/v1/workforce');
  }

  requests() {
    return this.http.get<FarmerRequest[]>('/api/v1/requests');
  }

  createRequest(body: { farmId: string; subject: string; message: string }) {
    return this.http.post<FarmerRequest>('/api/v1/requests', body);
  }

  replyRequest(id: string, reply: string) {
    return this.http.post<FarmerRequest>(`/api/v1/requests/${id}/reply`, { reply });
  }

  saveAttendance(body: { staffId: string; workDate: string; mark: string }) {
    return this.http.post<AttendanceMark>('/api/v1/staff/attendance', body);
  }

  saveShift(body: { staffId: string; weekday: number; starts: string; ends: string }) {
    return this.http.post<StaffShift>('/api/v1/staff/shifts', body);
  }

  saveTimesheet(body: { staffId: string; workDate: string; hours: number; note: string }) {
    return this.http.post<StaffTimesheet>('/api/v1/staff/timesheets', body);
  }

  saveReview(body: { staffId: string; score: number; reviewedOn: string; notes: string }) {
    return this.http.post<StaffReview>('/api/v1/staff/reviews', body);
  }

  saveTraining(body: { staffId: string; title: string; completedOn: string | null }) {
    return this.http.post<StaffTraining>('/api/v1/staff/training', body);
  }

  saveGoal(body: { staffId: string; title: string }) {
    return this.http.post<StaffGoal>('/api/v1/staff/goals', body);
  }

  completeGoal(id: string) {
    return this.http.post<StaffGoal>(`/api/v1/staff/goals/${id}/done`, {});
  }

  saveCompetency(body: { staffId: string; skill: string; level: string }) {
    return this.http.post<StaffCompetency>('/api/v1/staff/competencies', body);
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

  healthCenter() {
    return this.http.get<HealthBoard>('/api/v1/health-center');
  }

  recordTreatment(body: unknown) {
    return this.http.post<HealthTreatment>('/api/v1/health-center/treatments', body);
  }

  updateTreatment(id: string, body: unknown) {
    return this.http.put<HealthTreatment>(`/api/v1/health-center/treatments/${id}`, body);
  }

  saveVaccinationProtocol(body: unknown) {
    return this.http.post<HealthProtocol>('/api/v1/health-center/protocols', body);
  }

  setVaccinationProtocolActive(id: string, active: boolean) {
    return this.http.put<HealthProtocol>(`/api/v1/health-center/protocols/${id}`, { active });
  }

  recordVaccination(body: unknown) {
    return this.http.post<HealthVaccination>('/api/v1/health-center/vaccinations', body);
  }

  updateVaccination(id: string, body: unknown) {
    return this.http.put<HealthVaccination>(`/api/v1/health-center/vaccinations/${id}`, body);
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

  updateSettings(body: Pick<Settings, 'currencyCode' | 'timezone' | 'seasonLabel' | 'locale'> & { name: string; code: string }) {
    return this.http.put<Settings>('/api/v1/settings', body);
  }

  accessCatalog() {
    return this.http.get<AccessCatalog>('/api/v1/access/roles');
  }

  createRole(body: { label: string; description: string; permissions: string[] }) {
    return this.http.post<{ code: string; label: string }>('/api/v1/access/roles', body);
  }

  updateRolePermissions(code: string, permissions: string[]) {
    return this.http.put<void>(`/api/v1/access/roles/${encodeURIComponent(code)}/permissions`, { permissions });
  }

  search(q: string) {
    return this.http.get<SearchHit[]>('/api/v1/search', { params: { q } });
  }

  exportDashboard() {
    return this.http.get('/api/v1/reports/dashboard.csv', { responseType: 'blob' });
  }

  medicalSuppliers(params: { q?: string; animal?: string; category?: string; region?: string; district?: string } = {}) {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) {
        httpParams = httpParams.set(key, value);
      }
    });
    return this.http.get<MedicalBoard>('/api/v1/medical-suppliers', { params: httpParams });
  }

  registerMedicalSupplier(body: unknown) {
    return this.http.post<MedicalSupplier>('/api/v1/medical-suppliers', body);
  }

  updateMedicalSupplier(id: string, body: unknown) {
    return this.http.put<MedicalSupplier>(`/api/v1/medical-suppliers/${id}`, body);
  }

  verifyMedicalSupplier(id: string, approve: boolean) {
    return this.http.post<MedicalSupplier>(`/api/v1/medical-suppliers/${id}/verification`, { approve });
  }

  saveSupplierMedicine(supplierId: string, body: unknown, medicineId?: string) {
    if (medicineId) {
      return this.http.put<SupplierMedicine>(`/api/v1/medical-suppliers/${supplierId}/medicines/${medicineId}`, body);
    }
    return this.http.post<SupplierMedicine>(`/api/v1/medical-suppliers/${supplierId}/medicines`, body);
  }

  requestMedicine(body: unknown) {
    return this.http.post<MedicineOrder>('/api/v1/medical-suppliers/requests', body);
  }

  prescribeMedicine(id: string, approve: boolean, note: string) {
    return this.http.post<MedicineOrder>(`/api/v1/medical-suppliers/requests/${id}/prescription`, { approve, note });
  }

  decideMedicineRequest(id: string, action: string, note: string) {
    return this.http.post<MedicineOrder>(`/api/v1/medical-suppliers/requests/${id}/decision`, { action, note });
  }

  market(params: { q?: string; category?: string; region?: string; district?: string } = {}) {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) {
        httpParams = httpParams.set(key, value);
      }
    });
    return this.http.get<MarketBoard>('/api/v1/market', { params: httpParams });
  }

  registerMarketSeller(body: unknown) {
    return this.http.post<MarketSeller>('/api/v1/market/sellers', body);
  }

  updateMarketSeller(id: string, body: unknown) {
    return this.http.put<MarketSeller>(`/api/v1/market/sellers/${id}`, body);
  }

  verifyMarketSeller(id: string, accept: boolean) {
    return this.http.post<MarketSeller>(`/api/v1/market/sellers/${id}/verification`, { accept });
  }

  saveMarketProduct(body: unknown, productId?: string) {
    if (productId) {
      return this.http.put<MarketProduct>(`/api/v1/market/products/${productId}`, body);
    }
    return this.http.post<MarketProduct>('/api/v1/market/products', body);
  }

  moderateMarketProduct(id: string, accept: boolean) {
    return this.http.post<MarketProduct>(`/api/v1/market/products/${id}/moderation`, { accept });
  }

  postBuyingRequest(body: unknown) {
    return this.http.post<MarketDemand>('/api/v1/market/requests', body);
  }

  sendMarketOffer(requestId: string, body: unknown) {
    return this.http.post<MarketOffer>(`/api/v1/market/requests/${requestId}/offers`, body);
  }

  decideMarketOffer(id: string, accept: boolean) {
    return this.http.post<MarketOrder | null>(`/api/v1/market/offers/${id}/decision`, { accept });
  }

  advanceMarketOrder(id: string, status: string) {
    return this.http.post<MarketOrder>(`/api/v1/market/orders/${id}/status`, { status });
  }

  sendMarketMessage(body: unknown) {
    return this.http.post<MarketMessage>('/api/v1/market/messages', body);
  }

  reviewMarketOrder(id: string, score: number, note: string) {
    return this.http.post<void>(`/api/v1/market/orders/${id}/review`, { score, note });
  }
}
