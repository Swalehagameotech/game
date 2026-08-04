/**
 * You always at bottom center (visual 0). Others follow seated order around the table.
 * Positions are % of the table image — scales with phone width automatically.
 */

/** Landscape / desktop felt */
const LAYOUTS = {
  3: [
    { left: 50, top: 86 },
    { left: 14, top: 40 },
    { left: 86, top: 40 },
  ],
  4: [
    { left: 50, top: 86 },
    { left: 12, top: 44 },
    { left: 50, top: 12 },
    { left: 88, top: 44 },
  ],
  5: [
    { left: 50, top: 86 },
    { left: 11, top: 46 },
    { left: 18, top: 14 },
    { left: 82, top: 14 },
    { left: 89, top: 46 },
  ],
  6: [
    { left: 50, top: 87 },
    { left: 86, top: 58 },
    { left: 88, top: 24 },
    { left: 50, top: 10 },
    { left: 12, top: 24 },
    { left: 14, top: 58 },
  ],
};

/**
 * Phone portrait table (dealer baked into image top).
 * Green oval ~ left 20–80, top 40–78. Seats sit on the outer rail.
 *
 * 3: you bottom · left · right
 * 4: you bottom · left · top · right
 * 5: you bottom · lower-left · upper-left · upper-right · lower-right
 * 6: you bottom · lower-right · upper-right · top · upper-left · lower-left
 */
const PORTRAIT_LAYOUTS = {
  3: [
    { left: 50, top: 79 }, // you — bottom middle
    { left: 12, top: 55 }, // left rail
    { left: 88, top: 55 }, // right rail
  ],
  4: [
    { left: 50, top: 79 },
    { left: 11, top: 56 },
    { left: 50, top: 39 },
    { left: 89, top: 56 },
  ],
  5: [
    { left: 50, top: 79 },
    { left: 11, top: 63 },
    { left: 16, top: 43 },
    { left: 84, top: 43 },
    { left: 89, top: 63 },
  ],
  6: [
    { left: 50, top: 79 },
    { left: 89, top: 61 },
    { left: 87, top: 43 },
    { left: 50, top: 37 },
    { left: 13, top: 43 },
    { left: 11, top: 61 },
  ],
};

export function buildRotatedSeats(players, myUserId, { portrait = false } = {}) {
  const list = Array.isArray(players) ? players : [];
  const n = list.length;
  if (n === 0) return [];

  let myIndex = list.findIndex((p) => p?.userId === myUserId);
  if (myIndex < 0) myIndex = 0;

  const layoutKey = Math.min(6, Math.max(3, n));
  const layouts = portrait ? PORTRAIT_LAYOUTS : LAYOUTS;
  const layout = layouts[layoutKey] || layouts[6];

  return list
    .map((player, seatIndex) => {
      const visualIndex = (seatIndex - myIndex + n) % n;
      const position = layout[Math.min(visualIndex, layout.length - 1)];
      return { player, seatIndex, visualIndex, position };
    })
    .sort((a, b) => a.visualIndex - b.visualIndex);
}

export const DEALER_ORIGIN = { left: 50, top: 10 };
export const POT_ORIGIN = { left: 50, top: 50 };
export const POT_ORIGIN_PORTRAIT = { left: 50, top: 56 };

export function formatChipAmount(paiseOrRupees, { fromPaise = true } = {}) {
  const rupees = fromPaise ? Number(paiseOrRupees || 0) / 100 : Number(paiseOrRupees || 0);
  return `₹${Math.round(rupees).toLocaleString('en-IN')}`;
}
