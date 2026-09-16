import { Fragment, useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import AddProductForm from './AddProductForm.jsx';
import CategoriesTab from './CategoriesTab.jsx';
import CouponsTab from './CouponsTab.jsx';
import {
  Alert, Empty, Loading, Money, Spinner, formatDate, prettyStatus, priorityTone, statusTone, useAsync,
} from '../../ui.jsx';

const ORDER_STATUSES = ['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELED', 'RETURN_REQUEST', 'RETURNED', 'REFUNDED'];
const TICKET_STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

/**
 * A chat panel backed by one of the admin assistants.
 *
 * Both assistants answer from tools rather than free recall - the analytics one runs SQL
 * aggregates, the ticket one reads and updates real tickets - so the figures and actions here
 * are the same ones the database would give.
 */
function AssistantPanel({ title, subtitle, send, openers }) {
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const bodyRef = useRef(null);

  useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages]);

  async function ask(text) {
    const q = (text ?? draft).trim();
    if (!q || busy) return;
    setDraft('');
    setMessages((m) => [...m, { from: 'user', text: q }]);
    setBusy(true);
    try {
      const res = await send(q);
      setMessages((m) => [...m, { from: 'bot', text: res.response || res.answer }]);
    } catch (e) {
      setMessages((m) => [...m, { from: 'bot', text: `Could not answer: ${e.message}` }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="section-head">
        <div>
          <h2>{title}</h2>
          <p className="small muted" style={{ marginTop: 3 }}>{subtitle}</p>
        </div>
        <span className="badge badge-ai">✨ AI</span>
      </div>

      <div
        ref={bodyRef}
        className="stack"
        style={{ maxHeight: 380, overflowY: 'auto', gap: 10, padding: messages.length ? '6px 2px 12px' : 0 }}
      >
        {messages.map((m, i) => (
          <div key={i} className={`ai-msg ${m.from}`} style={{ maxWidth: '88%' }}>{m.text}</div>
        ))}
        {busy && <div className="ai-msg bot row" style={{ gap: 8 }}><Spinner /> <span className="muted">Working…</span></div>}
      </div>

      {messages.length === 0 && (
        <div className="chips" style={{ marginBottom: 12 }}>
          {openers.map((q) => <button key={q} className="chip" onClick={() => ask(q)}>{q}</button>)}
        </div>
      )}

      <form className="row" onSubmit={(e) => { e.preventDefault(); ask(); }} style={{ gap: 8 }}>
        <input className="input" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Ask a question…" disabled={busy} />
        <button className="btn btn-dark" disabled={busy || !draft.trim()}>Ask</button>
      </form>
    </div>
  );
}

function AnalyticsTab() {
  return (
    <AssistantPanel
      title="Business analytics assistant"
      subtitle="Figures come from live database aggregates, not from the model's memory."
      send={api.analyticsAssistant}
      openers={[
        'Give me a sales summary for this week',
        'Which products are low on stock?',
        'Top selling products this month',
        'How many orders are in each status?',
      ]}
    />
  );
}

/** Full item breakdown for one order, loaded on demand from the admin order endpoint. */
function AdminOrderDetail({ orderId }) {
  const { data, loading, error } = useAsync(() => api.adminOrder(orderId), [orderId]);

  if (loading) return <Loading label="Loading order" />;
  if (error) return <Alert>{error}</Alert>;
  if (!data) return null;

  return (
    <div style={{ padding: '4px 0 8px' }}>
      {(data.items || []).map((i, idx) => (
        <div className="row-between" key={idx} style={{ padding: '6px 0' }}>
          <span className="small">{i.productName} <span className="muted">× {i.quantity}</span></span>
          <Money value={i.lineTotal} />
        </div>
      ))}
      <div className="row-between" style={{ paddingTop: 8, borderTop: '1px solid var(--line)' }}>
        <span className="bold small">Order total</span>
        <Money value={data.totalAmount} />
      </div>
    </div>
  );
}

function OrdersTab() {
  const { data, loading, error, reload } = useAsync(() => api.adminOrders(1, 50), []);
  const [saving, setSaving] = useState(null);
  const [saveError, setSaveError] = useState('');
  const [expanded, setExpanded] = useState(null);

  const orders = data?.content || [];

  async function updateStatus(orderId, status) {
    setSaving(orderId); setSaveError('');
    try { await api.adminUpdateOrderStatus(orderId, status); reload(); }
    catch (e) { setSaveError(e.message); }
    finally { setSaving(null); }
  }

  if (loading) return <Loading label="Loading orders" />;

  return (
    <div className="panel">
      <h2 style={{ marginBottom: 12 }}>All orders</h2>
      {error && <Alert>{error}</Alert>}
      {saveError && <div style={{ marginBottom: 10 }}><Alert>{saveError}</Alert></div>}

      {orders.length === 0 ? <Empty icon="📦" title="No orders yet" /> : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Order</th><th>Placed</th><th>Items</th><th>Total</th><th>Status</th><th>Update</th></tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <Fragment key={o.id}>
                  <tr>
                    <td>
                      <div className="bold">{o.orderNumber}</div>
                      <button className="btn-link tiny" onClick={() => setExpanded(expanded === o.id ? null : o.id)}>
                        {expanded === o.id ? 'Hide items' : 'View items'}
                      </button>
                    </td>
                    <td className="muted">{formatDate(o.placedAt)}</td>
                    <td>{o.itemsCount}</td>
                    <td><Money value={o.totalAmount} /></td>
                    <td><span className={`badge ${statusTone(o.status)}`}>{prettyStatus(o.status)}</span></td>
                    <td>
                      <select
                        className="select"
                        value={o.status}
                        disabled={saving === o.id}
                        onChange={(e) => updateStatus(o.id, e.target.value)}
                        style={{ minWidth: 150 }}
                      >
                        {ORDER_STATUSES.map((s) => <option key={s} value={s}>{prettyStatus(s)}</option>)}
                      </select>
                    </td>
                  </tr>
                  {expanded === o.id && (
                    <tr>
                      <td colSpan={6} style={{ background: '#fafbfc' }}>
                        <AdminOrderDetail orderId={o.id} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function TicketsTab() {
  const [filter, setFilter] = useState('');
  const { data, loading, error, reload } = useAsync(() => api.adminTickets(1, 50, filter || undefined), [filter]);
  const [busy, setBusy] = useState(null);
  const [actionError, setActionError] = useState('');
  const [expanded, setExpanded] = useState(null);

  const tickets = data?.content || [];

  async function setStatus(id, status) {
    setBusy(id); setActionError('');
    try { await api.adminUpdateTicketStatus(id, { status }); reload(); }
    catch (e) { setActionError(e.message); }
    finally { setBusy(null); }
  }

  return (
    <div className="stack">
      <AssistantPanel
        title="Support assistant"
        subtitle="Can list, read, reply to and update real tickets."
        send={api.ticketAssistant}
        openers={['List all open tickets', 'Which tickets are urgent?', 'Show the conversation for ticket 1']}
      />

      <div className="panel">
        <div className="row-between wrap" style={{ marginBottom: 12 }}>
          <h2>Ticket queue</h2>
          <div className="chips">
            <button className={`chip ${filter === '' ? 'active' : ''}`} onClick={() => setFilter('')}>All</button>
            {TICKET_STATUSES.map((s) => (
              <button key={s} className={`chip ${filter === s ? 'active' : ''}`} onClick={() => setFilter(s)}>
                {prettyStatus(s)}
              </button>
            ))}
          </div>
        </div>

        {error && <Alert>{error}</Alert>}
        {actionError && <div style={{ marginBottom: 10 }}><Alert>{actionError}</Alert></div>}
        {loading && <Loading label="Loading tickets" />}

        {!loading && tickets.length === 0 && <Empty icon="🎧" title="No tickets" />}

        <div className="stack">
          {tickets.map((t) => (
            <div className="card card-pad" key={t.id}>
              <div className="row-between wrap" style={{ gap: 10 }}>
                <div style={{ minWidth: 0 }}>
                  <div className="row wrap" style={{ gap: 7 }}>
                    <span className="bold">{t.subject}</span>
                    {/* Category and priority are set by AI triage the moment the ticket is created. */}
                    {t.priority && <span className={`badge ${priorityTone(t.priority)}`}>{t.priority}</span>}
                    {t.category && <span className="badge badge-grey">{prettyStatus(t.category)}</span>}
                    <span className={`badge ${statusTone(t.status)}`}>{prettyStatus(t.status)}</span>
                  </div>
                  {t.aiSummary && (
                    <div className="small muted" style={{ marginTop: 5 }}>
                      <span className="badge badge-ai" style={{ marginRight: 6 }}>✨ Summary</span>
                      {t.aiSummary}
                    </div>
                  )}
                </div>

                <select
                  className="select"
                  value={t.status}
                  disabled={busy === t.id}
                  onChange={(e) => setStatus(t.id, e.target.value)}
                  style={{ width: 165 }}
                >
                  {TICKET_STATUSES.map((s) => <option key={s} value={s}>{prettyStatus(s)}</option>)}
                </select>
              </div>

              <button className="btn-link small" style={{ marginTop: 10 }} onClick={() => setExpanded(expanded === t.id ? null : t.id)}>
                {expanded === t.id ? 'Hide details' : 'View details'}
              </button>

              {expanded === t.id && <TicketDetail ticket={t} onChanged={reload} />}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function TicketDetail({ ticket, onChanged }) {
  const { data, loading, reload } = useAsync(
    () => Promise.all([api.adminTicketMessages(ticket.id), api.adminSuggestedReply(ticket.id)]),
    [ticket.id],
  );
  const [reply, setReply] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const messages = data?.[0] || [];
  const draft = data?.[1]?.suggestedReply || '';

  // Pre-fill the reply box with the AI draft once it arrives, so the admin edits rather than
  // starts from a blank box. Only prefills while the box is still untouched.
  useEffect(() => {
    if (draft && !reply) setReply(draft);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  async function send(e) {
    e.preventDefault();
    if (!reply.trim()) return;
    setBusy(true); setError('');
    try {
      await api.adminReplyToTicket(ticket.id, reply.trim());
      setReply('');
      reload();
      onChanged?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ borderTop: '1px solid var(--line)', marginTop: 12, paddingTop: 12 }}>
      {ticket.description && (
        <div className="small" style={{ marginBottom: 12 }}>
          <span className="label">Customer wrote</span>
          <p className="muted" style={{ marginTop: 3 }}>{ticket.description}</p>
        </div>
      )}

      {loading ? <Loading label="Loading messages" /> : (
        <div className="stack" style={{ gap: 9 }}>
          {messages.map((m) => (
            <div key={m.id} className={`ai-msg ${m.fromAdmin ? 'user' : 'bot'}`} style={{ maxWidth: '84%' }}>
              <div className="tiny" style={{ opacity: .7, marginBottom: 3 }}>
                {m.fromAdmin ? 'Support' : 'Customer'} · {formatDate(m.createdAt)}
              </div>
              {m.content}
            </div>
          ))}
          {messages.length === 0 && <p className="small muted">No messages yet.</p>}
        </div>
      )}

      {draft && (
        <div className="small" style={{ marginTop: 12 }}>
          <span className="badge badge-ai">✨ Drafted for you, edit before sending</span>
        </div>
      )}

      {error && <div style={{ marginTop: 10 }}><Alert>{error}</Alert></div>}

      <form className="stack" onSubmit={send} style={{ marginTop: 10, gap: 8 }}>
        <textarea className="textarea" value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Write a reply to the customer…" />
        <button className="btn btn-cta" style={{ alignSelf: 'flex-start' }} disabled={busy || !reply.trim()}>
          {busy ? <Spinner /> : 'Send reply'}
        </button>
      </form>
    </div>
  );
}

function CatalogTab() {
  const { data, loading, error, reload } = useAsync(() => api.products(1, 50), []);
  const [busy, setBusy] = useState(null);
  const [actionError, setActionError] = useState('');
  const [adding, setAdding] = useState(false);
  const [created, setCreated] = useState('');

  const products = data?.content || [];

  async function updateStock(id, stock) {
    setBusy(id); setActionError('');
    try { await api.adminUpdateProduct(id, { stock }); reload(); }
    catch (e) { setActionError(e.message); }
    finally { setBusy(null); }
  }

  // Delete is a soft delete on the server: the product is marked inactive and re-indexed, so
  // existing orders that reference it stay intact.
  async function removeProduct(id) {
    setBusy(id); setActionError('');
    try { await api.adminDeleteProduct(id); reload(); }
    catch (e) { setActionError(e.message); }
    finally { setBusy(null); }
  }

  if (loading) return <Loading label="Loading catalog" />;

  return (
    <>
      {adding && (
        <AddProductForm
          onCancel={() => setAdding(false)}
          onCreated={(p) => {
            setAdding(false);
            setCreated(`“${p.name}” added to the catalog.`);
            reload();
          }}
        />
      )}

      <div className="panel">
        <div className="row-between" style={{ marginBottom: 12 }}>
          <h2>Catalog</h2>
          <div className="row" style={{ gap: 10 }}>
            <span className="small muted">{products.length} product{products.length === 1 ? '' : 's'}</span>
            {!adding && <button className="btn btn-cta btn-sm" onClick={() => setAdding(true)}>+ Add product</button>}
          </div>
        </div>

        {created && <div style={{ marginBottom: 12 }}><Alert kind="success">{created}</Alert></div>}
        {error && <Alert>{error}</Alert>}
        {actionError && <div style={{ marginBottom: 10 }}><Alert>{actionError}</Alert></div>}

        <p className="small muted" style={{ marginBottom: 12 }}>
          Saving a product also re-indexes it for search and recommendations.
        </p>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Status</th><th /></tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td className="bold">{p.name}</td>
                  <td className="muted">{p.categoryName || '-'}</td>
                  <td><Money value={p.price} /></td>
                  <td>
                    <input
                      className="input"
                      style={{ width: 88 }}
                      type="number"
                      min="0"
                      defaultValue={p.stockQty}
                      disabled={busy === p.id}
                      onBlur={(e) => {
                        const v = Number(e.target.value);
                        if (v !== p.stockQty) updateStock(p.id, v);
                      }}
                    />
                  </td>
                  <td>
                    {p.stockQty <= 0
                      ? <span className="badge badge-red">Out of stock</span>
                      : p.stockQty <= 5
                        ? <span className="badge badge-amber">Low</span>
                        : <span className="badge badge-green">In stock</span>}
                  </td>
                  <td>
                    <div className="row" style={{ gap: 10 }}>
                      {busy === p.id && <Spinner />}
                      {p.active && (
                        <button className="btn-link small" onClick={() => removeProduct(p.id)}>Remove</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

/**
 * Role management.
 *
 * The API only exposes assign-role by user id - there is no endpoint that lists users - so the
 * id has to be entered by hand rather than picked from a table.
 */
function UsersTab() {
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState('ADMIN');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(''); setDone('');
    try {
      const res = await api.adminAssignRole(userId, role);
      setDone(res.message || 'Role updated.');
      setUserId('');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <h2 style={{ marginBottom: 6 }}>Roles</h2>
      <p className="small muted" style={{ marginBottom: 14 }}>
        Promote a customer to admin, or demote an admin back to a normal user.
      </p>

      <form className="stack" onSubmit={submit} style={{ maxWidth: 380 }}>
        {error && <Alert>{error}</Alert>}
        {done && <Alert kind="success">{done}</Alert>}

        <div className="field">
          <label className="label">User ID</label>
          <input className="input" type="number" min="1" value={userId}
            onChange={(e) => setUserId(e.target.value)} required placeholder="e.g. 4" />
          <span className="tiny muted">
            The API has no endpoint that lists users, so the id must be entered manually.
          </span>
        </div>

        <div className="field">
          <label className="label">Role</label>
          <select className="select" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="ADMIN">ADMIN</option>
            <option value="USER">USER</option>
          </select>
        </div>

        <button className="btn btn-cta" style={{ alignSelf: 'flex-start' }} disabled={busy}>
          {busy ? <Spinner /> : 'Update role'}
        </button>
      </form>
    </div>
  );
}

const TABS = [
  { id: 'analytics', label: 'Analytics', render: () => <AnalyticsTab /> },
  { id: 'orders', label: 'Orders', render: () => <OrdersTab /> },
  { id: 'tickets', label: 'Support', render: () => <TicketsTab /> },
  { id: 'catalog', label: 'Catalog', render: () => <CatalogTab /> },
  { id: 'categories', label: 'Categories', render: () => <CategoriesTab /> },
  { id: 'coupons', label: 'Coupons', render: () => <CouponsTab /> },
  { id: 'users', label: 'Roles', render: () => <UsersTab /> },
];

export default function AdminApp() {
  const [tab, setTab] = useState('analytics');
  const active = TABS.find((t) => t.id === tab);

  return (
    <div className="page">
      <h1 style={{ marginBottom: 4 }}>Admin Console</h1>
      <p className="muted small" style={{ marginBottom: 16 }}>
        Store operations, support queue and AI assistants.
      </p>

      <div className="tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
            {t.label}
          </button>
        ))}
      </div>

      {active.render()}
    </div>
  );
}
