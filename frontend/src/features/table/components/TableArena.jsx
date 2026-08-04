import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { buildRotatedSeats, formatChipAmount, POT_ORIGIN, POT_ORIGIN_PORTRAIT } from './seatLayout';
import PlayerSeat from './PlayerSeat';
import TablePot from './TablePot';
import DealAnimation from './DealAnimation';
import ShowdownHands from './ShowdownHands';

export const TABLE_BG_URL =
  'https://res.cloudinary.com/dsafvwkrf/image/upload/v1785347801/Untitled_1920_x_1080_px_1_1_qgc3s6.webp';

export const TABLE_BG_URL_PHONE =
  'https://res.cloudinary.com/dsafvwkrf/image/upload/v1785836535/7a6586ce-d1fa-4803-8b69-08d98b3ffe7b.png';

function usePhoneTableLayout() {
  const [phone, setPhone] = useState(() => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia('(max-width: 639px)').matches;
  });

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 639px)');
    const onChange = (e) => setPhone(e.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  return phone;
}

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
  const phoneLayout = usePhoneTableLayout();
  const potOrigin = phoneLayout ? POT_ORIGIN_PORTRAIT : POT_ORIGIN;

  const seats = useMemo(
    () => buildRotatedSeats(players, myUserId, { portrait: phoneLayout }),
    [players, myUserId, phoneLayout],
  );

  const enoughPlayers = seats.length >= Math.max(2, minPlayers);
  const readyForCards = Boolean(cardsLive && enoughPlayers);

  const [dealActive, setDealActive] = useState(false);
  const [dealDone, setDealDone] = useState(false);
  const [dealKey, setDealKey] = useState(0);
  const prevReady = useRef(false);

  useEffect(() => {
    const started = readyForCards && !prevReady.current;
    prevReady.current = readyForCards;
    if (!readyForCards) {
      setDealActive(false);
      setDealDone(false);
      return;
    }
    if (started) {
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
  const [turnTotal, setTurnTotal] = useState(
    Math.max(Number(turnDurationSeconds) || 0, Number(turnSecondsRemaining) || 0, 1),
  );

  useEffect(() => {
    setTurnTotal(Math.max(
      Number(turnDurationSeconds) || 0,
      Number(turnSecondsRemaining) || 0,
      1,
    ));
  }, [currentTurnUserId, turnDeadlineAt, turnDurationSeconds]);

  useEffect(() => {
    if (!handInProgress || !currentTurnUserId) {
      setLocalTurnSeconds(turnSecondsRemaining || 0);
      return undefined;
    }
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

  const turnProgress = Math.min(1, Math.max(0, localTurnSeconds / turnTotal));

  const showPayoutFly = Boolean(handEnded && winnerUserId);
  const showSeatCards = readyForCards && !dealActive && dealDone;

  return (
    <div className={`relative inline-block w-full sm:w-auto sm:max-w-full sm:max-h-full ${className}`}>
      {/* Phone — natural image size (width fills screen, height follows aspect) */}
      <img
        src={TABLE_BG_URL_PHONE}
        alt="Teen Patti table"
        className="block sm:hidden w-[118%] max-w-none h-auto max-h-[78dvh] object-contain select-none pointer-events-none relative left-1/2 -translate-x-1/2"
        draggable={false}
      />
      {/* Desktop / tablet — landscape felt */}
      <img
        src={TABLE_BG_URL}
        alt="Teen Patti table"
        className="hidden sm:block w-auto h-auto max-w-full max-h-[min(58dvh,500px)] md:max-h-[min(64dvh,600px)] lg:max-h-[min(68vh,660px)] object-contain select-none pointer-events-none"
        draggable={false}
      />

      <div className="absolute inset-0">
        <TablePot
          potRupees={potRupees}
          potPaise={potPaise}
          fromSeatPosition={turnSeat?.position}
          winnerPosition={winnerSeat?.position}
          showPayoutFly={showPayoutFly}
          hideLabel={Boolean(showdownRevealed && Object.keys(revealedHands || {}).length > 0)}
          potOrigin={potOrigin}
        />

        <ShowdownHands
          show={Boolean(showdownRevealed && Object.keys(revealedHands || {}).length > 0)}
          handsByUserId={revealedHands}
          players={players}
          winnerUserId={winnerUserId}
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
            const showBacks = showSeatCards && !hasFaceCards && (handInProgress || (player.cardCount > 0)) && !showdownRevealed;
            const seatCards = (showSeatCards || showdownRevealed) && hasFaceCards ? visibleCards : [];

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
