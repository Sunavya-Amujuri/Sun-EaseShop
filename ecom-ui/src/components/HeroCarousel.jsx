import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { Money, Thumb } from '../ui.jsx';

const AUTOPLAY_MS = 5500;
const MAX_SLIDES = 4;

/**
 * Home page banner rotation.
 *
 * Built from real catalog rows rather than invented artwork. The earlier version used bright
 * multi-hue gradients with a giant emoji standing in for a product, which is exactly what makes a
 * storefront look mocked up rather than open for business. Every slide here is a product that is
 * actually in stock, with its own photograph, real price and a link to its page, which is how
 * marketplace banners work: the merchandise is the artwork.
 *
 * Photos come from Cloudinary and are optional in local setups, so Thumb's coloured initials tile
 * stands in when a product has no image. The slide keeps its shape either way.
 */
export default function HeroCarousel() {
  const [products, setProducts] = useState([]);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const timer = useRef(null);

  useEffect(() => {
    let live = true;
    // In-stock items only: a banner is a promise, and promoting something unbuyable breaks it.
    api.products(1, 12)
      .then((page) => {
        if (!live) return;
        const inStock = (page?.content || []).filter((p) => (p.stockQty || 0) > 0);
        setProducts(inStock.slice(0, MAX_SLIDES));
      })
      .catch(() => { /* the banner is decoration, the catalog below is the real page */ });
    return () => { live = false; };
  }, []);

  const count = products.length;

  const go = useCallback((next) => {
    setIndex((i) => (count ? (next + count) % count : 0));
  }, [count]);

  useEffect(() => {
    // Skip the rotation for anyone who has asked the system to reduce motion, and while the
    // pointer is over the banner so a slide cannot move out from under a click.
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (paused || reduced || count < 2) return undefined;

    timer.current = setTimeout(() => go(index + 1), AUTOPLAY_MS);
    return () => clearTimeout(timer.current);
  }, [index, paused, go, count]);

  // Before the catalog arrives, hold the space with a quiet placeholder instead of letting the
  // page jump once the banner loads.
  if (count === 0) {
    return (
      <section className="hero hero-blank" aria-hidden="true">
        <div className="hero-blank-inner">
          <span className="skeleton" style={{ width: 180, height: 13 }} />
          <span className="skeleton" style={{ width: 340, height: 30 }} />
          <span className="skeleton" style={{ width: 240, height: 13 }} />
        </div>
      </section>
    );
  }

  return (
    <section
      className="hero"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
      aria-roledescription="carousel"
      aria-label="Featured products"
    >
      <div className="hero-track" style={{ transform: `translateX(-${index * 100}%)` }}>
        {products.map((p, i) => (
          <article className="hero-slide" key={p.id} aria-hidden={i !== index}>
            <div className="hero-copy">
              {p.categoryName && <span className="hero-eyebrow">{p.categoryName}</span>}
              <h1>{p.name}</h1>
              {p.description && <p>{p.description}</p>}

              <div className="hero-price">
                <Money value={p.price} />
                <span className="hero-stock">In stock</span>
              </div>

              <Link
                to={`/product/${p.id}`}
                className="btn btn-cta btn-lg"
                // Off-screen slides stay out of the tab order, so keyboard users are not
                // sent to a link they cannot see.
                tabIndex={i === index ? 0 : -1}
              >
                Shop now
              </Link>
            </div>

            <Link
              to={`/product/${p.id}`}
              className="hero-shot"
              tabIndex={-1}
              aria-hidden={i !== index}
            >
              <Thumb src={p.mainImage} name={p.name} size="lg" />
            </Link>
          </article>
        ))}
      </div>

      {count > 1 && (
        <>
          <button className="hero-arrow prev" onClick={() => go(index - 1)} aria-label="Previous slide">
            <Chevron dir="left" />
          </button>
          <button className="hero-arrow next" onClick={() => go(index + 1)} aria-label="Next slide">
            <Chevron dir="right" />
          </button>

          <div className="hero-dots">
            {products.map((p, i) => (
              <button
                key={p.id}
                className={`hero-dot ${i === index ? 'active' : ''}`}
                onClick={() => go(i)}
                aria-label={`Go to slide ${i + 1}`}
                aria-current={i === index}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

/** Drawn rather than typed: a text chevron inherits font quirks and never sits quite centred. */
function Chevron({ dir }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path
        d={dir === 'left' ? 'M15 5 8 12l7 7' : 'M9 5l7 7-7 7'}
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
