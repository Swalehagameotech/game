/**
 * You always at bottom center (visual 0). Others rotate around the oval.
 */

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

export function buildRotatedSeats(players, myUserId) {
  const list = Array.isArray(players) ? players : [];
  const n = list.length;
  if (n === 0) return [];

  let myIndex = list.findIndex((p) => p?.userId === myUserId);
  if (myIndex < 0) myIndex = 0;

  const layoutKey = Math.min(6, Math.max(3, n));
  const layout = LAYOUTS[layoutKey] || LAYOUTS[6];

  return list
    .map((player, seatIndex) => {
      const visualIndex = (seatIndex - myIndex + n) % n;
      const position = layout[Math.min(visualIndex, layout.length - 1)];
      return { player, seatIndex, visualIndex, position };
    })
    .sort((a, b) => a.visualIndex - b.visualIndex);
}

export const DEALER_ORIGIN = { left: 50, top: 10 };
/** Exact center of the table image / felt */
export const POT_ORIGIN = { left: 50, top: 50 };

export function formatChipAmount(paiseOrRupees, { fromPaise = true } = {}) {
  const rupees = fromPaise ? Number(paiseOrRupees || 0) / 100 : Number(paiseOrRupees || 0);
  return `₹${Math.round(rupees).toLocaleString('en-IN')}`;
}
