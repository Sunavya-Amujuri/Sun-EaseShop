import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import ProductCard from '../components/ProductCard.jsx';
import { Alert, Empty, ProductSkeletons, useAsync } from '../ui.jsx';

/**
 * Results from the backend's semantic search: the query is embedded and matched against the
 * catalog, so "something for my yoga practice" finds a yoga mat without sharing a keyword.
 */
export default function Search() {
  const [params] = useSearchParams();
  const q = params.get('q') || '';

  const { data, loading, error } = useAsync(() => (q ? api.smartSearch(q) : Promise.resolve([])), [q]);
  const products = data || [];

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <h1>Results for “{q}”</h1>
          <p className="small muted" style={{ marginTop: 4 }}>
            Matched by meaning, not keywords.
          </p>
        </div>
        <span className="badge badge-ai">✨ Smart search</span>
      </div>

      {error && <Alert>{error}</Alert>}
      {loading && <ProductSkeletons count={4} />}

      {!loading && !error && (
        products.length === 0
          ? (
            <Empty icon="🔍" title="No matches found">
              Nothing in the catalog is close enough to “{q}”. Try describing the item differently.
            </Empty>
          )
          : <div className="grid">{products.map((p) => <ProductCard key={p.id} product={p} />)}</div>
      )}
    </div>
  );
}
