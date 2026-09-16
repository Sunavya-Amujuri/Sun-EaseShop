import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, Empty, Loading, Money, formatDate, prettyStatus, statusTone, useAsync } from '../ui.jsx';

export default function Orders() {
  const { data, loading, error } = useAsync(() => api.myOrders(1, 30), []);
  const orders = data?.content || [];

  if (loading) return <div className="page"><Loading label="Loading your orders" /></div>;

  return (
    <div className="page-narrow">
      <h1 style={{ marginBottom: 14 }}>Your Orders</h1>

      {error && <Alert>{error}</Alert>}

      {!error && orders.length === 0 && (
        <Empty
          icon="📦"
          title="No orders yet"
          action={<Link to="/" className="btn btn-cta btn-lg">Start shopping</Link>}
        >
          When you place an order it will appear here.
        </Empty>
      )}

      <div className="stack">
        {orders.map((o) => (
          <div className="order-card" key={o.id}>
            <div className="order-head">
              <div className="order-head-cell">
                <div className="k">Order placed</div>
                <div className="v">{formatDate(o.placedAt)}</div>
              </div>
              <div className="order-head-cell">
                <div className="k">Total</div>
                <div className="v"><Money value={o.totalAmount} /></div>
              </div>
              <div className="order-head-cell">
                <div className="k">Items</div>
                <div className="v">{o.itemsCount}</div>
              </div>
              <div className="order-head-cell right">
                <div className="k">Order #</div>
                <div className="v">{o.orderNumber}</div>
              </div>
            </div>

            <div className="row-between" style={{ padding: '14px 18px' }}>
              <span className={`badge ${statusTone(o.status)}`}>{prettyStatus(o.status)}</span>
              <Link to={`/orders/${o.id}`} className="btn btn-sm">View order details</Link>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
