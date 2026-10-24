const ZONE = 'Africa/Dar_es_Salaam';

export function formatNumber(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits
  }).format(Number(value));
}

export function formatMoney(value: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0
  }).format(value);
}

export function formatBoardDate(iso: string, season: string): string {
  const date = new Date(iso);
  const formatted = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: ZONE
  }).format(date);
  return `${formatted} · ${season}`;
}

export function formatMonth(iso: string): string {
  return new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(new Date(iso));
}

export function formatValidUntil(iso: string): string {
  const formatted = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC'
  }).format(new Date(iso));
  return `Valid until ${formatted}`;
}

export function relativeTime(iso: string): string {
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  const hours = Math.max(1, Math.round(Math.abs(diffMs) / 36e5));
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.max(1, Math.round(Math.abs(diffMs) / 864e5));
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

export function activityWhen(iso: string): string {
  const date = new Date(iso);
  const time = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: ZONE
  }).format(date);
  const dayKey = (value: Date) => new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: ZONE
  }).format(value);
  const today = dayKey(new Date());
  const yesterday = dayKey(new Date(Date.now() - 864e5));
  const key = dayKey(date);
  if (key === today) {
    return `Today · ${time}`;
  }
  if (key === yesterday) {
    return `Yesterday · ${time}`;
  }
  return `${key} · ${time}`;
}

export function signedPercent(value: number | null): string {
  if (value === null || value === undefined) {
    return '';
  }
  const sign = value > 0 ? '+' : '';
  return `${sign}${formatNumber(value, 1)}%`;
}

export function apiError(error: unknown, fallback: string): string {
  const message = (error as { error?: { message?: string } })?.error?.message;
  if (!message || message.length > 180 || /exception|stack trace|\bsql\b/i.test(message)) {
    return fallback;
  }
  return message;
}
