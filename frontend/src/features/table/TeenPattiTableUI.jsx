import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Eye, DollarSign, ArrowUpRight, Ban, EyeOff, Award, LogOut, ShieldAlert, Sparkles, Coins, BookOpen, Trash2 } from 'lucide-react';
import { wsGameService } from '@/shared/api/websocketService';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import RulebookModal from './RulebookModal';
import axiosClient from '@/shared/api/axiosClient';
import {
  normalizeGameState,
  mergeGameState,
  isActiveHandStatus,
  getWaitingBannerText,
} from './tableUtils';
import { RealTimeEventType } from '@/shared/api/realtimeEvents';
import stompService from '@/shared/api/stompService';

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
  const [showRulebook, setShowRulebook] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [startLoading, setStartLoading] = useState(false);
  const [startError, setStartError] = useState('');
  const [wsError, setWsError] = useState('');
  const [isTableLoading, setIsTableLoading] = useState(true);
  const [localTurnSeconds, setLocalTurnSeconds] = useState(0);

  useEffect(() => {
    if (!tableId) return;

    let cancelled = false;
    setIsTableLoading(true);

    axiosClient.get(`/tables/${tableId}`).then((res) => {
      if (cancelled) return;
      const data = res.data?.data || res.data;
      if (data) updateGameState(normalizeGameState(data, user));
    }).catch((err) => console.error('Error fetching table details:', err))
      .finally(() => {
        if (!cancelled) setIsTableLoading(false);
      });

    const handleWsMessage = (message) => {
      if (message.type === 'GAME_STATE_UPDATE' || message.type === 'STATE_UPDATE') {
        updateGameState((prev) => mergeGameState(prev, message.payload || message.state, user));
      }
      if (message.type === 'ACTION_REJECTED') {
        setWsError(message.reason || 'Action rejected');
        setTimeout(() => setWsError(''), 4000);
      }
    };

    if (accessToken) {
      wsGameService.connect(accessToken);
      const unsub = wsGameService.subscribe(handleWsMessage);

      const timer = setTimeout(() => {
        wsGameService.sendMessage('JOIN_TABLE', tableId, {});
      }, 500);

      return () => {
        cancelled = true;
        clearTimeout(timer);
        unsub();
      };
    }

    return () => {
      cancelled = true;
    };
  }, [tableId, accessToken, user?.id, updateGameState]);

  useEffect(() => {
    if (!tableId) return undefined;

    const applyCountdownPatch = (payload) => {
      const seconds = typeof payload === 'number'
        ? payload
        : payload?.countdownSeconds ?? payload?.seconds ?? 0;
      updateGameState((prev) => mergeGameState(prev, {
        countdownSeconds: seconds,
        status: seconds > 0 ? 'COUNTDOWN' : (prev?.status === 'COUNTDOWN' ? 'WAITING' : prev?.status),
      }, user));
    };

    const handleRealtime = (e) => {
      const event = e.detail;
      if (!event?.eventType) return;

      const payload = event.payload;
      const payloadTableId = typeof payload === 'object' && payload !== null ? payload.tableId : null;
      const matchesTable =
        event.destination?.includes(`/tables/${tableId}`)
        || payloadTableId === tableId
        || payload === tableId;

      if (!matchesTable) return;

      switch (event.eventType) {
        case RealTimeEventType.COUNTDOWN_STARTED:
          applyCountdownPatch(typeof payload === 'number' ? payload : payload?.countdownSeconds ?? 5);
          break;
        case RealTimeEventType.COUNTDOWN_TICK:
          applyCountdownPatch(payload);
          break;
        case RealTimeEventType.COUNTDOWN_CANCELLED:
          applyCountdownPatch(0);
          break;
        case RealTimeEventType.DEALER_SELECTED: {
          const dealerSeat = typeof payload === 'number' ? payload : payload?.dealerSeatIndex;
          if (dealerSeat != null) {
            updateGameState((prev) => mergeGameState(prev, { dealerSeatIndex: dealerSeat }, user));
          }
          break;
        }
        case RealTimeEventType.TURN_STARTED:
        case RealTimeEventType.TURN_CHANGED: {
          const turnPatch = typeof payload === 'object' && payload !== null
            ? {
                currentTurnPlayerId: payload.activeUserId || payload.currentTurnUserId,
                currentTurnSeatIndex: payload.seatIndex ?? payload.currentTurnSeatIndex,
                turnTimeoutSeconds: payload.durationSeconds ?? payload.turnTimeoutSeconds,
                turnSecondsRemaining: payload.turnSecondsRemaining ?? payload.durationSeconds,
                turnDeadlineAt: payload.turnDeadlineAt,
                dealerSeatIndex: payload.dealerSeatIndex,
                activePlayerIds: payload.activePlayerIds,
                blindPlayerIds: payload.blindPlayerIds,
                seenPlayerIds: payload.seenPlayerIds,
                packedPlayerIds: payload.packedPlayerIds,
              }
            : {};
          updateGameState((prev) => mergeGameState(prev, turnPatch, user));
          if (turnPatch.turnSecondsRemaining != null) {
            setLocalTurnSeconds(turnPatch.turnSecondsRemaining);
          }
          break;
        }
        case RealTimeEventType.TURN_ENDED:
          updateGameState((prev) => mergeGameState(prev, {
            turnSecondsRemaining: 0,
            turnDeadlineAt: null,
          }, user));
          setLocalTurnSeconds(0);
          break;
        case RealTimeEventType.BETTING_STATE:
          if (payload && typeof payload === 'object') {
            updateGameState((prev) => mergeGameState(prev, {
              potPaise: payload.potPaise,
              currentBaseStakePaise: payload.currentBaseStakePaise,
              requiredBetPaise: payload.requiredBetPaise,
              minRaiseBetPaise: payload.minRaiseBetPaise,
              maxBetPaise: payload.maxBetPaise,
              playerContributedPaise: payload.playerContributedPaise,
              blindSeenRatio: payload.blindSeenRatio,
              myTurn: payload.myTurn,
              allowedActions: payload.allowedActions,
            }, user));
          }
          break;
        case RealTimeEventType.WINNER_DECLARED:
          if (payload && typeof payload === 'object') {
            updateGameState((prev) => mergeGameState(prev, {
              winnerSnapshot: payload,
              status: 'ROUND_END',
            }, user));
          }
          break;
        case RealTimeEventType.WALLET_SETTLED:
          if (payload?.winnerUserId === user?.id && payload?.winnerBalanceAfterPaise != null) {
            window.dispatchEvent(new CustomEvent('wallet:updated', {
              detail: { balancePaise: payload.winnerBalanceAfterPaise },
            }));
          }
          break;
        case RealTimeEventType.TABLE_UPDATED:
          if (payload && (payload.countdownSeconds != null || payload.status)) {
            updateGameState((prev) => mergeGameState(prev, payload, user));
          }
          break;
        case RealTimeEventType.TABLE_STATUS_CHANGED:
          if (payload && (payload.countdownSeconds != null || payload.status)) {
            updateGameState((prev) => mergeGameState(prev, payload, user));
          }
          break;
        default:
          break;
      }
    };

    window.addEventListener('realtime', handleRealtime);
    const tableSub = stompService.subscribeTable(tableId);

    return () => {
      window.removeEventListener('realtime', handleRealtime);
      tableSub?.unsubscribe?.();
    };
  }, [tableId, user, updateGameState]);

  const sendPlayerAction = (actionType, multiplier = 1) => {
    if (!tableId || actionLoading) return;
    setActionLoading(true);

    const requiredBetPaise = gameState?.requiredBetPaise || 1000;
    const minRaiseBetPaise = gameState?.minRaiseBetPaise || requiredBetPaise * 2;
    const amountPaise = actionType === 'RAISE'
      ? minRaiseBetPaise
      : requiredBetPaise * multiplier;

    wsGameService.sendMessage(actionType, tableId, {
      amountPaise,
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

  const seatedIds = gameState?.seatedPlayerIds || gameState?.seatedPlayers?.map((p) => p.userId) || [];
  const rawPlayers = gameState?.players || [];
  const players = rawPlayers.length > 0
    ? rawPlayers
    : seatedIds.map((id, index) => ({
        userId: id,
        displayName: id === user?.id ? (user?.displayName || 'You') : `Player ${index + 1}`,
        status: 'BLIND',
        cards: [],
        cardCount: 0,
      }));

  const status = gameState?.status;
  const minPlayers = gameState?.minPlayers ?? 3;
  const maxPlayers = gameState?.maxPlayers ?? 6;
  const hostId = gameState?.hostId;
  const tableType = gameState?.tableType;
  const isPrivateTable = tableType === 'PRIVATE';
  const countdownSeconds = gameState?.countdownSeconds ?? 0;
  const isHost = Boolean(hostId && user?.id === hostId);
  const canStart = !isTableLoading
    && status
    && (status === 'WAITING' || status === 'ROUND_END')
    && players.length >= minPlayers;

  const handleStartGame = async () => {
    if (!isPrivateTable || !isHost || !canStart || startLoading) return;
    setStartLoading(true);
    setStartError('');
    try {
      const res = await axiosClient.post(`/tables/${tableId}/start`);
      const data = res.data?.data || res.data;
      if (data) {
        updateGameState(normalizeGameState(data, user));
      }
      wsGameService.sendMessage('JOIN_TABLE', tableId, {});
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Failed to start game';
      setStartError(msg);
    } finally {
      setStartLoading(false);
    }
  };

  const currentTurnUserId = gameState?.currentTurnPlayerId;
  const allowedActions = gameState?.allowedActions || [];
  const canAct = (action) => allowedActions.length === 0
    ? currentTurnUserId === user?.id
    : allowedActions.includes(action);
  const isMyTurn = gameState?.myTurn ?? currentTurnUserId === user?.id;
  const myPlayer = players.find((p) => p.userId === user?.id);
  const myStatus = myPlayer?.status || 'BLIND';
  const potRupees = (gameState?.potPaise || 0) / 100;
  const requiredBetRupees = (gameState?.requiredBetPaise || 0) / 100;
  const minRaiseBetRupees = (gameState?.minRaiseBetPaise || 0) / 100;
  const baseStakeRupees = (gameState?.currentBaseStakePaise || 0) / 100;
  const handOutcome = gameState?.handOutcome;
  const winnerSnapshot = gameState?.winnerSnapshot;
  const winnerDisplayName = winnerSnapshot?.winnerDisplayName
    || (handOutcome?.winnerId === user?.id ? (user?.displayName || 'You') : null);
  const winnerPayoutRupees = ((winnerSnapshot?.payoutPaise ?? handOutcome?.winnerPayoutPaise) || 0) / 100;
  const winningCategoryLabel = winnerSnapshot?.winningHandDescription
    || winnerSnapshot?.winningCategory
    || handOutcome?.winningCategory
    || 'FOLD WIN';
  const handEnded = Boolean(handOutcome || winnerSnapshot || status === 'ROUND_END');
  const handInProgress = isActiveHandStatus(status) && !handEnded;
  const turnDisplaySeconds = localTurnSeconds || gameState?.turnSecondsRemaining || 0;
  const dealerSeatIndex = gameState?.dealerSeatIndex ?? -1;

  useEffect(() => {
    const deadline = gameState?.turnDeadlineAt;
    if (!deadline || !handInProgress) {
      setLocalTurnSeconds(gameState?.turnSecondsRemaining ?? 0);
      return undefined;
    }

    const computeRemaining = () => {
      const ms = new Date(deadline).getTime() - Date.now();
      return Math.max(0, Math.ceil(ms / 1000));
    };

    setLocalTurnSeconds(computeRemaining());
    const interval = setInterval(() => {
      setLocalTurnSeconds(computeRemaining());
    }, 1000);

    return () => clearInterval(interval);
  }, [gameState?.turnDeadlineAt, gameState?.turnSecondsRemaining, handInProgress]);

  const statusBannerText = isTableLoading
    ? 'Loading table…'
    : getWaitingBannerText({
        status: status || 'WAITING',
        playerCount: players.length,
        minPlayers,
        maxPlayers,
        isHost,
        canStart,
        tableType,
        countdownSeconds,
      });

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
            <div className="flex items-center gap-2">
              <h3 className="font-bold text-slate-100 text-sm">Teen Patti Table #{tableId?.slice(-6)}</h3>
              <button
                onClick={() => {
                  const codeToCopy = gameState?.inviteCode || tableId;
                  if (codeToCopy) navigator.clipboard.writeText(codeToCopy);
                }}
                className="px-2.5 py-0.5 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 font-mono text-[10px] font-bold rounded-lg transition-all cursor-pointer"
                title="Click to copy Room Code"
              >
                Copy Code: {gameState?.inviteCode || tableId?.slice(-6)}
              </button>
            </div>
            <span className="text-[10px] text-emerald-400 font-mono flex items-center gap-1 mt-0.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
              Live WebSocket Sync
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowRulebook(true)}
            className="px-3.5 py-1.5 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all cursor-pointer"
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Rules</span>
          </button>

          {hostId === user?.id && (
            <button
              onClick={async () => {
                if (window.confirm('Are you sure you want to delete this table? All seated players will be refunded.')) {
                  try {
                    await axiosClient.delete(`/tables/${tableId}`);
                  } catch (e) {
                    console.error('Delete table error:', e);
                  } finally {
                    onLeaveTable();
                  }
                }
              }}
              className="px-3.5 py-1.5 bg-rose-600/20 hover:bg-rose-600/30 border border-rose-500/40 text-rose-300 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all cursor-pointer"
              title="Delete Table (Creator Only)"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Delete Table</span>
            </button>
          )}

          <button
            onClick={() => setShowLeaveModal(true)}
            className="px-3.5 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-400 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Leave Table</span>
          </button>
        </div>
      </div>

      {/* Main Oval Poker Table Felt */}
      <div className="relative z-10 my-auto py-8">
        <div className="w-full max-w-4xl mx-auto h-[420px] rounded-[200px] bg-gradient-to-b from-emerald-800 via-emerald-900 to-emerald-950 border-[10px] border-amber-800/80 shadow-[inset_0_0_80px_rgba(0,0,0,0.8)] relative flex items-center justify-center">
          {/* Inner Felt Border */}
          <div className="absolute inset-4 rounded-[180px] border-2 border-emerald-500/20 pointer-events-none" />

          {/* Status banner — host-controlled start */}
          <div className="absolute top-8 z-20 bg-slate-900/90 border border-amber-500/30 px-5 py-2.5 rounded-2xl backdrop-blur-md shadow-xl text-center flex flex-col items-center gap-2 min-w-[280px]">
            <div className="flex items-center gap-2">
              <span className={`w-2.5 h-2.5 rounded-full ${handInProgress ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400 animate-ping'}`} />
              <span className="text-xs font-bold text-slate-200">{statusBannerText}</span>
            </div>
            {(status === 'COUNTDOWN' || countdownSeconds > 0) && (
              <div className="text-3xl font-black text-amber-400 font-mono tabular-nums">
                {countdownSeconds}
              </div>
            )}
            {!isTableLoading && isPrivateTable && isHost && canStart && !handInProgress && status !== 'COUNTDOWN' && (
              <button
                onClick={handleStartGame}
                disabled={startLoading}
                className="mt-1 px-5 py-2 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/30 disabled:opacity-60 cursor-pointer"
              >
                {startLoading ? 'Starting…' : status === 'ROUND_END' ? 'Start Next Round' : 'Start Game'}
              </button>
            )}
            {!isTableLoading && isPrivateTable && !isHost && canStart && !handInProgress && status !== 'COUNTDOWN' && (
              <span className="text-[10px] text-amber-300/80 font-semibold">Waiting for host to start</span>
            )}
            {!isTableLoading && !isPrivateTable && canStart && !handInProgress && status !== 'COUNTDOWN' && (
              <span className="text-[10px] text-emerald-300/80 font-semibold">Auto-start when countdown completes</span>
            )}
            {startError && <span className="text-[10px] text-rose-400">{startError}</span>}
          </div>

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
            {handInProgress && baseStakeRupees > 0 && (
              <span className="text-[10px] text-emerald-300 block mt-1">
                Stake unit: ₹{baseStakeRupees.toFixed(2)}
              </span>
            )}
          </div>

          {/* Player Seats Layout Around Oval */}
          {players.map((player, index) => {
            const isTurn = currentTurnUserId === player.userId;
            const isMe = player.userId === user?.id;
            const isDealer = dealerSeatIndex >= 0 && index === dealerSeatIndex;

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
              <div key={player.userId || `seat-${index}`} className={`absolute ${posClass} z-20 flex flex-col items-center`}>
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
                    {isDealer && (
                      <span className="px-1.5 py-0.5 rounded-full text-[8px] font-black uppercase bg-violet-500/20 text-violet-300 border border-violet-500/30">
                        D
                      </span>
                    )}
                  </div>

                  {/* Player Display Name */}
                  <span className="font-bold text-xs text-slate-100 truncate max-w-[110px]">
                    {player.displayName || `Player ${index + 1}`} {isMe && '(You)'}
                  </span>

                  {/* 3-Card Hand Display — own cards only */}
                  <div className="flex gap-1.5 my-2">
                    {isMe && player.cards && player.cards.length > 0 ? (
                      player.cards.map((card, cIdx) => {
                        const { symbol, color } = renderCardSymbol(card.suit);
                        return (
                          <div
                            key={`${player.userId}-${cIdx}`}
                            className="w-8 h-12 rounded-lg bg-slate-100 border border-slate-300 shadow-md flex flex-col items-center justify-between p-1 font-bold text-xs"
                          >
                            <span className={`leading-none ${color}`}>{card.rank}</span>
                            <span className={`text-sm ${color}`}>{symbol}</span>
                          </div>
                        );
                      })
                    ) : (
                      Array.from({ length: player.cardCount || 3 }).map((_, cIdx) => (
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
        {handEnded && (winnerSnapshot || handOutcome) && (
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
              {winnerDisplayName || `Player #${(winnerSnapshot?.winnerUserId || handOutcome?.winnerId || 'Winner').slice(-6)}`} won ₹{winnerPayoutRupees.toFixed(2)}!
            </h3>
            <p className="text-xs text-amber-300 font-mono mt-1">
              {winningCategoryLabel}
            </p>
            {winnerSnapshot?.winnerUserId === user?.id && (
              <p className="text-[10px] text-emerald-300 font-semibold mt-2">
                Credited to your wallet — new balance updated live
              </p>
            )}
            {winnerSnapshot?.participants?.length > 1 && (
              <div className="mt-3 flex flex-wrap justify-center gap-2">
                {winnerSnapshot.participants.map((p) => (
                  <span
                    key={p.userId}
                    className={`px-2 py-1 rounded-lg text-[10px] font-bold border ${
                      p.winner
                        ? 'bg-amber-500/20 border-amber-400 text-amber-200'
                        : 'bg-slate-800 border-slate-700 text-slate-400'
                    }`}
                  >
                    {p.displayName}: {p.handDescription || p.handRank}
                  </span>
                ))}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Beginner Help Mode Contextual Hint Banner (First 5 Matches) */}
      {(user?.matchesPlayedCount ?? 0) < 5 && handInProgress && (
        <div className="relative z-20 mb-3 px-4 py-2 bg-gradient-to-r from-amber-500/15 via-amber-500/10 to-amber-500/15 border border-amber-500/30 rounded-xl text-center backdrop-blur-md">
          <span className="text-xs font-semibold text-amber-300 flex items-center justify-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5" />
            {isMyTurn
              ? myStatus === 'BLIND'
                ? 'It is your turn! You are playing Blind (1x stake). Click Chaal to continue or See Cards to view.'
                : 'It is your turn! You are Seen (2x stake). Click Chaal to continue or Raise to double stake.'
              : players.length === 2
              ? 'Only 2 players remain! You may click Show to reveal hands and claim the pot.'
              : 'Help Mode Active (Match ' + ((user?.matchesPlayedCount || 0) + 1) + '/5): Follow turns and manage your bets!'}
          </span>
        </div>
      )}

      {/* Bottom Action Controls Bar — only during active hand */}
      {handInProgress && (
      <div className="relative z-20 bg-slate-900/90 backdrop-blur-xl border border-slate-800 p-4 rounded-2xl shadow-2xl">
        {wsError && (
          <div className="mb-3 text-center text-xs text-rose-400 font-semibold">{wsError}</div>
        )}
        {isMyTurn && turnDisplaySeconds > 0 && (
          <div className="mb-3 text-center">
            <span className="text-[10px] uppercase font-black tracking-widest text-amber-400">Your turn</span>
            <div className="text-2xl font-black text-amber-300 font-mono tabular-nums">{turnDisplaySeconds}s</div>
          </div>
        )}
        {!isMyTurn && handInProgress && currentTurnUserId && turnDisplaySeconds > 0 && (
          <div className="mb-3 text-center text-[10px] text-slate-400 font-semibold">
            Turn timer: <span className="text-amber-300 font-mono">{turnDisplaySeconds}s</span>
          </div>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* See Cards Action */}
          <div>
            {myStatus === 'BLIND' ? (
              <button
                onClick={() => sendPlayerAction('SEE_CARDS')}
                disabled={actionLoading || !canAct('SEE_CARDS')}
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
              disabled={!canAct('PACK') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 hover:bg-rose-950/80 border border-rose-500/40 text-rose-400 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Ban className="w-4 h-4" />
              <span>Pack (Fold)</span>
            </button>

            <button
              onClick={() => sendPlayerAction('CHAAL')}
              disabled={!canAct('CHAAL') || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-black text-xs rounded-xl shadow-lg shadow-emerald-600/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <DollarSign className="w-4 h-4" />
              <span>Chaal (₹{requiredBetRupees})</span>
            </button>

            <button
              onClick={() => sendPlayerAction('RAISE')}
              disabled={!canAct('RAISE') || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <ArrowUpRight className="w-4 h-4" />
              <span>Raise (₹{minRaiseBetRupees.toFixed(2)})</span>
            </button>

            <button
              onClick={() => sendPlayerAction('SIDE_SHOW_REQUEST')}
              disabled={!canAct('SIDE_SHOW_REQUEST') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 border border-cyan-500/40 text-cyan-400 hover:bg-cyan-500/10 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Eye className="w-4 h-4" />
              <span>Side Show</span>
            </button>

            <button
              onClick={() => sendPlayerAction('SHOW')}
              disabled={!canAct('SHOW') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 border border-amber-500/40 text-amber-400 hover:bg-amber-500/10 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>Show</span>
            </button>
          </div>
        </div>
      </div>
      )}

      {!handInProgress && (
        <div className="relative z-20 bg-slate-900/60 border border-slate-800 p-4 rounded-2xl text-center text-xs text-slate-400">
          Betting controls unlock when the host starts the game.
        </div>
      )}

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

      {/* Teen Patti Rules & Hand Rankings Modal */}
      <RulebookModal isOpen={showRulebook} onClose={() => setShowRulebook(false)} />
    </div>
  );
}
