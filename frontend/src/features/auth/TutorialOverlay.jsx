import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, ArrowRight, Check, X, Shield, Coins, Flame, Layers, Eye, Award } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';

const TUTORIAL_STEPS = [
  {
    title: 'Welcome to Teen Patti!',
    icon: Sparkles,
    content: 'Welcome to realtime multiplayer Teen Patti. This short guide covers how to play and win.',
  },
  {
    title: 'Your Wallet',
    icon: Coins,
    content: 'Use demo chips from your wallet to join tables. Top up anytime with Add Demo Chips.',
  },
  {
    title: 'Boot Amount',
    icon: Flame,
    content: 'Before cards are dealt, every seated player contributes the Boot Amount to start the round.',
  },
  {
    title: 'Table Pot',
    icon: Layers,
    content: 'All bets and boot contributions go into the central Pot. The hand winner takes it all.',
  },
  {
    title: 'Your 3 Cards',
    icon: Eye,
    content: 'You receive 3 cards face down from a shuffled 52-card deck.',
  },
  {
    title: 'Playing Blind',
    icon: Shield,
    content: 'Blind means you play without viewing cards. Blind bets are 1x the current stake.',
  },
  {
    title: 'Seeing Cards',
    icon: Eye,
    content: 'Tap See Cards on your turn to view your hand. Seen players bet 2x the stake.',
  },
  {
    title: 'Chaal',
    icon: ArrowRight,
    content: 'Chaal matches the required bet so you stay in the hand.',
  },
  {
    title: 'Raise',
    icon: Flame,
    content: 'Raise increases the stake unit for everyone still in the round.',
  },
  {
    title: 'Pack (Fold)',
    icon: X,
    content: 'Pack folds your hand. If everyone else packs, you win the pot.',
  },
  {
    title: 'Side Show',
    icon: Layers,
    content: 'With 3+ Seen players, request a Side Show with the previous Seen player.',
  },
  {
    title: 'Show',
    icon: Award,
    content: 'When only 2 players remain, Show reveals cards. Higher hand wins the pot.',
  },
  {
    title: 'Hand Rankings',
    icon: Sparkles,
    content: 'Trail > Pure Sequence > Sequence > Color > Pair > High Card.',
  },
  {
    title: 'You Are Ready!',
    icon: Check,
    content: 'Join a table and compete in realtime Teen Patti.',
  },
];

export default function TutorialOverlay({ user, onComplete }) {
  const [currentStep, setCurrentStep] = useState(0);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const isCompletedLocal = localStorage.getItem('firstLoginTutorialCompleted') === 'true';
    if (user && !user.firstLoginTutorialCompleted && !isCompletedLocal) {
      setIsVisible(true);
    }
  }, [user]);

  if (!isVisible) return null;

  const handleNext = () => {
    if (currentStep < TUTORIAL_STEPS.length - 1) {
      setCurrentStep((prev) => prev + 1);
    } else {
      finishTutorial();
    }
  };

  const finishTutorial = async () => {
    setIsVisible(false);
    localStorage.setItem('firstLoginTutorialCompleted', 'true');
    try {
      await axiosClient.post('/users/tutorial/complete');
    } catch (e) {
      console.warn('Tutorial completion sync note:', e);
    }
    if (onComplete) onComplete();
  };

  const StepIcon = TUTORIAL_STEPS[currentStep].icon;

  return (
    <AnimatePresence>
      <div className="tp-modal-backdrop fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 16, scale: 0.98 }}
          className="tp-modal-panel w-full sm:max-w-lg max-h-[92dvh] overflow-y-auto rounded-t-3xl sm:rounded-2xl p-5 sm:p-6 pb-[max(1.25rem,env(safe-area-inset-bottom))]"
        >
          <div className="flex items-center justify-between mb-4 gap-2">
            <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-[#1a1205] bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] px-3 py-1 rounded-full">
              Guide {currentStep + 1}/{TUTORIAL_STEPS.length}
            </span>
            <button
              type="button"
              onClick={finishTutorial}
              className="text-[#f5e6a8]/70 hover:text-[#f5e6a8] text-xs font-medium tp-btn-ghost px-3 py-1.5 cursor-pointer"
            >
              Skip
            </button>
          </div>

          <div className="flex items-start gap-3 mb-3">
            <div className="w-11 h-11 sm:w-12 sm:h-12 shrink-0 rounded-2xl border border-[#d4af37]/50 bg-black/35 flex items-center justify-center text-[#d4af37]">
              <StepIcon className="w-5 h-5 sm:w-6 sm:h-6" />
            </div>
            <h3 className="font-display text-lg sm:text-xl font-extrabold text-[#f5e6a8] leading-snug pt-1">
              {TUTORIAL_STEPS[currentStep].title}
            </h3>
          </div>

          <p className="text-[#f5e6a8]/80 leading-relaxed text-sm min-h-[72px] mb-5">
            {TUTORIAL_STEPS[currentStep].content}
          </p>

          <div className="flex items-center justify-center gap-1.5 mb-5 flex-wrap">
            {TUTORIAL_STEPS.map((_, idx) => (
              <div
                key={idx}
                className={`h-1.5 rounded-full transition-all duration-300 ${
                  idx === currentStep ? 'w-6 bg-[#d4af37]' : 'w-1.5 bg-[#d4af37]/25'
                }`}
              />
            ))}
          </div>

          <div className="flex items-center justify-between gap-3">
            <button
              type="button"
              disabled={currentStep === 0}
              onClick={() => setCurrentStep((prev) => Math.max(0, prev - 1))}
              className="tp-btn-ghost px-4 py-2.5 text-sm font-semibold disabled:opacity-30 disabled:pointer-events-none cursor-pointer"
            >
              Back
            </button>

            <button
              type="button"
              onClick={handleNext}
              className="tp-btn-gold flex-1 py-2.5 px-5 text-sm flex items-center justify-center gap-2 cursor-pointer"
            >
              {currentStep === TUTORIAL_STEPS.length - 1 ? (
                <>
                  Start Playing <Check className="w-4 h-4" />
                </>
              ) : (
                <>
                  Next <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
