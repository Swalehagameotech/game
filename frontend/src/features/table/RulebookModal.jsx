import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BookOpen, X, Award, Shield, Eye, Flame, Layers } from 'lucide-react';

const HAND_RANKINGS = [
  { rank: '1. Trail / Trio', desc: 'Three cards of the same rank (e.g. A-A-A, K-K-K).' },
  { rank: '2. Pure Sequence', desc: 'Three consecutive cards of the same suit (e.g. A-K-Q Hearts, 4-3-2 Spades).' },
  { rank: '3. Sequence / Straight', desc: 'Three consecutive cards not all in the same suit (e.g. A-K-Q mixed).' },
  { rank: '4. Color / Flush', desc: 'Three cards of the same suit that are not in sequence.' },
  { rank: '5. Pair', desc: 'Two cards of the same rank (e.g. K-K-5).' },
  { rank: '6. High Card', desc: 'Hand where no combination is formed; highest single card wins.' },
];

export default function RulebookModal({ isOpen, onClose }) {
  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/85 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.9, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: 20 }}
          className="w-full max-w-xl bg-slate-900 border border-amber-500/30 rounded-3xl p-6 shadow-2xl relative max-h-[85vh] overflow-y-auto"
        >
          {/* Close Button */}
          <button
            onClick={onClose}
            className="absolute top-5 right-5 p-2 text-slate-400 hover:text-slate-200 bg-slate-800 rounded-full transition"
          >
            <X className="w-5 h-5" />
          </button>

          {/* Header */}
          <div className="flex items-center gap-3 mb-5">
            <div className="w-10 h-10 rounded-2xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-amber-400">
              <BookOpen className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-slate-100">Teen Patti Rulebook</h2>
              <p className="text-xs text-slate-400">Official hand rankings, betting rules, and side show guidelines.</p>
            </div>
          </div>

          {/* Hand Rankings */}
          <div className="mb-6">
            <h3 className="text-sm font-bold text-amber-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Award className="w-4 h-4" /> 1. Hand Rankings (Highest to Lowest)
            </h3>
            <div className="grid gap-2">
              {HAND_RANKINGS.map((item, idx) => (
                <div key={idx} className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl">
                  <span className="font-bold text-xs text-amber-300 block">{item.rank}</span>
                  <span className="text-[11px] text-slate-400">{item.desc}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Blind vs Seen Rules */}
          <div className="mb-6">
            <h3 className="text-sm font-bold text-amber-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Eye className="w-4 h-4" /> 2. Blind vs Seen Betting
            </h3>
            <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-1.5 text-xs text-slate-300">
              <p>• <strong>Blind Player:</strong> Bets 1x current stake without seeing cards.</p>
              <p>• <strong>Seen Player:</strong> Must bet 2x current stake after viewing cards.</p>
              <p>• <strong>Chaal:</strong> Matches current bet to continue.</p>
              <p>• <strong>Raise:</strong> Doubles the current base stake unit for all players.</p>
            </div>
          </div>

          {/* Side Show & Show Rules */}
          <div>
            <h3 className="text-sm font-bold text-amber-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Layers className="w-4 h-4" /> 3. Side Show & Show
            </h3>
            <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-xl space-y-1.5 text-xs text-slate-300">
              <p>• <strong>Side Show:</strong> Allowed only between active Seen players when 3+ players remain. The opponent can accept or reject.</p>
              <p>• <strong>Show:</strong> Allowed when only 2 active players remain. Cards are revealed and highest hand wins the pot.</p>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
