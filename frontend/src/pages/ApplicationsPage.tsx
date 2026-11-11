import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, type ApplicationSummary } from '../api';
import { date, DECISION_LABEL, money } from '../format';

export default function ApplicationsPage() {
  const navigate = useNavigate();
  const [apps, setApps] = useState<ApplicationSummary[] | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [company, setCompany] = useState('');
  const [requested, setRequested] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const refresh = () =>
    api.list().then(setApps).catch((e) => {
      setApps([]);
      setError(e instanceof ApiError ? e.message : String(e));
    });

  useEffect(() => {
    refresh();
  }, []);

  const run = async (action: () => Promise<{ application: { id: number } }>) => {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      navigate(`/applications/${result.application.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const pick = (f: File | undefined) => {
    if (!f) return;
    setError(null);
    if (!/\.(xlsx|xls)$/i.test(f.name)) {
      setError('Please choose an Excel file (.xlsx or .xls).');
      return;
    }
    setFile(f);
  };

  const remove = async (id: number) => {
    if (!confirm('Delete this application and its data?')) return;
    await api.remove(id);
    refresh();
  };

  return (
    <div className="stack">
      <div>
        <h1>Financing applications</h1>
        <p className="secondary" style={{ marginTop: 4 }}>
          Upload a wholesaler's 12-month invoice export. The file is checked, cleaned and turned into a credit
          assessment you can review and send to the bank.
        </p>
      </div>

      <div className="grid-main">
        <section className="card">
          <div className="card-head">
            <h2>New application</h2>
          </div>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              if (file) run(() => api.upload(file, company, requested));
            }}
          >
            <div
              className={`dropzone${dragging ? ' active' : ''}`}
              role="button"
              tabIndex={0}
              onClick={() => input.current?.click()}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragging(false);
                pick(e.dataTransfer.files[0]);
              }}
            >
              <strong>{file ? file.name : 'Drop the invoice Excel file here'}</strong>
              <span className="muted small">
                {file ? `${(file.size / 1024).toFixed(0)} KB · click to change` : 'or click to choose · .xlsx / .xls'}
              </span>
              <input
                ref={input}
                type="file"
                accept=".xlsx,.xls"
                hidden
                onChange={(e) => pick(e.target.files?.[0])}
              />
            </div>

            <div className="grid-2">
              <label className="field">
                Company name
                <input
                  type="text"
                  value={company}
                  onChange={(e) => setCompany(e.target.value)}
                  placeholder="Taken from the file if empty"
                />
              </label>
              <label className="field">
                Requested amount (SAR)
                <input
                  type="text"
                  inputMode="numeric"
                  value={requested}
                  onChange={(e) => setRequested(e.target.value.replace(/[^\d,]/g, ''))}
                  placeholder="Optional, e.g. 3,000,000"
                />
              </label>
            </div>

            {error && <div className="alert alert-error" role="alert">{error}</div>}

            <div className="row">
              <button className="btn btn-primary" type="submit" disabled={!file || busy}>
                {busy ? 'Analysing…' : 'Analyse file'}
              </button>
            </div>
          </form>
        </section>

        <aside className="card stack">
          <h2>No file to hand?</h2>
          <p className="secondary">
            Load the sample: 12 months of made-up invoices for a fictional Riyadh food wholesaler, including 32
            deliberate data errors and a few risky customers for the analysis to find.
          </p>
          <div className="row">
            <button className="btn" disabled={busy} onClick={() => run(api.loadSample)}>
              Load sample data
            </button>
            <a className="small" href="/api/applications/sample/file" download>
              Download sample .xlsx
            </a>
          </div>
        </aside>
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Applications</h2>
          <p>Stored in memory: cleared when the backend restarts.</p>
        </div>
        {apps === null ? (
          <div className="loading">Loading…</div>
        ) : apps.length === 0 ? (
          <div className="empty">No applications yet. Upload a file or load the sample.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Company</th>
                  <th>Period</th>
                  <th className="r">Requested</th>
                  <th className="r">Rows used</th>
                  <th>Status</th>
                  <th>Uploaded</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => (
                  <tr key={a.id} className="clickable" onClick={() => navigate(`/applications/${a.id}`)}>
                    <td>
                      <Link to={`/applications/${a.id}`} onClick={(e) => e.stopPropagation()}>
                        {a.companyName}
                      </Link>
                      <div className="muted small">{a.sourceFileName}</div>
                    </td>
                    <td className="num">
                      {date(a.periodStart)} – {date(a.periodEnd)}
                    </td>
                    <td className="r">{a.requestedAmount ? money(a.requestedAmount) : '-'}</td>
                    <td className="r">
                      {a.rowsAccepted.toLocaleString()} / {a.rowsRead.toLocaleString()}
                    </td>
                    <td>
                      {a.decision ? (
                        <span className="badge badge-plain">{DECISION_LABEL[a.decision]}</span>
                      ) : (
                        <span className="badge">Awaiting review</span>
                      )}
                    </td>
                    <td className="muted">{date(a.uploadedAt)}</td>
                    <td className="r">
                      <button
                        className="btn btn-ghost btn-danger small"
                        onClick={(e) => {
                          e.stopPropagation();
                          remove(a.id);
                        }}
                      >
                        Delete
                      </button>
                    </td>
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
