import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TANZANIA_BOUNDS } from './tanzania-outline';
import { TANZANIA_OUTLINE, inTanzania, projectTanzania, unprojectTanzania } from './tanzania-geo';
import { TANZANIA_DISTRICTS } from './tanzania-districts';
import { TANZANIA_LAKES } from './tanzania-lakes';
import { TANZANIA_REGIONS } from './tanzania-regions';

export interface TanzaniaPin {
  id: string;
  label: string;
  title: string;
  latitude: number;
  longitude: number;
  link?: string[];
  tone?: 'ok' | 'warn' | 'alert';
  /** Short label drawn under the pin when the farm has an open disease alert. */
  notice?: string;
  /** Optional magnitude, such as a herd count. Larger values draw a larger marker. */
  weight?: number;
}

export interface MapCallout {
  id: string;
  lines: string[];
}

export interface MapPoint {
  latitude: number;
  longitude: number;
}

export interface MapRing {
  id: string;
  points: MapPoint[];
}

interface DrawnPin extends TanzaniaPin {
  x: number;
  y: number;
  anchor: 'start' | 'middle' | 'end';
  r: number;
  chipX: number;
  chipY: number;
  chipW: number;
  glintX: number;
  glintY: number;
  glintR: number;
  delay: string;
}

interface ScaleBar {
  km: number;
  width: number;
  x: number;
  y: number;
}

interface GridLine {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  lx: number;
  ly: number;
  label: string;
  anchor: 'start' | 'middle' | 'end';
}

const GRATICULE: GridLine[] = buildGraticule();

@Component({
  selector: 'app-tanzania-map',
  imports: [RouterLink],
  templateUrl: './tanzania-map.component.html',
  styleUrl: './tanzania-map.component.css'
})
export class TanzaniaMapComponent {
  pins = input<TanzaniaPin[]>([]);
  zoom = input(1);
  selectable = input(false);
  markerLatitude = input<number | null>(null);
  markerLongitude = input<number | null>(null);
  markerLabel = input('Farm');
  ariaLabel = input('Map of Tanzania');
  places = input(false);
  /** Draw official region borders on top of the country. */
  regions = input(false);
  selectedId = input<string | null>(null);
  callout = input<MapCallout | null>(null);
  rings = input<MapRing[]>([]);
  sketch = input<MapPoint[]>([]);
  drawing = input(false);
  picked = output<{ latitude: number; longitude: number }>();
  corner = output<MapPoint>();
  pinFocused = output<string>();
  pinPicked = output<string>();

  notice = signal('');
  readonly uid = `tz${Math.random().toString(36).slice(2, 8)}`;
  readonly width = TANZANIA_BOUNDS.width;
  readonly height = TANZANIA_BOUNDS.height;
  readonly viewBox = `0 0 ${TANZANIA_BOUNDS.width} ${TANZANIA_BOUNDS.height}`;
  readonly outline = TANZANIA_OUTLINE;
  readonly regionPaths = TANZANIA_REGIONS;
  readonly districtPaths = TANZANIA_DISTRICTS;
  readonly lakes = TANZANIA_LAKES;
  readonly graticule = GRATICULE;
  readonly neighbors = neighborLabels();
  readonly scaleBar: ScaleBar = buildScaleBar();
  readonly compass = { x: 36, y: 34 };
  private surface = viewChild<ElementRef<SVGSVGElement>>('surface');

  calloutDraw = computed(() => {
    const card = this.callout();
    if (!card?.lines.length) {
      return null;
    }
    const pin = this.placed().find(item => item.id === card.id);
    if (!pin) {
      return null;
    }
    const boxW = 228;
    const boxH = 16 + card.lines.length * 18;
    const x = pin.x + boxW + 28 > this.width ? Math.max(8, pin.x - boxW - 18) : pin.x + 18;
    const below = pin.y + 18;
    const y = below + boxH < this.height - 8 ? below : Math.max(8, pin.y - boxH - 28);
    return { x, y, boxW, boxH, lines: card.lines, tone: pin.tone };
  });

  noticeBox(pin: DrawnPin): { x: number; y: number; width: number } | null {
    const text = pin.notice?.trim();
    if (!text) {
      return null;
    }
    const width = Math.min(188, Math.max(64, text.length * 6.4 + 16));
    const x = Math.max(8, Math.min(this.width - width - 8, pin.x - width / 2));
    return { x, y: pin.chipY + 22, width };
  }

  placed = computed<DrawnPin[]>(() => {
    const pins = this.pins().filter(pin => Number.isFinite(Number(pin.latitude)) && Number.isFinite(Number(pin.longitude)));
    const weighted = pins.some(pin => Number(pin.weight) > 0);
    const max = Math.max(...pins.map(pin => Number(pin.weight) || 0), 1);
    const drawn = pins.map((pin, index) => {
      const point = this.draw(pin.label, Number(pin.latitude), Number(pin.longitude), pin);
      const radius = weighted ? 5 + 3.4 * Math.sqrt((Number(pin.weight) || 0) / max) : 6.2;
      return this.withMarker(point, radius, `${(index % 8) * 0.32}s`);
    });
    return separatePins(drawn);
  });

  selection = computed(() => {
    const latitude = this.markerLatitude();
    const longitude = this.markerLongitude();
    if (latitude == null || longitude == null || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return null;
    }
    const drawn = this.withMarker(this.draw(this.markerLabel(), latitude, longitude), 6.4, '0s');
    return {
      ...drawn,
      outside: !inTanzania(latitude, longitude)
    };
  });

  ringPaths = computed(() => this.rings()
    .map(ring => ({ id: ring.id, d: polygonPath(ring.points) }))
    .filter(ring => ring.d));

  sketchPath = computed(() => polygonPath(this.sketch()));

  sketchDots = computed(() => this.sketch()
    .filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude))
    .map(point => projectTanzania(point.latitude, point.longitude)));

  onMapClick(event: MouseEvent): void {
    if (!this.selectable() && !this.drawing()) {
      return;
    }
    const svg = this.surface()?.nativeElement;
    if (!svg) {
      return;
    }
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const matrix = svg.getScreenCTM();
    if (!matrix) {
      return;
    }
    const local = point.matrixTransform(matrix.inverse());
    const place = unprojectTanzania(local.x, local.y);
    if (!inTanzania(place.latitude, place.longitude)) {
      this.notice.set('Choose a point inside Tanzania.');
      return;
    }
    this.notice.set('');
    if (this.drawing()) {
      this.corner.emit(place);
      return;
    }
    this.picked.emit({
      latitude: Math.round(place.latitude * 1_000_000) / 1_000_000,
      longitude: Math.round(place.longitude * 1_000_000) / 1_000_000
    });
  }

  private draw<T extends object>(label: string, latitude: number, longitude: number, extra?: T): DrawnPin {
    const point = projectTanzania(latitude, longitude);
    const anchor: DrawnPin['anchor'] = point.x < 110 ? 'start' : point.x > TANZANIA_BOUNDS.width - 110 ? 'end' : 'middle';
    const chipW = Math.min(188, Math.max(52, label.length * 7.15 + 18));
    let chipX = point.x - chipW / 2;
    if (anchor === 'start') {
      chipX = point.x - 4;
    } else if (anchor === 'end') {
      chipX = point.x - chipW + 4;
    }
    chipX = Math.max(8, Math.min(this.width - chipW - 8, chipX));
    return {
      id: '',
      title: '',
      latitude,
      longitude,
      ...extra,
      label,
      x: point.x,
      y: point.y,
      anchor,
      r: 6.2,
      chipX,
      chipY: Math.max(8, point.y - 34),
      chipW,
      glintX: point.x - 1.6,
      glintY: point.y - 1.8,
      glintR: 1.5,
      delay: '0s'
    };
  }

  private withMarker(pin: DrawnPin, radius: number, delay: string): DrawnPin {
    return {
      ...pin,
      r: radius,
      delay,
      glintX: pin.x - radius * 0.28,
      glintY: pin.y - radius * 0.32,
      glintR: Math.max(1.15, radius * 0.28)
    };
  }
}

function polygonPath(points: MapPoint[]): string {
  const valid = points.filter(point => Number.isFinite(Number(point.latitude)) && Number.isFinite(Number(point.longitude)));
  if (valid.length < 2) {
    return '';
  }
  const path = valid.map((point, index) => {
    const projected = projectTanzania(Number(point.latitude), Number(point.longitude));
    return `${index === 0 ? 'M' : 'L'}${projected.x.toFixed(1)} ${projected.y.toFixed(1)}`;
  }).join(' ');
  return valid.length >= 3 ? `${path} Z` : path;
}

function separatePins(pins: DrawnPin[]): DrawnPin[] {
  const next = pins.map(pin => ({ ...pin }));
  for (let index = 0; index < next.length; index += 1) {
    for (let earlier = 0; earlier < index; earlier += 1) {
      const dx = next[index].x - next[earlier].x;
      const dy = next[index].y - next[earlier].y;
      const distance = Math.hypot(dx, dy);
      if (distance >= 18) {
        continue;
      }
      const angle = distance < 0.5 ? index * 0.9 : Math.atan2(dy || 1, dx || 0.2);
      const push = 18 - distance;
      const offsetX = Math.cos(angle) * push;
      const offsetY = Math.sin(angle) * push;
      next[index].x += offsetX;
      next[index].y += offsetY;
      next[index].chipX += offsetX;
      next[index].chipY += offsetY;
      next[index].glintX += offsetX;
      next[index].glintY += offsetY;
    }
  }
  return next;
}

function buildScaleBar(): ScaleBar {
  const latitude = -7.4;
  const longitude = 39.95;
  const km = 100;
  const kmPerDegree = 111.32 * Math.cos((latitude * Math.PI) / 180);
  const start = projectTanzania(latitude, longitude);
  const end = projectTanzania(latitude, longitude + km / kmPerDegree);
  const width = Math.abs(end.x - start.x);
  const x = Math.min(TANZANIA_BOUNDS.width - width - 16, Math.min(start.x, end.x));
  return { km, width, x, y: start.y };
}

function neighborLabels(): { name: string; x: number; y: number }[] {
  return [
    ['Kenya', -0.78, 37.6],
    ['Uganda', -1.05, 31.4],
    ['Rwanda', -2.2, 29.6],
    ['Burundi', -3.4, 29.7],
    ['Zambia', -9.2, 31.2],
    ['Malawi', -11.2, 34.2],
    ['Mozambique', -11.4, 38.4]
  ].map(([name, latitude, longitude]) => {
    const point = projectTanzania(Number(latitude), Number(longitude));
    return { name: String(name), x: point.x, y: point.y };
  }).filter(label => label.x > 8 && label.y > 8 && label.x < TANZANIA_BOUNDS.width - 8 && label.y < TANZANIA_BOUNDS.height - 8);
}

function buildGraticule(): GridLine[] {
  const bounds = TANZANIA_BOUNDS;
  const lines: GridLine[] = [];
  for (const longitude of [30, 32, 34, 36, 38, 40]) {
    const top = projectTanzania(bounds.maxLat, longitude);
    const bottom = projectTanzania(bounds.minLat, longitude);
    lines.push({
      id: `lon-${longitude}`,
      x1: top.x,
      y1: top.y,
      x2: bottom.x,
      y2: bottom.y,
      lx: bottom.x,
      ly: bounds.height - 16,
      label: `${longitude}°E`,
      anchor: 'middle'
    });
  }
  for (const latitude of [-2, -4, -6, -8, -10]) {
    const left = projectTanzania(latitude, bounds.minLon);
    const right = projectTanzania(latitude, bounds.maxLon);
    lines.push({
      id: `lat-${latitude}`,
      x1: left.x,
      y1: left.y,
      x2: right.x,
      y2: right.y,
      lx: 12,
      ly: left.y - 6,
      label: `${Math.abs(latitude)}°S`,
      anchor: 'start'
    });
  }
  return lines;
}
