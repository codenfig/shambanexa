import { TANZANIA_BOUNDS, TANZANIA_RINGS } from './tanzania-outline';

export interface MapPoint {
  latitude: number;
  longitude: number;
}

const MAP_PAD_X = 36;
const MAP_PAD_Y = 28;

export function projectTanzania(latitude: number, longitude: number): { x: number; y: number } {
  const bounds = TANZANIA_BOUNDS;
  const innerW = bounds.width - MAP_PAD_X * 2;
  const innerH = bounds.height - MAP_PAD_Y * 2;
  return {
    x: MAP_PAD_X + ((longitude - bounds.minLon) / (bounds.maxLon - bounds.minLon)) * innerW,
    y: MAP_PAD_Y + ((bounds.maxLat - latitude) / (bounds.maxLat - bounds.minLat)) * innerH
  };
}

export function unprojectTanzania(x: number, y: number): MapPoint {
  const bounds = TANZANIA_BOUNDS;
  const innerW = bounds.width - MAP_PAD_X * 2;
  const innerH = bounds.height - MAP_PAD_Y * 2;
  return {
    longitude: bounds.minLon + ((x - MAP_PAD_X) / innerW) * (bounds.maxLon - bounds.minLon),
    latitude: bounds.maxLat - ((y - MAP_PAD_Y) / innerH) * (bounds.maxLat - bounds.minLat)
  };
}

export function inTanzania(latitude: number, longitude: number): boolean {
  return TANZANIA_RINGS.some(ring => pointInRing(longitude, latitude, ring));
}

export const TANZANIA_OUTLINE = TANZANIA_RINGS.map(ring => {
  const projected = ring.map(([longitude, latitude]) => projectTanzania(latitude, longitude));
  const smooth = roundCorners(projected, 2);
  return smooth.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ') + ' Z';
}).join(' ');

/** Cuts polygon corners so the coastline reads as a smooth curve. */
function roundCorners(points: { x: number; y: number }[], iterations: number): { x: number; y: number }[] {
  let current = points;
  for (let step = 0; step < iterations; step += 1) {
    const next: { x: number; y: number }[] = [];
    const count = current.length;
    for (let index = 0; index < count; index += 1) {
      const start = current[index];
      const end = current[(index + 1) % count];
      next.push({ x: start.x * 0.75 + end.x * 0.25, y: start.y * 0.75 + end.y * 0.25 });
      next.push({ x: start.x * 0.25 + end.x * 0.75, y: start.y * 0.25 + end.y * 0.75 });
    }
    current = next;
  }
  return current;
}

function pointInRing(longitude: number, latitude: number, ring: ReadonlyArray<readonly [number, number]>): boolean {
  let inside = false;
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const [xi, yi] = ring[index];
    const [xj, yj] = ring[previous];
    const crosses = (yi > latitude) !== (yj > latitude)
      && longitude < ((xj - xi) * (latitude - yi)) / (yj - yi) + xi;
    if (crosses) {
      inside = !inside;
    }
  }
  return inside;
}
