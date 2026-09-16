import { useState } from 'react';
import { api } from '../api.js';
import HeroCarousel from '../components/HeroCarousel.jsx';
import ProductCard from '../components/ProductCard.jsx';
import { useAuth } from '../store.jsx';
import { Alert, Empty, ProductSkeletons, useAsync } from '../ui.jsx';

/** "Inspired by your purchases" - grounded in the shopper's real order history. */
function Recommendations() {
  const { data, loading, error } = useAsync(() => api.recommendations(4), []);

  if (loading || error) return null;
  if (!data?.products?.length) return null;

  return (
    <section style={{ marginBottom: 26 }}>
      <div className="section-head">
        <h2>Inspired by your purchases</h2>
        <span className="badge badge-ai">✨ AI picked</span>
      </div>
      {data.message && <div className="ai-note" style={{ marginBottom: 12 }}>{data.message}</div>}
      <div className="grid">
        {data.products.map((p) => <ProductCard key={p.id} product={p} />)}
      </div>
    </section>
  );
}

export default function Home() {
  const { user } = useAuth();

  const [page, setPage] = useState(1);

  const { data, loading, error } = useAsync(
    () => api.products(page, 10),
    [page]
  );

  const products = data?.content || [];

  return (
    <div className="page">
      <HeroCarousel />

      {user && <Recommendations />}

      <section id="catalog">
        <div className="section-head">
          <h2>All products</h2>
          {data?.totalElements != null && (
            <span className="small muted">{data.totalElements} item{data.totalElements === 1 ? '' : 's'}</span>
          )}
        </div>

        {error && <Alert>{error}</Alert>}
        {loading && <ProductSkeletons count={8} />}

        {!loading && !error && (
          products.length === 0
            ? <Empty icon="🗂️" title="No products yet" >The catalog is empty. An admin can add products from the Admin Console.</Empty>
            : (
              <>
                <div className="grid">
                  {products.map((p) => <ProductCard key={p.id} product={p} />)}
                </div>

                {data?.totalPages > 1 && (
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'center',
                      gap: 8,
                      marginTop: 30,
                      marginBottom: 30
                    }}
                  >
                    {Array.from(
                      { length: data.totalPages },
                      (_, index) => index + 1
                    ).map((pageNumber) => (
                      <button
                        key={pageNumber}
                        onClick={() => setPage(pageNumber)}
                        style={{
                          minWidth: 36,
                          height: 36,
                          border: '1px solid #ccc',
                          borderRadius: 4,
                          background: page === pageNumber ? '#ffd814' : '#fff',
                          fontWeight: page === pageNumber ? '700' : '400',
                          cursor: 'pointer'
                        }}
                      >
                        {pageNumber}
                      </button>
                    ))}
                  </div>
                )}
              </>
            )
        )}
      </section>
    </div>
  );
}
