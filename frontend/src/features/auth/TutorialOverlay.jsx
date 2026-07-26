import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, ArrowRight, Check, X, Shield, Coins, Flame, Layers, Eye, Award } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';

const TUTORIAL_STEPS = [
  {
    title: 'Step 1: Welcome to Teen Patti!',
    icon: Sparkles,
    content: 'Welcome to the premier realtime multiplayer Teen Patti platform! Let us quickly walk you through how to play and win.',
  },
  {
    title: 'Step 2: Your Wallet Balance',
    icon: Coins,
    content: 'Every new player starts with ₹1,000 in demo chips! You can top up anytime using the "Add Demo Chips" button in your wallet.',
  },
  {
    title: 'Step 3: Boot Amount',
    icon: Flame,
    content: 'Before cards are dealt, every seated player automatically contributes the Boot Amount to start the round.',
  },
  {
    title: 'Step 4: Table Pot',
    icon: Layers,
    content: 'All bets and boot contributions accumulate in the central Pot. The winner of the hand claims the entire Pot!',
  },
  {
    title: 'Step 5: Your 3 Dealt Cards',
    icon: Eye,
    content: 'You receive 3 cards dealt face down from a single 52-card deck shuffled using secure server-side randomness.',
  },
  {
    title: 'Step 6: Playing Blind',
    icon: Shield,
    content: 'Starting blind means you play without viewing your cards. Blind bets require 1x the current base stake.',
  },
  {
    title: 'Step 7: Seeing Your Cards',
    icon: Eye,
    content: 'Click "See Cards" anytime on your turn to view your cards. Once Seen, your bet requirement becomes 2x the base stake.',
  },
  {
    title: 'Step 8: Chaal (Continue)',
    icon: ArrowRight,
    content: 'Clicking Chaal matches the required bet to stay in the hand and advance turn to the next player.',
  },
  {
    title: 'Step 9: Raise',
    icon: Flame,
    content: 'Clicking Raise increases the base stake unit for all players, raising the stakes for the rest of the round.',
  },
  {
    title: 'Step 10: Pack (Fold)',
    icon: X,
    content: 'If your cards are weak or strategy calls for it, click Pack to fold. If all opponents Pack, you win automatically!',
  },
  {
    title: 'Step 11: Side Show',
    icon: Layers,
    content: 'When 3+ Seen players remain, you may request a Side Show with the previous Seen player to compare hands privately.',
  },
  {
    title: 'Step 12: Show',
    icon: Award,
    content: 'When only 2 active players remain, click Show to reveal cards. The player with the higher-ranked hand wins the Pot!',
  },
  {
    title: 'Step 13: Hand Rankings',
    icon: Sparkles,
    content: 'Rankings: 1. Trail (Trio) > 2. Pure Sequence (Straight Flush) > 3. Sequence (Straight) > 4. Color (Flush) > 5. Pair > 6. High Card.',
  },
  {
    title: 'Step 14: You Are Ready!',
    icon: Check,
    content: 'Congratulations! You are now fully prepared to join a table and compete in realtime multiplayer Teen Patti.',
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
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/85 backdrop-blur-lg">
        <motion.div
          initial={{ opacity: 0, scale: 0.9, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: 20 }}
          className="w-full max-w-lg bg-slate-900 border border-amber-500/30 rounded-3xl shadow-2xl p-6 relative overflow-hidden"
        >
          {/* Header Glow */}
          <div className="absolute -top-20 -right-20 w-48 h-48 bg-amber-500/15 rounded-full blur-3xl pointer-events-none" />

          {/* Top Bar */}
          <div className="flex items-center justify-between mb-4">
            <span className="text-xs font-semibold uppercase tracking-wider text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-1 rounded-full">
              Interactive Guide ({currentStep + 1} / {TUTORIAL_STEPS.length})
            </span>
            <button
              onClick={finishTutorial}
              className="text-slate-400 hover:text-slate-200 text-xs font-medium bg-slate-800/60 hover:bg-slate-800 px-3 py-1 rounded-full transition"
            >
              Skip Guide
            </button>
          </div>

          {/* Icon & Title */}
          <div className="flex items-center gap-3 mb-3">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-amber-400">
              <StepIcon className="w-6 h-6" />
            </div>
            <h3 className="text-xl font-bold text-slate-100">{TUTORIAL_STEPS[currentStep].title}</h3>
          </div>

          {/* Content */}
          <p className="text-slate-300 leading-relaxed text-sm min-h-[80px] mb-6">
            {TUTORIAL_STEPS[currentStep].content}
          </p>

          {/* Progress Indicator Dots */}
          <div className="flex items-center justify-center gap-1.5 mb-6">
            {TUTORIAL_STEPS.map((_, idx) => (
              <div
                key={idx}
                className={`h-1.5 rounded-full transition-all duration-300 ${
                  idx === currentStep ? 'w-6 bg-amber-400' : 'w-1.5 bg-slate-700'
                }`}
              />
            ))}
          </div>

          {/* Bottom Actions */}
          <div className="flex items-center justify-between gap-3">
            <button
              disabled={currentStep === 0}
              onClick={() => setCurrentStep((prev) => Math.max(0, prev - 1))}
              className="px-4 py-2.5 rounded-xl border border-slate-700 hover:border-slate-600 text-slate-300 text-sm font-semibold disabled:opacity-30 disabled:pointer-events-none transition"
            >
              Back
            </button>

            <button
              onClick={handleNext}
              className="flex-1 py-2.5 px-5 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold text-sm shadow-lg shadow-amber-500/20 flex items-center justify-center gap-2 transition"
            >
              {currentStep === TUTORIAL_STEPS.length - 1 ? (
                <>
                  Start Playing <Check className="w-4 h-4" />
                </>
              ) : (
                <>
                  Next Step <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
