import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth, useCart } from '../store.jsx';
import { Alert, Loading, Money, Spinner, Thumb, useAsync } from '../ui.jsx';

export default function ProductDetail() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { cart, setItem } = useCart();

  const { data: product, loading, error } = useAsync(() => api.product(productId), [productId]);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState('');
  const [added, setAdded] = useState(false);

  if (loading) return <div className="page"><Loading label="Loading product" /></div>;
  if (error) return <div className="page"><Alert>{error}</Alert></div>;
  if (!product) return null;

  const inCart = cart?.items?.find((i) => i.productId === product.id);
  const stock = product.stockQty || 0;
  const outOfStock = stock <= 0;

  async function addToCart(thenGoToCart) {
    setAdding(true);
    setAddError('');
    try {
      await setItem(product.id, qty);
      setAdded(true);
      if (thenGoToCart) navigate('/cart');
    } catch (e) {
      setAddError(e.message);
    } finally {
      setAdding(false);
    }
  }

  return (
    <div className="page">
      <div className="small muted" style={{ marginBottom: 12 }}>
        <Link to="/">Home</Link>
        {product.categoryName && <> {' › '} <Link to={`/category/${product.categoryId}`}>{product.categoryName}</Link></>}
        {' › '} <span>{product.name}</span>
      </div>

      <div className="pdp">
        <div className="pdp-gallery">
          <div className="pdp-image">
            <Thumb src={product.mainImage} name={product.name} size="lg" />
          </div>
        </div>

        <div>
          <h1 style={{ marginBottom: 8 }}>{product.name}</h1>
          {product.categoryName && (
            <Link to={`/category/${product.categoryId}`} className="small">in {product.categoryName}</Link>
          )}

          <div style={{ margin: '14px 0', paddingTop: 14, borderTop: '1px solid var(--line)' }}>
            <Money value={product.price} className="price-lg" />
            <div className="small muted" style={{ marginTop: 4 }}>Inclusive of all taxes</div>
          </div>

          {product.description && (
            <>
              <h3 style={{ marginBottom: 6 }}>About this item</h3>
              <p style={{ fontSize: 14, color: 'var(--muted)', lineHeight: 1.6 }}>{product.description}</p>
            </>
          )}

          <div style={{ marginTop: 18 }}>
            <h3 style={{ marginBottom: 6 }}>Details</h3>
            <div className="spec-row"><span className="spec-key">SKU</span><span>{product.sku || '-'}</span></div>
            <div className="spec-row"><span className="spec-key">Category</span><span>{product.categoryName || 'Uncategorised'}</span></div>
            <div className="spec-row"><span className="spec-key">Availability</span><span>{outOfStock ? 'Out of stock' : `${stock} in stock`}</span></div>
          </div>
        </div>

        <aside className="pdp-buybox">
          <Money value={product.price} className="price-lg" />

          <div style={{ margin: '12px 0' }}>
            {outOfStock
              ? <span className="badge badge-red">Currently unavailable</span>
              : stock <= 5
                ? <span className="badge badge-amber">Only {stock} left in stock</span>
                : <span className="badge badge-green">In stock</span>}
          </div>

          {!user ? (
            <Link to="/login" className="btn btn-cta btn-block">Sign in to buy</Link>
          ) : outOfStock ? (
            <button className="btn btn-block" disabled>Out of stock</button>
          ) : (
            <div className="stack" style={{ gap: 10 }}>
              <div className="row-between">
                <span className="small bold">Quantity</span>
                <div className="qty">
                  <button onClick={() => setQty((q) => Math.max(1, q - 1))} disabled={qty <= 1}>−</button>
                  <span>{qty}</span>
                  <button onClick={() => setQty((q) => Math.min(stock, q + 1))} disabled={qty >= stock}>+</button>
                </div>
              </div>

              <button className="btn btn-cta btn-block" onClick={() => addToCart(false)} disabled={adding}>
                {adding ? <Spinner /> : 'Add to Cart'}
              </button>
              <button className="btn btn-buy btn-block" onClick={() => addToCart(true)} disabled={adding}>
                Buy Now
              </button>

              {inCart && (
                <div className="small muted center">
                  {inCart.quantity} already in your cart · <Link to="/cart">View cart</Link>
                </div>
              )}
              {added && !addError && <Alert kind="success">Added to your cart.</Alert>}
              {addError && <Alert>{addError}</Alert>}
            </div>
          )}

          <div className="small muted" style={{ marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--line)', lineHeight: 1.8 }}>
            <div>🔒 Secure transaction</div>
            <div>↩️ 7 day return window after delivery</div>
            <div>💵 Cash on delivery available</div>
          </div>
        </aside>
      </div>
    </div>
  );
}
