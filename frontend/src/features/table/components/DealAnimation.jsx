import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import PlayingCard from './PlayingCard';
import { DEALER_ORIGIN } from './seatLayout';

/**
 * Deals 3 face-down cards from dealer origin to each seat position.
 * Purely visual — does not touch game state.
 */
export default function DealAnimation({
  active,
  seats,
  dealKey,
  onComplete,
}) {
  const [flies, setFlies] = useState([]);
  const seatsRef = useRef(seats);
  seatsRef.current = seats;
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  useEffect(() => {
    if (!active) {
      setFlies([]);
      return undefined;
    }

    const seatList = seatsRef.current || [];
    if (!seatList.length) {
      onCompleteRef.current?.();
      return undefined;
    }

    const batch = Date.now();
    const items = [];
    let delay = 0;
    seatList.forEach((seat) => {
      for (let c = 0; c < 3; c += 1) {
        items.push({
          id: `${batch}-${seat.seatIndex}-${c}`,
          to: seat.position,
          delay,
          rotate: (Math.random() - 0.5) * 36,
        });
        delay += 0.085;
      }
    });
    setFlies(items);

    const totalMs = (delay + 0.55) * 1000;
    const t = setTimeout(() => {
      setFlies([]);
      onCompleteRef.current?.();
    }, totalMs);
    return () => clearTimeout(t);
  }, [active, dealKey]);

  return (
    <div className="absolute inset-0 pointer-events-none z-30 overflow-hidden">
      <AnimatePresence>
        {flies.map((f) => (
          <motion.div
            key={f.id}
            className="absolute -translate-x-1/2 -translate-y-1/2"
            initial={{
              left: `${DEALER_ORIGIN.left}%`,
              top: `${DEALER_ORIGIN.top}%`,
              scale: 0.5,
              opacity: 1,
              rotate: -18,
            }}
            animate={{
              left: `${f.to.left}%`,
              top: `${f.to.top}%`,
              scale: 1,
              opacity: [1, 1, 0.15],
              rotate: f.rotate,
            }}
            exit={{ opacity: 0 }}
            transition={{
              delay: f.delay,
              duration: 0.5,
              ease: [0.22, 1, 0.36, 1],
            }}
          >
            <PlayingCard faceDown width={50} height={70} />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
