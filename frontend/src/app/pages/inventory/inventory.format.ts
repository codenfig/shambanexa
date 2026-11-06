import { InventoryItem } from '../../core/models';
import { formatNumber } from '../../core/format';

export const STOCK_GROUPS = ['FEED', 'MEDICINE', 'EQUIPMENT', 'SUPPLY'];

export function categoryLabel(category: string): string {
  if (category === 'FEED') {
    return 'Feed';
  }
  if (category === 'MEDICINE') {
    return 'Medicine';
  }
  if (category === 'EQUIPMENT') {
    return 'Equipment';
  }
  if (category === 'SUPPLY') {
    return 'Supplies';
  }
  return category ? category.charAt(0) + category.slice(1).toLowerCase() : '';
}

function daysUntil(value: string | null): number | null {
  if (!value) {
    return null;
  }
  const expiry = new Date(`${value.slice(0, 10)}T00:00:00Z`).getTime();
  const now = new Date();
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return (expiry - today) / 86400000;
}

export function expiring(item: InventoryItem): boolean {
  const days = daysUntil(item.expiresOn);
  return days != null && days >= 0 && days <= 30;
}

export function expired(item: InventoryItem): boolean {
  const days = daysUntil(item.expiresOn);
  return days != null && days < 0;
}

export function statusOf(item: InventoryItem): string {
  if (item.expiryStatus === 'EXPIRED') {
    return 'Expired';
  }
  if (item.expiryStatus === 'QUARANTINED') {
    return 'Quarantined';
  }
  if (item.expiryStatus === 'DISPOSED') {
    return 'Disposed';
  }
  if (item.available <= 0) {
    return 'Out of stock';
  }
  if (item.lowStock) {
    return 'Low stock';
  }
  if (item.expiryStatus === 'EXPIRING') {
    return 'Expiring soon';
  }
  if (item.expiryStatus === 'BEST_BEFORE') {
    return 'Best before approaching';
  }
  if (item.expiryStatus === 'OPEN_USE') {
    return 'Use soon after opening';
  }
  if (expired(item)) {
    return 'Expired';
  }
  if (expiring(item)) {
    return 'Expiring';
  }
  return 'In stock';
}

export function batchStatusLabel(code: string): string {
  if (code === 'VALID') return 'Valid';
  if (code === 'EXPIRING') return 'Expiring soon';
  if (code === 'EXPIRED') return 'Expired';
  if (code === 'BEST_BEFORE') return 'Best before approaching';
  if (code === 'OPEN_USE') return 'Use soon after opening';
  if (code === 'QUARANTINED') return 'Quarantined';
  if (code === 'DISPOSED') return 'Disposed';
  return code;
}

export function statusClass(item: InventoryItem): string {
  const status = statusOf(item);
  if (status === 'Out of stock' || status === 'Expired' || status === 'Quarantined' || status === 'Disposed') {
    return 'out';
  }
  if (status === 'Low stock') {
    return 'low';
  }
  if (status === 'Expiring' || status === 'Expiring soon' || status === 'Best before approaching' || status === 'Use soon after opening') {
    return 'soon';
  }
  return 'in';
}

export function expiryLabel(value: string | null): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${value.slice(0, 10)}T00:00:00Z`));
}

export function whenLabel(iso: string): string {
  const hours = Math.floor((Date.now() - new Date(iso).getTime()) / 3600000);
  if (hours < 1) {
    return 'Just now';
  }
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

export function movementTitle(type: string): string {
  if (type === 'OPENING') {
    return 'Opening balance';
  }
  if (type === 'RECEIPT') {
    return 'Stock receipt';
  }
  if (type === 'ISSUE') {
    return 'Stock issue';
  }
  if (type === 'TRANSFER_OUT') {
    return 'Transfer out';
  }
  if (type === 'TRANSFER_IN') {
    return 'Transfer in';
  }
  if (type === 'ADJUSTMENT') {
    return 'Stock adjustment';
  }
  return 'Stock movement';
}

export function signed(value: number): string {
  const text = formatNumber(value, value % 1 ? 2 : 0);
  return value > 0 ? `+${text}` : text;
}

export function scopeChip(names: string[]): string {
  const unique = [...new Set(names.filter(name => !!name))].sort((a, b) => a.localeCompare(b));
  return unique.length === 1 ? unique[0] : 'All farms';
}

export function downloadCsv(filename: string, header: string[], rows: string[][]): void {
  const lines = rows.map(row => row.map(value => `"${String(value ?? '').replaceAll('"', '""')}"`).join(','));
  const blob = new Blob([[header.join(','), ...lines].join('\n')], { type: 'text/csv' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

export function money(value: number | null, currency: string): string {
  if (value == null) {
    return '—';
  }
  try {
    return new Intl.NumberFormat('en-TZ', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value);
  } catch {
    return `${currency} ${formatNumber(value, 2)}`;
  }
}
