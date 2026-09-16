import { useState } from 'react';
import { api } from '../../api.js';
import { Alert, Empty, Loading, Money, Spinner, formatDate, useAsync } from '../../ui.jsx';

function todayPlus(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

const EMPTY = {
  code: '',
  discountType: 'FLAT',
  discountAmount: '',
  minimumOrderAmount: '',
  startDate: todayPlus(0),
  expiryDate: todayPlus(30),
  active: true,
};

export default function CouponsTab() {
  const { data, loading, error, reload } = useAsync(() => api.adminCoupons(1, 50), []);
  const [form, setForm] = useState(EMPTY);
  const [mode, setMode] = useState(null); // null | 'create' | id
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');

  const list = data?.content || [];
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function save(e) {
    e.preventDefault();
    setBusy(true); setActionError('');
    try {
      const payload = {
        ...form,
        code: form.code.trim().toUpperCase(),
        discountAmount: Number(form.discountAmount),
        minimumOrderAmount: Number(form.minimumOrderAmount || 0),
      };
      if (mode === 'create') await api.adminCreateCoupon(payload);
      else await api.adminUpdateCoupon(mode, payload);
      setMode(null); setForm(EMPTY);
      reload();
    } catch (err) {
      setActionError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function toggle(c) {
    setActionError('');
    // Dedicated status endpoint - it expects {isActive}, unlike the main update endpoint.
    try { await api.adminSetCouponActive(c.id, !c.active); reload(); }
    catch (err) { setActionError(err.message); }
  }

  async function remove(id) {
    setActionError('');
    try { await api.adminDeleteCoupon(id); reload(); }
    catch (err) { setActionError(err.message); }
  }

  if (loading) return <Loading label="Loading coupons" />;

  return (
    <div className="panel">
      <div className="row-between" style={{ marginBottom: 12 }}>
        <h2>Coupons</h2>
        {!mode && (
          <button className="btn btn-cta btn-sm" onClick={() => { setForm(EMPTY); setMode('create'); }}>
            + Create coupon
          </button>
        )}
      </div>

      {error && <Alert>{error}</Alert>}
      {actionError && <div style={{ marginBottom: 10 }}><Alert>{actionError}</Alert></div>}

      {mode && (
        <form className="stack card card-pad" onSubmit={save} style={{ marginBottom: 16 }}>
          <h3>{mode === 'create' ? 'New coupon' : 'Edit coupon'}</h3>

          <div className="form-grid">
            <div className="field">
              <label className="label">Code</label>
              <input className="input" value={form.code} onChange={set('code')} required
                placeholder="WELCOME10" style={{ textTransform: 'uppercase' }} />
            </div>
            <div className="field">
              <label className="label">Discount type</label>
              <select className="select" value={form.discountType} onChange={set('discountType')}>
                <option value="FLAT">Flat amount off</option>
                <option value="PERCENTAGE">Percentage off</option>
              </select>
            </div>
            <div className="field">
              <label className="label">{form.discountType === 'PERCENTAGE' ? 'Discount (%)' : 'Discount (₹)'}</label>
              <input className="input" type="number" min="0" step="0.01"
                value={form.discountAmount} onChange={set('discountAmount')} required />
            </div>
            <div className="field">
              <label className="label">Minimum order (₹)</label>
              <input className="input" type="number" min="0" step="0.01"
                value={form.minimumOrderAmount} onChange={set('minimumOrderAmount')} placeholder="0" />
            </div>
            <div className="field">
              <label className="label">Valid from</label>
              <input className="input" type="date" value={form.startDate} onChange={set('startDate')} required />
            </div>
            <div className="field">
              <label className="label">Expires on</label>
              <input className="input" type="date" value={form.expiryDate} onChange={set('expiryDate')} required />
            </div>
          </div>

          <div className="row">
            <button className="btn btn-cta" disabled={busy}>{busy ? <Spinner /> : 'Save coupon'}</button>
            <button type="button" className="btn" onClick={() => { setMode(null); setForm(EMPTY); }}>Cancel</button>
          </div>
        </form>
      )}

      {list.length === 0 ? <Empty icon="🎟️" title="No coupons yet" /> : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Code</th><th>Discount</th><th>Min. order</th><th>Valid</th><th>Status</th><th /></tr>
            </thead>
            <tbody>
              {list.map((c) => (
                <tr key={c.id}>
                  <td className="bold">{c.code}</td>
                  <td>
                    {c.discountType === 'PERCENTAGE'
                      ? `${c.discountAmount}%`
                      : <Money value={c.discountAmount} />}
                  </td>
                  <td>{Number(c.minimumOrderAmount) > 0 ? <Money value={c.minimumOrderAmount} /> : '-'}</td>
                  <td className="muted small">{formatDate(c.startDate)} to {formatDate(c.expiryDate)}</td>
                  <td>
                    <span className={`badge ${c.active ? 'badge-green' : 'badge-grey'}`}>
                      {c.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td>
                    <div className="row" style={{ gap: 12 }}>
                      <button className="btn-link small" onClick={() => { setForm({ ...c }); setMode(c.id); }}>Edit</button>
                      <button className="btn-link small" onClick={() => toggle(c)}>
                        {c.active ? 'Deactivate' : 'Activate'}
                      </button>
                      <button className="btn-link small" onClick={() => remove(c.id)}>Delete</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
