import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Header from './components/Header.jsx';
import Footer from './components/Footer.jsx';
import AiAssistant from './components/AiAssistant.jsx';
import { AuthProvider, CartProvider, CategoriesProvider, useAuth } from './store.jsx';
import { Loading } from './ui.jsx';

import Home from './pages/Home.jsx';
import Search from './pages/Search.jsx';
import Category from './pages/Category.jsx';
import ProductDetail from './pages/ProductDetail.jsx';
import Cart from './pages/Cart.jsx';
import Checkout from './pages/Checkout.jsx';
import Orders from './pages/Orders.jsx';
import OrderDetail from './pages/OrderDetail.jsx';
import Addresses from './pages/Addresses.jsx';
import Support from './pages/Support.jsx';
import Deals from './pages/Deals.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import VerifyEmail from './pages/VerifyEmail.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Account from './pages/Account.jsx';
import AdminApp from './pages/admin/AdminApp.jsx';

// Rendered without the storefront chrome: these are reached from an email or before sign-in,
// where a header full of shopping links is noise.
const BARE_ROUTES = ['/login', '/register', '/verify-email', '/forgot-password', '/reset-password'];

/**
 * Gate for signed-in routes.
 *
 * Waits for the session check before deciding, otherwise a refresh on /orders would bounce the
 * user to the login page for a moment before their session finished loading.
 */
function Protected({ children, adminOnly }) {
  const { user, ready, isAdmin } = useAuth();
  const location = useLocation();

  if (!ready) return <Loading label="Checking your session" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (adminOnly && !isAdmin) return <Navigate to="/" replace />;
  return children;
}

function Shell() {
  const location = useLocation();
  const bare = BARE_ROUTES.includes(location.pathname);

  return (
    <>
      {!bare && <Header />}

      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/search" element={<Search />} />
        <Route path="/category/:categoryId" element={<Category />} />
        <Route path="/product/:productId" element={<ProductDetail />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />

        <Route path="/account" element={<Protected><Account /></Protected>} />
        <Route path="/cart" element={<Protected><Cart /></Protected>} />
        <Route path="/checkout" element={<Protected><Checkout /></Protected>} />
        <Route path="/orders" element={<Protected><Orders /></Protected>} />
        <Route path="/orders/:orderId" element={<Protected><OrderDetail /></Protected>} />
        <Route path="/addresses" element={<Protected><Addresses /></Protected>} />
        <Route path="/support" element={<Protected><Support /></Protected>} />
        <Route path="/deals" element={<Protected><Deals /></Protected>} />
        <Route path="/admin" element={<Protected adminOnly><AdminApp /></Protected>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      {!bare && (
        <>
          <AiAssistant />
          <Footer />
        </>
      )}
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <CategoriesProvider>
            <Shell />
          </CategoriesProvider>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
