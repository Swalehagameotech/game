import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { POT_ORIGIN } from './seatLayout';

function ChipStack({ compact = false }) {
  const w = compact ? 48 : 72;
  const h = compact ? 28 : 40;
  return (
    <svg width={w} height={h} viewBox="0 0 90 50" aria-hidden className="sm:hidden">
      <ellipse cx="22" cy="38" rx="14" ry="6" fill="#8b1a1a" />
      <rect x="8" y="18" width="28" height="20" fill="#c41e3a" />
      <ellipse cx="22" cy="18" rx="14" ry="6" fill="#e85a5a" />
      <ellipse cx="22" cy="18" rx="9" ry="3.5" fill="none" stroke="#fff" strokeWidth="1.2" strokeDasharray="2 2" />
      <ellipse cx="45" cy="40" rx="13" ry="5.5" fill="#9ca3af" />
      <rect x="32" y="22" width="26" height="18" fill="#f3f4f6" />
      <ellipse cx="45" cy="22" rx="13" ry="5.5" fill="#fff" />
      <ellipse cx="45" cy="22" rx="8" ry="3" fill="none" stroke="#dc2626" strokeWidth="1.1" strokeDasharray="2 1.5" />
      <ellipse cx="68" cy="38" rx="13" ry="5.5" fill="#1f2937" />
      <rect x="55" y="16" width="26" height="22" fill="#111827" />
      <ellipse cx="68" cy="16" rx="13" ry="5.5" fill="#374151" />
      <ellipse cx="68" cy="16" rx="8" ry="3" fill="none" stroke="#d4af37" strokeWidth="1.2" strokeDasharray="2 1.5" />
    </svg>
  );
}

function ChipStackDesktop() {
  return (
    <svg width="72" height="40" viewBox="0 0 90 50" aria-hidden className="hidden sm:block">
      <ellipse cx="22" cy="38" rx="14" ry="6" fill="#8b1a1a" />
      <rect x="8" y="18" width="28" height="20" fill="#c41e3a" />
      <ellipse cx="22" cy="18" rx="14" ry="6" fill="#e85a5a" />
      <ellipse cx="22" cy="18" rx="9" ry="3.5" fill="none" stroke="#fff" strokeWidth="1.2" strokeDasharray="2 2" />
      <ellipse cx="45" cy="40" rx="13" ry="5.5" fill="#9ca3af" />
      <rect x="32" y="22" width="26" height="18" fill="#f3f4f6" />
      <ellipse cx="45" cy="22" rx="13" ry="5.5" fill="#fff" />
      <ellipse cx="45" cy="22" rx="8" ry="3" fill="none" stroke="#dc2626" strokeWidth="1.1" strokeDasharray="2 1.5" />
      <ellipse cx="68" cy="38" rx="13" ry="5.5" fill="#1f2937" />
      <rect x="55" y="16" width="26" height="22" fill="#111827" />
      <ellipse cx="68" cy="16" rx="13" ry="5.5" fill="#374151" />
      <ellipse cx="68" cy="16" rx="8" ry="3" fill="none" stroke="#d4af37" strokeWidth="1.2" strokeDasharray="2 1.5" />
    </svg>
  );
}

function FlyChip({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32">
      <circle cx="16" cy="16" r="15" fill="#b45309" stroke="#fde68a" strokeWidth="2" />
      <circle cx="16" cy="16" r="10" fill="none" stroke="#fbbf24" strokeWidth="2" strokeDasharray="4 3" />
      <circle cx="16" cy="16" r="6" fill="#f59e0b" />
    </svg>
  );
}

export default function TablePot({
  potRupees,
  potPaise,
  fromSeatPosition,
  winnerPosition,
  showPayoutFly,
  hideLabel = false,
  potOrigin = POT_ORIGIN,
}) {
  const prevPot = useRef(potPaise);
  const [betFlies, setBetFlies] = useState([]);
  const [payoutFlies, setPayoutFlies] = useState([]);

  useEffect(() => {
    const prev = prevPot.current ?? 0;
    if (potPaise > prev && fromSeatPosition) {
      const id = `bet-${Date.now()}`;
      setBetFlies((f) => [...f, { id, from: fromSeatPosition }]);
      const t = setTimeout(() => setBetFlies((f) => f.filter((x) => x.id !== id)), 700);
      prevPot.current = potPaise;
      return () => clearTimeout(t);
    }
    prevPot.current = potPaise;
    return undefined;
  }, [potPaise, fromSeatPosition]);

  useEffect(() => {
    if (!showPayoutFly || !winnerPosition) {
      setPayoutFlies([]);
      return undefined;
    }
    const items = Array.from({ length: 8 }).map((_, i) => ({
      id: `pay-${i}-${Date.now()}`,
      delay: i * 0.05,
    }));
    setPayoutFlies(items);
    const t = setTimeout(() => setPayoutFlies([]), 1400);
    return () => clearTimeout(t);
  }, [showPayoutFly, winnerPosition]);

  const amount = Math.round(Number(potRupees || 0)).toLocaleString('en-IN');

  return (
    <>
      <div
        className={`absolute z-20 flex flex-col items-center justify-center -translate-x-1/2 -translate-y-1/2 scale-[0.72] sm:scale-100 origin-center transition-opacity ${
          hideLabel ? 'opacity-0 pointer-events-none' : 'opacity-100'
        }`}
        style={{ left: `${potOrigin.left}%`, top: `${potOrigin.top}%` }}
      >
        <ChipStack compact />
        <ChipStackDesktop />
        <motion.div
          layout
          className="mt-0.5 px-2.5 sm:px-4 py-0.5 sm:py-1.5 rounded-full bg-black/50 backdrop-blur-sm text-center min-w-[72px] sm:min-w-[120px] shadow-[0_0_0_1px_rgba(212,175,55,0.55),0_0_18px_rgba(212,175,55,0.25)]"
        >
          <div className="text-[7px] sm:text-[8px] uppercase font-bold tracking-[0.2em] sm:tracking-[0.25em] text-[#d4af37]">POT</div>
          <motion.div
            key={potPaise}
            initial={{ scale: 1.12 }}
            animate={{ scale: 1 }}
            className="text-sm sm:text-lg font-black text-white tabular-nums leading-tight"
          >
            ₹{amount}
          </motion.div>
        </motion.div>
      </div>

      <div className="absolute inset-0 pointer-events-none z-40 overflow-hidden">
        <AnimatePresence>
          {betFlies.map((f) => (
            <motion.div
              key={f.id}
              className="absolute -translate-x-1/2 -translate-y-1/2"
              initial={{ left: `${f.from.left}%`, top: `${f.from.top}%`, opacity: 1, scale: 1 }}
              animate={{
                left: `${potOrigin.left}%`,
                top: `${potOrigin.top}%`,
                opacity: [1, 1, 0],
                scale: 0.65,
                rotate: 160,
              }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
            >
              <FlyChip size={16} />
            </motion.div>
          ))}
        </AnimatePresence>

        <AnimatePresence>
          {payoutFlies.map((f) => (
            <motion.div
              key={f.id}
              className="absolute -translate-x-1/2 -translate-y-1/2"
              initial={{
                left: `${potOrigin.left}%`,
                top: `${potOrigin.top}%`,
                opacity: 1,
                scale: 0.7,
              }}
              animate={{
                left: `${winnerPosition?.left ?? potOrigin.left}%`,
                top: `${winnerPosition?.top ?? potOrigin.top}%`,
                opacity: [1, 1, 0],
                scale: 1,
              }}
              transition={{ duration: 0.7, delay: f.delay, ease: [0.22, 1, 0.36, 1] }}
            >
              <FlyChip size={14} />
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </>
  );
}
