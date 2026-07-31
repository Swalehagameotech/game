import React, { useId } from 'react';
import MiniPlayingCard from './MiniPlayingCard';

function ArtBox({ children }) {
  return (
    <div className="relative h-[56px] w-full max-w-[88px] mx-auto flex items-center justify-center">
      {children}
    </div>
  );
}

function Fan({ cards, faceDown = false }) {
  const w = cards.length > 3 ? 22 : 28;
  const h = cards.length > 3 ? 32 : 40;
  return (
    <div className="relative h-[52px] w-[82px] flex items-end justify-center">
      {cards.map((c, i) => {
        const rot = (i - (cards.length - 1) / 2) * 12;
        const x = (i - (cards.length - 1) / 2) * 11;
        return (
          <div
            key={`${c.rank}-${c.suit}-${i}`}
            className="absolute bottom-0 drop-shadow-md"
            style={{ transform: `translateX(${x}px) rotate(${rot}deg)`, zIndex: i + 1 }}
          >
            <MiniPlayingCard
              rank={c.rank}
              suit={c.suit}
              width={w}
              height={h}
              faceDown={faceDown || c.faceDown}
            />
          </div>
        );
      })}
    </div>
  );
}

/** Full colorful joker playing card */
function JokerCard({ width = 40, height = 56 }) {
  const uid = useId().replace(/:/g, '');
  return (
    <svg width={width} height={height} viewBox="0 0 60 84" aria-hidden className="drop-shadow-md">
      <defs>
        <linearGradient id={`jf-${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fffef8" />
          <stop offset="100%" stopColor="#f3e8d0" />
        </linearGradient>
        <linearGradient id={`jh-${uid}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#c41e3a" />
          <stop offset="50%" stopColor="#d4af37" />
          <stop offset="100%" stopColor="#1e5a9c" />
        </linearGradient>
      </defs>
      <rect x="1" y="1" width="58" height="82" rx="6" fill={`url(#jf-${uid})`} stroke="#d4af37" strokeWidth="1.8" />
      <text x="7" y="14" fill="#c41e3a" fontSize="7" fontWeight="800" fontFamily="Georgia, serif">J</text>
      <text x="53" y="78" fill="#1e5a9c" fontSize="7" fontWeight="800" fontFamily="Georgia, serif" textAnchor="end">J</text>
      {/* Hat */}
      <ellipse cx="30" cy="22" rx="14" ry="5" fill="#1a0505" />
      <path d="M18 22 Q30 8 42 22" fill={`url(#jh-${uid})`} stroke="#1a0505" strokeWidth="0.8" />
      <circle cx="22" cy="14" r="2.2" fill="#c41e3a" />
      <circle cx="30" cy="11" r="2.2" fill="#d4af37" />
      <circle cx="38" cy="14" r="2.2" fill="#1e5a9c" />
      {/* Face */}
      <ellipse cx="30" cy="36" rx="11" ry="12" fill="#f5d0a9" stroke="#c9a07a" strokeWidth="0.6" />
      <circle cx="25" cy="34" r="1.6" fill="#1a1208" />
      <circle cx="35" cy="34" r="1.6" fill="#1a1208" />
      <path d="M25 42 Q30 46 35 42" fill="none" stroke="#c41e3a" strokeWidth="1.4" strokeLinecap="round" />
      {/* Collar / diamonds */}
      <path d="M20 48 L30 55 L40 48 L35 62 L25 62 Z" fill="#c41e3a" opacity="0.9" />
      <path d="M24 55 L30 66 L36 55" fill="#1e5a9c" />
      <text x="30" y="76" textAnchor="middle" fill="#a67c00" fontSize="6" fontWeight="800" letterSpacing="0.5">JOKER</text>
    </svg>
  );
}

function AuctionHammer() {
  const uid = useId().replace(/:/g, '');
  return (
    <svg width="54" height="52" viewBox="0 0 54 52" aria-hidden>
      <defs>
        <linearGradient id={`wood-${uid}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#e8c37a" />
          <stop offset="50%" stopColor="#b8860b" />
          <stop offset="100%" stopColor="#6b4a12" />
        </linearGradient>
        <linearGradient id={`head-${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#f5e6a8" />
          <stop offset="100%" stopColor="#a67c00" />
        </linearGradient>
      </defs>
      {/* Sound block */}
      <ellipse cx="18" cy="44" rx="14" ry="4" fill="#5a3a12" opacity="0.55" />
      <rect x="6" y="38" width="24" height="7" rx="1.5" fill={`url(#wood-${uid})`} stroke="#6b4a12" strokeWidth="0.8" />
      {/* Handle */}
      <rect x="24" y="10" width="5" height="30" rx="2" transform="rotate(38 26.5 25)" fill={`url(#wood-${uid})`} stroke="#6b4a12" strokeWidth="0.6" />
      {/* Hammer head */}
      <g transform="rotate(38 34 14)">
        <rect x="24" y="6" width="22" height="12" rx="2.5" fill={`url(#head-${uid})`} stroke="#7a5a12" strokeWidth="1" />
        <rect x="26" y="8" width="5" height="8" rx="1" fill="#fff4c2" opacity="0.5" />
        <rect x="39" y="8" width="5" height="8" rx="1" fill="#8a6a12" opacity="0.45" />
      </g>
    </svg>
  );
}

function MuflisBars() {
  return (
    <svg width="52" height="48" viewBox="0 0 52 48" aria-hidden>
      <rect x="6" y="22" width="8" height="18" rx="1.5" fill="#d4af37" opacity="0.55" />
      <rect x="18" y="14" width="8" height="26" rx="1.5" fill="#d4af37" opacity="0.75" />
      <rect x="30" y="8" width="8" height="32" rx="1.5" fill="#d4af37" />
      <rect x="42" y="28" width="8" height="12" rx="1.5" fill="#2ecc71" />
      <path d="M46 26 L46 18 M42 22 L46 18 L50 22" fill="none" stroke="#2ecc71" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function GoldStar() {
  const uid = useId().replace(/:/g, '');
  return (
    <svg width="50" height="50" viewBox="0 0 48 48" aria-hidden>
      <defs>
        <linearGradient id={`star-${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fff8d6" />
          <stop offset="45%" stopColor="#f5e6a8" />
          <stop offset="100%" stopColor="#d4af37" />
        </linearGradient>
        <filter id={`glow-${uid}`}>
          <feGaussianBlur stdDeviation="1.2" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <path
        filter={`url(#glow-${uid})`}
        fill={`url(#star-${uid})`}
        stroke="#a67c00"
        strokeWidth="1"
        d="M24 3l5.5 13.2L43 18.2l-10 8.6L36 41 24 33.5 12 41l3-14.2-10-8.6 13.5-2z"
      />
    </svg>
  );
}

function DownArrow() {
  const uid = useId().replace(/:/g, '');
  return (
    <svg width="44" height="48" viewBox="0 0 44 48" aria-hidden>
      <defs>
        <linearGradient id={`arr-${uid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#6dffb0" />
          <stop offset="100%" stopColor="#0f8a45" />
        </linearGradient>
      </defs>
      <path d="M16 4 H28 V24 H38 L22 44 L6 24 H16 Z" fill={`url(#arr-${uid})`} stroke="#0a5c30" strokeWidth="1.2" />
    </svg>
  );
}

function HiddenJokerCard() {
  return (
    <div className="relative">
      <MiniPlayingCard faceDown width={36} height={50} />
      <span className="absolute inset-0 flex items-center justify-center text-2xl font-black text-white drop-shadow-[0_0_6px_rgba(0,0,0,0.8)]">
        ?
      </span>
    </div>
  );
}

function BustBadge() {
  return (
    <svg width="54" height="54" viewBox="0 0 54 54" aria-hidden>
      <circle cx="27" cy="27" r="24" fill="#8b1a1a" stroke="#d4af37" strokeWidth="2.5" />
      <circle cx="27" cy="27" r="18" fill="none" stroke="#f5e6a8" strokeWidth="1" strokeDasharray="3 2" />
      <text x="27" y="31" textAnchor="middle" fill="#f5e6a8" fontSize="11" fontWeight="900" letterSpacing="1">
        BUST
      </text>
    </svg>
  );
}

function RevolvingArrows() {
  const uid = useId().replace(/:/g, '');
  return (
    <svg width="50" height="50" viewBox="0 0 50 50" aria-hidden>
      <defs>
        <linearGradient id={`rev-${uid}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#fff4c2" />
          <stop offset="100%" stopColor="#d4af37" />
        </linearGradient>
      </defs>
      <path
        d="M38 16 A14 14 0 0 0 14 16"
        fill="none"
        stroke={`url(#rev-${uid})`}
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path d="M12 12 L14 18 L20 14" fill="#d4af37" />
      <path
        d="M12 34 A14 14 0 0 0 36 34"
        fill="none"
        stroke={`url(#rev-${uid})`}
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path d="M38 38 L36 32 L30 36" fill="#d4af37" />
      <circle cx="25" cy="25" r="5" fill="#1a0505" stroke="#d4af37" strokeWidth="1.5" />
      <text x="25" y="28" textAnchor="middle" fill="#d4af37" fontSize="7" fontWeight="800">J</text>
    </svg>
  );
}

function NumberGlow({ text, size = 26 }) {
  return (
    <div className="h-[52px] flex items-center justify-center">
      <span
        className="font-display font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-b from-[#fff8d6] via-[#f5e6a8] to-[#d4af37]"
        style={{
          fontSize: size,
          textShadow: '0 0 18px rgba(212,175,55,0.45)',
          WebkitTextStroke: '0.4px rgba(166,124,0,0.35)',
        }}
      >
        {text}
      </span>
    </div>
  );
}

function TwentyTwenty() {
  return (
    <div className="flex items-center gap-1.5 h-[52px]">
      <div className="w-9 h-9 rounded-lg border border-[#d4af37] bg-gradient-to-b from-[#3a2208] to-[#1a0c04] flex items-center justify-center shadow-[0_0_10px_rgba(212,175,55,0.25)]">
        <span className="font-display text-sm font-black text-[#f5e6a8]">20</span>
      </div>
      <div className="w-9 h-9 rounded-lg border border-[#d4af37] bg-gradient-to-b from-[#3a2208] to-[#1a0c04] flex items-center justify-center shadow-[0_0_10px_rgba(212,175,55,0.25)]">
        <span className="font-display text-sm font-black text-[#f5e6a8]">20</span>
      </div>
    </div>
  );
}

function BankoDealer() {
  return (
    <svg width="56" height="48" viewBox="0 0 56 48" aria-hidden>
      <ellipse cx="28" cy="40" rx="24" ry="7" fill="#0d3d28" stroke="#d4af37" strokeWidth="1.2" />
      <ellipse cx="28" cy="40" rx="16" ry="4" fill="none" stroke="#d4af37" strokeWidth="0.6" opacity="0.5" />
      {/* Dealer silhouette */}
      <circle cx="28" cy="16" r="8" fill="#d4af37" />
      <path d="M16 36 Q28 22 40 36" fill="#d4af37" />
      <path d="M20 12 Q28 4 36 12" fill="#a67c00" />
      <rect x="22" y="20" width="12" height="3" rx="1" fill="#1a0505" opacity="0.35" />
    </svg>
  );
}

function DealersChoiceHat() {
  return (
    <svg width="52" height="48" viewBox="0 0 52 48" aria-hidden>
      {/* Fedora + silhouette */}
      <ellipse cx="26" cy="42" rx="14" ry="4" fill="#d4af37" opacity="0.35" />
      <path d="M16 38 Q26 24 36 38 Z" fill="#d4af37" />
      <circle cx="26" cy="20" r="9" fill="#d4af37" />
      <ellipse cx="26" cy="14" rx="16" ry="4" fill="#a67c00" />
      <path d="M12 14 Q26 2 40 14" fill="#8a6a12" />
      <rect x="18" y="12" width="16" height="3" rx="1" fill="#f5e6a8" opacity="0.4" />
    </svg>
  );
}

function LowestJokerArt() {
  return (
    <div className="relative flex items-center justify-center gap-1">
      <MiniPlayingCard rank="2" suit="HEARTS" width={30} height={42} />
      <svg width="22" height="18" viewBox="0 0 48 40" aria-hidden className="absolute -top-1 -right-1 drop-shadow">
        <path d="M4 30 L8 12 L16 22 L24 6 L32 22 L40 12 L44 30 Z" fill="#d4af37" stroke="#fff4c2" strokeWidth="1" />
        <rect x="6" y="30" width="36" height="5" rx="1" fill="#a67c00" />
      </svg>
    </div>
  );
}

function DiscardArt() {
  return (
    <div className="relative h-[52px] w-[86px] flex items-end justify-center">
      <Fan
        cards={[
          { rank: 'A', suit: 'SPADES' },
          { rank: 'K', suit: 'HEARTS' },
          { rank: 'Q', suit: 'DIAMONDS' },
        ]}
      />
      <div className="absolute -right-0 top-0 opacity-90" style={{ transform: 'rotate(22deg) translateY(-4px)' }}>
        <MiniPlayingCard faceDown width={24} height={34} />
      </div>
      <span className="absolute right-0 top-5 text-[9px] font-black text-rose-400">✕</span>
    </div>
  );
}

/** SVG artwork per Teen Patti variation tile. */
export default function VariantArt({ variantKey }) {
  switch (variantKey) {
    case 'CLASSIC':
      return (
        <ArtBox>
          <Fan
            cards={[
              { rank: 'A', suit: 'HEARTS' },
              { rank: 'A', suit: 'SPADES' },
              { rank: 'A', suit: 'CLUBS' },
            ]}
          />
        </ArtBox>
      );
    case 'AK47':
      return (
        <ArtBox>
          <Fan
            cards={[
              { rank: 'A', suit: 'SPADES' },
              { rank: 'K', suit: 'HEARTS' },
              { rank: '4', suit: 'CLUBS' },
              { rank: '7', suit: 'DIAMONDS' },
            ]}
          />
        </ArtBox>
      );
    case 'JOKER':
      return (
        <ArtBox>
          <JokerCard />
        </ArtBox>
      );
    case 'MUFLIS':
      return (
        <ArtBox>
          <MuflisBars />
        </ArtBox>
      );
    case 'BEST_OF_FOUR':
      return (
        <ArtBox>
          <Fan
            faceDown
            cards={[
              { rank: 'A', suit: 'SPADES' },
              { rank: 'K', suit: 'SPADES' },
              { rank: 'Q', suit: 'SPADES' },
              { rank: 'J', suit: 'SPADES' },
            ]}
          />
        </ArtBox>
      );
    case 'DISCARD_ONE':
      return (
        <ArtBox>
          <DiscardArt />
        </ArtBox>
      );
    case 'LOWEST_JOKER':
      return (
        <ArtBox>
          <LowestJokerArt />
        </ArtBox>
      );
    case 'HIGH_WILD':
      return (
        <ArtBox>
          <GoldStar />
        </ArtBox>
      );
    case 'LOW_WILD':
      return (
        <ArtBox>
          <DownArrow />
        </ArtBox>
      );
    case 'HIDDEN_JOKER':
      return (
        <ArtBox>
          <HiddenJokerCard />
        </ArtBox>
      );
    case 'ONE_EYED_JACK':
      return (
        <ArtBox>
          <Fan
            cards={[
              { rank: 'J', suit: 'HEARTS' },
              { rank: 'J', suit: 'SPADES' },
            ]}
          />
        </ArtBox>
      );
    case 'BUST_CARD':
      return (
        <ArtBox>
          <BustBadge />
        </ArtBox>
      );
    case 'REVOLVING_JOKER':
      return (
        <ArtBox>
          <RevolvingArrows />
        </ArtBox>
      );
    case 'NINE_NINE_NINE':
      return (
        <ArtBox>
          <NumberGlow text="999" size={28} />
        </ArtBox>
      );
    case 'TWENTY_TWENTY':
      return (
        <ArtBox>
          <TwentyTwenty />
        </ArtBox>
      );
    case 'AUCTION':
      return (
        <ArtBox>
          <AuctionHammer />
        </ArtBox>
      );
    case 'BANKO':
      return (
        <ArtBox>
          <BankoDealer />
        </ArtBox>
      );
    case 'DEALERS_CHOICE':
      return (
        <ArtBox>
          <DealersChoiceHat />
        </ArtBox>
      );
    default:
      return (
        <ArtBox>
          <Fan
            cards={[
              { rank: 'A', suit: 'HEARTS' },
              { rank: 'A', suit: 'DIAMONDS' },
              { rank: 'A', suit: 'SPADES' },
            ]}
          />
        </ArtBox>
      );
  }
}

export function BrandLogoAces({ className = '' }) {
  return (
    <div className={`relative flex items-end justify-center h-10 ${className}`}>
      <div className="absolute -top-3 left-1/2 -translate-x-1/2 z-20">
        <svg width="22" height="16" viewBox="0 0 48 40" aria-hidden>
          <path d="M4 30 L8 12 L16 22 L24 6 L32 22 L40 12 L44 30 Z" fill="#d4af37" stroke="#fff4c2" strokeWidth="1" />
        </svg>
      </div>
      <div className="flex items-end -space-x-3">
        <MiniPlayingCard rank="A" suit="HEARTS" width={26} height={36} rotation={-18} />
        <MiniPlayingCard rank="A" suit="DIAMONDS" width={28} height={38} rotation={0} className="z-10" />
        <MiniPlayingCard rank="A" suit="SPADES" width={26} height={36} rotation={18} />
      </div>
    </div>
  );
}
