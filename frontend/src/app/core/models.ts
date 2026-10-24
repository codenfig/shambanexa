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
  version: number;
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

export interface Settings {
  tenantName: string;
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
