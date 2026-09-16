import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth, useCart } from '../store.jsx';
import { Money, Spinner, Thumb } from '../ui.jsx';

export default function ProductCard({ product }) {
  const { user } = useAuth();
  const { cart, setItem } = useCart();
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState('');

  const inCart = cart?.items?.find((i) => i.productId === product.id);
  const outOfStock = !product.stockQty || product.stockQty <= 0;

  async function add() {
    setAdding(true);
    setError('');
    try {
      // The endpoint sets an absolute quantity, so adding again means current + 1.
      await setItem(product.id, (inCart?.quantity || 0) + 1);
    } catch (e) {
      setError(e.message);
    } finally {
      setAdding(false);
    }
  }

  return (
    <div className="product-card">
      <Link to={`/product/${product.id}`} className="product-thumb">
        <Thumb src={product.mainImage} name={product.name} size="md" />
      </Link>

      <Link to={`/product/${product.id}`} style={{ textDecoration: 'none' }}>
        <div className="product-title">{product.name}</div>
      </Link>

      {product.categoryName && <div className="tiny faint">{product.categoryName}</div>}

      <Money value={product.price} className="price-md" />

      {outOfStock ? (
        <span className="badge badge-red">Out of stock</span>
      ) : product.stockQty <= 5 ? (
        <span className="badge badge-amber">Only {product.stockQty} left</span>
      ) : (
        <span className="tiny" style={{ color: 'var(--success)' }}>In stock</span>
      )}

      {user ? (
        <button className="btn btn-cta btn-sm btn-block" onClick={add} disabled={adding || outOfStock}>
          {adding ? <Spinner /> : inCart ? `In cart (${inCart.quantity}) · Add more` : 'Add to Cart'}
        </button>
      ) : (
        <Link to="/login" className="btn btn-cta btn-sm btn-block">Sign in to buy</Link>
      )}

      {error && <div className="tiny" style={{ color: 'var(--danger)' }}>{error}</div>}
    </div>
  );
}
