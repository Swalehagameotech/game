import { useEffect } from 'react';

/**
 * Best-effort landscape lock (Android / installed PWA).
 * iOS Safari usually ignores this — CSS portrait-rotate covers that case.
 */
export default function useLandscapeLock() {
  useEffect(() => {
    const lock = async () => {
      try {
        const orientation = screen?.orientation;
        if (orientation && typeof orientation.lock === 'function') {
          await orientation.lock('landscape');
        }
      } catch {
        // Browser may require fullscreen / user gesture — ignore.
      }
    };

    lock();

    const onInteract = () => {
      lock();
    };

    window.addEventListener('pointerdown', onInteract, { once: true, passive: true });
    window.addEventListener('orientationchange', lock);

    return () => {
      window.removeEventListener('pointerdown', onInteract);
      window.removeEventListener('orientationchange', lock);
      try {
        screen?.orientation?.unlock?.();
      } catch {
        // ignore
      }
    };
  }, []);
}
