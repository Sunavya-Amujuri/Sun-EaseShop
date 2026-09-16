import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Spinner } from '../ui.jsx';

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api.forgotPassword(email.trim());
      // The reply is intentionally the same whether or not the account exists, so we always
      // move on to the code screen rather than revealing anything here.
      navigate('/reset-password', { state: { email: email.trim() } });
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
          <span className="logo-text" style={{ color: 'var(--ink)' }}>
            Telusko<span style={{ color: 'var(--accent-strong)' }}>Mart</span>
          </span>
        </Link>
      </div>

      <div className="panel">
        <h1 style={{ marginBottom: 6 }}>Forgot password</h1>
        <p className="small muted" style={{ marginBottom: 14 }}>
          Enter your account email and we will send you a 6 digit code to reset your password.
        </p>

        <form className="stack" onSubmit={submit}>
          {error && <Alert>{error}</Alert>}
          <div className="field">
            <label className="label">Email</label>
            <input className="input" type="email" value={email}
              onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </div>
          <button className="btn btn-cta btn-block" disabled={busy}>
            {busy ? <Spinner /> : 'Send code'}
          </button>
        </form>

        <div className="center" style={{ marginTop: 14 }}>
          <Link to="/login" className="small">Back to sign in</Link>
        </div>
      </div>
    </div>
  );
}
