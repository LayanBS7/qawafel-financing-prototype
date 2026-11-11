import { useEffect, useMemo, useState } from 'react';
import { api, type Analysis, type Issue, type Severity } from '../api';
import { SeverityBadge } from './bits';

type Filter = 'ALL' | Severity;

export default function IssuesTab({ analysis }: { analysis: Analysis }) {
  const dq = analysis.dataQuality;
  const [issues, setIssues] = useState<Issue[] | null>(null);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [type, setType] = useState<string>('ALL');

  useEffect(() => {
    api.issues(analysis.application.id).then(setIssues);
  }, [analysis.application.id]);

  const shown = useMemo(
    () => (issues ?? []).filter((i) => (filter === 'ALL' || i.severity === filter) && (type === 'ALL' || i.type === type)),
    [issues, filter, type],
  );

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <h2>What we found in the file</h2>
          <p>{dq.rowsRead.toLocaleString()} rows read</p>
        </div>
        <p className="secondary" style={{ marginBottom: 14 }}>
          Nothing is changed silently. Rows we can't trust are <strong>excluded</strong>; rows where the sale clearly
          happened but a detail is off are <strong>kept and flagged</strong>; the rest is for information.
        </p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Problem</th>
                <th>What we did</th>
                <th className="r">Rows</th>
              </tr>
            </thead>
            <tbody>
              {dq.byType.map((t) => (
                <tr
                  key={t.type}
                  className={`clickable${type === t.type ? ' selected' : ''}`}
                  onClick={() => setType(type === t.type ? 'ALL' : t.type)}
                >
                  <td>{t.label}</td>
                  <td><SeverityBadge severity={t.severity} /></td>
                  <td className="r">{t.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card">
        <div className="card-head">
          <h2>Row by row</h2>
          <div className="row" style={{ gap: 8 }}>
            {type !== 'ALL' && (
              <button className="btn btn-ghost small" onClick={() => setType('ALL')}>Clear problem filter</button>
            )}
            <select value={filter} onChange={(e) => setFilter(e.target.value as Filter)} style={{ width: 'auto' }}>
              <option value="ALL">All outcomes</option>
              <option value="EXCLUDED">Excluded</option>
              <option value="WARNING">Kept, flagged</option>
              <option value="INFO">Info</option>
            </select>
          </div>
        </div>
        {issues === null ? (
          <div className="loading">Loading…</div>
        ) : shown.length === 0 ? (
          <div className="empty">No issues match this filter.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th className="r">Row</th>
                  <th>Invoice</th>
                  <th>Problem</th>
                  <th>Details</th>
                  <th>Action taken</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((i, n) => (
                  <tr key={`${i.sourceRow}-${i.type}-${n}`}>
                    <td className="r">{i.sourceRow}</td>
                    <td className="num" style={{ whiteSpace: 'nowrap' }}>{i.invoiceNumber ?? '-'}</td>
                    <td>
                      <div style={{ marginBottom: 4 }}>{i.label}</div>
                      <SeverityBadge severity={i.severity} />
                    </td>
                    <td className="secondary">{i.message}</td>
                    <td className="secondary">{i.actionTaken}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
