import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Empty, Loading, Money, formatDate, useAsync } from '../ui.jsx';

export default function Deals() {
  const { data, loading, error } = useAsync(() => api.availableCoupons(1, 30), []);
  const coupons = data?.content || [];

  if (loading) return <div className="page"><Loading label="Loading coupons" /></div>;

  return (
    <div className="page-narrow">
      <h1 style={{ marginBottom: 4 }}>Coupons for you</h1>
      <p className="muted small" style={{ marginBottom: 16 }}>
        Apply any of these at checkout from your cart.
      </p>

      {error && <Alert>{error}</Alert>}

      {!error && coupons.length === 0 && (
        <Empty icon="🎟️" title="No coupons available" action={<Link to="/" className="btn btn-cta">Continue shopping</Link>}>
          There are no active coupons for your account right now.
        </Empty>
      )}

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))' }}>
        {coupons.map((c) => (
          <div className="card card-pad" key={c.id}>
            <div className="row-between" style={{ marginBottom: 8 }}>
              <span className="badge badge-amber" style={{ fontSize: 13, padding: '5px 11px' }}>{c.code}</span>
              <span className={`badge ${c.active ? 'badge-green' : 'badge-grey'}`}>
                {c.active ? 'Active' : 'Inactive'}
              </span>
            </div>

            <div className="bold" style={{ fontSize: 16 }}>
              {c.discountType === 'PERCENTAGE'
                ? `${c.discountAmount}% off`
                : <>Flat <Money value={c.discountAmount} /> off</>}
            </div>

            {Number(c.minimumOrderAmount) > 0 && (
              <div className="small muted" style={{ marginTop: 4 }}>
                On orders above <Money value={c.minimumOrderAmount} />
              </div>
            )}

            <div className="tiny faint" style={{ marginTop: 10 }}>
              Valid {formatDate(c.startDate)} to {formatDate(c.expiryDate)}
            </div>

            <Link to="/cart" className="btn btn-sm btn-block" style={{ marginTop: 12 }}>Use in cart</Link>
          </div>
        ))}
      </div>
    </div>
  );
}
