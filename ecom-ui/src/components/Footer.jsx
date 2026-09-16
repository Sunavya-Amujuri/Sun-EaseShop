import { Link } from 'react-router-dom';
import { useAuth } from '../store.jsx';

/**
 * Site footer.
 *
 * Replaces a single centred sentence, which is the one part of a storefront that always gives away
 * a demo. Real retail footers do three things: offer a way back to the top of a long catalog page,
 * group the account and help links so they are reachable from the bottom of any page, and carry the
 * legal line.
 *
 * Every link here points at a route this app actually serves. No Careers, Press or Privacy columns
 * were invented to fill space: a footer full of dead links is worse than a short honest one.
 */
export default function Footer() {
  const { user, isAdmin } = useAuth();

  return (
    <footer className="footer">
      <button
        className="footer-top"
        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      >
        Back to top
      </button>

      <div className="footer-cols">
        <div>
          <h3>Sun-EaseMart</h3>
          <p className="footer-about">
            A working storefront built on Spring Boot and Spring AI, with semantic search, a grounded
            shopping assistant and AI assisted support behind the scenes.
          </p>
        </div>

        <div>
          <h3>Shop</h3>
          <ul>
            <li><Link to="/">All products</Link></li>
            <li><Link to="/search?q=best%20sellers">Smart search</Link></li>
            {user && <li><Link to="/deals">Coupons and offers</Link></li>}
            <li><Link to="/cart">Your cart</Link></li>
          </ul>
        </div>

        <div>
          <h3>Your account</h3>
          <ul>
            {user ? (
              <>
                <li><Link to="/account">Account details</Link></li>
                <li><Link to="/orders">Your orders</Link></li>
                <li><Link to="/addresses">Saved addresses</Link></li>
                {isAdmin && <li><Link to="/admin">Admin console</Link></li>}
              </>
            ) : (
              <>
                <li><Link to="/login">Sign in</Link></li>
                <li><Link to="/register">Create an account</Link></li>
                <li><Link to="/forgot-password">Forgot password</Link></li>
              </>
            )}
          </ul>
        </div>

        <div>
          <h3>Help</h3>
          <ul>
            {user
              ? <li><Link to="/support">Customer support</Link></li>
              : <li><Link to="/login">Sign in to contact support</Link></li>}
            <li><Link to="/orders">Track an order</Link></li>
            <li><Link to="/orders">Returns</Link></li>
          </ul>
        </div>
      </div>

      <div className="footer-bar">
        <span className="footer-legal">
          © {new Date().getFullYear()} Sun-EaseMart. Cash on delivery and online payments accepted.
        </span>
        <span className="footer-built">Spring Boot · Spring AI · React</span>
      </div>
    </footer>
  );
}
