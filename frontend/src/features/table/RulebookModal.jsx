import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BookOpen, X, Award, Eye, Layers } from 'lucide-react';

const HAND_RANKINGS = [
  { rank: '1. Trail / Trio', desc: 'Three cards of the same rank (e.g. A-A-A, K-K-K).' },
  { rank: '2. Pure Sequence', desc: 'Three consecutive cards of the same suit (e.g. A-K-Q Hearts).' },
  { rank: '3. Sequence / Straight', desc: 'Three consecutive cards not all in the same suit.' },
  { rank: '4. Color / Flush', desc: 'Three cards of the same suit that are not in sequence.' },
  { rank: '5. Pair', desc: 'Two cards of the same rank (e.g. K-K-5).' },
  { rank: '6. High Card', desc: 'No combination; highest single card wins.' },
];

export default function RulebookModal({ isOpen, onClose }) {
  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="tp-modal-backdrop fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 16, scale: 0.98 }}
          className="tp-modal-panel relative w-full sm:max-w-xl max-h-[92dvh] overflow-y-auto rounded-t-3xl sm:rounded-2xl p-5 sm:p-6 pb-[max(1.25rem,env(safe-area-inset-bottom))]"
        >
          <button
            type="button"
            onClick={onClose}
            className="absolute top-4 right-4 p-2 text-[#f5e6a8]/70 hover:text-[#f5e6a8] bg-black/30 rounded-full cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex items-center gap-3 mb-5 pr-10">
            <div className="w-10 h-10 rounded-2xl border border-[#d4af37]/50 bg-black/35 flex items-center justify-center text-[#d4af37]">
              <BookOpen className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-display text-lg sm:text-xl font-extrabold text-[#f5e6a8]">Teen Patti Rulebook</h2>
              <p className="text-[11px] sm:text-xs text-[#f5e6a8]/60">Hand rankings, betting, and show rules.</p>
            </div>
          </div>

          <div className="mb-6">
            <h3 className="text-xs sm:text-sm font-bold text-[#d4af37] uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Award className="w-4 h-4" /> Hand Rankings
            </h3>
            <div className="grid gap-2">
              {HAND_RANKINGS.map((item, idx) => (
                <div key={idx} className="p-3 bg-black/35 border border-[#d4af37]/20 rounded-xl">
                  <span className="font-bold text-xs text-[#f5e6a8] block">{item.rank}</span>
                  <span className="text-[11px] text-[#f5e6a8]/65">{item.desc}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="mb-6">
            <h3 className="text-xs sm:text-sm font-bold text-[#d4af37] uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Eye className="w-4 h-4" /> Blind vs Seen
            </h3>
            <div className="p-3 bg-black/35 border border-[#d4af37]/20 rounded-xl space-y-1.5 text-xs text-[#f5e6a8]/80">
              <p>• <strong className="text-[#f5e6a8]">Blind:</strong> Bets 1x without seeing cards.</p>
              <p>• <strong className="text-[#f5e6a8]">Seen:</strong> Must bet 2x after viewing cards.</p>
              <p>• <strong className="text-[#f5e6a8]">Chaal:</strong> Matches current bet to continue.</p>
              <p>• <strong className="text-[#f5e6a8]">Raise:</strong> Doubles the base stake for all players.</p>
            </div>
          </div>

          <div>
            <h3 className="text-xs sm:text-sm font-bold text-[#d4af37] uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Layers className="w-4 h-4" /> Side Show & Show
            </h3>
            <div className="p-3 bg-black/35 border border-[#d4af37]/20 rounded-xl space-y-1.5 text-xs text-[#f5e6a8]/80">
              <p>• <strong className="text-[#f5e6a8]">Side Show:</strong> Between Seen players when 3+ remain. Opponent may accept or reject.</p>
              <p>• <strong className="text-[#f5e6a8]">Show:</strong> When only 2 remain. Highest hand wins the pot.</p>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
