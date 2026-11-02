import { Link } from '@tanstack/react-router';
import { COMPANY, PAGES } from '@/components/legal/Legal';
import { Logo } from '@/components/layout/Logo';
import { PauseButton, vars } from '../wire';
import './09-footer.css';

const o = (n: number) => vars({ '--order': n });

// The skill pages the sitemap asks search engines to index, linked from the page that carries the most weight.
const PRACTISE = [
  { to: '/speaking', label: 'Speaking practice' },
  { to: '/writing', label: 'Writing practice' },
  { to: '/listening', label: 'Listening tests' },
  { to: '/reading', label: 'Reading tests' },
  { to: '/bank', label: 'Question bank' },
] as const;

/**
 * The legal footer with an ink wordmark (the FAQ is its own section, 08b-faq). Root is <footer> (rendered after
 * </main>) so the page keeps its contentinfo landmark. Legal text comes from the same COMPANY/PAGES source as SiteFooter.
 */
export function Footer() {
  return (
    <footer id="footer" aria-label="Site footer" className="lp-footer">
      <div className="lp-container lp-footer-inner">
        <span className="lp-footer-rule lp-draw-x" aria-hidden="true" />
        <div className="lp-footer-grid">
          <div className="lp-footer-ask" style={o(0)}>
            <Logo className="lp-footer-logo" />
            <a className="lp-footer-mail" href={`mailto:${COMPANY.email}`}>
              {COMPANY.email}
            </a>
            <Link to="/login" className="lp-link lp-footer-signin">
              Sign in
            </Link>
          </div>
          <nav aria-labelledby="ft-practise" className="lp-footer-nav" style={o(1)}>
            <p id="ft-practise" className="lp-footer-h">
              Practise
            </p>
            <ul>
              {PRACTISE.map((l) => (
                <li key={l.to}>
                  <Link to={l.to}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
          <nav aria-labelledby="ft-company" className="lp-footer-nav" style={o(2)}>
            <p id="ft-company" className="lp-footer-h">
              Company
            </p>
            <ul>
              {PAGES.map((l) => (
                <li key={l.to}>
                  <Link to={l.to}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
          <address className="lp-footer-meta" style={o(3)}>
            <p>
              {COMPANY.product} is a product of {COMPANY.name}.
            </p>
            <p>{COMPANY.address}</p>
            <p>Trade licence {COMPANY.tradeLicense}</p>
            <PauseButton className="lp-footer-pause" label />
          </address>
        </div>
      </div>
    </footer>
  );
}
