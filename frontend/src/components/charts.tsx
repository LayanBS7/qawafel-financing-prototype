import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { AgingBucket, Concentration, MonthlyPoint } from '../api';
import { axisMoney, money, monthLabel, share } from '../format';

/* Recharts passes these to custom tooltips. */
interface TipProps<T> {
  active?: boolean;
  payload?: ReadonlyArray<{ payload: T }>;
}

/* ---------------------------------------------------------------- revenue by month */

function RevenueTip({ active, payload }: TipProps<MonthlyPoint>) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="chart-tip">
      <strong>{monthLabel(p.month)}</strong>
      <div className="line">Net sales <b>{money(p.netSales)}</b></div>
      <div className="line">Gross sales <b>{money(p.grossSales)}</b></div>
      <div className="line">Credit notes <b>−{money(p.creditNotes)}</b></div>
      <div className="line">Invoices <b>{p.invoiceCount}</b></div>
      <div className="line">Buying customers <b>{p.activeCustomers}</b></div>
    </div>
  );
}

export function RevenueChart({ data, average }: { data: MonthlyPoint[]; average: number }) {
  return (
    <>
      <div className="chart" aria-label="Net sales by month, bar chart">
        <ResponsiveContainer>
          <BarChart data={data} margin={{ top: 16, right: 8, bottom: 0, left: 0 }} barCategoryGap="22%">
            <CartesianGrid vertical={false} />
            <XAxis dataKey="month" tickFormatter={monthLabel} tickLine={false} interval={0} minTickGap={0} />
            <YAxis tickFormatter={axisMoney} tickLine={false} axisLine={false} width={44} />
            <Tooltip content={(p) => <RevenueTip {...(p as TipProps<MonthlyPoint>)} />} />
            <ReferenceLine
              y={average}
              stroke="var(--ink-muted)"
              strokeDasharray="4 4"
            />
            <Bar dataKey="netSales" fill="var(--series-1)" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <DataTable
        head={['Month', 'Net sales', 'Invoices', 'Customers']}
        rows={data.map((m) => [monthLabel(m.month), money(m.netSales), String(m.invoiceCount), String(m.activeCustomers)])}
      />
    </>
  );
}

/* ---------------------------------------------------------------- customer concentration */

interface ShareRow {
  name: string;
  share: number;
  revenue: number;
}

function ShareTip({ active, payload }: TipProps<ShareRow>) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="chart-tip">
      <strong>{p.name}</strong>
      <div className="line">Share of sales <b>{share(p.share)}</b></div>
      <div className="line">Net sales <b>{money(p.revenue)}</b></div>
    </div>
  );
}

export function ConcentrationChart({ c, totalRevenue }: { c: Concentration; totalRevenue: number }) {
  const rows: ShareRow[] = c.topCustomers.map((t) => ({ name: t.name, share: t.share, revenue: t.revenue }));
  const rest = 1 - rows.reduce((s, r) => s + r.share, 0);
  rows.push({ name: 'All other customers', share: rest, revenue: totalRevenue * rest });
  return (
    <>
      <div className="chart" style={{ height: 230 }} aria-label="Share of sales by customer, bar chart">
        <ResponsiveContainer>
          <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 0 }} barCategoryGap="28%">
            <XAxis type="number" hide domain={[0, 'dataMax']} />
            <YAxis type="category" dataKey="name" width={170} tickLine={false} axisLine={false} />
            <Tooltip content={(p) => <ShareTip {...(p as TipProps<ShareRow>)} />} />
            <Bar dataKey="share" radius={[0, 4, 4, 0]} fill="var(--series-1)">
              <LabelList
                dataKey="share"
                position="right"
                formatter={(v: unknown) => share(Number(v))}
                className="recharts-text"
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <DataTable
        head={['Customer', 'Share', 'Net sales']}
        rows={rows.map((r) => [r.name, share(r.share), money(r.revenue)])}
      />
    </>
  );
}

/* ---------------------------------------------------------------- receivables aging */

function AgingTip({ active, payload }: TipProps<AgingBucket>) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="chart-tip">
      <strong>{p.label}</strong>
      <div className="line">Amount <b>{money(p.amount)}</b></div>
      <div className="line">Share of receivables <b>{share(p.share)}</b></div>
      <div className="line">Invoices <b>{p.invoiceCount}</b></div>
    </div>
  );
}

export function AgingChart({ aging }: { aging: AgingBucket[] }) {
  return (
    <>
      <div className="chart" style={{ height: 230 }} aria-label="Unpaid invoices by days overdue, bar chart">
        <ResponsiveContainer>
          <BarChart data={aging} layout="vertical" margin={{ top: 0, right: 56, bottom: 0, left: 0 }} barCategoryGap="28%">
            <XAxis type="number" hide domain={[0, 'dataMax']} />
            <YAxis type="category" dataKey="label" width={130} tickLine={false} axisLine={false} />
            <Tooltip content={(p) => <AgingTip {...(p as TipProps<AgingBucket>)} />} />
            <Bar dataKey="amount" radius={[0, 4, 4, 0]} fill="var(--series-1)">
              <LabelList
                dataKey="share"
                position="right"
                formatter={(v: unknown) => share(Number(v))}
                className="recharts-text"
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <DataTable
        head={['Bucket', 'Amount', 'Invoices', 'Share']}
        rows={aging.map((a) => [a.label, money(a.amount), String(a.invoiceCount), share(a.share)])}
      />
    </>
  );
}

/* ---------------------------------------------------------------- table view for every chart */

function DataTable({ head, rows }: { head: string[]; rows: string[][] }) {
  return (
    <details className="data-table">
      <summary>Show as table</summary>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {head.map((h, i) => (
                <th key={h} className={i > 0 ? 'r' : undefined}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r[0]}>
                {r.map((cell, i) => (
                  <td key={i} className={i > 0 ? 'r' : undefined}>{cell}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
