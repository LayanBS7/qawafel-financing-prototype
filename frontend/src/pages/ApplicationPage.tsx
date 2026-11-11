import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, ApiError, type Analysis } from '../api';
import { BandBadge, Kpi, Meter, RatingBadge, ScoreRing } from '../components/bits';
import { AgingChart, ConcentrationChart, RevenueChart } from '../components/charts';
import CustomersTab from '../components/CustomersTab';
import DecisionPanel from '../components/DecisionPanel';
import FinancingPanel from '../components/FinancingPanel';
import IssuesTab from '../components/IssuesTab';
import { date, days, money, moneyShort, pct, share } from '../format';

type Tab = 'overview' | 'customers' | 'issues';

export default function ApplicationPage() {
  const id = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab) ?? 'overview';
  const [data, setData] = useState<Analysis | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get(id).then(setData).catch((e) => setError(e instanceof ApiError ? e.message : String(e)));
  }, [id]);

  if (error) return <div className="alert alert-error">{error} <Link to="/">Back to applications</Link></div>;
  if (!data) return <div className="loading">Loading analysis…</div>;

  const { application: app, headline: h, risk, financing, dataQuality: dq } = data;
  const flagged = data.customers.filter((c) => c.flags.length > 0).length;
  const setTab = (t: Tab) => setParams(t === 'overview' ? {} : { tab: t }, { replace: true });

  return (
    <div className="stack">
      <div className="spread">
        <div>
          <Link to="/" className="small">← All applications</Link>
          <h1 style={{ marginTop: 6 }}>{app.companyName}</h1>
          <p className="muted small" style={{ marginTop: 2 }}>
            {date(app.periodStart)} – {date(app.periodEnd)} · data as of {date(app.asOfDate)} · {app.sourceFileName}
          </p>
        </div>
        <Link className="btn" to={`/applications/${id}/package`}>Bank package</Link>
      </div>

      {/* ---------------------------------------------------------- verdict */}
      <section className="card">
        <div className="grid-2">
          <div className="score-hero">
            <ScoreRing score={risk.score} max={risk.maxScore} band={risk.band} />
            <div className="stack" style={{ gap: 6 }}>
              <div className="row" style={{ gap: 8 }}>
                <h2>Credit score</h2>
                <BandBadge band={risk.band} />
              </div>
              <p>{risk.recommendation}</p>
              <p className="muted small">Rule-based: every point is explained under "Why this score".</p>
            </div>
          </div>
          <div className="stack" style={{ gap: 6 }}>
            <h2>Suggested financing limit</h2>
            <div className="limit-figure num">{money(financing.suggestedLimit)}</div>
            <p className="secondary">
              {financing.requestedAmount != null ? (
                (financing.requestCoverage ?? 0) >= 100 ? (
                  <>
                    Requested {money(financing.requestedAmount)}: <strong>the request fits within the limit</strong>.
                  </>
                ) : (
                  <>
                    Requested {money(financing.requestedAmount)}: the limit covers only{' '}
                    <strong>{financing.requestCoverage?.toFixed(0)}%</strong> of the request.
                  </>
                )
              ) : (
                'No amount was requested.'
              )}
            </p>
            <p className="muted small">
              {Math.round(financing.advanceRate * 100)}% of {money(financing.eligibleReceivables)} eligible receivables.
            </p>
          </div>
        </div>
      </section>

      <div className="tabs" role="tablist">
        <button className="tab" role="tab" aria-selected={tab === 'overview'} onClick={() => setTab('overview')}>
          Overview
        </button>
        <button className="tab" role="tab" aria-selected={tab === 'customers'} onClick={() => setTab('customers')}>
          Customers<span className="count">{flagged} flagged</span>
        </button>
        <button className="tab" role="tab" aria-selected={tab === 'issues'} onClick={() => setTab('issues')}>
          Data issues<span className="count">{dq.rowsExcluded + dq.warnings + dq.info}</span>
        </button>
      </div>

      {tab === 'customers' && <CustomersTab analysis={data} />}
      {tab === 'issues' && <IssuesTab analysis={data} />}
      {tab === 'overview' && (
        <>
          <div className="kpis">
            <Kpi label="Net sales (12 months)" value={moneyShort(h.netRevenue)} sub={`${moneyShort(h.averageMonthlyRevenue)} / month`} />
            <Kpi label="Sales trend" value={pct(h.revenueTrendPct, true)} sub="Last 3 vs first 3 months" />
            <Kpi label="Owed by customers" value={moneyShort(h.openReceivables)} sub={`${moneyShort(h.overdueReceivables)} overdue`} />
            <Kpi label="Days to collect (DSO)" value={days(h.daysSalesOutstanding)} sub={`Paid ${days(h.averageDaysLate)} after due on avg.`} />
            <Kpi label="Largest customer" value={share(data.concentration.topCustomerShare)} sub={data.concentration.topCustomers[0]?.name} />
            <Kpi label="Active customers" value={String(h.activeCustomers)} sub={`${h.invoiceCount.toLocaleString()} invoices`} />
          </div>

          <div className="grid-main">
            <div className="stack">
              <section className="card">
                <div className="card-head">
                  <h2>Net sales by month</h2>
                  <p>Excl. VAT, after credit notes · dashed line = monthly average</p>
                </div>
                <RevenueChart data={data.monthly} average={h.averageMonthlyRevenue} />
              </section>

              <section className="card">
                <div className="card-head">
                  <h2>Why this score</h2>
                  <p>{risk.score} of {risk.maxScore} points</p>
                </div>
                <div>
                  {risk.factors.map((f) => (
                    <div className="factor" key={f.name}>
                      <div className="row" style={{ gap: 8 }}>
                        <h3>{f.name}</h3>
                        <RatingBadge rating={f.rating} />
                      </div>
                      <div className="num" style={{ fontWeight: 600 }}>
                        {f.points}<span className="muted"> / {f.maxPoints}</span>
                      </div>
                      <Meter value={f.points} max={f.maxPoints} rating={f.rating} />
                      <p className="why">{f.explanation}</p>
                    </div>
                  ))}
                </div>
              </section>

              <div className="grid-2">
                <section className="card">
                  <div className="card-head">
                    <h2>Who buys</h2>
                    <p>Share of net sales</p>
                  </div>
                  <ConcentrationChart c={data.concentration} totalRevenue={h.netRevenue} />
                </section>
                <section className="card">
                  <div className="card-head">
                    <h2>Unpaid invoices by age</h2>
                    <p>{moneyShort(h.openReceivables)} owed, incl. VAT</p>
                  </div>
                  <AgingChart aging={data.collections.aging} />
                </section>
              </div>
            </div>

            <div className="stack">
              <DecisionPanel application={app} />
              <FinancingPanel financing={financing} />
              <section className="card stack" style={{ gap: 10 }}>
                <h2>Data checked</h2>
                <p className="secondary">
                  {dq.rowsAccepted.toLocaleString()} of {dq.rowsRead.toLocaleString()} rows used.{' '}
                  {dq.rowsExcluded} excluded, {dq.warnings} kept with a warning.
                </p>
                <button className="btn" onClick={() => setTab('issues')}>Review data issues</button>
              </section>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
