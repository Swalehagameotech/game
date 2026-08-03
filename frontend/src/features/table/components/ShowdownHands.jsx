import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import PlayingCard from './PlayingCard';

/**
 * After Show accept: both players' hands face-up in horizontal rows, visible to everyone.
 */
export default function ShowdownHands({
  show,
  handsByUserId = {},
  players = [],
  winnerUserId,
}) {
  const entries = Object.entries(handsByUserId || {})
    .filter(([, cards]) => Array.isArray(cards) && cards.length > 0)
    .map(([userId, cards]) => {
      const player = players.find((p) => String(p.userId) === String(userId));
      return {
        userId,
        cards,
        name: player?.displayName || `Player`,
        isWinner: winnerUserId && String(winnerUserId) === String(userId),
      };
    });

  if (!show || entries.length === 0) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, scale: 0.92, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96 }}
        className="absolute z-30 left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[min(92%,340px)] sm:w-[min(88%,420px)] pointer-events-none"
      >
        <div className="rounded-2xl border border-[#d4af37]/45 bg-black/75 backdrop-blur-md px-3 sm:px-4 py-2.5 sm:py-3 shadow-[0_8px_32px_rgba(0,0,0,0.55)]">
          <p className="text-center text-[8px] sm:text-[9px] font-bold uppercase tracking-[0.28em] text-[#d4af37]/90 mb-2">
            Showdown
          </p>
          <div className="flex flex-col gap-2.5 sm:gap-3">
            {entries.map((entry) => (
              <div
                key={entry.userId}
                className={`rounded-xl px-2 py-1.5 ${
                  entry.isWinner
                    ? 'bg-[#d4af37]/15 shadow-[inset_0_0_0_1px_rgba(212,175,55,0.45)]'
                    : 'bg-white/5'
                }`}
              >
                <div className="flex items-center justify-between mb-1 px-0.5">
                  <span className={`text-[10px] sm:text-[11px] font-bold truncate ${
                    entry.isWinner ? 'text-[#f5e6a8]' : 'text-white/85'
                  }`}
                  >
                    {entry.name}
                  </span>
                  {entry.isWinner && (
                    <span className="text-[8px] font-black uppercase tracking-wider text-[#d4af37]">
                      Winner
                    </span>
                  )}
                </div>
                {/* Horizontal row of face-up cards */}
                <div className="flex flex-row items-center justify-center gap-1.5 sm:gap-2">
                  {entry.cards.map((card, i) => (
                    <motion.div
                      key={`${entry.userId}-${i}`}
                      initial={{ opacity: 0, y: 10, rotateY: 90 }}
                      animate={{ opacity: 1, y: 0, rotateY: 0 }}
                      transition={{ delay: 0.08 * i, type: 'spring', stiffness: 260, damping: 22 }}
                    >
                      <PlayingCard
                        suit={card.suit}
                        rank={card.rank}
                        width={42}
                        height={58}
                      />
                    </motion.div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
