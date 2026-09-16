import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Loading, Money, Spinner, formatDate, prettyStatus, statusTone, useAsync } from '../ui.jsx';

const FLOW = ['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'];

function Progress({ status }) {
  // Cancelled and return flows leave the happy path, so the tracker is not meaningful there.
  if (!FLOW.includes(status)) return null;
  const reached = FLOW.indexOf(status);

  return (
    <div style={{ padding: '4px 2px 0' }}>
      <div className="steps">
        {FLOW.map((s, i) => (
          <div key={s} className={`step ${i <= reached ? 'done' : ''}`}>
            <span className="step-dot" />
            {i < FLOW.length - 1 && <span className="step-line" />}
          </div>
        ))}
      </div>
      <div className="step-labels">
        {FLOW.map((s) => <span key={s}>{prettyStatus(s)}</span>)}
      </div>
    </div>
  );
}

/**
 * Return check.
 *
 * Eligibility is decided by the backend from the order's real status and date; the assistant
 * only writes the explanation. The badge therefore reflects policy, not a model's opinion.
 */
function ReturnCheck({ orderId }) {
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function check() {
    setLoading(true); setError(''); setResult(null);
    try {
      setResult(await api.returnEligibility(orderId));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="section-head">
        <h2>Returns &amp; refunds</h2>
        <span className="badge badge-ai">✨ AI explained</span>
      </div>

      {!result && (
        <button className="btn" onClick={check} disabled={loading}>
          {loading ? <Spinner /> : 'Can I return this order?'}
        </button>
      )}

      {error && <Alert>{error}</Alert>}

      {result && (
        <div className="stack" style={{ gap: 10 }}>
          <div className="row" style={{ gap: 10 }}>
            <span className={`badge ${result.eligible ? 'badge-green' : 'badge-red'}`}>
              {result.eligible ? 'Eligible for return' : 'Not eligible'}
            </span>
            <span className="small muted">{result.reason}</span>
          </div>
          <div className="ai-note">{result.explanation}</div>
          {result.eligible && (
            <Link to="/support" className="btn btn-cta" style={{ alignSelf: 'flex-start' }}>
              Raise a return request
            </Link>
          )}
        </div>
      )}
    </div>
  );
}

export default function OrderDetail() {
  const { orderId } = useParams();
  const location = useLocation();
  const justPlaced = location.state?.justPlaced;

  const { data: order, loading, error } = useAsync(() => api.myOrder(orderId), [orderId]);

  if (loading) return <div className="page"><Loading label="Loading order" /></div>;
  if (error) return <div className="page"><Alert>{error}</Alert></div>;
  if (!order) return null;

  return (
    <div className="page-narrow">
      {justPlaced && (
        <div style={{ marginBottom: 14 }}>
          <Alert kind="success">
            Order placed successfully. A confirmation has been sent to your email.
          </Alert>
        </div>
      )}

      <div className="row-between" style={{ marginBottom: 14 }}>
        <div>
          <h1>Order {order.orderNumber}</h1>
          <p className="small muted" style={{ marginTop: 4 }}>Placed on {formatDate(order.placedAt)}</p>
        </div>
        <Link to="/orders" className="btn btn-sm">All orders</Link>
      </div>

      <div className="stack">
        <section className="panel">
          <div className="row-between" style={{ marginBottom: 12 }}>
            <h2>Status</h2>
            <span className={`badge ${statusTone(order.status)}`}>{prettyStatus(order.status)}</span>
          </div>
          <Progress status={order.status} />
        </section>

        <section className="panel">
          <h2 style={{ marginBottom: 10 }}>Items in this order</h2>
          {(order.items || []).map((item, i) => (
            <div className="row-between" key={i} style={{ padding: '11px 0', borderBottom: '1px solid var(--line)' }}>
              <div>
                <Link to={`/product/${item.productId}`} className="bold" style={{ color: 'var(--ink)' }}>
                  {item.productName}
                </Link>
                <div className="small muted" style={{ marginTop: 2 }}>
                  Quantity {item.quantity} · <Money value={item.unitPrice} /> each
                </div>
              </div>
              <Money value={item.lineTotal} className="price-md" />
            </div>
          ))}

          <div className="row-between" style={{ paddingTop: 14 }}>
            <span className="bold" style={{ fontSize: 16 }}>Order total</span>
            <Money value={order.totalAmount} className="price-md" />
          </div>
        </section>

        <ReturnCheck orderId={order.id} />
      </div>
    </div>
  );
}
