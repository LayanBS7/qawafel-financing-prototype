import { useEffect, useMemo, useState } from 'react';
import { api, type Analysis, type CustomerSummary, type InvoiceRow } from '../api';
import { date, days, money, share } from '../format';
import { FlagBadge } from './bits';

export default function CustomersTab({ analysis }: { analysis: Analysis }) {
  const [onlyFlagged, setOnlyFlagged] = useState(false);
  const [selected, setSelected] = useState<CustomerSummary | null>(null);
  const maxShare = analysis.customers[0]?.share ?? 1;

  const rows = useMemo(
    () => (onlyFlagged ? analysis.customers.filter((c) => c.flags.length) : analysis.customers),
    [analysis.customers, onlyFlagged],
  );

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <h2>Customers</h2>
          <label className="row small" style={{ gap: 6, cursor: 'pointer' }}>
            <input type="checkbox" checked={onlyFlagged} onChange={(e) => setOnlyFlagged(e.target.checked)} />
            Only customers with flags
          </label>
        </div>
        <p className="muted small" style={{ marginBottom: 10 }}>
          "Days late" = days paid after the due date. "Recent" compares invoices due in the last 4 months with the
          start of the year. Click a customer to see its invoices.
        </p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Customer</th>
                <th>Share of sales</th>
                <th className="r">Net sales</th>
                <th className="r">Days late (start → recent)</th>
                <th className="r">Owed now</th>
                <th className="r">60+ days overdue</th>
                <th>Flags</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr
                  key={c.customerKey}
                  className={`clickable${selected?.customerKey === c.customerKey ? ' selected' : ''}`}
                  onClick={() => setSelected(selected?.customerKey === c.customerKey ? null : c)}
                >
                  <td>
                    <strong style={{ fontWeight: 550 }}>{c.name}</strong>
                    <div className="muted small">{[c.segment, c.city].filter(Boolean).join(' · ')}</div>
                  </td>
                  <td className="num" style={{ whiteSpace: 'nowrap' }}>
                    <span className="share-bar" style={{ width: `${(c.share / maxShare) * 60}px` }} />
                    {share(c.share)}
                  </td>
                  <td className="r">{money(c.revenue)}</td>
                  <td className="r">
                    {days(c.baselineDaysLate)} → {days(c.recentDaysLate)}
                  </td>
                  <td className="r">{money(c.openBalance)}</td>
                  <td className="r">{c.overdue60Balance > 0 ? money(c.overdue60Balance) : '-'}</td>
                  <td>
                    <div className="row" style={{ gap: 4 }}>
                      {c.flags.map((f) => <FlagBadge key={f} flag={f} />)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selected && <CustomerInvoices applicationId={analysis.application.id} customer={selected} />}
    </div>
  );
}

function CustomerInvoices({ applicationId, customer }: { applicationId: number; customer: CustomerSummary }) {
  const [rows, setRows] = useState<InvoiceRow[] | null>(null);

  useEffect(() => {
    setRows(null);
    api.invoices(applicationId, customer.customerKey).then(setRows);
  }, [applicationId, customer.customerKey]);

  const status = (r: InvoiceRow) => {
    if (r.type === 'CREDIT_NOTE') return <span className="badge">Credit note</span>;
    if (r.paymentStatus === 'PAID') return <span className="badge badge-good">Paid</span>;
    if (r.paymentStatus === 'PARTIALLY_PAID') return <span className="badge badge-serious">Partly paid</span>;
    return <span className="badge badge-serious">Unpaid</span>;
  };
  const late = (r: InvoiceRow) => {
    if (!r.paidDate || r.type === 'CREDIT_NOTE') return '-';
    const d = Math.round((new Date(r.paidDate).getTime() - new Date(r.dueDate).getTime()) / 86_400_000);
    return d > 0 ? `${d} d late` : 'on time';
  };

  return (
    <section className="card">
      <div className="card-head">
        <h2>{customer.name}: invoices</h2>
        <p>
          First {date(customer.firstInvoice)} · last {date(customer.lastInvoice)} · {customer.invoiceCount} invoices
        </p>
      </div>
      {rows === null ? (
        <div className="loading">Loading…</div>
      ) : (
        <div className="table-wrap" style={{ maxHeight: 420, overflowY: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Invoice</th>
                <th>Date</th>
                <th>Due</th>
                <th className="r">Total</th>
                <th>Status</th>
                <th>Paid</th>
                <th className="r">Owed</th>
              </tr>
            </thead>
            <tbody>
              {[...rows].reverse().map((r) => (
                <tr key={r.sourceRow}>
                  <td className="num">{r.invoiceNumber}<div className="muted small">row {r.sourceRow}</div></td>
                  <td className="num">{date(r.invoiceDate)}</td>
                  <td className="num">{date(r.dueDate)}</td>
                  <td className="r">{money(r.total)}</td>
                  <td>{status(r)}</td>
                  <td className="num">{r.paidDate ? `${date(r.paidDate)} · ${late(r)}` : '-'}</td>
                  <td className="r">{r.outstanding > 0 ? money(r.outstanding) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
