import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { buildRotatedSeats, formatChipAmount } from './seatLayout';
import PlayerSeat from './PlayerSeat';
import TablePot from './TablePot';
import DealAnimation from './DealAnimation';

export const TABLE_BG_URL =
  'https://res.cloudinary.com/dsafvwkrf/image/upload/v1785347801/Untitled_1920_x_1080_px_1_1_qgc3s6.webp';

/** Table image shown fully (no crop). Seats/pot overlay the image bounds.
 * Deal + seat cards only after enough players are seated and the hand is live.
 */
export default function TableArena({
  players,
  myUserId,
  currentTurnUserId,
  dealerSeatIndex,
  hostId,
  handInProgress,
  cardsLive = false,
  minPlayers = 3,
  handEnded,
  showdownRevealed,
  revealedHands,
  disconnectedIds = [],
  potRupees,
  potPaise,
  walletBalancePaise,
  turnDeadlineAt,
  turnSecondsRemaining,
  turnDurationSeconds = 30,
  winnerUserId,
  className = '',
}) {
  const seats = useMemo(
    () => buildRotatedSeats(players, myUserId),
    [players, myUserId],
  );

  const enoughPlayers = seats.length >= Math.max(2, minPlayers);
  const readyForCards = Boolean(cardsLive && enoughPlayers);

  const [dealActive, setDealActive] = useState(false);
  const [dealDone, setDealDone] = useState(false);
  const [dealKey, setDealKey] = useState(0);
  const prevReady = useRef(false);

  useEffect(() => {
    // Only deal when ALL required players are in and hand is actually running
    const started = readyForCards && !prevReady.current;
    prevReady.current = readyForCards;
    if (!readyForCards) {
      setDealActive(false);
      setDealDone(false);
      return;
    }
    if (started) {
      // Mid-hand refresh/reconnect: cards already exist — skip re-deal, just show them
      const alreadyDealt = seats.some(
        (s) => (s.player?.cardCount > 0) || (Array.isArray(s.player?.cards) && s.player.cards.length > 0),
      );
      if (alreadyDealt) {
        setDealActive(false);
        setDealDone(true);
      } else {
        setDealDone(false);
        setDealKey((k) => k + 1);
        setDealActive(true);
      }
    }
  }, [readyForCards, seats]);

  const onDealComplete = useCallback(() => {
    setDealActive(false);
    setDealDone(true);
  }, []);

  const turnSeat = seats.find((s) => s.player?.userId === currentTurnUserId);
  const winnerSeat = seats.find((s) => s.player?.userId === winnerUserId);

  const [localTurnSeconds, setLocalTurnSeconds] = useState(turnSecondsRemaining || 0);

  useEffect(() => {
    if (!handInProgress || !currentTurnUserId) {
      setLocalTurnSeconds(turnSecondsRemaining || 0);
      return undefined;
    }
    // Prefer live deadline; fall back to counting down from turnSecondsRemaining
    if (turnDeadlineAt) {
      const tick = () => {
        const ms = new Date(turnDeadlineAt).getTime() - Date.now();
        setLocalTurnSeconds(Math.max(0, Math.ceil(ms / 1000)));
      };
      tick();
      const id = setInterval(tick, 250);
      return () => clearInterval(id);
    }
    setLocalTurnSeconds(turnSecondsRemaining || 0);
    if (!turnSecondsRemaining || turnSecondsRemaining <= 0) return undefined;
    const started = Date.now();
    const initial = turnSecondsRemaining;
    const id = setInterval(() => {
      const elapsed = Math.floor((Date.now() - started) / 1000);
      setLocalTurnSeconds(Math.max(0, initial - elapsed));
    }, 250);
    return () => clearInterval(id);
  }, [turnDeadlineAt, handInProgress, currentTurnUserId, turnSecondsRemaining]);

  const turnProgress = turnDurationSeconds > 0
    ? Math.min(1, localTurnSeconds / turnDurationSeconds)
    : 1;

  const showPayoutFly = Boolean(handEnded && winnerUserId);
  /* Seat cards only after deal finishes (or mid-hand refresh when already dealt) */
  const showSeatCards = readyForCards && !dealActive && dealDone;

  return (
    <div className={`relative inline-block max-w-full max-h-full ${className}`}>
      {/* Image defines size — never cropped */}
      <img
        src={TABLE_BG_URL}
        alt="Teen Patti table"
        className="block w-auto h-auto max-w-full max-h-[min(58dvh,520px)] sm:max-h-[min(72dvh,720px)] md:max-h-[min(82vh,820px)] object-contain select-none pointer-events-none"
        draggable={false}
      />

      {/* Overlays match the rendered image box */}
      <div className="absolute inset-0">
        <TablePot
          potRupees={potRupees}
          potPaise={potPaise}
          fromSeatPosition={turnSeat?.position}
          winnerPosition={winnerSeat?.position}
          showPayoutFly={showPayoutFly}
        />

        <DealAnimation active={dealActive} seats={seats} dealKey={dealKey} onComplete={onDealComplete} />

        <AnimatePresence mode="popLayout">
          {seats.map(({ player, seatIndex, position }) => {
            const isMe = player.userId === myUserId;
            const isTurn = currentTurnUserId === player.userId;
            const isDealer = dealerSeatIndex >= 0 && seatIndex === dealerSeatIndex;
            const isHost = Boolean(hostId && player.userId === hostId);
            const isDisconnected = disconnectedIds.includes(player.userId) || player.connected === false;
            const isWinner = Boolean(player.isWinner || (winnerUserId && player.userId === winnerUserId && handEnded));

            const visibleCards = showdownRevealed
              ? (revealedHands?.[player.userId] || player.cards || [])
              : (isMe ? (player.cards || []) : []);

            const hasFaceCards = visibleCards.length > 0;
            /* Cards only after all required players are in AND deal animation finished */
            const showBacks = showSeatCards && !hasFaceCards && (handInProgress || (player.cardCount > 0));
            const seatCards = showSeatCards && hasFaceCards ? visibleCards : [];

            let balanceLabel = '₹—';
            if (isMe && walletBalancePaise != null) {
              balanceLabel = formatChipAmount(walletBalancePaise);
            } else if (player.walletBalancePaise != null) {
              balanceLabel = formatChipAmount(player.walletBalancePaise);
            } else if (player.totalContributedPaise != null && player.totalContributedPaise > 0) {
              balanceLabel = formatChipAmount(player.totalContributedPaise);
            }

            return (
              <div
                key={player.userId || `seat-${seatIndex}`}
                className="absolute z-20 -translate-x-1/2 -translate-y-1/2"
                style={{ left: `${position.left}%`, top: `${position.top}%` }}
              >
                <PlayerSeat
                  player={player}
                  seatIndex={seatIndex}
                  isMe={isMe}
                  isTurn={isTurn && handInProgress}
                  isDealer={isDealer && handInProgress}
                  isHost={isHost && !handInProgress}
                  isWinner={isWinner}
                  isDisconnected={isDisconnected}
                  turnProgress={isTurn && handInProgress ? turnProgress : 1}
                  turnSeconds={isTurn && handInProgress ? localTurnSeconds : 0}
                  balanceLabel={balanceLabel}
                  cards={seatCards}
                  showCardBacks={showBacks}
                  cardCount={player.cardCount || 3}
                  showStatus={handInProgress}
                />
              </div>
            );
          })}
        </AnimatePresence>
      </div>
    </div>
  );
}
