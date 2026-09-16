import { useState } from 'react';
import { api } from '../api.js';
import { Alert, Empty, Loading, Spinner, formatDate, prettyStatus, statusTone, useAsync } from '../ui.jsx';

function Conversation({ ticket }) {
  const { data, loading, reload } = useAsync(() => api.ticketMessages(ticket.id), [ticket.id]);
  const [reply, setReply] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const messages = Array.isArray(data) ? data : (data?.content || []);

  async function send(e) {
    e.preventDefault();
    if (!reply.trim()) return;
    setBusy(true); setError('');
    try {
      await api.replyToTicket(ticket.id, reply.trim());
      setReply('');
      reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ borderTop: '1px solid var(--line)', marginTop: 12, paddingTop: 12 }}>
      {loading ? <Loading label="Loading conversation" /> : (
        <div className="stack" style={{ gap: 9 }}>
          {messages.map((m) => (
            <div
              key={m.id}
              className={`ai-msg ${m.fromAdmin ? 'bot' : 'user'}`}
              style={{ maxWidth: '82%', alignSelf: m.fromAdmin ? 'flex-start' : 'flex-end' }}
            >
              <div className="tiny" style={{ opacity: .7, marginBottom: 3 }}>
                {m.fromAdmin ? 'Support' : 'You'} · {formatDate(m.createdAt)}
              </div>
              {m.content}
            </div>
          ))}
          {messages.length === 0 && <p className="small muted">No messages yet.</p>}
        </div>
      )}

      {error && <div style={{ marginTop: 10 }}><Alert>{error}</Alert></div>}

      <form className="row" onSubmit={send} style={{ marginTop: 12, gap: 8 }}>
        <input className="input" value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Write a reply…" />
        <button className="btn btn-dark btn-sm" disabled={busy || !reply.trim()}>
          {busy ? <Spinner light /> : 'Send'}
        </button>
      </form>
    </div>
  );
}

export default function Support() {
  const { data, loading, error, reload } = useAsync(() => api.myTickets(1, 30), []);
  const [form, setForm] = useState({ subject: '', description: '' });
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [expanded, setExpanded] = useState(null);

  const tickets = data?.content || [];
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function create(e) {
    e.preventDefault();
    setBusy(true); setFormError('');
    try {
      await api.createTicket(form);
      setForm({ subject: '', description: '' });
      setOpen(false);
      reload();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <div className="page"><Loading label="Loading your tickets" /></div>;

  return (
    <div className="page-narrow">
      <div className="row-between" style={{ marginBottom: 14 }}>
        <div>
          <h1>Customer Support</h1>
          <p className="small muted" style={{ marginTop: 4 }}>
            Raise an issue and our team will get back to you.
          </p>
        </div>
        {!open && <button className="btn btn-cta" onClick={() => setOpen(true)}>+ New ticket</button>}
      </div>

      {error && <Alert>{error}</Alert>}
      {formError && <div style={{ marginBottom: 12 }}><Alert>{formError}</Alert></div>}

      {open && (
        <div className="panel" style={{ marginBottom: 16 }}>
          <h2 style={{ marginBottom: 12 }}>Raise a ticket</h2>
          <form className="stack" onSubmit={create}>
            <div className="field">
              <label className="label">Subject</label>
              <input className="input" value={form.subject} onChange={set('subject')} required placeholder="Short summary of the issue" />
            </div>
            <div className="field">
              <label className="label">Describe the problem</label>
              <textarea className="textarea" value={form.description} onChange={set('description')} required
                placeholder="Tell us what happened, including order numbers if relevant." />
            </div>
            <div className="row">
              <button className="btn btn-cta" disabled={busy}>{busy ? <Spinner /> : 'Submit ticket'}</button>
              <button type="button" className="btn" onClick={() => setOpen(false)}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      {tickets.length === 0 && !open ? (
        <Empty icon="🎧" title="No support tickets" action={<button className="btn btn-cta" onClick={() => setOpen(true)}>Raise a ticket</button>}>
          If something goes wrong with an order, tell us here.
        </Empty>
      ) : (
        <div className="stack">
          {tickets.map((t) => (
            <div className="panel" key={t.id}>
              <div className="row-between wrap" style={{ gap: 10 }}>
                <div>
                  <div className="bold" style={{ fontSize: 15.5 }}>{t.subject}</div>
                  <div className="small muted" style={{ marginTop: 3 }}>Ticket #{t.id}</div>
                </div>
                <div className="row" style={{ gap: 7 }}>
                  <span className={`badge ${statusTone(t.status)}`}>{prettyStatus(t.status)}</span>
                </div>
              </div>

              {t.description && <p className="small muted" style={{ marginTop: 9 }}>{t.description}</p>}

              <button
                className="btn-link small"
                style={{ marginTop: 10 }}
                onClick={() => setExpanded(expanded === t.id ? null : t.id)}
              >
                {expanded === t.id ? 'Hide conversation' : 'View conversation'}
              </button>

              {expanded === t.id && <Conversation ticket={t} />}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
