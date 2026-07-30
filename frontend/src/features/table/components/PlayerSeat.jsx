import React from 'react';
import { motion } from 'framer-motion';
import { Crown } from 'lucide-react';
import PlayingCard from './PlayingCard';

function MiniChip({ size = 11 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="11" fill="#d4af37" stroke="#f5e6a8" strokeWidth="1.5" />
      <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.2" strokeDasharray="2 1.5" />
    </svg>
  );
}

/** Turn timer: full gold at start → fades to gray as seconds run out */
function TurnTimer({ progress, active }) {
  if (!active) return null;
  const r = 38;
  const c = 2 * Math.PI * r;
  const p = Math.min(1, Math.max(0, progress));
  const offset = c * (1 - p);

  // Gold → gray as time depletes (easy to see whose turn + how much left)
  const rC = Math.round(212 + (148 - 212) * (1 - p)); // 212→148
  const gC = Math.round(175 + (163 - 175) * (1 - p)); // 175→163
  const bC = Math.round(55 + (184 - 55) * (1 - p));   // 55→184
  const color = p > 0.08
    ? `rgb(${rC}, ${gC}, ${bC})`
    : '#94a3b8';
  const glow = p > 0.35
    ? `drop-shadow(0 0 8px rgba(212,175,55,${0.35 + p * 0.5}))`
    : 'drop-shadow(0 0 3px rgba(148,163,184,0.45))';

  return (
    <svg
      className="absolute inset-[-6px] w-[calc(100%+12px)] h-[calc(100%+12px)] pointer-events-none"
      viewBox="0 0 88 88"
    >
      {/* Gray track — always visible so turn is obvious */}
      <circle cx="44" cy="44" r={r} fill="none" stroke="rgba(148,163,184,0.45)" strokeWidth="5" />
      <circle
        cx="44"
        cy="44"
        r={r}
        fill="none"
        stroke={color}
        strokeWidth="5"
        strokeLinecap="round"
        strokeDasharray={c}
        strokeDashoffset={offset}
        transform="rotate(-90 44 44)"
        style={{
          filter: glow,
          transition: 'stroke-dashoffset 0.35s linear, stroke 0.35s ease',
        }}
      />
    </svg>
  );
}

function initials(name) {
  if (!name) return '?';
  const parts = String(name).trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

const STATUS = {
  BLIND: 'bg-[#6b2d9b] text-white',
  SEEN: 'bg-[#1a7a45] text-white',
  PACKED: 'bg-[#8b1a28] text-white',
};

const CARD_W = 44;
const CARD_H = 62;
const CARD_GAP = 16;
/** ~30% of card height tucked behind top of avatar */
const CARD_BEHIND = Math.round(CARD_H * 0.3);

/**
 * Cards ABOVE avatar (bottom 30% tucked behind).
 * Opponent names under avatar. Current user name lives in top bar beside Rules.
 */
export default function PlayerSeat({
  player,
  seatIndex = 0,
  isMe,
  isTurn,
  isDealer,
  isHost,
  isWinner,
  isDisconnected,
  turnProgress = 1,
  turnSeconds = 0,
  balanceLabel,
  cards = [],
  showCardBacks = false,
  cardCount = 3,
  showStatus = false,
}) {
  const status = player?.status || 'BLIND';
  const displayName = player?.displayName || 'Player';
  const size = isMe ? 72 : 62;

  const badgeLabel = isTurn && turnSeconds >= 0
    ? String(Math.max(0, turnSeconds)).padStart(2, '0')
    : String((seatIndex % 99) + 1).padStart(2, '0');

  const faceCards = cards.length > 0;
  const backs = !faceCards && showCardBacks;
  const nCards = faceCards ? cards.length : (backs ? (cardCount || 3) : 0);

  const statusBadge = showStatus ? (
    <span
      className={`mt-1 px-2.5 py-0.5 rounded-full text-[9px] font-extrabold uppercase tracking-wide ${
        STATUS[status] || STATUS.BLIND
      }`}
    >
      {status}
    </span>
  ) : null;

  const metaBadges = (isDealer || isWinner) ? (
    <div className="mt-1 flex items-center gap-1 justify-center">
      {isDealer && (
        <span className="px-1 py-0.5 rounded text-[8px] font-black bg-[#d4af37] text-black">D</span>
      )}
      {isWinner && (
        <span className="inline-flex items-center gap-0.5 px-1 py-0.5 rounded text-[8px] font-black bg-amber-400 text-black">
          <Crown className="w-2.5 h-2.5" /> WIN
        </span>
      )}
    </div>
  ) : null;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.85 }}
      animate={{
        opacity: 1,
        scale: isTurn ? [1, 1.06, 1] : 1,
      }}
      exit={{ opacity: 0, scale: 0.7 }}
      transition={
        isTurn
          ? { scale: { duration: 1.1, repeat: Infinity, ease: 'easeInOut' } }
          : { type: 'spring', stiffness: 360, damping: 28 }
      }
      className="relative flex flex-col items-center select-none"
      style={{ width: isMe ? Math.max(size + 90, 160) : 120 }}
    >
      {/* Whose turn callout */}
      {isTurn && (
        <motion.div
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          className={`absolute -top-5 left-1/2 -translate-x-1/2 z-30 px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider whitespace-nowrap shadow-lg ${
            isMe
              ? 'bg-[#d4af37] text-black'
              : 'bg-black/80 text-[#f5e6a8] shadow-[0_0_0_1px_rgba(212,175,55,0.55)]'
          }`}
        >
          {isMe ? 'Your Turn' : 'Turn'}
        </motion.div>
      )}
      {/* Cards ABOVE avatar — bottom 30% tucked behind avatar */}
      <div className="relative flex items-center">
        <div
          className="relative"
          style={{
            width: size,
            height: size + (nCards > 0 ? CARD_H - CARD_BEHIND : 0),
          }}
        >
          {/* Cards sit above; bottom 30% goes behind avatar */}
          {nCards > 0 && (
            <div
              className="absolute left-1/2 top-0 z-[1] pointer-events-none"
              style={{
                width: CARD_W + CARD_GAP * 2,
                height: CARD_H,
                transform: 'translateX(-50%)',
              }}
            >
              {Array.from({ length: nCards }).map((_, i) => (
                <div
                  key={i}
                  className="absolute left-1/2 top-0"
                  style={{
                    transform: `translateX(calc(-50% + ${(i - 1) * CARD_GAP}px)) rotate(${(i - 1) * 9}deg)`,
                    zIndex: i + 1,
                  }}
                >
                  {faceCards ? (
                    <PlayingCard suit={cards[i].suit} rank={cards[i].rank} width={CARD_W} height={CARD_H} />
                  ) : (
                    <PlayingCard faceDown width={CARD_W} height={CARD_H} />
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Avatar below cards, overlapping bottom 30% */}
          <div
            className="absolute left-0 z-10"
            style={{
              top: nCards > 0 ? CARD_H - CARD_BEHIND : 0,
              width: size,
              height: size,
            }}
          >
            <TurnTimer progress={turnProgress} active={Boolean(isTurn)} />
            {/* Pulsing outer glow when it's this player's turn */}
            {isTurn && (
              <motion.span
                className="absolute inset-[-10px] rounded-full pointer-events-none"
                style={{
                  boxShadow: isMe
                    ? '0 0 0 3px rgba(212,175,55,0.85), 0 0 28px rgba(245,215,110,0.9)'
                    : '0 0 0 2px rgba(212,175,55,0.7), 0 0 20px rgba(245,215,110,0.65)',
                }}
                animate={{ opacity: [0.45, 1, 0.45], scale: [0.96, 1.04, 0.96] }}
                transition={{ duration: 1.2, repeat: Infinity, ease: 'easeInOut' }}
              />
            )}
            <div
              className="w-full h-full rounded-full overflow-hidden flex items-center justify-center bg-gradient-to-br from-[#3a3a48] to-[#0c0c12]"
              style={{
                border: isTurn
                  ? `3.5px solid ${turnProgress > 0.35 ? '#d4af37' : turnProgress > 0.08 ? '#a8b0bc' : '#94a3b8'}`
                  : '3.5px solid #d4af37',
                boxShadow: isTurn
                  ? (turnProgress > 0.35
                    ? '0 0 0 1px #f5e6a8, 0 0 28px rgba(245,215,110,0.95)'
                    : '0 0 0 1px #94a3b8, 0 0 12px rgba(148,163,184,0.45)')
                  : '0 0 0 1px rgba(245,230,168,0.35), 0 6px 16px rgba(0,0,0,0.5)',
                transition: 'border-color 0.35s ease, box-shadow 0.35s ease',
              }}
            >
              <span className="text-sm font-black text-[#f5e6a8]">
                {initials(displayName)}
              </span>
            </div>

            <span
              className={`absolute -bottom-0.5 -right-0.5 z-20 min-w-[22px] h-[22px] px-0.5 rounded-full bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[9px] font-black text-black flex items-center justify-center tabular-nums shadow ${
                isTurn ? 'ring-2 ring-[#f5d76e]' : ''
              }`}
            >
              {badgeLabel}
            </span>
          </div>
        </div>

        {/* Balance box beside avatar for current player */}
        {isMe && balanceLabel && balanceLabel !== '₹—' && (
          <div
            className="ml-2.5 px-3 py-2 rounded-md bg-black/70 backdrop-blur-sm shadow-[0_0_0_1px_rgba(212,175,55,0.5)] whitespace-nowrap"
            style={{ marginTop: nCards > 0 ? CARD_H - CARD_BEHIND + size * 0.25 : size * 0.25 }}
          >
            <p className="flex items-center justify-center gap-1 text-[11px] font-semibold text-white leading-none">
              <MiniChip />
              {balanceLabel}
            </p>
          </div>
        )}
      </div>

      {/* Opponent name removed — current user name sits top-right beside Rules */}

      {!isMe && balanceLabel && balanceLabel !== '₹—' && (
        <p className="mt-1 flex items-center justify-center gap-1 text-[10px] font-semibold text-white drop-shadow">
          <MiniChip />
          {balanceLabel}
        </p>
      )}

      {statusBadge}
      {metaBadges}
    </motion.div>
  );
}
