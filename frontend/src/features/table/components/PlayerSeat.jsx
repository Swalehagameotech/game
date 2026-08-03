import React from 'react';
import { motion } from 'framer-motion';
import { Crown } from 'lucide-react';
import PlayingCard from './PlayingCard';
import avatarBoy from '@/assets/avatars/boy-ai.png';
import avatarGirl from '@/assets/avatars/girl-ai.png';

function MiniChip({ size = 11 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="11" fill="#d4af37" stroke="#f5e6a8" strokeWidth="1.5" />
      <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.2" strokeDasharray="2 1.5" />
    </svg>
  );
}

/** Health-bar ring: full gold at start, shrinks as time runs out, color fades to gray. No gray track. */
function TurnRing({ progress, active }) {
  const r = 45;
  const c = 2 * Math.PI * r;
  const p = Math.min(1, Math.max(0, Number(progress) || 0));
  const color = `rgb(${Math.round(212 + (107 - 212) * (1 - p))}, ${Math.round(175 + (114 - 175) * (1 - p))}, ${Math.round(55 + (128 - 55) * (1 - p))})`;

  if (!active) {
    return (
      <svg
        className="absolute inset-[-4px] w-[calc(100%+8px)] h-[calc(100%+8px)] pointer-events-none"
        viewBox="0 0 100 100"
        aria-hidden
      >
        <circle cx="50" cy="50" r={r} fill="none" stroke="#d4af37" strokeWidth="5" />
      </svg>
    );
  }

  return (
    <svg
      className="absolute inset-[-4px] w-[calc(100%+8px)] h-[calc(100%+8px)] pointer-events-none"
      viewBox="0 0 100 100"
      aria-hidden
    >
      <circle
        cx="50"
        cy="50"
        r={r}
        fill="none"
        strokeWidth="6"
        strokeLinecap="round"
        strokeDasharray={c}
        strokeDashoffset={c * (1 - p)}
        transform="rotate(-90 50 50)"
        style={{
          stroke: color,
          transition: 'stroke-dashoffset 0.25s linear, stroke 0.25s linear',
        }}
      />
    </svg>
  );
}

/** Stable boy/girl pick from userId. Prefer real avatarUrl when present. */
function resolveAvatarSrc(player) {
  if (player?.avatarUrl) return player.avatarUrl;
  const key = String(player?.userId || player?.displayName || 'x');
  let hash = 0;
  for (let i = 0; i < key.length; i += 1) hash = (hash + key.charCodeAt(i) * (i + 1)) % 2;
  return hash === 0 ? avatarBoy : avatarGirl;
}

const STATUS = {
  BLIND: 'bg-[#6b2d9b] text-white',
  SEEN: 'bg-[#1a7a45] text-white',
  PACKED: 'bg-[#8b1a28] text-white',
};

const CARD_W = 50;
const CARD_H = 70;
const CARD_GAP = 14;
const CARD_BEHIND = Math.round(CARD_H * 0.28);

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
  const size = isMe ? 36 : 30;
  const avatarSrc = resolveAvatarSrc(player);

  const badgeLabel = isTurn && turnSeconds >= 0
    ? String(Math.max(0, turnSeconds)).padStart(2, '0')
    : String((seatIndex % 99) + 1).padStart(2, '0');

  const faceCards = cards.length > 0;
  const backs = !faceCards && showCardBacks;
  const nCards = faceCards ? cards.length : (backs ? (cardCount || 3) : 0);

  const statusBadge = showStatus ? (
    <span
      className={`mt-1 px-2 py-0.5 rounded-full text-[8px] font-extrabold uppercase tracking-wide ${
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
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.7 }}
      transition={{ type: 'spring', stiffness: 360, damping: 28 }}
      className="relative flex flex-col items-center select-none"
      style={{ width: isMe ? Math.max(CARD_W + CARD_GAP * 2 + 70, 140) : Math.max(CARD_W + CARD_GAP * 2, 100) }}
    >
      <div className="relative flex items-center">
        <div
          className="relative"
          style={{
            width: size,
            height: size + (nCards > 0 ? CARD_H - CARD_BEHIND : 0),
          }}
        >
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

          <div
            className="absolute left-0 z-10"
            style={{
              top: nCards > 0 ? CARD_H - CARD_BEHIND : 0,
              width: size,
              height: size,
            }}
          >
            <TurnRing progress={turnProgress} active={Boolean(isTurn)} />
            <div
              className="w-full h-full rounded-full overflow-hidden bg-[#1a1a22]"
              style={{ boxShadow: '0 4px 12px rgba(0,0,0,0.5)' }}
            >
              <img
                src={avatarSrc}
                alt=""
                className="w-full h-full object-cover"
                draggable={false}
              />
            </div>

            <span
              className={`absolute -bottom-0.5 -right-0.5 z-20 min-w-[16px] h-[16px] px-0.5 rounded-full bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[7px] font-black text-black flex items-center justify-center tabular-nums shadow ${
                isTurn ? 'ring-2 ring-[#f5d76e]' : ''
              }`}
            >
              {badgeLabel}
            </span>
          </div>
        </div>

        {isMe && balanceLabel && balanceLabel !== '₹—' && (
          <div
            className="ml-2 px-2.5 py-1.5 rounded-md bg-black/70 backdrop-blur-sm shadow-[0_0_0_1px_rgba(212,175,55,0.5)] whitespace-nowrap"
            style={{ marginTop: nCards > 0 ? CARD_H - CARD_BEHIND + size * 0.25 : size * 0.25 }}
          >
            <p className="flex items-center justify-center gap-1 text-[10px] font-semibold text-white leading-none">
              <MiniChip size={10} />
              {balanceLabel}
            </p>
          </div>
        )}
      </div>

      {!isMe && balanceLabel && balanceLabel !== '₹—' && (
        <p className="mt-1 flex items-center justify-center gap-1 text-[9px] font-semibold text-white drop-shadow">
          <MiniChip size={10} />
          {balanceLabel}
        </p>
      )}

      {statusBadge}
      {metaBadges}
    </motion.div>
  );
}
