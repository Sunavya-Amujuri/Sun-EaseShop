import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, tokens } from './api.js';

const AuthContext = createContext(null);
const CartContext = createContext(null);
const CategoriesContext = createContext(null);

/**
 * Session state.
 *
 * The JWT is kept in localStorage by api.js; this only holds the profile. On boot we ask
 * /users/me rather than trusting a stored user object, so a revoked or expired session is
 * detected before the UI renders an account menu for someone who is no longer signed in.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!tokens.access()) { setReady(true); return; }
    api.me()
      .then(setUser)
      .catch(() => tokens.clear())
      .finally(() => setReady(true));
  }, []);

  const login = useCallback(async (email, password) => {
    const t = await api.login({ email, password });
    tokens.save(t);
    const profile = await api.me();
    setUser(profile);
    return profile;
  }, []);

  const logout = useCallback(async () => {
    try { await api.logout(); } catch { /* the local session is dropped either way */ }
    tokens.clear();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, ready, login, logout, isAdmin: user?.role === 'ADMIN' }),
    [user, ready, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

/**
 * Cart state.
 *
 * The server owns the cart, so every mutation returns the recalculated cart and we store that
 * rather than adjusting a local copy. That keeps totals, coupon discounts and stock rules
 * consistent with the backend instead of re-implementing them here.
 */
export function CartProvider({ children }) {
  const { user } = useAuth();
  const [cart, setCart] = useState(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    if (!user) { setCart(null); return; }
    try { setCart(await api.cart()); } catch { /* cart stays as-is on a transient failure */ }
  }, [user]);

  useEffect(() => { refresh(); }, [refresh]);

  const mutate = useCallback(async (fn) => {
    setBusy(true);
    try {
      const updated = await fn();
      setCart(updated);
      return updated;
    } finally {
      setBusy(false);
    }
  }, []);

  const value = useMemo(() => ({
    cart,
    busy,
    refresh,
    count: cart?.items?.reduce((n, i) => n + (i.quantity || 0), 0) || 0,
    setItem: (productId, quantity) => mutate(() => api.setCartItem(productId, quantity)),
    removeItem: (productId) => mutate(() => api.removeCartItem(productId)),
    clear: () => mutate(() => api.clearCart()),
    applyCoupon: (code) => mutate(() => api.applyCoupon(code)),
    removeCoupon: () => mutate(() => api.removeCoupon()),
  }), [cart, busy, refresh, mutate]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export const useCart = () => useContext(CartContext);

/**
 * Category state.
 *
 * Held once for the whole app instead of fetched per component. The header, the category page and
 * the admin screens all show the same list, and each used to load its own copy: the header's copy
 * was fetched when it mounted and never again, so a category added in the Admin Console did not
 * appear in the navigation until the page was reloaded by hand.
 *
 * With one shared list, a create, edit or delete calls `reload` and every screen showing categories
 * updates at once.
 */
export function CategoriesProvider({ children }) {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.categories(1, 50);
      setCategories(page?.content || []);
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const value = useMemo(
    () => ({ categories, loading, error, reload }),
    [categories, loading, error, reload],
  );

  return <CategoriesContext.Provider value={value}>{children}</CategoriesContext.Provider>;
}

export const useCategories = () => useContext(CategoriesContext);
