export interface AuthSession {
  token: string | null;
  userId: string;
  email: string;
  fullName: string;
  jobTitle: string;
  roleLabel: string;
  allFarms: boolean;
  farmIds: string[];
  accessScope: 'OWN_FARM' | 'SELECTED_FARMS' | 'ALL_FARMS';
  canManageAccess: boolean;
  canWriteOperations: boolean;
  canAdvise: boolean;
  phone?: string | null;
  hasPhoto?: boolean;
}

export interface GeoPoint {
  latitude: number;
  longitude: number;
}

export interface UserAccess {
  userId: string;
  fullName: string;
  email: string;
  roleCode: string;
  roleLabel: string;
  accessScope: 'OWN_FARM' | 'SELECTED_FARMS' | 'ALL_FARMS';
  farmIds: string[];
  farmNames: string[];
}

export interface Dashboard {
  generatedAt: string;
  seasonLabel: string;
  currencyCode: string;
  totalAnimals: number;
  animalChangePercent: number | null;
  farmCount: number;
  newFarmsThisQuarter: number;
  milkTodayLitres: number;
  milkDeltaVsYesterday: number;
  revenue: number;
  revenueChangePercent: number | null;
  fieldCount: number;
  regionCount: number;
  reconciled: boolean;
  species: SpeciesCount[];
  locations: FarmPin[];
  trends: TrendPoint[];
  alerts: AlertItem[];
  activity: ActivityItem[];
  subscription: { plan: string; validUntil: string };
  openAlertCount: number;
}

export interface SpeciesCount {
  name: string;
  count: number;
  color: string;
}

export interface FarmPin {
  id: string;
  name: string;
  region: string;
  status: string;
  latitude: number;
  longitude: number;
  animalCount: number;
}

export interface TrendPoint {
  month: string;
  milkLitres: number;
  revenue: number;
}

export interface AlertItem {
  id: string;
  severity: string;
  title: string;
  body: string;
  status: string;
  farmId: string | null;
  farmName: string | null;
  occurredAt: string;
  sourceType?: string | null;
}

export interface ActivityItem {
  id: string;
  eventType: string;
  title: string;
  detail: string;
  farmId: string | null;
  occurredAt: string;
  farmName?: string | null;
  farmCode?: string | null;
}

export interface ForecastDay {
  date: string;
  label: string;
  highC: number | null;
  lowC: number | null;
  rainMm: number | null;
  rainChance: number | null;
  humidity: number | null;
  windMs: number | null;
}

export interface ReportedIllness {
  id: string;
  title: string;
  sourceType: string;
  farmName: string | null;
  notes: string | null;
  openedOn: string | null;
}

export interface FarmForecast {
  farmId: string;
  farmName: string;
  available: boolean;
  message: string | null;
  source: string;
  attribution: string;
  retrievedAt: string | null;
  cached: boolean;
  currentTemperatureC: number | null;
  currentHumidity: number | null;
  currentRainMm: number | null;
  currentWindMs: number | null;
  days: ForecastDay[];
  reported: ReportedIllness[];
  checks: string[];
}

export interface FarmClimate {
  farmId: string;
  farmName: string;
  available: boolean;
  message: string | null;
  source: string;
  attribution: string;
  retrievedAt: string | null;
  cached: boolean;
  start: string | null;
  end: string | null;
  days: number;
  rainfallMm: number | null;
  rainfallNormalMm: number | null;
  rainfallBalance: string | null;
  evapotranspirationMm: number | null;
  evapotranspirationNormalMm: number | null;
  evapotranspirationBalance: string | null;
  averageSurfaceWetness: number | null;
  averageRootWetness: number | null;
  rootWetnessNormal: number | null;
  rootWetnessBalance: string | null;
  averageMaxC: number | null;
  averageMinC: number | null;
  averageHumidity: number | null;
  averageDewPointC: number | null;
  averageWetBulbC: number | null;
  hotDays: number | null;
  coldNights: number | null;
  heatStressDays: number | null;
  growingDegreeDays: number | null;
  averageSunlightMj: number | null;
  sunlightDays: number | null;
  averageParMj: number | null;
  parNormalMj: number | null;
  averageWindMs: number | null;
  dryDays: number | null;
  longestDrySpell: number | null;
}

export interface Farm {
  id: string;
  code: string;
  name: string;
  region: string;
  status: string;
  latitude: number;
  longitude: number;
  timezone: string;
  address: string;
  managerName: string | null;
  contactPhone: string | null;
  establishedOn: string | null;
  animalCount: number;
  facilityCount: number;
  version: number;
  updatedAt: string | null;
  boundary: GeoPoint[];
}

export interface Facility {
  id: string;
  code: string;
  name: string;
  facilityType: string;
  status: string;
  capacity: number | null;
  capacityUnit: string | null;
  supervisorName: string | null;
  createdAt: string | null;
}

export interface Animal {
  id: string;
  tag: string;
  name: string | null;
  sex: string;
  status: string;
  healthStatus: string;
  birthDate: string | null;
  color: string | null;
  farmId: string;
  farmName: string;
  speciesId: string;
  speciesName: string;
  breedId: string | null;
  breedName: string | null;
  version: number;
}

export interface MovementRecord {
  id: string;
  eventType: string;
  subjectType: string;
  subjectId: string;
  subjectCode: string;
  subjectName: string;
  speciesName: string;
  quantity: number;
  farmId: string;
  farmName: string;
  sourceFarmId: string | null;
  sourceFarmName: string | null;
  destinationFarmId: string | null;
  destinationFarmName: string | null;
  effectiveAt: string;
  reason: string | null;
  actorName: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface HerdBatch {
  id: string;
  code: string;
  name: string;
  quantity: number;
  purpose: string;
  status: string;
  farmId: string;
  farmName: string;
  speciesName: string;
}

export interface Species {
  id: string;
  code: string;
  name: string;
  category: string;
  chartColor: string;
}

export interface Breed {
  id: string;
  name: string;
  speciesId: string;
}

export interface MasterCategory {
  code: string;
  label: string;
  description: string;
  total: number;
  active: number;
}

export interface MasterRecord {
  id: string;
  code: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | string;
  origin: 'SYSTEM' | 'ADMIN' | string;
  groupLabel: string;
  parentId: string | null;
  chartColor: string | null;
  sortOrder: number | null;
  individualTracking: boolean | null;
  usage: number;
  usageLabel: string;
  codeLocked: boolean;
  createdAt: string;
  updatedAt: string;
  createdByName: string;
  updatedByName: string;
  detail?: string | null;
  numericValue?: number | null;
}

export interface MasterPage {
  category: string;
  label: string;
  total: number;
  page: number;
  size: number;
  canEdit: boolean;
  records: MasterRecord[];
}

export interface MasterHistory {
  action: string;
  detail: string;
  recordedAt: string;
  actorName: string;
}

export interface MasterDetail {
  record: MasterRecord;
  history: MasterHistory[];
}

export interface MasterImportRow {
  line: number;
  code: string;
  name: string;
  action: 'CREATE' | 'UPDATE' | 'SKIP' | 'FAIL' | string;
  message: string;
}

export interface MasterImport {
  created: number;
  updated: number;
  skipped: number;
  failed: number;
  rows: MasterImportRow[];
}

export interface ProductionRecord {
  id: string;
  farmId: string;
  farmName: string;
  productType: string;
  quantity: number;
  unit: string;
  recordedOn: string;
  notes: string | null;
}

export interface InventoryItem {
  id: string;
  sku: string;
  name: string;
  category: string;
  farmId: string;
  farmName: string;
  quantityOnHand: number;
  reservedQuantity: number;
  available: number;
  unit: string;
  reorderPoint: number;
  lowStock: boolean;
  lotCode: string | null;
  expiresOn: string | null;
  unitCost: number | null;
  withdrawalDays?: number | null;
  stockValue: number | null;
  supplierName: string | null;
  storeName: string | null;
  version: number;
  usable?: number;
  batchCount?: number;
  daysLeft?: number | null;
  expiryStatus?: string | null;
  productType?: string | null;
}

export interface StockBatch {
  id: string;
  itemId: string;
  productName: string;
  sku: string;
  category: string;
  productType: string | null;
  farmId: string;
  farmName: string;
  lotCode: string;
  quantityReceived: number;
  quantityRemaining: number;
  manufacturedOn: string | null;
  receivedOn: string;
  expiresOn: string | null;
  bestBeforeOn: string | null;
  openedOn: string | null;
  openDays: number | null;
  useBy: string | null;
  daysLeft: number | null;
  status: string;
  storeName: string | null;
  supplierName: string | null;
  unitCost: number | null;
  unit: string;
}

export interface StockAlert {
  batchId: string | null;
  itemId: string;
  kind: string;
  severity: string;
  productName: string;
  category: string;
  lotCode: string | null;
  quantity: number;
  useBy: string | null;
  daysLeft: number | null;
  action: string;
  assigneeName: string | null;
  acknowledged: boolean;
  farmName: string;
}

export interface SupplierRecord {
  id: string;
  name: string;
  supplierType: string;
  phone: string | null;
  location: string | null;
  registrationNo: string | null;
  licenseNo: string | null;
  leadDays: number | null;
  status: string;
  productCount: number;
}

export interface InventoryReport {
  totalValue: number;
  currency: string;
  byCategory: { category: string; products: number; value: number }[];
  expiring: { days: number; batches: number }[];
  expiredValue: number;
  expiredBatches: number;
  nearExpiry: number;
  quarantined: number;
}

export interface InventoryMovement {
  id: string;
  itemId: string;
  itemName: string;
  sku: string;
  farmId: string;
  farmName: string;
  movementType: string;
  quantityDelta: number;
  quantityAfter: number;
  unit: string;
  reason: string;
  lotCode: string | null;
  expiresOn: string | null;
  supplierName: string | null;
  storeName: string | null;
  actorName: string;
  occurredAt: string;
}

export interface InventoryAdjustment {
  id: string;
  itemId: string;
  itemName: string;
  sku: string;
  farmId: string;
  farmName: string;
  delta: number;
  unit: string;
  reason: string;
  status: string;
  requestedByName: string;
  requestedAt: string;
  decidedByName: string | null;
  decidedAt: string | null;
}

export interface StaffMember {
  id: string;
  fullName: string;
  roleTitle: string;
  farmId: string;
  farmName: string;
  phone: string | null;
  status: string;
  hiredOn: string | null;
}

export interface FarmerRequest {
  id: string;
  farmId: string;
  farmName: string;
  authorName: string;
  subject: string;
  message: string;
  status: string;
  reply: string | null;
  repliedBy: string | null;
  createdAt: string;
  answeredAt: string | null;
}

export interface AttendanceMark {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  workDate: string;
  mark: string;
}

export interface StaffShift {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  weekday: number;
  starts: string;
  ends: string;
}

export interface StaffTimesheet {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  workDate: string;
  hours: number;
  note: string | null;
}

export interface StaffReview {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  score: number;
  reviewedOn: string;
  notes: string | null;
  reviewerName: string;
}

export interface StaffTraining {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  title: string;
  completedOn: string | null;
}

export interface StaffGoal {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  title: string;
  status: string;
}

export interface StaffCompetency {
  id: string;
  staffId: string;
  staffName: string;
  farmId: string;
  farmName: string;
  skill: string;
  level: string;
}

export interface WorkforceBundle {
  requests: FarmerRequest[];
  attendance: AttendanceMark[];
  shifts: StaffShift[];
  timesheets: StaffTimesheet[];
  reviews: StaffReview[];
  training: StaffTraining[];
  goals: StaffGoal[];
  competencies: StaffCompetency[];
}

export interface WorkTask {
  id: string;
  title: string;
  taskType: string;
  status: string;
  dueOn: string;
  assigneeName: string | null;
  notes: string | null;
  farmId: string;
  farmName: string;
}

export interface FishCycle {
  id: string;
  code: string;
  status: string;
  stockedQuantity: number;
  mortality: number;
  harvested: number;
  estimatedLiveCount: number;
  startedOn: string;
  farmId: string;
  farmName: string;
}

export interface AdviceNote {
  id: string;
  topic: string;
  advice: string;
  medication: string | null;
  medicationNote: string | null;
  authorId: string;
  authorName: string;
  createdAt: string;
  mine: boolean;
}

export interface AdviserPlace {
  farmId: string;
  farmName: string;
  region: string;
  latitude: number;
  longitude: number;
}

export interface AdviserEndorsement {
  recommenderName: string;
  note: string | null;
  createdAt: string;
}

export interface Adviser {
  id: string;
  fullName: string;
  self: boolean;
  allFarms: boolean;
  availability: 'AVAILABLE' | 'LIMITED' | 'UNAVAILABLE' | 'UNSET';
  availabilityNote: string | null;
  talents: string[];
  assignedFarmCount: number;
  openAlerts: number;
  openTasks: number;
  load: 'Light' | 'Steady' | 'Heavy';
  ratingAverage: number | null;
  ratingCount: number;
  myScore: number | null;
  myRatingNote: string | null;
  recommendationCount: number;
  recommendedByMe: boolean;
  myRecommendationNote: string | null;
  recommendations: AdviserEndorsement[];
  places: AdviserPlace[];
}

export interface HealthCase {
  id: string;
  title: string;
  caseStatus: string;
  severity: string;
  openedOn: string;
  notes: string | null;
  farmId: string;
  farmName: string;
}

export interface HealthDose {
  itemId: string;
  itemName: string;
  unit: string;
  batchId: string | null;
  lotCode: string | null;
  quantity: number;
  withdrawalDays: number | null;
  withdrawalEndsOn: string | null;
  issued: boolean;
  knownCost: number | null;
}

export interface HealthTreatment {
  id: string;
  farmId: string;
  farmName: string;
  animalId: string | null;
  herdId: string | null;
  subject: string;
  speciesName: string;
  symptoms: string;
  diagnosis: string;
  description: string;
  veterinarianName: string;
  status: string;
  startedOn: string;
  outcome: string | null;
  followUpOn: string | null;
  dose: HealthDose | null;
}

export interface HealthProtocol {
  id: string;
  speciesName: string;
  name: string;
  ageGroup: string;
  intervalDays: number;
  itemId: string | null;
  itemName: string | null;
  notes: string | null;
  approvedByName: string;
  active: boolean;
  approvedOn: string;
}

export interface HealthVaccination {
  id: string;
  farmId: string;
  farmName: string;
  animalId: string | null;
  herdId: string | null;
  subject: string;
  protocolId: string | null;
  protocolName: string | null;
  speciesName: string;
  ageGroup: string | null;
  vaccineName: string;
  itemId: string | null;
  itemName: string | null;
  batchId: string | null;
  lotCode: string | null;
  quantity: number | null;
  unit: string | null;
  withdrawalDays: number | null;
  withdrawalEndsOn: string | null;
  doseIssued: boolean;
  knownCost: number | null;
  administeredOn: string | null;
  nextDueOn: string | null;
  veterinarianName: string;
  notes: string | null;
  status: string;
  displayStatus: string;
}

export interface HealthNamedCount {
  name: string;
  count: number;
}

export interface HealthMedicineUse {
  name: string;
  unit: string;
  quantity: number;
  knownCost: number | null;
}

export interface HealthReport {
  inProgress: number;
  completedTreatments: number;
  plannedVaccinations: number;
  dueSoon: number;
  overdue: number;
  missed: number;
  completedVaccinations: number;
  knownCost: number | null;
  dosesWithoutCost: number;
  conditions: HealthNamedCount[];
  medicines: HealthMedicineUse[];
  veterinarians: HealthNamedCount[];
  reminderNote: string;
}

export interface HealthBoard {
  treatments: HealthTreatment[];
  vaccinations: HealthVaccination[];
  protocols: HealthProtocol[];
  report: HealthReport;
  canRecord: boolean;
  canApproveProtocols: boolean;
}

export interface Settings {
  tenantName: string;
  code: string;
  currencyCode: string;
  timezone: string;
  seasonLabel: string;
  locale: string;
  subscriptionPlan: string;
  subscriptionValidUntil: string;
}

export interface SearchHit {
  type: string;
  id: string;
  title: string;
  subtitle: string;
}

export interface AccessCatalog {
  roles: AccessRole[];
  activity: AccessAudit[];
  permissions: AccessPermission[];
}

export interface AccessPermission {
  code: string;
  label: string;
  detail: string;
}

export interface AccessRole {
  code: string;
  label: string;
  description: string;
  systemRole: boolean;
  scope: 'ALL_FARMS' | 'SELECTED_FARMS';
  scopeLabel: string;
  userCount: number;
  canManageUsers: boolean;
  requireApproval: boolean;
  modules: AccessModule[];
  users: AccessUser[];
}

export interface AccessModule {
  id: string;
  label: string;
  view: boolean;
  create: boolean;
  edit: boolean;
  delete: boolean;
  approve: boolean;
  export: boolean;
  basis: string;
}

export interface AccessUser {
  id: string;
  fullName: string;
  email: string;
  jobTitle: string;
  status: string;
  farms: string[];
}

export interface AccessAudit {
  id: string;
  action: string;
  entityType: string;
  detail: string;
  actorName: string;
  recordedAt: string;
}

export interface SupplierMedicine {
  id: string;
  name: string;
  animalTypes: string;
  category: string;
  priceAmount: number;
  currency: string;
  quantity: number;
  unit: string;
  stock: string;
  prescriptionRequired: boolean;
  summary: string;
}

export interface MedicalSupplier {
  id: string;
  name: string;
  mark: string;
  region: string;
  district: string;
  address: string;
  phone: string;
  hours: string;
  latitude: number;
  longitude: number;
  licenseNumber: string;
  licenseAuthority: string;
  licenseExpiresOn: string | null;
  productsOffered: string;
  summary: string;
  approval: string;
  shopState: string;
  owned: boolean;
  medicines: SupplierMedicine[];
}

export interface MedicineOrder {
  id: string;
  supplierId: string;
  supplierName: string;
  medicineId: string | null;
  medicineName: string;
  quantity: number;
  unit: string;
  fulfillment: string;
  status: string;
  prescriptionRequired: boolean;
  requesterName: string;
  farmId: string | null;
  farmName: string;
  requesterNote: string;
  adviserName: string;
  adviserNote: string;
  supplierNote: string;
  ownedShop: boolean;
  ownRequest: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MedicalBoard {
  suppliers: MedicalSupplier[];
  regions: string[];
  districts: string[];
  ownShop: MedicalSupplier | null;
  pendingReview: MedicalSupplier[];
  requests: MedicineOrder[];
  administrator: boolean;
  adviser: boolean;
}

export interface MarketStats {
  products: number;
  buyers: number;
  sellers: number;
  openRequests: number;
  pendingOffers: number;
  completed: number;
}

export interface MarketSeller {
  id: string;
  name: string;
  region: string;
  district: string;
  phone: string;
  latitude: number;
  longitude: number;
  summary: string;
  approval: string;
  owned: boolean;
  rating: number | null;
  reviews: number;
}

export interface MarketProduct {
  id: string;
  name: string;
  category: string;
  imageKey: string;
  description: string;
  quantity: number;
  unit: string;
  priceAmount: number;
  currency: string;
  minOrder: number;
  grade: string;
  region: string;
  district: string;
  latitude: number;
  longitude: number;
  fulfillment: string;
  status: string;
  sellerName: string;
  verified: boolean;
  owned: boolean;
  rating: number | null;
}

export interface MarketDemand {
  id: string;
  productId: string | null;
  productName: string;
  category: string;
  quantity: number;
  unit: string;
  grade: string;
  targetPrice: number;
  currency: string;
  region: string;
  district: string;
  latitude: number | null;
  longitude: number | null;
  neededOn: string;
  note: string;
  status: string;
  buyerName: string;
  ownRequest: boolean;
  createdAt: string;
}

export interface MarketOffer {
  id: string;
  requestId: string;
  sellerId: string;
  sellerName: string;
  productId: string | null;
  quantity: number;
  unit: string;
  priceAmount: number;
  currency: string;
  terms: string;
  status: string;
  ownOffer: boolean;
  createdAt: string;
}

export interface MarketOrder {
  id: string;
  requestId: string;
  productName: string;
  quantity: number;
  unit: string;
  priceAmount: number;
  currency: string;
  fulfillment: string;
  status: string;
  buyerName: string;
  sellerName: string;
  sellerPhone: string;
  ownPurchase: boolean;
  ownSale: boolean;
  reviewed: boolean;
  createdAt: string;
}

export interface MarketMessage {
  id: string;
  requestId: string | null;
  orderId: string | null;
  senderName: string;
  body: string;
  own: boolean;
  createdAt: string;
}

export interface MarketNotice {
  id: string;
  title: string;
  detail: string;
  createdAt: string;
}

export interface MarketBoard {
  stats: MarketStats;
  products: MarketProduct[];
  listings: MarketProduct[];
  reviewListings: MarketProduct[];
  requests: MarketDemand[];
  offers: MarketOffer[];
  orders: MarketOrder[];
  messages: MarketMessage[];
  notices: MarketNotice[];
  ownSeller: MarketSeller | null;
  pendingSellers: MarketSeller[];
  administrator: boolean;
}
