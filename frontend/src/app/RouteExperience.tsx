import { useLayoutEffect, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

export function RouteExperience() {
  const location = useLocation();
  const navigation = useNavigationType();
  const positions = useRef(new Map<string, number>());
  const urls = useRef(new Map<string, number>());
  const previousPath = useRef('');
  useLayoutEffect(() => {
    const historyPositions = positions.current, urlPositions = urls.current;
    const url = location.pathname + location.search;
    if (previousPath.current !== location.pathname) {
      const saved = navigation === 'POP' ? historyPositions.get(location.key) : location.state?.restoreScroll ? urlPositions.get(url) : 0;
      window.scrollTo({ top: saved ?? 0, behavior: 'instant' });
      const main = document.querySelector<HTMLElement>('.og-main-viewport, .og-seller-content, .og-admin-content');
      const frame = window.requestAnimationFrame(() => {
        if (navigation !== 'POP') {
          const heading = main?.querySelector<HTMLElement>('h1');
          if (heading) { heading.tabIndex = -1; heading.focus({ preventScroll: true }); }
        }
        if (main && typeof main.animate === 'function' && !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) main.animate([{ opacity: .65, transform: 'translateY(5px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 180, easing: 'ease-out' });
      });
      previousPath.current = location.pathname;
      return () => { window.cancelAnimationFrame(frame); historyPositions.set(location.key, window.scrollY); urlPositions.set(url, window.scrollY); };
    }
    return () => { historyPositions.set(location.key, window.scrollY); urlPositions.set(url, window.scrollY); };
  }, [location.key, location.pathname, location.search, location.state, navigation]);
  return null;
}
