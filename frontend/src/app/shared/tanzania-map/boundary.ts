import { inTanzania } from './tanzania-geo';

export interface BoundaryPoint {
  latitude: number;
  longitude: number;
}

const SAME = 1e-6;

export function roundCoord(value: number): number {
  return Math.round(value * 1_000_000) / 1_000_000;
}

export function roundPoint(point: BoundaryPoint): BoundaryPoint {
  return {
    latitude: roundCoord(point.latitude),
    longitude: roundCoord(point.longitude)
  };
}

export function locationIssue(latitude: number, longitude: number): string {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    return 'Enter a latitude between -90 and 90.';
  }
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    return 'Enter a longitude between -180 and 180.';
  }
  if (!inTanzania(latitude, longitude)) {
    return 'Choose a point inside Tanzania.';
  }
  return '';
}

/** Empty means no boundary yet, which is allowed. A partial or crossed ring returns a message. */
export function boundaryIssue(points: BoundaryPoint[]): string {
  if (points.length === 0) {
    return '';
  }
  if (points.length > 60) {
    return 'A boundary can have at most 60 corners.';
  }
  const ring = opened(points);
  for (let index = 0; index < ring.length; index += 1) {
    const issue = locationIssue(ring[index].latitude, ring[index].longitude);
    if (issue) {
      return `Corner ${index + 1}: ${issue.charAt(0).toLowerCase()}${issue.slice(1)}`;
    }
  }
  if (ring.length < 3) {
    return 'Add at least 3 corners to close the boundary.';
  }
  for (let index = 0; index < ring.length; index += 1) {
    if (same(ring[index], ring[(index + 1) % ring.length])) {
      return `Corner ${index + 1} repeats the next corner.`;
    }
  }
  if (Math.abs(area(ring)) < 1e-12) {
    return 'These corners do not enclose an area.';
  }
  if (crosses(ring)) {
    return 'The boundary crosses itself. Reorder the corners so the outline does not overlap.';
  }
  return '';
}

function opened(points: BoundaryPoint[]): BoundaryPoint[] {
  if (points.length > 1 && same(points[0], points[points.length - 1])) {
    return points.slice(0, -1);
  }
  return points;
}

function same(left: BoundaryPoint, right: BoundaryPoint): boolean {
  return Math.abs(left.latitude - right.latitude) < SAME && Math.abs(left.longitude - right.longitude) < SAME;
}

function area(ring: BoundaryPoint[]): number {
  let sum = 0;
  for (let index = 0; index < ring.length; index += 1) {
    const next = ring[(index + 1) % ring.length];
    sum += ring[index].longitude * next.latitude - next.longitude * ring[index].latitude;
  }
  return sum / 2;
}

function crosses(ring: BoundaryPoint[]): boolean {
  const count = ring.length;
  for (let first = 0; first < count; first += 1) {
    for (let second = first + 1; second < count; second += 1) {
      const firstEnd = (first + 1) % count;
      const secondEnd = (second + 1) % count;
      if (firstEnd === second || first === secondEnd || firstEnd === secondEnd) {
        continue;
      }
      if (proper(ring[first], ring[firstEnd], ring[second], ring[secondEnd])) {
        return true;
      }
    }
  }
  return false;
}

function proper(a: BoundaryPoint, b: BoundaryPoint, c: BoundaryPoint, d: BoundaryPoint): boolean {
  const one = orient(a, b, c);
  const two = orient(a, b, d);
  const three = orient(c, d, a);
  const four = orient(c, d, b);
  if (one === 0 && two === 0 && three === 0 && four === 0) {
    return overlap(a, b, c, d);
  }
  return one * two < 0 && three * four < 0;
}

function orient(start: BoundaryPoint, end: BoundaryPoint, point: BoundaryPoint): number {
  const value = (end.longitude - start.longitude) * (point.latitude - start.latitude)
    - (end.latitude - start.latitude) * (point.longitude - start.longitude);
  return Math.abs(value) < 1e-14 ? 0 : Math.sign(value);
}

function overlap(a: BoundaryPoint, b: BoundaryPoint, c: BoundaryPoint, d: BoundaryPoint): boolean {
  return Math.min(a.longitude, b.longitude) <= Math.max(c.longitude, d.longitude) + SAME
    && Math.min(c.longitude, d.longitude) <= Math.max(a.longitude, b.longitude) + SAME
    && Math.min(a.latitude, b.latitude) <= Math.max(c.latitude, d.latitude) + SAME
    && Math.min(c.latitude, d.latitude) <= Math.max(a.latitude, b.latitude) + SAME;
}
