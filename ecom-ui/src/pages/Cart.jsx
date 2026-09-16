import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import ProductCard from '../components/ProductCard.jsx';
import { useCart } from '../store.jsx';
import { Alert, Empty, Loading, Money, ProductSkeletons, Spinner, Thumb } from '../ui.jsx';

/**
 * "Frequently bought together" - items that pair with the cart, minus what is already in it.
 *
 * These come from a model call, so the section carries its own loading state. On the first load it
 * reserves the space with card skeletons instead of popping in and pushing the page down. When the
 * cart changes, the picks already on screen stay put and dim while the new set is fetched, so the
 * shopper never watches a block disappear and come back.
 */
function CartSuggestions({ signature }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    setLoading(true);
    api.cartSuggestions(4)
      .then((d) => { if (live) setData(d); })
      .catch(() => { /* suggestions are a bonus, a failure just leaves the section out */ })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [signature]);

  const products = data?.products || [];
  const firstLoad = loading && products.length === 0;

  // Nothing to show and nothing on the way, so the section stays out of the page completely.
  if (!loading && products.length === 0) return null;

  return (
    <section style={{ marginTop: 26 }} aria-busy={loading}>
      <div className="section-head">
        <h2>Goes well with your cart</h2>
        <span className="badge badge-ai">
          {loading
            ? <>Picking for you <span className="ai-dots" aria-hidden="true"><i /><i /><i /></span></>
            : <>✨ AI picked</>}
        </span>
      </div>

      {loading && <div className="suggest-bar" aria-hidden="true"><i /></div>}

      {firstLoad ? (
        <>
          <div className="skeleton" style={{ height: 42, marginBottom: 12 }} />
          <ProductSkeletons count={4} />
        </>
      ) : (
        <div className={loading ? 'is-refreshing' : 'fade-up'}>
          {data.message && <div className="ai-note" style={{ marginBottom: 12 }}>{data.message}</div>}
          <div className="grid">
            {products.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </div>
      )}
    </section>
  );
}

function CouponBox() {
  const { cart, applyCoupon, removeCoupon } = useCart();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function apply(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try { await applyCoupon(code.trim()); setCode(''); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function remove() {
    setBusy(true); setError('');
    try { await removeCoupon(); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  if (cart?.appliedCouponCode) {
    return (
      <div style={{ marginTop: 12 }}>
        <div className="row-between" style={{ padding: '9px 12px', background: 'var(--success-soft)', border: '1px solid #b8e0d5', borderRadius: 'var(--radius-sm)' }}>
          <span className="small bold" style={{ color: 'var(--success)' }}>🎟️ {cart.appliedCouponCode} applied</span>
          <button className="btn-link small" onClick={remove} disabled={busy}>Remove</button>
        </div>
        {error && <div style={{ marginTop: 8 }}><Alert>{error}</Alert></div>}
      </div>
    );
  }

  return (
    <div style={{ marginTop: 12 }}>
      <form className="row" onSubmit={apply} style={{ gap: 8 }}>
        <input
          className="input"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="Coupon code"
        />
        <button className="btn btn-sm" disabled={busy || !code.trim()}>{busy ? <Spinner /> : 'Apply'}</button>
      </form>
      <div className="tiny muted" style={{ marginTop: 6 }}>
        <Link to="/deals">See available coupons</Link>
      </div>
      {error && <div style={{ marginTop: 8 }}><Alert>{error}</Alert></div>}
    </div>
  );
}

export default function Cart() {
  const { cart, setItem, removeItem, clear, busy } = useCart();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  if (!cart) return <div className="page"><Loading label="Loading your cart" /></div>;

  const items = cart.items || [];

  if (items.length === 0) {
    return (
      <div className="page">
        <Empty
          icon="🛒"
          title="Your cart is empty"
          action={<Link to="/" className="btn btn-cta btn-lg">Start shopping</Link>}
        >
          Add items from the catalog and they will show up here.
        </Empty>
      </div>
    );
  }

  async function change(productId, quantity) {
    setError('');
    try {
      if (quantity <= 0) await removeItem(productId);
      else await setItem(productId, quantity);
    } catch (e) { setError(e.message); }
  }

  // Changes whenever the set of products changes, which is when suggestions should be refetched.
  const signature = items.map((i) => `${i.productId}x${i.quantity}`).join(',');

  return (
    <div className="page">
      <h1 style={{ marginBottom: 14 }}>Shopping Cart</h1>
      {error && <div style={{ marginBottom: 12 }}><Alert>{error}</Alert></div>}

      <div className="cart-layout">
        <div className="panel">
          {items.map((item) => (
            <div className="cart-line" key={item.productId}>
              <Link to={`/product/${item.productId}`} className="cart-thumb">
                <Thumb src={item.mainImage} name={item.productName} size="sm" />
              </Link>

              <div>
                <Link to={`/product/${item.productId}`} className="bold" style={{ color: 'var(--ink)', fontSize: 15 }}>
                  {item.productName}
                </Link>
                <div className="small" style={{ color: 'var(--success)', margin: '4px 0 10px' }}>In stock</div>

                <div className="row wrap" style={{ gap: 14 }}>
                  <div className="qty">
                    <button onClick={() => change(item.productId, item.quantity - 1)} disabled={busy}>−</button>
                    <span>{item.quantity}</span>
                    <button onClick={() => change(item.productId, item.quantity + 1)} disabled={busy}>+</button>
                  </div>
                  <button className="btn-link small" onClick={() => removeItem(item.productId)} disabled={busy}>
                    Delete
                  </button>
                </div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <Money value={item.lineTotal} className="price-md" />
                <div className="tiny muted" style={{ marginTop: 3 }}>
                  <Money value={item.unitPrice} /> each
                </div>
              </div>
            </div>
          ))}

          <div className="row-between" style={{ paddingTop: 14, borderTop: '1px solid var(--line)' }}>
            <button className="btn-link small" onClick={clear} disabled={busy}>Clear cart</button>
            <Link to="/" className="small">Continue shopping</Link>
          </div>
        </div>

        <aside className="panel summary">
          <h2 style={{ marginBottom: 10 }}>Order Summary</h2>

          <div className="summary-row">
            <span className="muted">Items ({items.reduce((n, i) => n + i.quantity, 0)})</span>
            <Money value={cart.totalAmount} />
          </div>
          {Number(cart.discountAmount) > 0 && (
            <div className="summary-row">
              <span className="muted">Coupon discount</span>
              <span style={{ color: 'var(--success)', fontWeight: 600 }}>− <Money value={cart.discountAmount} /></span>
            </div>
          )}
          <div className="summary-row">
            <span className="muted">Delivery</span>
            <span style={{ color: 'var(--success)', fontWeight: 600 }}>FREE</span>
          </div>

          <div className="summary-row summary-total">
            <span>Order total</span>
            <Money value={cart.payableAmount ?? cart.totalAmount} />
          </div>

          <CouponBox />

          <button
            className="btn btn-cta btn-block btn-lg"
            style={{ marginTop: 14 }}
            onClick={() => navigate('/checkout')}
            disabled={busy}
          >
            Proceed to Checkout
          </button>
        </aside>
      </div>

      <CartSuggestions signature={signature} />
    </div>
  );
}
