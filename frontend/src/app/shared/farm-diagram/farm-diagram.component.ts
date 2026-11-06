import { Component, computed, input } from '@angular/core';
import { BoundaryPoint } from '../tanzania-map/boundary';

interface DiagramDot {
  x: number;
  y: number;
  label: string;
}

@Component({
  selector: 'app-farm-diagram',
  templateUrl: './farm-diagram.component.html',
  styleUrl: './farm-diagram.component.css'
})
export class FarmDiagramComponent {
  points = input<BoundaryPoint[]>([]);
  latitude = input<number | null>(null);
  longitude = input<number | null>(null);
  note = input('');

  view = computed(() => drawDiagram(this.points(), this.latitude(), this.longitude()));
}

function drawDiagram(points: BoundaryPoint[], latitude: number | null, longitude: number | null) {
  const corners = points.filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
  if (corners.length === 0) {
    return null;
  }
  const width = 360;
  const height = 240;
  const pad = 28;
  let minLat = Math.min(...corners.map(point => point.latitude));
  let maxLat = Math.max(...corners.map(point => point.latitude));
  let minLon = Math.min(...corners.map(point => point.longitude));
  let maxLon = Math.max(...corners.map(point => point.longitude));
  const pinInside = latitude != null && longitude != null
    && latitude >= minLat - Math.max(maxLat - minLat, 0.001)
    && latitude <= maxLat + Math.max(maxLat - minLat, 0.001)
    && longitude >= minLon - Math.max(maxLon - minLon, 0.001)
    && longitude <= maxLon + Math.max(maxLon - minLon, 0.001);
  if (pinInside && latitude != null && longitude != null) {
    minLat = Math.min(minLat, latitude);
    maxLat = Math.max(maxLat, latitude);
    minLon = Math.min(minLon, longitude);
    maxLon = Math.max(maxLon, longitude);
  }
  const latSpan = Math.max(maxLat - minLat, 0.0004);
  const lonSpan = Math.max(maxLon - minLon, 0.0004);
  minLat -= latSpan * 0.18;
  maxLat += latSpan * 0.18;
  minLon -= lonSpan * 0.18;
  maxLon += lonSpan * 0.18;
  const project = (lat: number, lon: number) => ({
    x: pad + ((lon - minLon) / (maxLon - minLon)) * (width - pad * 2),
    y: pad + ((maxLat - lat) / (maxLat - minLat)) * (height - pad * 2)
  });
  const dots: DiagramDot[] = corners.map((point, index) => {
    const placed = project(point.latitude, point.longitude);
    return { x: placed.x, y: placed.y, label: String(index + 1) };
  });
  const chain = dots.map(dot => `${dot.x.toFixed(1)},${dot.y.toFixed(1)}`).join(' ');
  const pin = pinInside && latitude != null && longitude != null ? project(latitude, longitude) : null;
  return { width, height, dots, chain, closed: corners.length >= 3, pin };
}
