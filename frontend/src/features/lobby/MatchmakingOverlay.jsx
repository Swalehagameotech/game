import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const MESSAGES = [
  'Finding players…',
  'Searching for available table…',
  'Connecting…',
  'Please wait…',
  'Matching boot & variant…',
];

/**
 * Full-screen matchmaking feel while Quick Play seats the player / fills bots.
 */
export default function MatchmakingOverlay({ active, variantLabel, bootLabel }) {
  const [msgIndex, setMsgIndex] = useState(0);

  useEffect(() => {
    if (!active) {
      setMsgIndex(0);
      return undefined;
    }
    const id = setInterval(() => {
      setMsgIndex((i) => (i + 1) % MESSAGES.length);
    }, 1800);
    return () => clearInterval(id);
  }, [active]);

  return (
    <AnimatePresence>
      {active && (
        <motion.div
          className="fixed inset-0 z-[80] flex items-center justify-center bg-[#0a0508]/88 backdrop-blur-md"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <div className="relative w-full max-w-sm mx-4 text-center">
            <div className="absolute inset-0 rounded-3xl bg-gradient-to-b from-[#d4af37]/15 to-transparent blur-2xl pointer-events-none" />
            <motion.div
              className="relative rounded-3xl border border-[#d4af37]/35 bg-black/70 px-8 py-10 shadow-[0_20px_60px_rgba(0,0,0,0.65)]"
              initial={{ scale: 0.94, y: 12 }}
              animate={{ scale: 1, y: 0 }}
            >
              <div className="mx-auto mb-6 h-14 w-14 rounded-full border-2 border-[#d4af37]/40 border-t-[#d4af37] animate-spin" />
              <p className="text-[#f5e6a8] text-lg font-black tracking-wide min-h-[1.75rem]">
                {MESSAGES[msgIndex]}
              </p>
              <p className="mt-3 text-xs text-slate-400">
                {(variantLabel || 'CLASSIC').replaceAll('_', ' ')}
                {bootLabel ? ` · ${bootLabel}` : ''}
              </p>
              <div className="mt-6 flex justify-center gap-1.5">
                {[0, 1, 2].map((i) => (
                  <motion.span
                    key={i}
                    className="h-1.5 w-1.5 rounded-full bg-[#d4af37]"
                    animate={{ opacity: [0.3, 1, 0.3], scale: [0.85, 1.15, 0.85] }}
                    transition={{ duration: 1.1, repeat: Infinity, delay: i * 0.2 }}
                  />
                ))}
              </div>
            </motion.div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
