import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Spinner } from '../ui.jsx';

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phoneNumber: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api.register(form);
      // Straight to the code screen with the email carried over, so the customer never has to
      // retype it or go hunting for a link.
      navigate('/verify-email', { state: { email: form.email } });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="center" style={{ marginBottom: 18 }}>
        <Link to="/" className="row" style={{ justifyContent: 'center', gap: 9, textDecoration: 'none' }}>
          <span className="logo-mark">T</span>
          <span className="logo-text" style={{ color: 'var(--ink)' }}>Telusko<span style={{ color: 'var(--accent-strong)' }}>Mart</span></span>
        </Link>
      </div>

      <div className="panel">
        <h1 style={{ marginBottom: 14 }}>Create account</h1>

        <form className="stack" onSubmit={submit}>
          {error && <Alert>{error}</Alert>}

          <div className="field">
            <label className="label">Your name</label>
            <input className="input" value={form.fullName} onChange={set('fullName')} required autoFocus />
          </div>

          <div className="field">
            <label className="label">Email</label>
            <input className="input" type="email" value={form.email} onChange={set('email')} required />
          </div>

          <div className="field">
            <label className="label">Mobile number</label>
            <input className="input" value={form.phoneNumber} onChange={set('phoneNumber')} required inputMode="numeric" />
          </div>

          <div className="field">
            <label className="label">Password</label>
            <input className="input" type="password" value={form.password} onChange={set('password')} required minLength={6} />
            <span className="tiny muted">At least 6 characters.</span>
          </div>

          <button className="btn btn-cta btn-block" disabled={busy}>
            {busy ? <Spinner /> : 'Create account'}
          </button>
        </form>
      </div>

      <div className="divider">Already have an account?</div>
      <Link to="/login" className="btn btn-block">Sign in instead</Link>
    </div>
  );
}
