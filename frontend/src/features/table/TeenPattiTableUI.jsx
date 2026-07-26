import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Eye, DollarSign, ArrowUpRight, Ban, EyeOff, Award, LogOut, ShieldAlert, Sparkles, Coins } from 'lucide-react';
import { wsGameService } from '@/shared/api/websocketService';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import axiosClient from '@/shared/api/axiosClient';

// Card Helper Function
const renderCardSymbol = (suit) => {
  switch (suit) {
    case 'HEARTS': return { symbol: '♥', color: 'text-rose-500' };
    case 'DIAMONDS': return { symbol: '♦', color: 'text-rose-500' };
    case 'SPADES': return { symbol: '♠', color: 'text-slate-900' };
    case 'CLUBS': return { symbol: '♣', color: 'text-slate-900' };
    default: return { symbol: '?', color: 'text-slate-500' };
  }
};

export default function TeenPattiTableUI({ tableId, onLeaveTable }) {
  const { user, accessToken } = useAuth();
  const { gameState, updateGameState } = useGame();
  const [showLeaveModal, setShowLeaveModal] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    if (!accessToken || !tableId) return;

    // Connect to WebSocket and join game table
    wsGameService.connect(accessToken, (message) => {
      if (message.type === 'GAME_STATE_UPDATE' || message.type === 'STATE_UPDATE') {
        updateGameState(message.payload || message.state);
      }
    });

    // Send JOIN_TABLE message
    const timer = setTimeout(() => {
      wsGameService.sendMessage('JOIN_TABLE', tableId, {});
    }, 500);

    return () => {
      clearTimeout(timer);
    };
  }, [tableId, accessToken]);

  const sendPlayerAction = (actionType, raiseMultiplier = 1) => {
    if (!tableId || actionLoading) return;
    setActionLoading(true);

    wsGameService.sendMessage('GAME_ACTION', tableId, {
      actionType,
      raiseMultiplier,
    });

    setTimeout(() => setActionLoading(false), 500);
  };

  const handleConfirmLeave = async () => {
    try {
      await axiosClient.post(`/tables/${tableId}/leave`);
      wsGameService.sendMessage('LEAVE_TABLE', tableId, {});
    } catch (err) {
      console.error('Leave table error:', err);
    } finally {
      onLeaveTable();
    }
  };

  const players = gameState?.players || [];
  const currentTurnUserId = gameState?.currentTurnPlayerId;
  const isMyTurn = currentTurnUserId === user?.id;
  const myPlayer = players.find((p) => p.userId === user?.id);
  const myStatus = myPlayer?.status || 'BLIND';
  const potRupees = (gameState?.potPaise || 0) / 100;
  const requiredBetRupees = (gameState?.requiredBetPaise || 0) / 100;
  const handOutcome = gameState?.handOutcome;

  return (
    <div className="min-h-[85vh] flex flex-col justify-between p-4 relative overflow-hidden bg-slate-950 rounded-3xl border border-slate-800 shadow-2xl">
      {/* Table Felt Background & Ambient Glow */}
      <div className="absolute inset-0 bg-gradient-to-b from-slate-950 via-emerald-950/20 to-slate-950 pointer-events-none" />

      {/* Top Header Bar */}
      <div className="relative z-20 flex items-center justify-between bg-slate-900/80 backdrop-blur-md px-5 py-3 rounded-2xl border border-slate-800">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-amber-500 to-amber-300 text-slate-950 flex items-center justify-center font-extrabold text-lg shadow-md shadow-amber-500/20">
            ♠
          </div>
          <div>
            <h3 className="font-bold text-slate-100 text-sm">Teen Patti Table #{tableId?.slice(-6)}</h3>
            <span className="text-[10px] text-emerald-400 font-mono flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
              Live WebSocket Sync
            </span>
          </div>
        </div>

        <button
          onClick={() => setShowLeaveModal(true)}
          className="px-3.5 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-400 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all cursor-pointer"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Leave Table</span>
        </button>
      </div>

      {/* Main Oval Poker Table Felt */}
      <div className="relative z-10 my-auto py-8">
        <div className="w-full max-w-4xl mx-auto h-[420px] rounded-[200px] bg-gradient-to-b from-emerald-800 via-emerald-900 to-emerald-950 border-[10px] border-amber-800/80 shadow-[inset_0_0_80px_rgba(0,0,0,0.8)] relative flex items-center justify-center">
          {/* Inner Felt Border */}
          <div className="absolute inset-4 rounded-[180px] border-2 border-emerald-500/20 pointer-events-none" />

          {/* Central Pot Display */}
          <div className="text-center z-10 bg-slate-950/80 backdrop-blur-md border border-amber-500/40 px-6 py-4 rounded-3xl shadow-2xl">
            <div className="flex items-center justify-center gap-1.5 text-amber-400 mb-1">
              <Coins className="w-5 h-5 animate-bounce" />
              <span className="text-[10px] uppercase font-black tracking-widest text-amber-300">TOTAL POT</span>
            </div>
            <span className="text-3xl md:text-4xl font-black text-transparent bg-clip-text bg-gradient-to-r from-amber-300 via-amber-400 to-amber-500 font-mono">
              ₹{potRupees.toFixed(2)}
            </span>
            <span className="text-[10px] text-slate-400 block mt-1">({gameState?.potPaise || 0} Paise)</span>
          </div>

          {/* Player Seats Layout Around Oval */}
          {players.map((player, index) => {
            const isTurn = currentTurnUserId === player.userId;
            const isMe = player.userId === user?.id;

            // Position mapping for 6 seats around oval
            const seatPositions = [
              'bottom-[-35px] left-1/2 -translate-x-1/2', // Seat 0 (Me / Bottom)
              'bottom-[60px] left-[-30px]',                // Seat 1 (Bottom Left)
              'top-[60px] left-[-30px]',                   // Seat 2 (Top Left)
              'top-[-35px] left-1/2 -translate-x-1/2',    // Seat 3 (Top Center)
              'top-[60px] right-[-30px]',                  // Seat 4 (Top Right)
              'bottom-[60px] right-[-30px]',               // Seat 5 (Bottom Right)
            ];

            const posClass = seatPositions[index % seatPositions.length];

            return (
              <div key={player.userId} className={`absolute ${posClass} z-20 flex flex-col items-center`}>
                {/* Seat Box */}
                <div className={`p-3 rounded-2xl bg-slate-900/90 border backdrop-blur-md shadow-2xl transition-all flex flex-col items-center min-w-[140px] ${
                  isTurn ? 'border-amber-400 ring-4 ring-amber-500/30 scale-105' : 'border-slate-800'
                }`}>
                  {/* Player Status Badge */}
                  <div className="flex items-center gap-1.5 mb-2">
                    <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider ${
                      player.status === 'SEEN' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' :
                      player.status === 'PACKED' ? 'bg-slate-800 text-slate-500 border border-slate-700' :
                      'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                    }`}>
                      {player.status || 'BLIND'}
                    </span>
                    {isTurn && (
                      <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
                    )}
                  </div>

                  {/* Player Display Name */}
                  <span className="font-bold text-xs text-slate-100 truncate max-w-[110px]">
                    {player.displayName || `Player ${index + 1}`} {isMe && '(You)'}
                  </span>

                  {/* 3-Card Hand Display */}
                  <div className="flex gap-1.5 my-2">
                    {player.cards && player.cards.length > 0 ? (
                      player.cards.map((card, cIdx) => {
                        const { symbol, color } = renderCardSymbol(card.suit);
                        return (
                          <motion.div
                            key={cIdx}
                            initial={{ scale: 0.8, rotateY: 90 }}
                            animate={{ scale: 1, rotateY: 0 }}
                            className="w-8 h-12 rounded-lg bg-slate-100 border border-slate-300 shadow-md flex flex-col items-center justify-between p-1 font-bold text-xs"
                          >
                            <span className={`leading-none ${color}`}>{card.rank}</span>
                            <span className={`text-sm ${color}`}>{symbol}</span>
                          </motion.div>
                        );
                      })
                    ) : (
                      // Hidden Card Backs
                      [1, 2, 3].map((_, cIdx) => (
                        <div
                          key={cIdx}
                          className="w-7 h-11 rounded-lg bg-gradient-to-br from-amber-700 to-amber-900 border border-amber-500/40 shadow-sm flex items-center justify-center text-[10px] text-amber-300 font-extrabold"
                        >
                          ♠
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Hand Outcome Announcement Banner */}
      <AnimatePresence>
        {handOutcome && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="relative z-30 mb-4 p-4 bg-gradient-to-r from-amber-500/20 via-amber-500/10 to-amber-500/20 border border-amber-500/40 rounded-2xl text-center backdrop-blur-md shadow-2xl"
          >
            <div className="flex items-center justify-center gap-2 text-amber-400 mb-1">
              <Award className="w-6 h-6 animate-spin" />
              <span className="font-black text-lg uppercase tracking-wider">Hand Winner Announced!</span>
            </div>
            <h3 className="text-2xl font-black text-slate-100">
              Player #{handOutcome.winnerId ? handOutcome.winnerId.slice(-6) : 'Winner'} Won ₹{(handOutcome.winnerPayoutPaise / 100).toFixed(2)}!
            </h3>
            <p className="text-xs text-amber-300 font-mono mt-1">
              Winning Rank: {handOutcome.winningCategory || 'FOLD WIN'}
            </p>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Bottom Action Controls Bar */}
      <div className="relative z-20 bg-slate-900/90 backdrop-blur-xl border border-slate-800 p-4 rounded-2xl shadow-2xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* See Cards Action */}
          <div>
            {myStatus === 'BLIND' ? (
              <button
                onClick={() => sendPlayerAction('SEE_CARDS')}
                disabled={actionLoading}
                className="px-4 py-2.5 bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-cyan-600/20 flex items-center gap-2 transition-all cursor-pointer"
              >
                <Eye className="w-4 h-4" />
                <span>See Cards (Seen)</span>
              </button>
            ) : (
              <div className="px-3 py-1.5 bg-slate-950 border border-cyan-500/30 rounded-xl text-cyan-400 text-xs font-bold flex items-center gap-1.5">
                <Eye className="w-3.5 h-3.5" />
                <span>Cards Seen</span>
              </div>
            )}
          </div>

          {/* Betting Action Buttons */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => sendPlayerAction('PACK')}
              disabled={!isMyTurn || actionLoading}
              className="px-4 py-2.5 bg-slate-950 hover:bg-rose-950/80 border border-rose-500/40 text-rose-400 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Ban className="w-4 h-4" />
              <span>Pack (Fold)</span>
            </button>

            <button
              onClick={() => sendPlayerAction('CHAAL')}
              disabled={!isMyTurn || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-black text-xs rounded-xl shadow-lg shadow-emerald-600/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <DollarSign className="w-4 h-4" />
              <span>Chaal (₹{requiredBetRupees})</span>
            </button>

            <button
              onClick={() => sendPlayerAction('RAISE', 2)}
              disabled={!isMyTurn || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <ArrowUpRight className="w-4 h-4" />
              <span>Raise 2x</span>
            </button>

            <button
              onClick={() => sendPlayerAction('SHOW')}
              disabled={!isMyTurn || actionLoading}
              className="px-4 py-2.5 bg-slate-950 border border-amber-500/40 text-amber-400 hover:bg-amber-500/10 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>Show</span>
            </button>
          </div>
        </div>
      </div>

      {/* Leave Table Confirmation Modal */}
      <AnimatePresence>
        {showLeaveModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-sm bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl text-center"
            >
              <ShieldAlert className="w-12 h-12 text-rose-400 mx-auto mb-2" />
              <h3 className="text-xl font-bold text-slate-100">Leave Teen Patti Table?</h3>
              <p className="text-xs text-slate-400 my-2">
                Leaving mid-hand will automatically fold your current hand and forfeit any bets placed.
              </p>

              <div className="flex gap-2 mt-5">
                <button
                  onClick={() => setShowLeaveModal(false)}
                  className="flex-1 py-2.5 bg-slate-800 text-slate-300 text-xs font-bold rounded-xl hover:bg-slate-700"
                >
                  Stay in Game
                </button>
                <button
                  onClick={handleConfirmLeave}
                  className="flex-1 py-2.5 bg-rose-600 text-white text-xs font-bold rounded-xl hover:bg-rose-500 shadow-lg shadow-rose-600/20"
                >
                  Leave Table
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
