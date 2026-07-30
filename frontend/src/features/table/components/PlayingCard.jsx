import React from 'react';
import { motion } from 'framer-motion';

const RANK_LABEL = {
  ACE: 'A', KING: 'K', QUEEN: 'Q', JACK: 'J', TEN: '10',
  NINE: '9', EIGHT: '8', SEVEN: '7', SIX: '6', FIVE: '5',
  FOUR: '4', THREE: '3', TWO: '2',
};

const SUIT_META = {
  HEARTS: { symbol: '♥', color: '#e11d48' },
  DIAMONDS: { symbol: '♦', color: '#e11d48' },
  SPADES: { symbol: '♠', color: '#0f172a' },
  CLUBS: { symbol: '♣', color: '#0f172a' },
};

function rankLabel(rank) {
  if (!rank) return '?';
  return RANK_LABEL[rank] || String(rank).slice(0, 2);
}

/** Black + gold Ace-of-Spades card back matching reference UI */
export function CardBack({ className = '', width = 44, height = 62 }) {
  const id = React.useId();
  return (
    <svg
      viewBox="0 0 70 98"
      width={width}
      height={height}
      className={className}
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
    >
      <defs>
        <linearGradient id={`${id}-bg`} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#1a1a2e" />
          <stop offset="50%" stopColor="#0b0b14" />
          <stop offset="100%" stopColor="#050508" />
        </linearGradient>
        <linearGradient id={`${id}-gold`} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#f5e6a8" />
          <stop offset="45%" stopColor="#d4af37" />
          <stop offset="100%" stopColor="#8a6a1a" />
        </linearGradient>
        <pattern id={`${id}-orn`} width="14" height="14" patternUnits="userSpaceOnUse">
          <path d="M7 1 L9 5 L13 7 L9 9 L7 13 L5 9 L1 7 L5 5 Z" fill="none" stroke="#d4af3728" strokeWidth="0.6" />
        </pattern>
      </defs>
      <rect x="1" y="1" width="68" height="96" rx="7" fill={`url(#${id}-bg)`} stroke={`url(#${id}-gold)`} strokeWidth="2.2" />
      <rect x="6" y="6" width="58" height="86" rx="4" fill="none" stroke="#d4af3755" strokeWidth="1" />
      <rect x="9" y="9" width="52" height="80" rx="3" fill={`url(#${id}-orn)`} />
      <circle cx="35" cy="49" r="16" fill="#0a0a12" stroke={`url(#${id}-gold)`} strokeWidth="1.6" />
      <circle cx="35" cy="49" r="11" fill="none" stroke="#d4af3744" strokeWidth="0.8" />
      <text x="35" y="56" textAnchor="middle" fontSize="18" fill={`url(#${id}-gold)`} fontFamily="Georgia, serif">♠</text>
    </svg>
  );
}

export default function PlayingCard({
  suit,
  rank,
  faceDown = false,
  className = '',
  width = 44,
  height = 62,
  style,
  layoutId,
}) {
  if (faceDown || !suit || !rank) {
    const back = <CardBack className={className} width={width} height={height} />;
    if (layoutId) {
      return (
        <motion.div layoutId={layoutId} style={style} className="inline-block drop-shadow-[0_4px_8px_rgba(0,0,0,0.55)]">
          {back}
        </motion.div>
      );
    }
    return (
      <span className="inline-block drop-shadow-[0_4px_8px_rgba(0,0,0,0.55)]" style={style}>
        {back}
      </span>
    );
  }

  const meta = SUIT_META[suit] || { symbol: '?', color: '#64748b' };
  const label = rankLabel(rank);
  const id = React.useId();
  const isFace = ['JACK', 'QUEEN', 'KING'].includes(rank);

  const svg = (
    <svg
      viewBox="0 0 70 98"
      width={width}
      height={height}
      className={className}
      xmlns="http://www.w3.org/2000/svg"
      aria-label={`${label} of ${suit}`}
    >
      <defs>
        <linearGradient id={`${id}-face`} x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="100%" stopColor="#f1f5f9" />
        </linearGradient>
      </defs>
      <rect x="1" y="1" width="68" height="96" rx="7" fill={`url(#${id}-face)`} stroke="#d4af37" strokeWidth="1.5" />
      <text x="10" y="20" fontSize="14" fontWeight="700" fill={meta.color} fontFamily="Georgia, serif">{label}</text>
      <text x="10" y="34" fontSize="12" fill={meta.color}>{meta.symbol}</text>
      <text x="35" y={isFace ? 58 : 62} textAnchor="middle" fontSize={isFace ? 28 : 32} fill={meta.color} fontFamily="Georgia, serif">
        {meta.symbol}
      </text>
      <g transform="rotate(180 35 49)">
        <text x="10" y="20" fontSize="14" fontWeight="700" fill={meta.color} fontFamily="Georgia, serif">{label}</text>
        <text x="10" y="34" fontSize="12" fill={meta.color}>{meta.symbol}</text>
      </g>
    </svg>
  );

  if (layoutId) {
    return <motion.div layoutId={layoutId} style={style} className="inline-block">{svg}</motion.div>;
  }
  return <span className="inline-block" style={style}>{svg}</span>;
}
