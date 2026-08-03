/**
 * Client-side Teen Patti hand label for the SEEN player only.
 * Mirrors backend HandEvaluator categories (display only — never trust for settlement).
 */

const RANK_VALUE = {
  TWO: 2, THREE: 3, FOUR: 4, FIVE: 5, SIX: 6, SEVEN: 7, EIGHT: 8,
  NINE: 9, TEN: 10, JACK: 11, QUEEN: 12, KING: 13, ACE: 14,
  2: 2, 3: 3, 4: 4, 5: 5, 6: 6, 7: 7, 8: 8, 9: 9, 10: 10,
  J: 11, Q: 12, K: 13, A: 14,
};

function rankValue(card) {
  const r = card?.rank ?? card?.value ?? card?.Rank;
  if (r == null) return 0;
  if (typeof r === 'number') return r;
  const key = String(r).toUpperCase();
  return RANK_VALUE[key] || parseInt(key, 10) || 0;
}

function suitOf(card) {
  return String(card?.suit ?? card?.Suit ?? '').toUpperCase();
}

/**
 * @returns {{ category: string, label: string } | null}
 */
export function evaluateHandLabel(cards) {
  if (!Array.isArray(cards) || cards.length !== 3) return null;
  if (cards.some((c) => !c || (!c.rank && !c.value))) return null;

  const sorted = [...cards].sort((a, b) => rankValue(b) - rankValue(a));
  const v0 = rankValue(sorted[0]);
  const v1 = rankValue(sorted[1]);
  const v2 = rankValue(sorted[2]);
  const flush = suitOf(sorted[0]) === suitOf(sorted[1]) && suitOf(sorted[1]) === suitOf(sorted[2]);

  if (v0 === v1 && v1 === v2) {
    return { category: 'TRAIL', label: 'Trail' };
  }

  const standardSeq = (v0 - v1 === 1) && (v1 - v2 === 1);
  const lowAceSeq = v0 === 14 && v1 === 3 && v2 === 2;
  if (standardSeq || lowAceSeq) {
    return flush
      ? { category: 'PURE_SEQUENCE', label: 'Pure Sequence' }
      : { category: 'SEQUENCE', label: 'Sequence' };
  }

  if (flush) {
    return { category: 'COLOR', label: 'Color' };
  }

  if (v0 === v1 || v1 === v2 || v0 === v2) {
    return { category: 'PAIR', label: 'Pair' };
  }

  return { category: 'HIGH_CARD', label: 'High Card' };
}
