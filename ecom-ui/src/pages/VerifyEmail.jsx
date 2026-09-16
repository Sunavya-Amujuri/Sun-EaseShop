import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import OtpInput from '../components/OtpInput.jsx';
import { Alert, Spinner } from '../ui.jsx';

const RESEND_SECONDS = 60;

/**
 * Confirms an email address with the six digit code that was mailed out.
 *
 * The email address is carried over from registration so the customer usually only has to type
 * the code. It stays editable for anyone who lands here directly from the sign-in page.
 */
export default function VerifyEmail() {
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();

  const [email, setEmail] = useState(location.state?.email || params.get('email') || '');
  const [otp, setOtp] = useState('');
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
    e?.preventDefault();
    if (otp.length !== 6 || busy) return;
    setBusy(true); setError(''); setInfo('');
    try {
      await api.verifyEmail(email.trim(), otp);
      setDone(true);
      setTimeout(() => navigate('/login', { state: { email: email.trim() } }), 2000);
    } catch (err) {
      setError(err.message);
      setOtp('');
    } finally {
      setBusy(false);
    }
  }

  // Submit as soon as the sixth digit lands - nobody wants to reach for a button after typing
  // a code they just read off their screen.
  useEffect(() => {
    if (otp.length === 6 && email.trim() && !busy && !done) submit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [otp]);

  async function resend() {
    if (!email.trim()) { setError('Enter your email first.'); return; }
    setBusy(true); setError(''); setInfo('');
    try {
      const res = await api.resendVerification(email.trim());
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
            <h1 style={{ marginBottom: 8 }}>Email verified</h1>
            <p className="muted" style={{ marginBottom: 16 }}>
              Your account is active. Taking you to sign in…
            </p>
            <Link to="/login" className="btn btn-cta btn-block">Sign in now</Link>
          </div>
        ) : (
          <>
            <h1 style={{ marginBottom: 6 }}>Verify your email</h1>
            <p className="small muted" style={{ marginBottom: 16 }}>
              Enter the 6 digit code we sent to your inbox. It is valid for 10 minutes.
            </p>

            <form className="stack" onSubmit={submit}>
              {error && <Alert>{error}</Alert>}
              {info && <Alert kind="info">{info}</Alert>}

              <div className="field">
                <label className="label">Email</label>
                <input
                  className="input" type="email" value={email}
                  onChange={(e) => setEmail(e.target.value)} required
                  placeholder="you@example.com"
                />
              </div>

              <div className="field">
                <label className="label center" style={{ marginBottom: 4 }}>Verification code</label>
                <OtpInput value={otp} onChange={setOtp} disabled={busy} autoFocus={!!email} />
              </div>

              <button className="btn btn-cta btn-block" disabled={busy || otp.length !== 6}>
                {busy ? <Spinner /> : 'Verify'}
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
