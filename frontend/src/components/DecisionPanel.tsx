import { useState } from 'react';
import { api, ApiError, type ApplicationInfo, type Decision } from '../api';
import { date, DECISION_LABEL } from '../format';

const OPTIONS: Decision[] = ['RECOMMEND', 'RECOMMEND_WITH_CONDITIONS', 'NEED_MORE_INFO', 'DECLINE'];

/** The analyst stays in charge: the score suggests, a person decides. */
export default function DecisionPanel({ application }: { application: ApplicationInfo }) {
  const [saved, setSaved] = useState<{ decision: Decision; note: string; at: string } | null>(
    application.decision
      ? { decision: application.decision, note: application.decisionNote ?? '', at: application.decidedAt ?? '' }
      : null,
  );
  const [editing, setEditing] = useState(!application.decision);
  const [decision, setDecision] = useState<Decision | null>(application.decision);
  const [note, setNote] = useState(application.decisionNote ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    if (!decision) return;
    setBusy(true);
    setError(null);
    try {
      await api.decide(application.id, decision, note);
      setSaved({ decision, note, at: new Date().toISOString() });
      setEditing(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card stack" style={{ gap: 12 }}>
      <h2>Analyst decision</h2>
      {!editing && saved ? (
        <>
          <div className="decision-done">
            <strong>{DECISION_LABEL[saved.decision]}</strong>
            {saved.note && <p className="secondary small" style={{ marginTop: 4 }}>{saved.note}</p>}
            <p className="muted small" style={{ marginTop: 4 }}>Recorded {date(saved.at)}</p>
          </div>
          <button className="btn" onClick={() => setEditing(true)}>Change decision</button>
        </>
      ) : (
        <>
          <div className="radio-list" role="radiogroup">
            {OPTIONS.map((o) => (
              <label key={o}>
                <input type="radio" name="decision" checked={decision === o} onChange={() => setDecision(o)} />
                {DECISION_LABEL[o]}
              </label>
            ))}
          </div>
          <label className="field">
            Note for the bank
            <textarea rows={3} value={note} maxLength={2000} onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Cap Al-Rayyan exposure at 25%; confirm bank statements." />
          </label>
          {error && <div className="alert alert-error">{error}</div>}
          <div className="row">
            <button className="btn btn-primary" disabled={!decision || busy} onClick={save}>
              {busy ? 'Saving…' : 'Save decision'}
            </button>
            {saved && <button className="btn btn-ghost" onClick={() => setEditing(false)}>Cancel</button>}
          </div>
        </>
      )}
    </section>
  );
}
