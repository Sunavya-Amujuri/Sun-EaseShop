import { useState } from 'react';
import { api } from '../api.js';
import { Alert, Empty, Loading, Spinner, useAsync } from '../ui.jsx';

const EMPTY = { line1: '', line2: '', city: '', state: '', country: 'India', pinCode: '', label: 'HOME', isDefault: false };

function AddressFields({ form, set }) {
  return (
    <>
      <div className="field">
        <label className="label">Address line 1</label>
        <input className="input" value={form.line1} onChange={set('line1')} required placeholder="Flat, house no., building" />
      </div>
      <div className="field">
        <label className="label">Address line 2</label>
        <input className="input" value={form.line2 || ''} onChange={set('line2')} placeholder="Area, street, landmark (optional)" />
      </div>
      <div className="form-grid">
        <div className="field"><label className="label">City</label><input className="input" value={form.city} onChange={set('city')} required /></div>
        <div className="field"><label className="label">State</label><input className="input" value={form.state} onChange={set('state')} required /></div>
        <div className="field"><label className="label">PIN code</label><input className="input" value={form.pinCode} onChange={set('pinCode')} required inputMode="numeric" /></div>
        <div className="field"><label className="label">Country</label><input className="input" value={form.country} onChange={set('country')} required /></div>
      </div>
      <div className="form-grid">
        <div className="field">
          <label className="label">Label</label>
          <select className="select" value={form.label || 'HOME'} onChange={set('label')}>
            <option value="HOME">Home</option>
            <option value="WORK">Work</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div className="field">
          <label className="label">Default address</label>
          <label className="row" style={{ gap: 8, paddingTop: 8 }}>
            <input
              type="checkbox"
              checked={!!form.isDefault}
              onChange={(e) => set('isDefault')({ target: { value: e.target.checked } })}
            />
            <span className="small">Use this address by default at checkout</span>
          </label>
        </div>
      </div>
    </>
  );
}

export default function Addresses() {
  const { data, loading, error, reload } = useAsync(() => api.addresses(1, 30), []);
  const [form, setForm] = useState(EMPTY);
  const [mode, setMode] = useState(null);   // null | 'create' | address id being edited
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');

  const list = data?.content || [];
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  function startCreate() {
    setForm(EMPTY);
    setMode('create');
    setFormError('');
  }

  function startEdit(a) {
    setForm({ ...a });
    setMode(a.id);
    setFormError('');
  }

  async function save(e) {
    e.preventDefault();
    setBusy(true); setFormError('');
    try {
      // The update endpoint takes the same shape as create and only applies non-null fields.
      if (mode === 'create') await api.addAddress(form);
      else await api.updateAddress(mode, form);
      setMode(null);
      setForm(EMPTY);
      reload();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id) {
    setFormError('');
    try { await api.deleteAddress(id); reload(); }
    catch (err) { setFormError(err.message); }
  }

  if (loading) return <div className="page"><Loading label="Loading addresses" /></div>;

  return (
    <div className="page-narrow">
      <div className="row-between" style={{ marginBottom: 14 }}>
        <h1>Your Addresses</h1>
        {!mode && <button className="btn btn-cta" onClick={startCreate}>+ Add address</button>}
      </div>

      {error && <Alert>{error}</Alert>}
      {formError && <div style={{ marginBottom: 12 }}><Alert>{formError}</Alert></div>}

      {mode && (
        <div className="panel" style={{ marginBottom: 16 }}>
          <h2 style={{ marginBottom: 12 }}>{mode === 'create' ? 'New address' : 'Edit address'}</h2>
          <form className="stack" onSubmit={save}>
            <AddressFields form={form} set={set} />
            <div className="row">
              <button className="btn btn-cta" disabled={busy}>
                {busy ? <Spinner /> : mode === 'create' ? 'Save address' : 'Save changes'}
              </button>
              <button type="button" className="btn" onClick={() => { setMode(null); setForm(EMPTY); }}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      {list.length === 0 && !mode ? (
        <Empty icon="📍" title="No addresses saved" action={<button className="btn btn-cta" onClick={startCreate}>Add your first address</button>}>
          Add an address so checkout is quicker.
        </Empty>
      ) : (
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))' }}>
          {list.map((a) => (
            <div className="card card-pad" key={a.id}>
              <div className="row" style={{ gap: 8, marginBottom: 6 }}>
                <span className="bold">{a.label || 'Address'}</span>
                {a.isDefault && <span className="badge badge-grey">Default</span>}
              </div>
              <div className="small muted" style={{ lineHeight: 1.65 }}>
                {a.line1}<br />
                {a.line2 && <>{a.line2}<br /></>}
                {a.city}, {a.state} {a.pinCode}<br />
                {a.country}
              </div>
              <div className="row" style={{ gap: 14, marginTop: 10 }}>
                <button className="btn-link small" onClick={() => startEdit(a)}>Edit</button>
                <button className="btn-link small" onClick={() => remove(a.id)}>Remove</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
