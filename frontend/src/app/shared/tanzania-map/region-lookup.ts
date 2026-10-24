import { TANZANIA_REGIONS } from './tanzania-regions';
import { inTanzania, projectTanzania } from './tanzania-geo';

const RINGS = TANZANIA_REGIONS.map(region => ({
  name: region.name,
  rings: parseRings(region.d)
}));

export const TANZANIA_REGION_NAMES = [...new Set(TANZANIA_REGIONS.map(region => region.name))]
  .sort((left, right) => left.localeCompare(right));

/** Official region that contains this point. Several farms may share the same region. */
export function regionContaining(latitude: number, longitude: number): string | null {
  if (!inTanzania(latitude, longitude)) {
    return null;
  }
  const point = projectTanzania(latitude, longitude);
  for (const region of RINGS) {
    let inside = false;
    for (const ring of region.rings) {
      if (contains(point.x, point.y, ring)) {
        inside = !inside;
      }
    }
    if (inside) {
      return region.name;
    }
  }
  return null;
}

function parseRings(path: string): number[][][] {
  const rings: number[][][] = [];
  let ring: number[][] = [];
  let command = '';
  let x: number | null = null;
  for (const token of path.match(/[MLZ]|-?\d+(?:\.\d+)?/g) ?? []) {
    if (token === 'M' || token === 'L' || token === 'Z') {
      if (token === 'Z' && ring.length > 2) {
        rings.push(ring);
        ring = [];
      }
      if (token === 'M' && ring.length > 2) {
        rings.push(ring);
        ring = [];
      }
      command = token;
      x = null;
      continue;
    }
    if (command === 'Z') {
      continue;
    }
    const value = Number(token);
    if (x === null) {
      x = value;
    } else {
      ring.push([x, value]);
      x = null;
    }
  }
  if (ring.length > 2) {
    rings.push(ring);
  }
  return rings;
}

function contains(x: number, y: number, ring: number[][]): boolean {
  let inside = false;
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const [xi, yi] = ring[index];
    const [xj, yj] = ring[previous];
    const crosses = (yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi;
    if (crosses) {
      inside = !inside;
    }
  }
  return inside;
}
