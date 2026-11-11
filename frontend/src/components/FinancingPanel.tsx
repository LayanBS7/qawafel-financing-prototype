import type { FinancingEstimate } from '../api';
import { money } from '../format';

export default function FinancingPanel({ financing }: { financing: FinancingEstimate }) {
  const last = financing.steps.length - 1;
  return (
    <section className="card">
      <div className="card-head">
        <h2>How the limit is calculated</h2>
        <p>{financing.method}</p>
      </div>
      <div className="steps">
        {financing.steps.map((s, i) => (
          <div key={s.label} className={`step${i === last ? ' result' : i === last - 1 ? ' total' : ''}`}>
            <span style={{ fontWeight: i >= last - 1 ? 600 : 500 }}>{s.label}</span>
            <span className={`amt${s.amount < 0 ? ' neg' : ''}`}>
              {s.amount < 0 ? `−${money(-s.amount)}` : money(s.amount)}
            </span>
            <span className="why">{s.explanation}</span>
          </div>
        ))}
      </div>
      <h3 style={{ marginTop: 18, marginBottom: 8 }}>Conditions to send with the package</h3>
      <ul className="list small secondary">
        {financing.conditions.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
    </section>
  );
}
