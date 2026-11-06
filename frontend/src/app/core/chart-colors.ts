/** Green is the safe reading, orange needs attention, and red is the dangerous reading. */
export const CHART_SAFE = '#1e8330';
export const CHART_CAUTION = '#e07a12';
export const CHART_DANGER = '#d23b35';

const SERIES = [CHART_SAFE, CHART_CAUTION, CHART_DANGER];

export function chartSlice(index: number): string {
  return SERIES[index % SERIES.length];
}

export function healthChartColor(status: string): string {
  if (status === 'HEALTHY') {
    return CHART_SAFE;
  }
  if (status === 'WATCH') {
    return CHART_CAUTION;
  }
  if (status === 'SICK' || status === 'QUARANTINE') {
    return CHART_DANGER;
  }
  return '#94a3b8';
}

export function stockChartColor(status: string): string {
  if (status === 'Out of stock' || status === 'Expired') {
    return CHART_DANGER;
  }
  if (status === 'Low stock' || status === 'Expiring') {
    return CHART_CAUTION;
  }
  return CHART_SAFE;
}
