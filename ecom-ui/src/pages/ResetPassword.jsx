import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import OtpInput from '../components/OtpInput.jsx';
import { Alert, Spinner } from '../ui.jsx';

const RESEND_SECONDS = 60;

/**
 * Sets a new password using the six digit code from the reset email.
 *
 * The email is carried over from the forgot-password step, so in the normal flow the customer
 * only types the code and their new password.
 */
export default function ResetPassword() {
  const location = useLocation();
  const navigate = useNavigate();

  const [email, setEmail] = useState(location.state?.email || '');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState(location.state?.email ? 'We emailed a 6 digit code to you.' : '');
  const [done, setDone] = useState(false);
  const [cooldown, setCooldown] = useState(location.state?.email ? RESEND_SECONDS : 0);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  async function submit(e) {
    e.preventDefault();
    if (password !== confirm) {
      setError('The two passwords do not match.');
      return;
    }
    setBusy(true); setError(''); setInfo('');
    try {
      await api.resetPassword(email.trim(), otp, password);
      setDone(true);
      setTimeout(() => navigate('/login', { state: { email: email.trim() } }), 2000);
    } catch (err) {
      setError(err.message);
      setOtp('');
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    if (!email.trim()) { setError('Enter your email first.'); return; }
    setBusy(true); setError(''); setInfo('');
    try {
      const res = await api.forgotPassword(email.trim());
      setInfo(res.message || 'A new code has been sent.');
      setCooldown(RESEND_SECONDS);
      setOtp('');
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
        {done ? (
          <div className="center">
            <div style={{ fontSize: 46, marginBottom: 10 }}>✅</div>
            <h1 style={{ marginBottom: 8 }}>Password updated</h1>
            <p className="muted" style={{ marginBottom: 16 }}>Taking you to sign in…</p>
            <Link to="/login" className="btn btn-cta btn-block">Sign in now</Link>
          </div>
        ) : (
          <>
            <h1 style={{ marginBottom: 6 }}>Set a new password</h1>
            <p className="small muted" style={{ marginBottom: 16 }}>
              Enter the 6 digit code we emailed you, then choose a new password.
            </p>

            <form className="stack" onSubmit={submit}>
              {error && <Alert>{error}</Alert>}
              {info && <Alert kind="info">{info}</Alert>}

              <div className="field">
                <label className="label">Email</label>
                <input className="input" type="email" value={email}
                  onChange={(e) => setEmail(e.target.value)} required placeholder="you@example.com" />
              </div>

              <div className="field">
                <label className="label center" style={{ marginBottom: 4 }}>Reset code</label>
                <OtpInput value={otp} onChange={setOtp} disabled={busy} autoFocus={!!email} />
              </div>

              <div className="field">
                <label className="label">New password</label>
                <input className="input" type="password" value={password} minLength={6}
                  onChange={(e) => setPassword(e.target.value)} required />
                <span className="tiny muted">At least 6 characters.</span>
              </div>

              <div className="field">
                <label className="label">Confirm new password</label>
                <input className="input" type="password" value={confirm} minLength={6}
                  onChange={(e) => setConfirm(e.target.value)} required />
              </div>

              <button className="btn btn-cta btn-block" disabled={busy || otp.length !== 6}>
                {busy ? <Spinner /> : 'Update password'}
              </button>
            </form>

            <div className="center" style={{ marginTop: 14 }}>
              {cooldown > 0 ? (
                <span className="small muted">Resend code in {cooldown}s</span>
              ) : (
                <button className="btn-link small" onClick={resend} disabled={busy}>
                  Didn’t get it? Send a new code
                </button>
              )}
            </div>

            <div className="center" style={{ marginTop: 10 }}>
              <Link to="/login" className="small">Back to sign in</Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
