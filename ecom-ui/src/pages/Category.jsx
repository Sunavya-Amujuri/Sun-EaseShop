import { useParams } from 'react-router-dom';
import { api } from '../api.js';
import ProductCard from '../components/ProductCard.jsx';
import { useCategories } from '../store.jsx';
import { Alert, Empty, ProductSkeletons, useAsync } from '../ui.jsx';

export default function Category() {
  const { categoryId } = useParams();

  const { data: page, loading, error } = useAsync(
    () => api.productsByCategory(categoryId, 1, 24),
    [categoryId],
  );

  // The heading comes from the shared list, so renaming a category updates this page too.
  const { categories } = useCategories();
  const category = categories.find((c) => String(c.id) === String(categoryId));
  const products = page?.content || [];

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <h1>{category?.name || 'Category'}</h1>
          {category?.description && <p className="small muted" style={{ marginTop: 4 }}>{category.description}</p>}
        </div>
        {page?.totalElements != null && (
          <span className="small muted">{page.totalElements} item{page.totalElements === 1 ? '' : 's'}</span>
        )}
      </div>

      {error && <Alert>{error}</Alert>}
      {loading && <ProductSkeletons count={6} />}

      {!loading && !error && (
        products.length === 0
          ? <Empty icon="🗂️" title="Nothing here yet">This category has no products at the moment.</Empty>
          : <div className="grid">{products.map((p) => <ProductCard key={p.id} product={p} />)}</div>
      )}
    </div>
  );
}
