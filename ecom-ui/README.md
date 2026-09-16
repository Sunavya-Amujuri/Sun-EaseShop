# Sun-EaseMart — storefront UI

React 18 + Vite frontend for the E-Commerce backend. Same stack as the other course
frontends, plus `react-router-dom` because a storefront needs real URLs for products,
categories and orders.

## Running

```bash
# 1. Backend first (from ../E-Commerce-Backend)
mvn spring-boot:run          # needs Docker running for the pgvector database

# 2. Frontend
npm install
npm run dev                  # http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`, so the browser only ever talks to one
origin and there is no CORS preflight in development.

## Layout

```
src/
  api.js            every backend call, plus JWT storage and refresh
  store.jsx         AuthProvider (session) and CartProvider (server-owned cart)
  ui.jsx            shared pieces: Money, Thumb, useAsync, badges, skeletons
  styles.css        design tokens and component styles
  components/       Header, ProductCard, AiAssistant
  pages/            one file per route
  pages/admin/      admin console (tabs: Analytics, Orders, Support, Catalog)
```

## Notes

- **The cart lives on the server.** Every mutation returns the recalculated cart and that
  becomes the new state, so totals, discounts and stock rules are never re-implemented in
  the browser.
- **Tokens refresh automatically.** Access tokens last 30 minutes; on a 401 `api.js` swaps
  the refresh token for a new pair and replays the request once. Parallel 401s share one
  refresh rather than firing several.
- **Product images are optional.** Uploads go to Cloudinary, which most local setups do not
  configure, so `Thumb` draws a coloured tile from the product name instead of showing a
  broken image.
- **Search is semantic.** The header search calls the backend's smart-search, so
  "something for my yoga practice" finds a yoga mat without sharing a keyword.

## Where the AI shows up

| Feature | Where |
|---|---|
| Shopping assistant | Floating button, every signed-in page |
| Ask about this product | Product detail page |
| Recommendations | Home, for shoppers with order history |
| Cart suggestions | Cart page |
| Return eligibility | Order detail — the decision is the backend's, the AI only explains it |
| Analytics assistant | Admin → Analytics |
| Ticket triage + draft reply | Admin → Support |
