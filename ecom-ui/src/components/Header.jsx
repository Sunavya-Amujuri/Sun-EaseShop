import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth, useCart, useCategories } from '../store.jsx';
import { useClickOutside } from '../ui.jsx';

/**
 * Brand mark: a shopping bag with the store's initial on it.
 *
 * Drawn as a flat single-colour shape. The previous mark was a letter T on a two-stop orange
 * gradient, and gradient-filled rounded squares are template styling, not a logo. Real retail marks
 * are one solid colour so they survive being printed, faviconned and shrunk to 16px.
 */
function LogoMark() {
  return (
    <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">
      <path
        d="M6.4 10h19.2l-1.7 16a2.2 2.2 0 0 1-2.2 2H10.3a2.2 2.2 0 0 1-2.2-2z"
        fill="currentColor"
      />
      {/* The handle sits above the bag, drawn as a stroke so it reads at small sizes. */}
      <path
        d="M11.6 12.2V8.4a4.4 4.4 0 0 1 8.8 0v3.8"
        fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"
      />
      <text
        x="16" y="23.4" textAnchor="middle"
        fontSize="11.5" fontWeight="800" fill="#131a22"
        fontFamily="Segoe UI, Helvetica, Arial, sans-serif"
      >
        T
      </text>
    </svg>
  );
}

/* Drawn icons rather than emoji. Emoji render as a different picture on every platform, carry
   their own colour, and are the quickest way to make a shop look like a prototype. */
function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="m16 16 4.5 4.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg viewBox="0 0 24 24" width="23" height="23" aria-hidden="true">
      <path
        d="M2.5 4h2.2l2.4 10.2a1.6 1.6 0 0 0 1.56 1.24h8.9a1.6 1.6 0 0 0 1.56-1.23L21 7.5H6"
        fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"
      />
      <circle cx="10" cy="19.4" r="1.5" fill="currentColor" />
      <circle cx="17.4" cy="19.4" r="1.5" fill="currentColor" />
    </svg>
  );
}

export default function Header() {
  const { user, isAdmin, logout } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();
  const [term, setTerm] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useClickOutside(() => setMenuOpen(false));

  // The endpoint already returns active categories only; the nav bar has room for about a dozen.
  const { categories } = useCategories();
  const categoryList = categories.slice(0, 12);

  function search(e) {
    e.preventDefault();
    const q = term.trim();
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`);
  }

  return (
    <header className="header">
      <div className="header-main">
        <Link to="/" className="logo" aria-label="Sun-EaseMart home">
          <span className="logo-text hide-sm">Sun-EaseMart</span>
        </Link>

        {/* Search runs through the AI smart-search endpoint, so plain-language queries
            like "something for my yoga practice" work, not just keyword matches. */}
        <form className="searchbar" onSubmit={search} role="search">
          <input
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="Search products, try “something for my yoga practice”"
            aria-label="Search products"
          />
          <button type="submit" aria-label="Search">
            <SearchIcon />
            <span className="hide-sm">Search</span>
          </button>
        </form>

        {user ? (
          <div className="menu-wrap" ref={menuRef}>
            <button className="header-btn" onClick={() => setMenuOpen((o) => !o)}>
              <span className="l1">Hello, {user.fullName?.split(' ')[0] || 'there'}</span>
              <span className="l2">Account &amp; Lists ▾</span>
            </button>
            {menuOpen && (
              <div className="menu">
                <div className="menu-head">
                  <div className="bold">{user.fullName}</div>
                  <div className="small muted">{user.email}</div>
                </div>
                <Link to="/account" onClick={() => setMenuOpen(false)}>Your Account</Link>
                <Link to="/orders" onClick={() => setMenuOpen(false)}>Your Orders</Link>
                <Link to="/addresses" onClick={() => setMenuOpen(false)}>Your Addresses</Link>
                <Link to="/support" onClick={() => setMenuOpen(false)}>Customer Support</Link>
                {isAdmin && <Link to="/admin" onClick={() => setMenuOpen(false)}>Admin Console</Link>}
                <button onClick={() => { setMenuOpen(false); logout(); navigate('/'); }}>Sign out</button>
              </div>
            )}
          </div>
        ) : (
          <Link to="/login" className="header-btn">
            <span className="l1">Hello, sign in</span>
            <span className="l2">Account &amp; Lists</span>
          </Link>
        )}

        <Link to="/orders" className="header-btn hide-sm">
          <span className="l1">Returns</span>
          <span className="l2">&amp; Orders</span>
        </Link>

        <Link to="/cart" className="cart-btn">
          <span className="cart-icon">
            <CartIcon />
            {count > 0 && <span className="cart-count">{count}</span>}
          </span>
          <span className="l2 bold hide-sm" style={{ fontSize: 13.5 }}>Cart</span>
        </Link>
      </div>

      <nav className="header-nav">
        <div className="header-nav-inner">
          <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>All</NavLink>
          {categoryList.map((c) => (
            <NavLink
              key={c.id}
              to={`/category/${c.id}`}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              {c.name}
            </NavLink>
          ))}
          {user && (
            <NavLink to="/deals" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Coupons
            </NavLink>
          )}
          {isAdmin && (
            <NavLink to="/admin" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Admin
            </NavLink>
          )}
        </div>
      </nav>
    </header>
  );
}
