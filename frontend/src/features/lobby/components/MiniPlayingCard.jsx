import React from 'react';

const SUIT_PATH = {
  HEARTS:
    'M12 21s-7.5-4.8-9.6-9.2C.6 8.2 1.8 5 5 5c1.8 0 3.2 1.1 4 2.4C9.8 6.1 11.2 5 13 5c3.2 0 4.4 3.2 2.6 6.8C19.5 16.2 12 21 12 21z',
  DIAMONDS: 'M12 2 L20 12 L12 22 L4 12 Z',
  SPADES:
    'M12 2 C8 8 3 11 3 15c0 2.5 2 4 4.5 4 1.4 0 2.6-.6 3.5-1.5V21h2v-3.5c.9.9 2.1 1.5 3.5 1.5 2.5 0 4.5-1.5 4.5-4 0-4-5-7-9-13z',
  CLUBS:
    'M12 10a3.2 3.2 0 1 1 0-6.4 3.2 3.2 0 0 1 0 6.4zm-4.8 5.2a3.2 3.2 0 1 1 0-6.4 3.2 3.2 0 0 1 0 6.4zm9.6 0a3.2 3.2 0 1 1 0-6.4 3.2 3.2 0 0 1 0 6.4zM11 14.5V21h2v-6.5c-.6.2-1.3.2-2 0z',
};

const RED = '#c41e3a';
const BLACK = '#1a1208';

/**
 * Tiny SVG playing card for lobby tiles / logo.
 */
export default function MiniPlayingCard({
  rank = 'A',
  suit = 'SPADES',
  width = 36,
  height = 50,
  rotation = 0,
  className = '',
  faceDown = false,
}) {
  const isRed = suit === 'HEARTS' || suit === 'DIAMONDS';
  const color = isRed ? RED : BLACK;
  const path = SUIT_PATH[suit] || SUIT_PATH.SPADES;

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 60 84"
      className={className}
      style={{ transform: rotation ? `rotate(${rotation}deg)` : undefined }}
      aria-hidden
    >
      <defs>
        <linearGradient id="cardFace" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#fffef8" />
          <stop offset="100%" stopColor="#f0e6d0" />
        </linearGradient>
        <linearGradient id="cardBack" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#6b1a24" />
          <stop offset="100%" stopColor="#3a0c12" />
        </linearGradient>
      </defs>
      <rect x="1" y="1" width="58" height="82" rx="6" fill={faceDown ? 'url(#cardBack)' : 'url(#cardFace)'} stroke="#d4af37" strokeWidth="1.5" />
      {faceDown ? (
        <>
          <rect x="8" y="10" width="44" height="64" rx="3" fill="none" stroke="#d4af37" strokeWidth="1" opacity="0.5" />
          <text x="30" y="48" textAnchor="middle" fill="#d4af37" fontSize="18" fontWeight="700">♠</text>
        </>
      ) : (
        <>
          <text x="8" y="18" fill={color} fontSize="12" fontWeight="800" fontFamily="Georgia, serif">{rank}</text>
          <g transform="translate(5, 20) scale(0.35)">
            <path d={path} fill={color} />
          </g>
          <g transform="translate(18, 30) scale(0.9)">
            <path d={path} fill={color} />
          </g>
          <text x="52" y="74" fill={color} fontSize="12" fontWeight="800" fontFamily="Georgia, serif" textAnchor="end">{rank}</text>
        </>
      )}
    </svg>
  );
}

export function SuitIcon({ suit = 'SPADES', size = 16, className = '' }) {
  const isRed = suit === 'HEARTS' || suit === 'DIAMONDS';
  const color = isRed ? RED : BLACK;
  const path = SUIT_PATH[suit] || SUIT_PATH.SPADES;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" className={className} aria-hidden>
      <path d={path} fill={color} />
    </svg>
  );
}
