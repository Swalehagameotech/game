import React, { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

const WINNER_PAUSE_SECONDS = 5;
const COIN_COUNT = 36;

function FallingCoins() {
  return (
    <div
      className="fixed inset-0 z-[39] pointer-events-none overflow-hidden"
      aria-hidden
    >
      {[...Array(COIN_COUNT)].map((_, i) => {
        const left = ((i * 29) % 97) + 1;
        const delay = (i % 12) * 0.1;
        const duration = 2.2 + (i % 7) * 0.3;
        const size = 11 + (i % 5) * 3;
        return (
          <motion.span
            key={i}
            className="absolute rounded-full"
            style={{
              left: `${left}%`,
              width: size,
              height: size,
              background:
                'radial-gradient(circle at 30% 28%, #fff6c8 0%, #f5e6a8 28%, #d4af37 62%, #8a6a12 100%)',
              boxShadow: '0 0 10px rgba(212,175,55,0.6), inset 0 1px 0 rgba(255,255,255,0.5)',
              border: '1px solid rgba(166,124,0,0.55)',
            }}
            initial={{ top: '-10%', opacity: 0, rotate: 0 }}
            animate={{
              top: '112%',
              opacity: [0, 1, 1, 0.9, 0],
              rotate: 420 + i * 30,
              x: [0, (i % 2 === 0 ? 22 : -22), (i % 3 === 0 ? -10 : 10)],
            }}
            transition={{
              duration,
              delay,
              repeat: Infinity,
              ease: 'linear',
            }}
          />
        );
      })}
    </div>
  );
}

/**
 * Winner celebration overlay.
 * - Winner: golden coins raining from top + simple "You Won" box with payout
 * - Others: no coins — "{name} won" + "countdown will start in 5s", then next-round timer
 */
export default function WinnerEffects({
  show,
  winnerDisplayName,
  winnerPayoutRupees,
  winningCategoryLabel,
  countdownSeconds,
  status,
  isSelfWinner,
}) {
  const [pauseSeconds, setPauseSeconds] = useState(WINNER_PAUSE_SECONDS);

  useEffect(() => {
    if (!show || status !== 'ROUND_END') {
      return undefined;
    }
    setPauseSeconds(WINNER_PAUSE_SECONDS);
    const id = setInterval(() => {
      setPauseSeconds((s) => Math.max(0, s - 1));
    }, 1000);
    return () => clearInterval(id);
  }, [show, status, winnerDisplayName]);

  const nextRoundActive = countdownSeconds > 0;
  const payout = Number(winnerPayoutRupees || 0);

  return (
    <AnimatePresence>
      {show && (
        <>
          {isSelfWinner && <FallingCoins />}

          <motion.div
            initial={{ opacity: 0, y: 18, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
            transition={{ type: 'spring', stiffness: 280, damping: 24 }}
            className="relative z-40 mx-auto w-full max-w-sm pointer-events-none"
          >
            <div className="rounded-2xl border border-[#d4af37]/40 bg-black/80 backdrop-blur-md px-6 py-7 text-center shadow-[0_10px_40px_rgba(0,0,0,0.6)]">
              {isSelfWinner ? (
                <>
                  <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-[#d4af37]/85 mb-2">
                    Congratulations
                  </p>
                  <h2 className="font-display text-3xl sm:text-4xl font-extrabold text-[#f5e6a8] tracking-wide">
                    You Won
                  </h2>
                  <p className="mt-3 text-2xl sm:text-3xl font-black text-white tabular-nums">
                    ₹{payout.toFixed(0)}
                  </p>
                  {winningCategoryLabel && (
                    <p className="mt-2 text-[11px] font-semibold uppercase tracking-wider text-white/50">
                      {winningCategoryLabel}
                    </p>
                  )}
                </>
              ) : (
                <>
                  <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-white/45 mb-2">
                    Round over
                  </p>
                  <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-wide">
                    {winnerDisplayName || 'Player'} won
                  </h2>
                  <p className="mt-2 text-lg font-bold text-[#f5e6a8] tabular-nums">
                    ₹{payout.toFixed(0)}
                  </p>
                  {winningCategoryLabel && (
                    <p className="mt-1.5 text-[11px] font-semibold uppercase tracking-wider text-white/40">
                      {winningCategoryLabel}
                    </p>
                  )}
                </>
              )}

              <div className="mt-5 pt-4 border-t border-white/10">
                {nextRoundActive ? (
                  <p className="text-sm font-bold text-emerald-300 tabular-nums">
                    Next round starts in {countdownSeconds}s
                  </p>
                ) : status === 'ROUND_END' ? (
                  <p className="text-sm text-white/65">
                    {pauseSeconds > 0
                      ? `Next round countdown will start in ${pauseSeconds}s`
                      : 'Starting countdown…'}
                  </p>
                ) : null}
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
