import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError, type Analysis } from '../api';
import { date, DECISION_LABEL, days, FLAG_LABEL, money, pct, share } from '../format';

/** One-page summary for the bank. Print it or save it as PDF from the browser. */
export default function PackagePage() {
  const id = Number(useParams().id);
  const [data, setData] = useState<Analysis | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get(id).then(setData).catch((e) => setError(e instanceof ApiError ? e.message : String(e)));
  }, [id]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <div className="loading">Loading…</div>;

  const { application: app, headline: h, risk, financing: f, concentration: c, collections: col, dataQuality: dq } = data;
  const flagged = data.customers.filter((x) => x.flags.length > 0);

  return (
    <div className="stack">
      <div className="spread no-print">
        <Link to={`/applications/${id}`} className="small">← Back to analysis</Link>
        <button className="btn btn-primary" onClick={() => window.print()}>Print / save as PDF</button>
      </div>

      <article className="package">
        <p className="muted small">Financing package · prepared {date(new Date().toISOString())}</p>
        <h1 style={{ marginTop: 4 }}>{app.companyName}</h1>
        <p className="secondary">
          Sales invoices {date(app.periodStart)} – {date(app.periodEnd)} · receivables as of {date(app.asOfDate)}
        </p>

        <section>
          <h2>Summary</h2>
          <div className="facts">
            <div>
              <div className="muted small">Credit score</div>
              <strong className="num">{risk.score} / {risk.maxScore} · {risk.band}</strong>
            </div>
            <div>
              <div className="muted small">Suggested limit</div>
              <strong className="num">{money(f.suggestedLimit)}</strong>
            </div>
            <div>
              <div className="muted small">Requested</div>
              <strong className="num">{f.requestedAmount != null ? money(f.requestedAmount) : '-'}</strong>
            </div>
          </div>
          <p style={{ marginTop: 10 }}>{risk.recommendation}</p>
          {app.decision && (
            <p style={{ marginTop: 6 }}>
              <strong>Analyst decision:</strong> {DECISION_LABEL[app.decision]}
              {app.decisionNote ? `: ${app.decisionNote}` : ''}
            </p>
          )}
        </section>

        <section>
          <h2>Key figures</h2>
          <table>
            <tbody>
              <tr><td>Net sales, 12 months (excl. VAT)</td><td className="r">{money(h.netRevenue)}</td></tr>
              <tr><td>Average monthly sales</td><td className="r">{money(h.averageMonthlyRevenue)}</td></tr>
              <tr><td>Sales trend (last 3 vs first 3 months)</td><td className="r">{pct(h.revenueTrendPct, true)}</td></tr>
              <tr><td>Largest customer / top 5 customers</td><td className="r">{share(c.topCustomerShare)} / {share(c.top5Share)}</td></tr>
              <tr><td>Receivables owed (incl. VAT)</td><td className="r">{money(h.openReceivables)}</td></tr>
              <tr><td>Days sales outstanding</td><td className="r">{days(col.daysSalesOutstanding)}</td></tr>
              <tr><td>Average payment after due date</td><td className="r">{days(col.averageDaysLate)}</td></tr>
              <tr><td>Rows used from the upload</td><td className="r">{dq.rowsAccepted.toLocaleString()} of {dq.rowsRead.toLocaleString()}</td></tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>Score breakdown</h2>
          <table>
            <tbody>
              {risk.factors.map((x) => (
                <tr key={x.name}>
                  <td style={{ width: '28%' }}><strong style={{ fontWeight: 600 }}>{x.name}</strong></td>
                  <td className="secondary">{x.explanation}</td>
                  <td className="r" style={{ whiteSpace: 'nowrap' }}>{x.points} / {x.maxPoints}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section>
          <h2>Financing calculation</h2>
          <table>
            <tbody>
              {f.steps.map((s) => (
                <tr key={s.label}>
                  <td>{s.label}<div className="muted small">{s.explanation}</div></td>
                  <td className="r">{s.amount < 0 ? `−${money(-s.amount)}` : money(s.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {flagged.length > 0 && (
          <section>
            <h2>Customers to note</h2>
            <table>
              <tbody>
                {flagged.map((x) => (
                  <tr key={x.customerKey}>
                    <td>{x.name}</td>
                    <td className="secondary">{x.flags.map((fl) => FLAG_LABEL[fl]).join(', ')}</td>
                    <td className="r">{share(x.share)} of sales</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}

        <section>
          <h2>Conditions</h2>
          <ul className="list">
            {f.conditions.map((x) => <li key={x}>{x}</li>)}
          </ul>
        </section>

        <p className="muted small" style={{ marginTop: 28 }}>
          Generated from the wholesaler's own invoice export. Figures are not audited; paid invoices should be matched
          against bank statements before funding.
        </p>
      </article>
    </div>
  );
}
