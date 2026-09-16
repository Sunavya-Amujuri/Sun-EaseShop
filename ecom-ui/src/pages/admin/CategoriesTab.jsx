import { useState } from 'react';
import { api } from '../../api.js';
import { useCategories } from '../../store.jsx';
import { Alert, Empty, Loading, Spinner } from '../../ui.jsx';

const EMPTY = { name: '', description: '', active: true };

export default function CategoriesTab() {
  // The app-wide list, so saving here also refreshes the shop navigation and the product form
  // rather than only this table.
  const { categories: list, loading, error, reload } = useCategories();
  const [form, setForm] = useState(EMPTY);
  const [mode, setMode] = useState(null); // null | 'create' | id
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function save(e) {
    e.preventDefault();
    setBusy(true); setActionError('');
    try {
      if (mode === 'create') await api.adminCreateCategory(form);
      else await api.adminUpdateCategory(mode, form);
      setMode(null); setForm(EMPTY);
      reload();
    } catch (err) {
      setActionError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleActive(c) {
    setActionError('');
    try { await api.adminUpdateCategory(c.id, { ...c, active: !c.active }); reload(); }
    catch (err) { setActionError(err.message); }
  }

  async function remove(id) {
    setActionError('');
    try { await api.adminDeleteCategory(id); reload(); }
    catch (err) { setActionError(err.message); }
  }

  if (loading) return <Loading label="Loading categories" />;

  return (
    <div className="panel">
      <div className="row-between" style={{ marginBottom: 12 }}>
        <h2>Categories</h2>
        {!mode && (
          <button className="btn btn-cta btn-sm" onClick={() => { setForm(EMPTY); setMode('create'); }}>
            + Add category
          </button>
        )}
      </div>

      {error && <Alert>{error}</Alert>}
      {actionError && <div style={{ marginBottom: 10 }}><Alert>{actionError}</Alert></div>}

      {mode && (
        <form className="stack card card-pad" onSubmit={save} style={{ marginBottom: 16 }}>
          <h3>{mode === 'create' ? 'New category' : 'Edit category'}</h3>
          <div className="field">
            <label className="label">Name</label>
            <input className="input" value={form.name} onChange={set('name')} required placeholder="Running Shoes" />
          </div>
          <div className="field">
            <label className="label">Description</label>
            <input className="input" value={form.description || ''} onChange={set('description')} placeholder="What belongs in this category" />
          </div>
          <div className="row">
            <button className="btn btn-cta" disabled={busy}>{busy ? <Spinner /> : 'Save'}</button>
            <button type="button" className="btn" onClick={() => { setMode(null); setForm(EMPTY); }}>Cancel</button>
          </div>
        </form>
      )}

      {list.length === 0 ? <Empty icon="🗂️" title="No categories yet" /> : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Description</th><th>Status</th><th /></tr>
            </thead>
            <tbody>
              {list.map((c) => (
                <tr key={c.id}>
                  <td className="bold">{c.name}</td>
                  <td className="muted">{c.description || '-'}</td>
                  <td>
                    <span className={`badge ${c.active ? 'badge-green' : 'badge-grey'}`}>
                      {c.active ? 'Active' : 'Hidden'}
                    </span>
                  </td>
                  <td>
                    <div className="row" style={{ gap: 12 }}>
                      <button className="btn-link small" onClick={() => { setForm({ ...c }); setMode(c.id); }}>Edit</button>
                      <button className="btn-link small" onClick={() => toggleActive(c)}>
                        {c.active ? 'Hide' : 'Show'}
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
