import { useCallback, useEffect, useRef, useState } from 'react';

/** Rupee formatting, used everywhere prices are shown. */
export function Money({ value, className = '' }) {
  const n = Number(value ?? 0);
  return (
    <span className={`price ${className}`}>
      <span className="price-sym">₹</span>
      {n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}
    </span>
  );
}

/**
 * Product image with a fallback for rows that have none.
 *
 * Images are uploaded to Cloudinary, which is optional in local setups, so a catalog row can have
 * mainImage = null. A broken <img> would make the storefront look unfinished, so a placeholder
 * tile stands in.
 *
 * The tile is plain grey with the product's initials. It used to be a saturated two-colour gradient
 * keyed to a hash of the name, which gave the catalog rows of randomly coloured squares - the exact
 * look of a generated mockup rather than a shop waiting on photography.
 */
export function Thumb({ src, name = '', size = 'md' }) {
  const [failed, setFailed] = useState(false);
  if (src && !failed) {
    return <img src={src} alt={name} onError={() => setFailed(true)} loading="lazy" />;
  }
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?';
  return (
    <div className={`thumb-fallback ${size}`} aria-label={name}>
      {initials}
    </div>
  );
}

export function Spinner({ light }) {
  return <span className={`spinner ${light ? 'light' : ''}`} />;
}

export function Loading({ label = 'Loading' }) {
  return (
    <div className="row center" style={{ justifyContent: 'center', padding: 44, gap: 10 }}>
      <Spinner /> <span className="muted">{label}…</span>
    </div>
  );
}

export function Empty({ icon = '📦', title, children, action }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <h2 style={{ marginBottom: 6 }}>{title}</h2>
      {children && <p className="muted" style={{ marginBottom: 16 }}>{children}</p>}
      {action}
    </div>
  );
}

export function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return <div className={`alert alert-${kind}`}>{children}</div>;
}

export function ProductSkeletons({ count = 8 }) {
  return (
    <div className="grid">
      {Array.from({ length: count }).map((_, i) => (
        <div className="product-card" key={i}>
          <div className="skeleton" style={{ aspectRatio: 1 }} />
          <div className="skeleton" style={{ height: 13 }} />
          <div className="skeleton" style={{ height: 13, width: '65%' }} />
          <div className="skeleton" style={{ height: 20, width: '45%' }} />
        </div>
      ))}
    </div>
  );
}

/** Maps an order status to a badge colour, so status reads at a glance. */
export function statusTone(status) {
  switch (status) {
    case 'DELIVERED': return 'badge-green';
    case 'CANCELED': case 'RETURNED': case 'REFUNDED': return 'badge-red';
    case 'SHIPPED': case 'PACKED': return 'badge-blue';
    case 'RETURN_REQUEST': return 'badge-amber';
    default: return 'badge-grey';
  }
}

export function priorityTone(priority) {
  switch (priority) {
    case 'URGENT': return 'badge-red';
    case 'HIGH': return 'badge-amber';
    case 'MEDIUM': return 'badge-blue';
    case 'LOW': return 'badge-grey';
    default: return 'badge-grey';
  }
}

export function formatDate(value) {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function prettyStatus(s) {
  return String(s || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * Runs an async loader and tracks {data, loading, error}.
 * `deps` behaves like useEffect's. A ref guards against a slow first response overwriting a
 * newer one when the user navigates quickly.
 */
export function useAsync(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  const runId = useRef(0);

  const run = useCallback(() => {
    const id = ++runId.current;
    setState((s) => ({ ...s, loading: true, error: '' }));
    Promise.resolve()
      .then(loader)
      .then((data) => { if (id === runId.current) setState({ data, loading: false, error: '' }); })
      .catch((e) => { if (id === runId.current) setState({ data: null, loading: false, error: e.message }); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { run(); }, [run]);
  return { ...state, reload: run };
}

/** Closes a popover when the user clicks anywhere outside it. */
export function useClickOutside(onOutside) {
  const ref = useRef(null);
  useEffect(() => {
    function handler(e) {
      if (ref.current && !ref.current.contains(e.target)) onOutside();
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onOutside]);
  return ref;
}
