import { Link } from '@tanstack/react-router';
import { Logo } from '@/components/layout/Logo';
import { ContinueStrip } from './ContinueStrip';
import { PauseButton, StartFree } from './wire';

/**
 * Fixed header: skip link, the ink logo, jump links to this page's essay and examiner acts (named so they differ from the
 * /writing and /speaking links in the question bank; 1024px and up) and Pricing (dropped under 480px, where Sign in
 * stays), the pause switch (640px and up; the footer has one too), Sign in, and the essay pill with the hero button's own
 * label ("Try free" under 480px, with the rest of the name read aloud). A guest's ContinueStrip is a second row, there
 * from the first paint.
 */
export function LandingHeader() {
  return (
    <header className="lp-header">
      <a href="#main" className="lp-skip">
        Skip to content
      </a>
      <div className="lp-container lp-header-bar">
        <Link to="/" aria-label="NextBand, home" className="lp-header-logo">
          <Logo />
        </Link>
        <nav aria-label="Main" className="lp-header-nav">
          <a href="#essay" className="lp-header-link lp-header-link--jump">
            Marked essays
          </a>
          <a href="#examiner" className="lp-header-link lp-header-link--jump">
            AI examiner
          </a>
          <a href="#pricing" className="lp-header-link lp-header-link--wide">
            Pricing
          </a>
          <Link to="/login" className="lp-header-link">
            Sign in
          </Link>
          <PauseButton className="lp-header-pause" />
          <StartFree size="sm">
            <span className="lp-btn-full">Take a full mock free</span>
            <span className="lp-btn-short">
              Try free<span className="sr-only">: a full mock test</span>
            </span>
          </StartFree>
        </nav>
      </div>
      <ContinueStrip />
    </header>
  );
}
