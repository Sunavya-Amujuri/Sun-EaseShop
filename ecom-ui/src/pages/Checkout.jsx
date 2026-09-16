import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useCart } from '../store.jsx';
import { Alert, Loading, Money, Spinner, useAsync } from '../ui.jsx';

const EMPTY_ADDRESS = {
  line1: '', line2: '', city: '', state: '', country: 'India', pinCode: '', label: 'HOME', isDefault: false,
};

function AddressForm({ onSaved, onCancel }) {
  const [form, setForm] = useState(EMPTY_ADDRESS);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setSaving(true); setError('');
    try {
      const saved = await api.addAddress(form);
      onSaved(saved);
      setForm(EMPTY_ADDRESS);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit} style={{ marginTop: 12 }}>
      {error && <Alert>{error}</Alert>}
      <div className="field">
        <label className="label">Address line 1</label>
        <input className="input" value={form.line1} onChange={set('line1')} required placeholder="Flat, house no., building" />
      </div>
      <div className="field">
        <label className="label">Address line 2</label>
        <input className="input" value={form.line2} onChange={set('line2')} placeholder="Area, street, landmark (optional)" />
      </div>
      <div className="form-grid">
        <div className="field">
          <label className="label">City</label>
          <input className="input" value={form.city} onChange={set('city')} required />
        </div>
        <div className="field">
          <label className="label">State</label>
          <input className="input" value={form.state} onChange={set('state')} required />
        </div>
        <div className="field">
          <label className="label">PIN code</label>
          <input className="input" value={form.pinCode} onChange={set('pinCode')} required inputMode="numeric" />
        </div>
        <div className="field">
          <label className="label">Country</label>
          <input className="input" value={form.country} onChange={set('country')} required />
        </div>
      </div>
      <div className="field">
        <label className="label">Label</label>
        <select className="select" value={form.label} onChange={set('label')}>
          <option value="HOME">Home</option>
          <option value="WORK">Work</option>
          <option value="OTHER">Other</option>
        </select>
      </div>
      <div className="row">
        <button className="btn btn-cta" disabled={saving}>{saving ? <Spinner /> : 'Save address'}</button>
        {onCancel && <button type="button" className="btn" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

export default function Checkout() {
  const { cart, refresh } = useCart();
  const navigate = useNavigate();

  const { data: addressPage, loading, reload } = useAsync(() => api.addresses(1, 20), []);
  const [addresses, setAddresses] = useState([]);
  const [selected, setSelected] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [method, setMethod] = useState('COD');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const list = addressPage?.content || [];
    setAddresses(list);
    if (list.length && selected == null) {
      setSelected((list.find((a) => a.isDefault) || list[0]).id);
    }
    if (!list.length) setShowForm(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [addressPage]);

  if (loading) return <div className="page"><Loading label="Loading checkout" /></div>;

  const items = cart?.items || [];
  if (items.length === 0) {
    return (
      <div className="page-narrow">
        <div className="panel center">
          <h2>Your cart is empty</h2>
          <p className="muted" style={{ margin: '8px 0 16px' }}>Add something before checking out.</p>
          <Link to="/" className="btn btn-cta">Browse products</Link>
        </div>
      </div>
    );
  }

  /**
   * Loads Razorpay's checkout script on demand.
   * It is not bundled because most orders are cash on delivery and there is no reason to pull a
   * third-party script into every page load.
   */
  function loadRazorpay() {
    if (window.Razorpay) return Promise.resolve(true);
    return new Promise((resolve) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.razorpay.com/v1/checkout.js';
      s.onload = () => resolve(true);
      s.onerror = () => resolve(false);
      document.body.appendChild(s);
    });
  }

  /**
   * Online payment.
   *
   * The order is created first and stays unpaid until the signature is verified server-side -
   * the browser only forwards Razorpay's response, it never decides that a payment succeeded.
   */
  async function payOnline(order) {
    const ok = await loadRazorpay();
    if (!ok) throw new Error('Could not load the payment gateway. Check your connection.');

    const rp = await api.createPaymentOrder(order.id);

    return new Promise((resolve, reject) => {
      const checkout = new window.Razorpay({
        key: rp.keyId,
        order_id: rp.razorpayOrderId,
        amount: rp.amount,
        currency: rp.currency,
        name: 'Sun-EaseMart',
        description: `Order ${order.orderNumber}`,
        handler: async (response) => {
          try {
            await api.verifyPayment({
              orderId: order.id,
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            resolve();
          } catch (err) {
            reject(new Error(`Payment could not be verified: ${err.message}`));
          }
        },
        modal: {
          // Closing the popup is a deliberate cancellation, so tell the server rather than
          // leaving the order stuck as pending.
          ondismiss: async () => {
            try { await api.cancelPayment(order.id); } catch { /* best effort */ }
            reject(new Error('Payment was cancelled.'));
          },
        },
      });

      checkout.on('payment.failed', async (resp) => {
        try { await api.paymentFailed(order.id, resp?.error?.description); } catch { /* best effort */ }
        reject(new Error(resp?.error?.description || 'Payment failed.'));
      });

      checkout.open();
    });
  }

  async function placeOrder() {
    if (!selected) { setError('Please select a delivery address.'); return; }
    setPlacing(true); setError('');
    try {
      // The coupon has to be sent with the order. Applying one only returns a preview - the
      // server does not store it on the cart - so an order placed without couponCode is charged
      // at full price no matter what the cart screen showed.
      //
      // Only the code travels: the server re-reads the cart, re-checks validity and recalculates
      // the discount itself, so no amount is trusted from the browser.
      const order = await api.placeOrder({
        addressId: selected,
        paymentMethod: method,
        couponCode: cart.appliedCouponCode || undefined,
      });

      if (method === 'ONLINE') {
        await payOnline(order);
      }

      await refresh();
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (e) {
      setError(e.message);
    } finally {
      setPlacing(false);
    }
  }

  return (
    <div className="page">
      <h1 style={{ marginBottom: 14 }}>Checkout</h1>

      <div className="cart-layout">
        <div className="stack">
          <section className="panel">
            <div className="row-between">
              <h2>1 · Delivery address</h2>
              {addresses.length > 0 && !showForm && (
                <button className="btn-link small" onClick={() => setShowForm(true)}>+ Add new address</button>
              )}
            </div>

            {addresses.length > 0 && (
              <div className="stack" style={{ marginTop: 12, gap: 10 }}>
                {addresses.map((a) => (
                  <label
                    key={a.id}
                    className="card card-pad row"
                    style={{
                      gap: 12, cursor: 'pointer', alignItems: 'flex-start',
                      borderColor: selected === a.id ? 'var(--accent-strong)' : 'var(--line)',
                      background: selected === a.id ? '#fffaf2' : 'var(--surface)',
                    }}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={selected === a.id}
                      onChange={() => setSelected(a.id)}
                      style={{ marginTop: 4 }}
                    />
                    <div>
                      <div className="row" style={{ gap: 8 }}>
                        <span className="bold">{a.label || 'Address'}</span>
                        {a.isDefault && <span className="badge badge-grey">Default</span>}
                      </div>
                      <div className="small muted" style={{ marginTop: 3 }}>
                        {[a.line1, a.line2, a.city, a.state, a.pinCode, a.country].filter(Boolean).join(', ')}
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            )}

            {showForm && (
              <AddressForm
                onSaved={(saved) => {
                  setShowForm(false);
                  setSelected(saved.id);
                  reload();
                }}
                onCancel={addresses.length ? () => setShowForm(false) : null}
              />
            )}
          </section>

          <section className="panel">
            <h2>2 · Payment method</h2>
            <div className="stack" style={{ marginTop: 12, gap: 10 }}>
              {[
                { id: 'COD', title: 'Cash on Delivery', note: 'Pay in cash when your order arrives.' },
                { id: 'ONLINE', title: 'Pay online (Razorpay)', note: 'Card, UPI, net banking or wallet.' },
              ].map((m) => (
                <label
                  key={m.id}
                  className="card card-pad row"
                  style={{
                    gap: 12, cursor: 'pointer', alignItems: 'flex-start',
                    borderColor: method === m.id ? 'var(--accent-strong)' : 'var(--line)',
                    background: method === m.id ? '#fffaf2' : 'var(--surface)',
                  }}
                >
                  <input type="radio" name="pay" checked={method === m.id} onChange={() => setMethod(m.id)} style={{ marginTop: 4 }} />
                  <div>
                    <div className="bold">{m.title}</div>
                    <div className="small muted">{m.note}</div>
                    {m.id === 'ONLINE' && (
                      <div className="tiny muted" style={{ marginTop: 4 }}>
                        You will be taken to Razorpay's secure checkout after placing the order.
                      </div>
                    )}
                  </div>
                </label>
              ))}
            </div>
          </section>

          <section className="panel">
            <h2>3 · Review items</h2>
            <div style={{ marginTop: 10 }}>
              {items.map((i) => (
                <div className="row-between" key={i.productId} style={{ padding: '8px 0', borderBottom: '1px solid var(--line)' }}>
                  <span className="small">{i.productName} <span className="muted">× {i.quantity}</span></span>
                  <Money value={i.lineTotal} />
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="panel summary">
          <h2 style={{ marginBottom: 10 }}>Order Summary</h2>
          <div className="summary-row">
            <span className="muted">Items</span><Money value={cart.totalAmount} />
          </div>
          {Number(cart.discountAmount) > 0 && (
            <div className="summary-row">
              <span className="muted">Discount</span>
              <span style={{ color: 'var(--success)', fontWeight: 600 }}>− <Money value={cart.discountAmount} /></span>
            </div>
          )}
          <div className="summary-row">
            <span className="muted">Delivery</span>
            <span style={{ color: 'var(--success)', fontWeight: 600 }}>FREE</span>
          </div>
          <div className="summary-row summary-total">
            <span>Order total</span><Money value={cart.payableAmount ?? cart.totalAmount} />
          </div>

          {error && <div style={{ marginTop: 12 }}><Alert>{error}</Alert></div>}

          <button
            className="btn btn-cta btn-block btn-lg"
            style={{ marginTop: 14 }}
            onClick={placeOrder}
            disabled={placing || !selected}
          >
            {placing ? <Spinner /> : 'Place your order'}
          </button>
          <p className="tiny muted center" style={{ marginTop: 10 }}>
            By placing your order you agree to our terms and return policy.
          </p>
        </aside>
      </div>
    </div>
  );
}
