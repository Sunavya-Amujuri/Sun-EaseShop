// All backend calls in one place. Relative URLs go through the Vite proxy to Spring Boot.

const BASE = `/api/v1`;

const ACCESS_KEY = 'ecom.accessToken';
const REFRESH_KEY = 'ecom.refreshToken';

export const tokens = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  save(t) {
    if (t?.accessToken) localStorage.setItem(ACCESS_KEY, t.accessToken);
    if (t?.refreshToken) localStorage.setItem(REFRESH_KEY, t.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

/**
 * Turns a failed response into an Error carrying the backend's message.
 * The API returns a consistent shape ({status, error, message, path}), so surfacing `message`
 * gives the user the real reason ("Account is disabled...") instead of a generic failure.
 */
async function toError(res) {
  let message = `Request failed (${res.status})`;
  try {
    const data = await res.json();
    message = data.message || data.error || message;
    if (data.errors) {
      // Validation failures carry a field -> message map.
      message = Object.entries(data.errors).map(([f, m]) => `${f}: ${m}`).join('\n');
    }
  } catch {
    /* body was not JSON - keep the status message */
  }
  const err = new Error(message);
  err.status = res.status;
  return err;
}

async function parse(res) {
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  return type.includes('application/json') ? res.json() : res.text();
}

let refreshing = null;

/**
 * Core request helper.
 *
 * Access tokens live 30 minutes, so a browsing session will outlive one. On the first 401 we
 * swap the refresh token for a new pair and replay the request once. `refreshing` is shared so
 * ten parallel calls hitting 401 together trigger a single refresh rather than ten.
 */
async function request(path, { method = 'GET', body, form, auth = true, retry = true } = {}) {
  const headers = {};
  if (auth && tokens.access()) headers.Authorization = `Bearer ${tokens.access()}`;

  let payload;
  if (form) {
    payload = form; // browser sets the multipart boundary itself
  } else if (body !== undefined) {
    if (typeof body === 'string') {
      headers['Content-Type'] = 'text/plain';
      payload = body;
    } else {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
  }

  const res = await fetch(BASE + path, { method, headers, body: payload });

  if (res.status === 401 && retry && tokens.refresh() && auth) {
    if (!refreshing) {
      refreshing = fetch(`${BASE}/users/refresh-token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: tokens.refresh() }),
      })
        .then((r) => (r.ok ? r.json() : Promise.reject(new Error('session expired'))))
        .then((t) => { tokens.save(t); return t; })
        .catch((e) => { tokens.clear(); throw e; })
        .finally(() => { refreshing = null; });
    }
    try {
      await refreshing;
      return request(path, { method, body, form, auth, retry: false });
    } catch {
      throw await toError(res);
    }
  }

  if (!res.ok) throw await toError(res);
  return parse(res);
}

const get = (p) => request(p);
const post = (p, body) => request(p, { method: 'POST', body });
const patch = (p, body) => request(p, { method: 'PATCH', body });
const del = (p) => request(p, { method: 'DELETE' });

export const api = {
  // ---------- Auth ----------
  register: (body) => request('/users/register', { method: 'POST', body, auth: false }),
  login: (body) => request('/users/login', { method: 'POST', body, auth: false }),
  logout: () => post('/users/logout', {}),
  me: () => get('/users/me'),
  // Email confirmation and password reset both use a 6 digit code rather than a link.
  verifyEmail: (email, otp) =>
    request('/users/verify-email', { method: 'POST', body: { email, otp }, auth: false }),
  // Takes the email as a query parameter, not a JSON body.
  resendVerification: (email) =>
    request(`/users/resend-email-verification?email=${encodeURIComponent(email)}`, { method: 'POST', auth: false }),
  forgotPassword: (email) => request('/users/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (email, otp, newPassword) =>
    request('/users/reset-password', { method: 'POST', body: { email, otp, newPassword }, auth: false }),
  changePassword: (body) => post('/users/change-password', body),

  // ---------- Catalog ----------
  products: (page = 1, limit = 12) => get(`/ecommerce/products?page=${page}&limit=${limit}`),
  product: (id) => get(`/ecommerce/products/${id}`),
  productsByCategory: (categoryId, page = 1, limit = 12) =>
    get(`/ecommerce/products/category/${categoryId}?page=${page}&limit=${limit}`),
  categories: (page = 1, limit = 50) => get(`/ecommerce/categories?page=${page}&limit=${limit}`),
  category: (id) => get(`/ecommerce/categories/${id}`),
  smartSearch: (query) => get(`/ecommerce/products/smart-search?query=${encodeURIComponent(query)}`),

  // ---------- Cart ----------
  cart: () => get('/ecommerce/cart'),
  setCartItem: (productId, quantity) => post(`/ecommerce/cart/item/${productId}`, { quantity }),
  removeCartItem: (productId) => del(`/ecommerce/cart/item/${productId}`),
  clearCart: () => del('/ecommerce/cart/clear'),

  // ---------- Coupons ----------
  availableCoupons: (page = 1, limit = 20) => get(`/ecommerce/coupons/customer/available?page=${page}&limit=${limit}`),
  applyCoupon: (couponCode) => post('/ecommerce/coupons/c/apply', { couponCode }),
  removeCoupon: () => post('/ecommerce/coupons/c/remove', {}),

  // ---------- Addresses ----------
  addresses: (page = 1, limit = 20) => get(`/ecommerce/addresses?page=${page}&limit=${limit}`),
  address: (id) => get(`/ecommerce/addresses/${id}`),
  addAddress: (body) => post('/ecommerce/addresses', body),
  updateAddress: (id, body) => patch(`/ecommerce/addresses/${id}`, body),
  deleteAddress: (id) => del(`/ecommerce/addresses/${id}`),

  // ---------- Orders ----------
  placeOrder: (body) => post('/ecommerce/orders', body),
  myOrders: (page = 1, limit = 20) => get(`/ecommerce/profile/my-orders?page=${page}&limit=${limit}`),
  myOrder: (id) => get(`/ecommerce/profile/my-orders/${id}`),

  // ---------- Payments (Razorpay) ----------
  createPaymentOrder: (orderId) => post(`/ecommerce/payments/create/${orderId}`, {}),
  verifyPayment: (body) => post('/ecommerce/payments/verify', body),
  paymentFailed: (orderId, reason) =>
    request(`/ecommerce/payments/failure/${orderId}?reason=${encodeURIComponent(reason || 'unknown')}`, { method: 'POST' }),
  cancelPayment: (orderId) => post(`/ecommerce/payments/cancel/${orderId}`, {}),

  // ---------- Support ----------
  createTicket: (body) => post('/ecommerce/support/tickets', body),
  myTickets: (page = 1, limit = 20) => get(`/ecommerce/support/tickets?page=${page}&limit=${limit}`),
  ticket: (id) => get(`/ecommerce/support/tickets/${id}`),
  ticketMessages: (id) => get(`/ecommerce/support/tickets/${id}/messages`),
  // The message endpoints take the raw text as the body, not a JSON object.
  replyToTicket: (id, content) =>
    request(`/ecommerce/support/tickets/${id}/messages`, { method: 'POST', body: content }),

  // ---------- AI, customer facing ----------
  assistant: (message) => request('/ecommerce/ai/assistant', { method: 'POST', body: message }),
  askAboutProduct: (productId, question) =>
    request(`/ecommerce/ai/products/${productId}/ask`, { method: 'POST', body: question }),
  recommendations: (limit = 4) => get(`/ecommerce/ai/recommendations?limit=${limit}`),
  cartSuggestions: (limit = 4) => get(`/ecommerce/ai/cart/suggestions?limit=${limit}`),
  returnEligibility: (orderId) => get(`/ecommerce/ai/orders/${orderId}/return-eligibility`),

  // ---------- Admin ----------
  analyticsAssistant: (message) =>
    request('/ecommerce/ai/admin/analytics-assistant', { method: 'POST', body: message }),
  ticketAssistant: (message) =>
    request('/ecommerce/support/ticket-assistant', { method: 'POST', body: message }),
  adminOrders: (page = 1, limit = 20) => get(`/ecommerce/orders/list/admin?page=${page}&limit=${limit}`),
  adminOrder: (orderId) => get(`/ecommerce/orders/${orderId}/admin`),
  adminUpdateOrderStatus: (orderId, status) => patch(`/ecommerce/orders/status/${orderId}`, { status }),
  adminTickets: (page = 1, limit = 20, status) =>
    get(`/ecommerce/support/admin/tickets?page=${page}&limit=${limit}${status ? `&status=${status}` : ''}`),
  adminTicket: (id) => get(`/ecommerce/support/admin/tickets/${id}`),
  adminTicketMessages: (id) => get(`/ecommerce/support/admin/tickets/${id}/messages`),
  adminReplyToTicket: (id, content) =>
    request(`/ecommerce/support/admin/tickets/${id}/messages`, { method: 'POST', body: content }),
  // The AI draft is hidden from the ticket JSON so customers never see it; admins read it here.
  adminSuggestedReply: (id) => get(`/ecommerce/support/admin/tickets/${id}/suggested-reply`),
  adminUpdateTicketStatus: (id, body) => patch(`/ecommerce/support/admin/tickets/${id}/status`, body),
  // Categories
  adminCreateCategory: (body) => post('/ecommerce/categories', body),
  adminUpdateCategory: (id, body) => patch(`/ecommerce/categories/${id}`, body),
  adminDeleteCategory: (id) => del(`/ecommerce/categories/${id}`),

  // Coupons
  adminCoupons: (page = 1, limit = 50) => get(`/ecommerce/coupons?page=${page}&limit=${limit}`),
  adminCoupon: (id) => get(`/ecommerce/coupons/${id}`),
  adminCreateCoupon: (body) => post('/ecommerce/coupons', body),
  adminUpdateCoupon: (id, body) => patch(`/ecommerce/coupons/${id}`, body),
  adminDeleteCoupon: (id) => del(`/ecommerce/coupons/${id}`),
  // The status endpoint expects {isActive}, not {active}.
  adminSetCouponActive: (id, isActive) => patch(`/ecommerce/coupons/status/${id}`, { isActive }),

  // Products
  adminDeleteProduct: (id) => del(`/ecommerce/products/${id}`),

  // Users
  adminAssignRole: (userId, role) =>
    request(`/users/assign-role/${userId}?role=${encodeURIComponent(role)}`, { method: 'POST' }),

  /**
   * Creates a product. The endpoint is multipart and mainImage is required, so the caller must
   * supply either an uploaded file or one generated by adminGenerateImage.
   */
  adminCreateProduct: (fields, mainImage, subImages = []) => {
    const form = new FormData();
    Object.entries(fields).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') form.append(k, v);
    });
    form.append('mainImage', mainImage);
    subImages.forEach((f) => form.append('subImages', f));
    return request('/ecommerce/products', { method: 'POST', form });
  },

  adminGenerateDescription: (name, category, brand) =>
    request(`/ecommerce/product/generate-description?name=${encodeURIComponent(name)}`
      + `&category=${encodeURIComponent(category)}&brand=${encodeURIComponent(brand)}`, { method: 'POST' }),

  adminGenerateImage: (name, category, description, brand) =>
    request(`/ecommerce/product/generate-image?name=${encodeURIComponent(name)}`
      + `&category=${encodeURIComponent(category)}&description=${encodeURIComponent(description)}`
      + `&brand=${encodeURIComponent(brand)}`, { method: 'POST' }),
  adminUpdateProduct: (id, fields) => {
    const form = new FormData();
    Object.entries(fields).forEach(([k, v]) => v !== undefined && v !== '' && form.append(k, v));
    return request(`/ecommerce/products/${id}`, { method: 'PATCH', form });
  },
};
