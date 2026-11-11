import type { CustomerFlag, Decision } from './api';

const sar = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** SAR 1,234,567 */
export const money = (v: number) => `SAR ${sar.format(Math.round(v))}`;

/** SAR 3.74M / SAR 845K: for headline tiles and axis ticks. */
export const moneyShort = (v: number) => {
  const a = Math.abs(v);
  if (a >= 1_000_000) return `SAR ${(v / 1_000_000).toFixed(a >= 10_000_000 ? 1 : 2)}M`;
  if (a >= 1_000) return `SAR ${Math.round(v / 1_000)}K`;
  return `SAR ${Math.round(v)}`;
};

export const axisMoney = (v: number) => (Math.abs(v) >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}M` : `${Math.round(v / 1_000)}K`);

/** 0.2422 -> "24.2%" */
export const share = (v: number) => `${(v * 100).toFixed(1)}%`;

export const pct = (v: number, signed = false) => `${signed && v > 0 ? '+' : ''}${v.toFixed(1)}%`;

export const days = (v: number | null | undefined) => (v == null ? '-' : `${Math.round(v)} d`);

const monthFmt = new Intl.DateTimeFormat('en-GB', { month: 'short', year: '2-digit' });
/** "2026-02" -> "Feb 26" */
export const monthLabel = (ym: string) => monthFmt.format(new Date(`${ym}-01T00:00:00`));

const dateFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
export const date = (iso: string | null | undefined) => (iso ? dateFmt.format(new Date(iso)) : '-');

export const FLAG_LABEL: Record<CustomerFlag, string> = {
  LARGEST_CUSTOMER: 'Largest customer',
  PAYING_SLOWER: 'Paying slower',
  CHRONIC_LATE_PAYER: 'Chronic late payer',
  STOPPED_BUYING: 'Stopped buying',
  NEW_CUSTOMER: 'New customer',
  HIGH_OVERDUE: 'High overdue',
};

/** Which flags are risks (vs neutral information). */
export const FLAG_TONE: Record<CustomerFlag, 'critical' | 'serious' | 'neutral'> = {
  LARGEST_CUSTOMER: 'serious',
  PAYING_SLOWER: 'serious',
  CHRONIC_LATE_PAYER: 'critical',
  STOPPED_BUYING: 'critical',
  NEW_CUSTOMER: 'neutral',
  HIGH_OVERDUE: 'critical',
};

export const DECISION_LABEL: Record<Decision, string> = {
  RECOMMEND: 'Recommend to bank',
  RECOMMEND_WITH_CONDITIONS: 'Recommend with conditions',
  NEED_MORE_INFO: 'Need more information',
  DECLINE: 'Decline',
};
