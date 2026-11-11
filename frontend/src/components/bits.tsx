import type { CustomerFlag, Rating, Severity } from '../api';
import { FLAG_LABEL, FLAG_TONE } from '../format';

const RATING_COLOR: Record<Rating, string> = {
  GOOD: 'var(--good)',
  FAIR: 'var(--warning)',
  POOR: 'var(--critical)',
};

export function ScoreRing({ score, max, band }: { score: number; max: number; band: string }) {
  const r = 48;
  const c = 2 * Math.PI * r;
  const ratio = Math.max(0, Math.min(1, score / max));
  const color = band === 'Strong' ? 'var(--good)' : band === 'Moderate' ? 'var(--warning)' : 'var(--critical)';
  return (
    <div className="score-ring" role="img" aria-label={`Score ${score} out of ${max}, ${band}`}>
      <svg width="112" height="112" viewBox="0 0 112 112">
        <circle cx="56" cy="56" r={r} fill="none" stroke="var(--surface-2)" strokeWidth="10" />
        <circle
          cx="56"
          cy="56"
          r={r}
          fill="none"
          stroke={color}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${c * ratio} ${c}`}
        />
      </svg>
      <div className="center">
        <div>
          <div className="value num">{score}</div>
          <div className="of">out of {max}</div>
        </div>
      </div>
    </div>
  );
}

export function BandBadge({ band }: { band: string }) {
  const cls = band === 'Strong' ? 'badge-good' : band === 'Moderate' ? 'badge-serious' : 'badge-critical';
  return <span className={`badge ${cls}`}>{band}</span>;
}

export function RatingBadge({ rating }: { rating: Rating }) {
  const cls = rating === 'GOOD' ? 'badge-good' : rating === 'FAIR' ? 'badge-serious' : 'badge-critical';
  const label = rating === 'GOOD' ? 'Good' : rating === 'FAIR' ? 'Fair' : 'Poor';
  return <span className={`badge ${cls}`}>{label}</span>;
}

export function Meter({ value, max, rating }: { value: number; max: number; rating: Rating }) {
  return (
    <div className="meter" aria-hidden>
      <span style={{ width: `${(value / max) * 100}%`, background: RATING_COLOR[rating] }} />
    </div>
  );
}

export function FlagBadge({ flag }: { flag: CustomerFlag }) {
  const tone = FLAG_TONE[flag];
  const cls = tone === 'critical' ? 'badge-critical' : tone === 'serious' ? 'badge-serious' : '';
  return <span className={`badge ${cls}`}>{FLAG_LABEL[flag]}</span>;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  if (severity === 'EXCLUDED') return <span className="badge badge-critical">Excluded</span>;
  if (severity === 'WARNING') return <span className="badge badge-serious">Kept, flagged</span>;
  return <span className="badge">Info</span>;
}

export function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value num">{value}</div>
      {sub && <div className="kpi-sub">{sub}</div>}
    </div>
  );
}
