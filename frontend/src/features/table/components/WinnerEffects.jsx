import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Award, Crown } from 'lucide-react';

/** Premium winner overlay banner + confetti (UI only). */
export default function WinnerEffects({
  show,
  winnerDisplayName,
  winnerPayoutRupees,
  winningCategoryLabel,
  countdownSeconds,
  status,
  isSelfWinner,
  participants,
}) {
  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0, scale: 0.85, y: 24 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: -12 }}
          transition={{ type: 'spring', stiffness: 260, damping: 22 }}
          className="relative z-40 mb-4 mx-auto w-full max-w-3xl overflow-hidden rounded-3xl border-2 border-amber-400/60 bg-gradient-to-b from-amber-500/25 via-slate-950/95 to-slate-950 shadow-[0_0_60px_rgba(245,158,11,0.35)] backdrop-blur-xl"
        >
          {/* Rays */}
          <motion.div
            className="absolute inset-0 pointer-events-none opacity-40"
            style={{
              background:
                'repeating-conic-gradient(from 0deg at 50% 40%, rgba(251,191,36,0.35) 0deg 8deg, transparent 8deg 24deg)',
            }}
            animate={{ rotate: 360 }}
            transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
          />

          {/* Confetti */}
          <div className="absolute inset-0 pointer-events-none overflow-hidden">
            {[...Array(24)].map((_, i) => (
              <motion.span
                key={i}
                className="absolute w-2 h-2 rounded-sm"
                style={{
                  left: `${(i * 17) % 100}%`,
                  background: i % 3 === 0 ? '#fbbf24' : i % 3 === 1 ? '#f43f5e' : '#34d399',
                }}
                initial={{ top: '-5%', rotate: 0, opacity: 1 }}
                animate={{ top: '110%', rotate: 360 + i * 40, opacity: [1, 1, 0] }}
                transition={{ duration: 2.2 + (i % 5) * 0.2, repeat: Infinity, delay: i * 0.08 }}
              />
            ))}
          </div>

          <div className="relative px-6 py-8 sm:px-10 sm:py-10 text-center">
            <div className="flex items-center justify-center gap-3 text-amber-300 mb-4">
              <Crown className="w-8 h-8 sm:w-10 sm:h-10 drop-shadow-[0_0_12px_rgba(251,191,36,0.8)]" />
              <span className="font-black text-sm sm:text-base uppercase tracking-[0.35em]">
                Winner
              </span>
              <Award className="w-8 h-8 sm:w-10 sm:h-10" />
            </div>

            <h2 className="text-4xl sm:text-6xl md:text-7xl font-black uppercase tracking-wide text-transparent bg-clip-text bg-gradient-to-b from-amber-200 via-yellow-300 to-amber-500 drop-shadow-[0_4px_24px_rgba(251,191,36,0.45)] leading-tight break-words">
              {winnerDisplayName || 'Winner'}
            </h2>

            <p className="mt-4 text-2xl sm:text-3xl font-black text-slate-50">
              Won ₹{Number(winnerPayoutRupees || 0).toFixed(2)}
            </p>
            <p className="mt-2 text-sm sm:text-base font-bold uppercase tracking-[0.2em] text-amber-300/90">
              {winningCategoryLabel}
            </p>

            {countdownSeconds > 0 && (status === 'NEXT_ROUND' || status === 'ROUND_END') && (
              <p className="mt-5 text-lg sm:text-xl font-black text-emerald-300 tabular-nums">
                Next round in {countdownSeconds}s
              </p>
            )}

            {isSelfWinner && (
              <p className="mt-3 text-sm text-emerald-300 font-semibold">
                Credited to your wallet
              </p>
            )}

            {participants?.length > 1 && (
              <div className="mt-5 flex flex-wrap justify-center gap-2">
                {participants.map((p) => (
                  <span
                    key={p.userId}
                    className={`px-3 py-1.5 rounded-xl text-xs font-bold border ${
                      p.winner
                        ? 'bg-amber-500/25 border-amber-400 text-amber-100'
                        : 'bg-slate-900/80 border-slate-700 text-slate-400'
                    }`}
                  >
                    {p.displayName}: {p.handDescription || p.handRank}
                  </span>
                ))}
              </div>
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
