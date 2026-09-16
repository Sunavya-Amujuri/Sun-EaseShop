import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../store.jsx';
import { Alert, Loading, Spinner, formatDate } from '../ui.jsx';

function ChangePassword() {
  const [form, setForm] = useState({ oldPassword: '', newPassword: '', confirm: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    if (form.newPassword !== form.confirm) {
      setError('The two new passwords do not match.');
      return;
    }
    setBusy(true); setError(''); setDone('');
    try {
      const res = await api.changePassword({
        oldPassword: form.oldPassword,
        newPassword: form.newPassword,
      });
      setDone(res.message || 'Password updated.');
      setForm({ oldPassword: '', newPassword: '', confirm: '' });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <h2 style={{ marginBottom: 12 }}>Change password</h2>
      <form className="stack" onSubmit={submit} style={{ maxWidth: 420 }}>
        {error && <Alert>{error}</Alert>}
        {done && <Alert kind="success">{done}</Alert>}

        <div className="field">
          <label className="label">Current password</label>
          <input className="input" type="password" value={form.oldPassword} onChange={set('oldPassword')} required />
        </div>
        <div className="field">
          <label className="label">New password</label>
          <input className="input" type="password" value={form.newPassword} onChange={set('newPassword')} required minLength={6} />
          <span className="tiny muted">At least 6 characters.</span>
        </div>
        <div className="field">
          <label className="label">Confirm new password</label>
          <input className="input" type="password" value={form.confirm} onChange={set('confirm')} required minLength={6} />
        </div>

        <button className="btn btn-cta" style={{ alignSelf: 'flex-start' }} disabled={busy}>
          {busy ? <Spinner /> : 'Update password'}
        </button>
      </form>
    </section>
  );
}

const LINKS = [
  { to: '/orders', icon: '📦', title: 'Your Orders', note: 'Track, return or buy things again' },
  { to: '/addresses', icon: '📍', title: 'Your Addresses', note: 'Edit or add delivery addresses' },
  { to: '/deals', icon: '🎟️', title: 'Coupons', note: 'See what you can apply at checkout' },
  { to: '/support', icon: '🎧', title: 'Customer Support', note: 'Raise and track support tickets' },
];

export default function Account() {
  const { user, ready } = useAuth();

  if (!ready) return <div className="page"><Loading label="Loading your account" /></div>;

  return (
    <div className="page-narrow">
      <h1 style={{ marginBottom: 14 }}>Your Account</h1>

      <div className="stack">
        <section className="panel">
          <h2 style={{ marginBottom: 12 }}>Profile</h2>
          <div className="spec-row"><span className="spec-key">Name</span><span>{user?.fullName}</span></div>
          <div className="spec-row"><span className="spec-key">Email</span><span>{user?.email}</span></div>
          <div className="spec-row"><span className="spec-key">Mobile</span><span>{user?.phoneNumber || '-'}</span></div>
          <div className="spec-row">
            <span className="spec-key">Email status</span>
            <span>
              {user?.emailVerified
                ? <span className="badge badge-green">Verified</span>
                : <span className="badge badge-amber">Not verified</span>}
            </span>
          </div>
          <div className="spec-row"><span className="spec-key">Role</span><span>{user?.role}</span></div>
          <div className="spec-row"><span className="spec-key">Member since</span><span>{formatDate(user?.createdDate)}</span></div>
        </section>

        <section>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }}>
            {LINKS.map((l) => (
              <Link key={l.to} to={l.to} className="card card-pad row" style={{ gap: 12, textDecoration: 'none', color: 'inherit' }}>
                <span style={{ fontSize: 26 }}>{l.icon}</span>
                <span>
                  <span className="bold" style={{ display: 'block' }}>{l.title}</span>
                  <span className="small muted">{l.note}</span>
                </span>
              </Link>
            ))}
          </div>
        </section>

        <ChangePassword />
      </div>
    </div>
  );
}
