import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useCategories } from '../../store.jsx';
import { Alert, Spinner } from '../../ui.jsx';

const EMPTY = { name: '', brand: '', category: '', price: '', stock: '', description: '' };

/** Turns the base64 the generate-image endpoint returns into a File the upload can accept. */
function base64ToFile(base64, filename) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new File([bytes], filename, { type: 'image/png' });
}

/**
 * Create-product form.
 *
 * The endpoint requires an image, and sourcing product photography is the slowest part of adding
 * a catalog item. So the description and the photo can both be generated from just a name, brand
 * and category - the admin reviews and edits rather than starting from nothing.
 */
export default function AddProductForm({ onCreated, onCancel }) {
  // Shared list, so a category created moments ago on the Categories tab is already selectable.
  const { categories } = useCategories();

  const [form, setForm] = useState(EMPTY);
  const [imageFile, setImageFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const categoryName = categories.find((c) => String(c.id) === String(form.category))?.name || '';

  // Revoke the object URL when it is replaced, otherwise each generated image leaks a blob.
  useEffect(() => () => { if (preview.startsWith('blob:')) URL.revokeObjectURL(preview); }, [preview]);

  function pickFile(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    setImageFile(file);
    setPreview(URL.createObjectURL(file));
  }

  async function generateDescription() {
    if (!form.name.trim() || !form.category) {
      setError('Enter a product name and pick a category first.');
      return;
    }
    setBusy('description'); setError('');
    try {
      const res = await api.adminGenerateDescription(form.name, categoryName, form.brand || form.name);
      setForm((f) => ({ ...f, description: res.description }));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy('');
    }
  }

  async function generateImage() {
    if (!form.name.trim() || !form.category) {
      setError('Enter a product name and pick a category first.');
      return;
    }
    setBusy('image'); setError('');
    try {
      const res = await api.adminGenerateImage(
        form.name, categoryName, form.description || form.name, form.brand || form.name,
      );
      const file = base64ToFile(res.image, `${form.name.replace(/\s+/g, '-').toLowerCase()}.png`);
      setImageFile(file);
      setPreview(URL.createObjectURL(file));
    } catch (e) {
      setError(`Image generation failed: ${e.message}`);
    } finally {
      setBusy('');
    }
  }

  async function submit(e) {
    e.preventDefault();
    if (!imageFile) {
      setError('A main image is required. Upload one or generate it.');
      return;
    }
    setBusy('save'); setError('');
    try {
      const created = await api.adminCreateProduct(
        {
          name: form.name,
          description: form.description,
          price: form.price,
          stock: form.stock,
          category: form.category,
        },
        imageFile,
      );
      setForm(EMPTY);
      setImageFile(null);
      setPreview('');
      onCreated?.(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy('');
    }
  }

  return (
    <div className="panel" style={{ marginBottom: 16 }}>
      <div className="section-head">
        <h2>Add a product</h2>
        <span className="badge badge-ai">✨ AI can write the copy and the photo</span>
      </div>

      {error && <div style={{ marginBottom: 12 }}><Alert>{error}</Alert></div>}

      <form className="stack" onSubmit={submit}>
        <div className="form-grid">
          <div className="field">
            <label className="label">Product name</label>
            <input className="input" value={form.name} onChange={set('name')} required placeholder="Trailblaze Running Shoes" />
          </div>
          <div className="field">
            <label className="label">Brand</label>
            <input className="input" value={form.brand} onChange={set('brand')} placeholder="Used for AI copy and the logo on generated images" />
          </div>
          <div className="field">
            <label className="label">Category</label>
            <select className="select" value={form.category} onChange={set('category')} required>
              <option value="">Select a category…</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="field">
            <label className="label">Price (₹)</label>
            <input className="input" type="number" min="0" step="0.01" value={form.price} onChange={set('price')} required />
          </div>
          <div className="field">
            <label className="label">Stock quantity</label>
            <input className="input" type="number" min="0" value={form.stock} onChange={set('stock')} required />
          </div>
        </div>

        <div className="field">
          <div className="row-between">
            <label className="label">Description</label>
            <button type="button" className="btn-link small" onClick={generateDescription} disabled={!!busy}>
              {busy === 'description' ? <Spinner /> : '✨ Generate with AI'}
            </button>
          </div>
          <textarea className="textarea" value={form.description} onChange={set('description')}
            placeholder="Describe the product, or let AI draft it from the name and brand." />
        </div>

        <div className="field">
          <div className="row-between">
            <label className="label">Main image (required)</label>
            <button type="button" className="btn-link small" onClick={generateImage} disabled={!!busy}>
              {busy === 'image' ? <Spinner /> : '✨ Generate product photo'}
            </button>
          </div>

          <div className="row wrap" style={{ gap: 14, alignItems: 'flex-start' }}>
            <div
              className="product-thumb"
              style={{ width: 140, height: 140, flexShrink: 0, border: '1px solid var(--line)' }}
            >
              {preview
                ? <img src={preview} alt="Product preview" style={{ objectFit: 'contain' }} />
                : <span className="tiny faint center">No image yet</span>}
            </div>

            <div className="stack" style={{ gap: 6 }}>
              <input type="file" accept="image/*" onChange={pickFile} />
              <span className="tiny muted">
                Upload your own, or generate one. Generating takes around 15 seconds and uses your
                OpenAI quota.
              </span>
              {busy === 'image' && <span className="tiny muted">Drawing the product photo…</span>}
            </div>
          </div>
        </div>

        <div className="row">
          <button className="btn btn-cta" disabled={!!busy}>
            {busy === 'save' ? <Spinner /> : 'Create product'}
          </button>
          <button type="button" className="btn" onClick={onCancel} disabled={!!busy}>Cancel</button>
        </div>
      </form>
    </div>
  );
}
