import { useEffect } from 'react';

/**
 * Best-effort landscape lock while in a game room (Android / installed PWA).
 * Lobby stays free so phones can use vertical home.
 */
export default function useLandscapeLock(enabled = false) {
  useEffect(() => {
    if (!enabled) {
      try {
        screen?.orientation?.unlock?.();
      } catch {
        // ignore
      }
      return undefined;
    }

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
  }, [enabled]);
}
