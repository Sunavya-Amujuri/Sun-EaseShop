import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../store.jsx';
import { Alert, Spinner } from '../ui.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from || '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="center" style={{ marginBottom: 18 }}>
        <Link to="/" className="row" style={{ justifyContent: 'center', textDecoration: 'none' }}>
          <span className="logo-text" style={{ color: 'var(--ink)' }}>Sun-Ease<span style={{ color: 'var(--accent-strong)' }}>Mart</span></span>
        </Link>
      </div>

      <div className="panel">
        <h1 style={{ marginBottom: 14 }}>Sign in</h1>

        <form className="stack" onSubmit={submit}>
          {error && <Alert>{error}</Alert>}

          <div className="field">
            <label className="label">Email</label>
            <input
              className="input" type="email" value={email}
              onChange={(e) => setEmail(e.target.value)} required autoFocus
            />
          </div>

          <div className="field">
            <label className="label">Password</label>
            <input
              className="input" type="password" value={password}
              onChange={(e) => setPassword(e.target.value)} required
            />
          </div>

          <button className="btn btn-cta btn-block" disabled={busy}>
            {busy ? <Spinner /> : 'Sign in'}
          </button>
        </form>

        <div className="row-between" style={{ marginTop: 12 }}>
          <Link to="/forgot-password" className="small">Forgot your password?</Link>
          <Link to="/verify-email" className="small">Need a verification link?</Link>
        </div>

        <div className="tiny muted" style={{ marginTop: 10 }}>
          New accounts must verify their email before signing in.
        </div>
      </div>

      <div className="divider">New to Sun-EaseMart?</div>
      <Link to="/register" className="btn btn-block">Create your account</Link>
    </div>
  );
}
