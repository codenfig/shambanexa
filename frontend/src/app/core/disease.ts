import { AlertItem } from './models';

const HEALTH = ['DEATH', 'DISEASE', 'SICK'];

export function healthAlert(alerts: AlertItem[], farmId: string): AlertItem | undefined {
  const open = alerts.filter(alert => alert.farmId === farmId && alert.status === 'OPEN' && HEALTH.includes(alert.sourceType || ''));
  return open.find(alert => alert.sourceType === 'DEATH')
    ?? open.find(alert => alert.sourceType === 'DISEASE')
    ?? open.find(alert => alert.sourceType === 'SICK');
}

export function healthLabel(alert: AlertItem): string {
  return alert.title
    .replace(/^Disease outbreak:\s*/i, '')
    .replace(/^Sick animals:\s*/i, '')
    .replace(/^Animal deaths:\s*/i, '')
    .trim() || alert.title;
}

export function healthNotice(alert: AlertItem): string {
  const name = healthLabel(alert);
  return name.length > 22 ? `${name.slice(0, 21)}…` : name;
}

export function healthPhrase(alert: AlertItem): string {
  if (alert.sourceType === 'DEATH') {
    return 'Deaths reported';
  }
  if (alert.sourceType === 'SICK') {
    return 'Sick animals';
  }
  return alert.severity === 'CRITICAL' ? 'Outbreak alert' : 'Suspected disease';
}
