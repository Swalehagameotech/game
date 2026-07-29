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
const SUIT_FROM_SYMBOL = { s: 'SPADES', h: 'HEARTS', d: 'DIAMONDS', c: 'CLUBS' };
const RANK_FROM_SYMBOL = {
  2: 'TWO', 3: 'THREE', 4: 'FOUR', 5: 'FIVE', 6: 'SIX', 7: 'SEVEN', 8: 'EIGHT',
  9: 'NINE', 10: 'TEN', J: 'JACK', Q: 'QUEEN', K: 'KING', A: 'ACE',
};

const parseCardShort = (code) => {
  if (!code || typeof code !== 'string') return null;
  const normalized = code.trim();
  if (normalized.length < 2) return null;
  const suitSymbol = normalized.slice(-1).toLowerCase();
  const rankSymbol = normalized.slice(0, -1).toUpperCase();
  const suit = SUIT_FROM_SYMBOL[suitSymbol];
  const rank = RANK_FROM_SYMBOL[rankSymbol] || rankSymbol;
  if (!suit || !rank) return null;
  return { suit, rank };
};

const normalizeCard = (card) => {
  if (!card) return null;
  if (typeof card === 'string') return parseCardShort(card);
  if (card.suit && card.rank) return card;
  return null;
};

const parseHandsMap = (hands) => {
  if (!hands || typeof hands !== 'object') return {};
  const out = {};
  Object.entries(hands).forEach(([userId, cards]) => {
    if (!Array.isArray(cards)) return;
    out[userId] = cards.map(normalizeCard).filter(Boolean);
  });
  return out;
};

const renderCardSymbol = (suit) => {
  switch (suit) {
    case 'HEARTS': return { symbol: '♥', color: 'text-rose-500' };
    case 'DIAMONDS': return { symbol: '♦', color: 'text-rose-500' };
    case 'SPADES': return { symbol: '♠', color: 'text-slate-900' };
    case 'CLUBS': return { symbol: '♣', color: 'text-slate-900' };
    default: return { symbol: '?', color: 'text-slate-500' };
  }
};

const formatRankLabel = (rank) => {
  if (!rank) return '?';
  const labels = {
    ACE: 'A', KING: 'K', QUEEN: 'Q', JACK: 'J', TEN: '10',
    NINE: '9', EIGHT: '8', SEVEN: '7', SIX: '6', FIVE: '5',
    FOUR: '4', THREE: '3', TWO: '2',
  };
  return labels[rank] || rank;
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

    const applyTablePayload = (data) => {
      if (!data || cancelled) return;
      updateGameState((prev) => mergeGameState(prev, data, user));
    };

    // REST shell first, then live projection so refresh restores turn/cards/actions.
    Promise.allSettled([
      axiosClient.get(`/tables/${tableId}`),
      axiosClient.get(`/tables/${tableId}/live`),
    ]).then(([detailResult, liveResult]) => {
      if (cancelled) return;
      if (detailResult.status === 'fulfilled') {
        applyTablePayload(detailResult.value.data?.data || detailResult.value.data);
      }
      if (liveResult.status === 'fulfilled') {
        applyTablePayload(liveResult.value.data?.data || liveResult.value.data);
      } else if (detailResult.status === 'rejected') {
        console.error('Error fetching table details:', detailResult.reason);
      }
    }).finally(() => {
      if (!cancelled) setIsTableLoading(false);
    });

    const handleWsMessage = (message) => {
      if (!message?.type) return;

      if (message.type === 'GAME_STATE_UPDATE' || message.type === 'STATE_UPDATE') {
        const state = message.payload || message.state;
        updateGameState((prev) => {
          const merged = mergeGameState(prev, state, user);
          // Synthesize pending show modal from live projection when STOMP event was missed.
          if (merged?.status === 'SHOW' && state?.pendingShow && !merged.pendingShow) {
            return { ...merged, pendingShow: state.pendingShow };
          }
          if (merged?.status === 'SHOW'
              && (merged.allowedActions || []).includes('SHOW_ACCEPT')
              && !merged.pendingShow
              && state?.pendingShow) {
            return { ...merged, pendingShow: state.pendingShow };
          }
          return merged;
        });
        return;
      }
      if (message.type === 'ACTION_REJECTED') {
        setWsError(message.reason || 'Action rejected');
        setTimeout(() => setWsError(''), 4000);
        return;
      }

      // Mirror STOMP gameplay events delivered over raw /ws/game
      window.dispatchEvent(new CustomEvent('realtime', {
        detail: {
          eventType: message.type,
          payload: message.payload ?? message,
          destination: `/topic/tables/${tableId}`,
        },
      }));
    };

    if (accessToken) {
      wsGameService.connect(accessToken);
      const unsub = wsGameService.subscribe(handleWsMessage);
      const unsubOpen = wsGameService.onOpen(() => {
        wsGameService.sendMessage('JOIN_TABLE', tableId, {});
      });

      return () => {
        cancelled = true;
        unsub();
        unsubOpen();
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
      const isUserQueueEvent = [
        RealTimeEventType.SHOW_REQUEST,
        RealTimeEventType.SHOW_REQUESTED,
        RealTimeEventType.PLAYER_CARDS_REVEALED_TO_SELF,
        RealTimeEventType.BETTING_STATE,
      ].includes(event.eventType);
      const matchesTable =
        event.destination?.includes(`/tables/${tableId}`)
        || payloadTableId === tableId
        || payload === tableId
        || (isUserQueueEvent && (payloadTableId == null || payloadTableId === tableId));

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
          const turnUserId = typeof payload === 'object' && payload !== null
            ? (payload.activeUserId || payload.currentTurnUserId || null)
            : null;
          if (!turnUserId && typeof payload !== 'object') break;
          const turnPatch = {
            tableId,
            currentTurnPlayerId: turnUserId,
            currentTurnUserId: turnUserId,
            activeUserId: turnUserId,
            activeDisplayName: payload?.activeDisplayName || payload?.displayName,
            currentTurnSeatIndex: payload?.seatIndex ?? payload?.currentTurnSeatIndex,
            turnTimeoutSeconds: payload?.durationSeconds ?? payload?.turnTimeoutSeconds,
            turnSecondsRemaining: payload?.turnSecondsRemaining ?? payload?.durationSeconds,
            turnDeadlineAt: payload?.turnDeadlineAt,
            myTurn: Boolean(turnUserId && user?.id && turnUserId === user.id),
          };
          if (payload?.dealerSeatIndex != null) turnPatch.dealerSeatIndex = payload.dealerSeatIndex;
          if (Array.isArray(payload?.activePlayerIds)) turnPatch.activePlayerIds = payload.activePlayerIds;
          if (Array.isArray(payload?.blindPlayerIds)) turnPatch.blindPlayerIds = payload.blindPlayerIds;
          if (Array.isArray(payload?.seenPlayerIds)) turnPatch.seenPlayerIds = payload.seenPlayerIds;
          if (Array.isArray(payload?.packedPlayerIds)) turnPatch.packedPlayerIds = payload.packedPlayerIds;
          updateGameState((prev) => mergeGameState(prev, turnPatch, user));
          if (turnPatch.turnSecondsRemaining != null) {
            setLocalTurnSeconds(turnPatch.turnSecondsRemaining);
          }
          break;
        }
        case RealTimeEventType.TURN_ENDED: {
          const endedUserId = typeof payload === 'string' ? payload : payload?.userId;
          updateGameState((prev) => mergeGameState(prev, {
            turnSecondsRemaining: 0,
            turnDeadlineAt: null,
            ...(endedUserId && user?.id === endedUserId ? { myTurn: false } : {}),
          }, user));
          if (endedUserId && user?.id === endedUserId) {
            setLocalTurnSeconds(0);
          }
          break;
        }
        case RealTimeEventType.BETTING_STATE:
          if (payload && typeof payload === 'object') {
            const isMine = !payload.userId || payload.userId === user?.id;
            updateGameState((prev) => mergeGameState(prev, {
              potPaise: payload.potPaise,
              currentBaseStakePaise: payload.currentBaseStakePaise,
              ...(isMine ? {
                requiredBetPaise: payload.requiredBetPaise,
                blindAmountPaise: payload.blindAmountPaise,
                chaalAmountPaise: payload.chaalAmountPaise,
                showCostPaise: payload.showCostPaise,
                sideShowCostPaise: payload.sideShowCostPaise,
                minRaiseBetPaise: payload.minRaiseBetPaise,
                raiseOptionsPaise: payload.raiseOptionsPaise,
                maxBetPaise: payload.maxBetPaise,
                playerContributedPaise: payload.playerContributedPaise,
                walletBalancePaise: payload.walletBalancePaise,
                playerState: payload.playerState,
                turnTimerSeconds: payload.turnTimerSeconds,
                blindSeenRatio: payload.blindSeenRatio,
                myTurn: payload.myTurn,
                allowedActions: payload.allowedActions,
              } : {}),
            }, user));
          }
          break;
        case RealTimeEventType.PLAYER_SEEN_CARDS:
        case RealTimeEventType.SEEN_PLAYED: {
          const seenUserId = typeof payload === 'string'
            ? payload
            : (payload?.playerId || payload?.userId);
          if (!seenUserId) break;
          updateGameState((prev) => {
            if (!prev) return prev;
            const players = (prev.players || []).map((p) => (
              p.userId === seenUserId
                ? {
                    ...p,
                    status: 'SEEN',
                    // Never invent opponent card values from status events
                    cards: p.userId === user?.id ? p.cards : [],
                  }
                : p
            ));
            const seenPlayerIds = [...new Set([...(prev.seenPlayerIds || []), seenUserId])];
            const blindPlayerIds = (prev.blindPlayerIds || []).filter((id) => id !== seenUserId);
            return mergeGameState(prev, {
              players,
              seenPlayerIds,
              blindPlayerIds,
            }, user);
          });
          break;
        }
        case RealTimeEventType.WINNER_DECLARED:
          if (payload && typeof payload === 'object') {
            setLocalTurnSeconds(0);
            updateGameState((prev) => mergeGameState(prev, {
              winnerSnapshot: payload,
              status: 'ROUND_END',
              myTurn: false,
              allowedActions: [],
              turnSecondsRemaining: 0,
              turnDeadlineAt: null,
              currentTurnPlayerId: null,
            }, user));
          }
          break;
        case RealTimeEventType.SHOW_ENABLED:
          // Backend signal only — buttons come from allowedActions / BETTING_STATE.
          break;
        case RealTimeEventType.ROUND_FINISHED: {
          const seconds = typeof payload === 'number' ? payload : payload?.nextRoundInSeconds ?? 0;
          updateGameState((prev) => mergeGameState(prev, {
            status: seconds > 0 ? 'NEXT_ROUND' : 'ROUND_END',
            countdownSeconds: seconds,
            nextRoundSeconds: seconds,
          }, user));
          break;
        }
        case RealTimeEventType.NEXT_ROUND_COUNTDOWN: {
          const seconds = typeof payload === 'number'
            ? payload
            : (payload?.secondsRemaining ?? payload?.countdownSeconds ?? 0);
          updateGameState((prev) => mergeGameState(prev, {
            status: 'NEXT_ROUND',
            countdownSeconds: seconds,
            nextRoundSeconds: seconds,
          }, user));
          break;
        }
        case RealTimeEventType.NEXT_ROUND_STARTED:
        case RealTimeEventType.GAME_STARTED:
        case RealTimeEventType.GAME_RUNNING:
          updateGameState((prev) => {
            const resetPlayers = (prev?.players || []).map((p) => ({
              ...p,
              status: 'BLIND',
              cards: [],
              cardCount: 3,
            }));
            return mergeGameState(prev, {
              winnerSnapshot: null,
              handOutcome: null,
              pendingShow: null,
              revealedHands: null,
              countdownSeconds: 0,
              nextRoundSeconds: 0,
              status: 'RUNNING',
              potPaise: typeof payload === 'object' && payload?.potPaise != null
                ? payload.potPaise
                : (prev?.bootAmountPaise ? prev.bootAmountPaise * resetPlayers.length : prev?.potPaise),
              bootAmountPaise: typeof payload === 'object' && payload?.bootPaise != null
                ? payload.bootPaise
                : prev?.bootAmountPaise,
              players: resetPlayers,
              seenPlayerIds: [],
              packedPlayerIds: [],
              blindPlayerIds: resetPlayers.map((p) => p.userId),
              myTurn: false,
              allowedActions: [],
              ...(typeof payload === 'object' && payload !== null ? payload : {}),
            }, user, { forceResetHands: true });
          });
          break;
        case RealTimeEventType.TABLE_WAITING_FOR_PLAYERS:
          updateGameState((prev) => mergeGameState(prev, {
            status: 'WAITING',
            countdownSeconds: 0,
            nextRoundSeconds: 0,
            winnerSnapshot: null,
          }, user));
          break;
        case RealTimeEventType.WALLET_SETTLED:
          if (payload?.winnerUserId === user?.id && payload?.winnerBalanceAfterPaise != null) {
            window.dispatchEvent(new CustomEvent('wallet:updated', {
              detail: { balancePaise: payload.winnerBalanceAfterPaise },
            }));
          }
          break;
        case RealTimeEventType.TABLE_UPDATED:
        case RealTimeEventType.TABLE_STATUS_CHANGED: {
          if (!payload || typeof payload !== 'object') break;
          // Never merge raw Table entity seats into hand view — that wiped SEEN cards.
          const safePatch = {
            status: payload.status,
            countdownSeconds: payload.countdownSeconds,
            potPaise: payload.potPaise,
            currentTurnPlayerId: payload.currentTurnPlayerId || payload.currentTurnUserId,
            currentTurnUserId: payload.currentTurnUserId || payload.currentTurnPlayerId,
            dealerSeatIndex: payload.dealerSeatIndex,
          };
          if (Array.isArray(payload.seenPlayerIds)) safePatch.seenPlayerIds = payload.seenPlayerIds;
          if (Array.isArray(payload.blindPlayerIds)) safePatch.blindPlayerIds = payload.blindPlayerIds;
          if (Array.isArray(payload.packedPlayerIds)) safePatch.packedPlayerIds = payload.packedPlayerIds;
          if (Array.isArray(payload.activePlayerIds)) safePatch.activePlayerIds = payload.activePlayerIds;
          if (Array.isArray(payload.disconnectedPlayerIds)) safePatch.disconnectedPlayerIds = payload.disconnectedPlayerIds;
          if (payload.hostId) safePatch.hostId = payload.hostId;
          Object.keys(safePatch).forEach((k) => safePatch[k] === undefined && delete safePatch[k]);
          if (Object.keys(safePatch).length) {
            updateGameState((prev) => mergeGameState(prev, safePatch, user));
          }
          break;
        }
        case RealTimeEventType.HOST_CHANGED: {
          const newHostId = payload?.hostId || payload?.hostUserId;
          if (!newHostId) break;
          updateGameState((prev) => mergeGameState(prev, {
            hostId: newHostId,
          }, user));
          break;
        }
        case RealTimeEventType.PLAYER_DISCONNECTED: {
          const disconnectedId = payload?.userId || (typeof payload === 'string' ? payload : null);
          if (!disconnectedId) break;
          updateGameState((prev) => {
            const disconnectedPlayerIds = [...new Set([...(prev?.disconnectedPlayerIds || []), disconnectedId])];
            const players = (prev?.players || []).map((p) => (
              p.userId === disconnectedId ? { ...p, connected: false } : p
            ));
            return mergeGameState(prev, { disconnectedPlayerIds, players }, user);
          });
          break;
        }
        case RealTimeEventType.PLAYER_RECONNECTED: {
          const reconnectedId = payload?.userId || (typeof payload === 'string' ? payload : null);
          if (!reconnectedId) break;
          updateGameState((prev) => {
            const disconnectedPlayerIds = (prev?.disconnectedPlayerIds || []).filter((id) => id !== reconnectedId);
            const players = (prev?.players || []).map((p) => (
              p.userId === reconnectedId ? { ...p, connected: true } : p
            ));
            return mergeGameState(prev, { disconnectedPlayerIds, players }, user);
          });
          break;
        }
        case RealTimeEventType.PLAYER_LEFT: {
          const leftId = payload?.userId || (typeof payload === 'string' ? payload : null);
          if (!leftId) break;
          updateGameState((prev) => {
            const players = (prev?.players || []).filter((p) => p.userId !== leftId);
            const disconnectedPlayerIds = (prev?.disconnectedPlayerIds || []).filter((id) => id !== leftId);
            const seatedPlayerIds = (prev?.seatedPlayerIds || []).filter((id) => id !== leftId);
            return mergeGameState(prev, { players, disconnectedPlayerIds, seatedPlayerIds }, user);
          });
          break;
        }
        case RealTimeEventType.PLAYER_PACKED:
        case RealTimeEventType.PACK_PLAYED: {
          const packedId = payload?.userId;
          if (packedId) {
            if (packedId === user?.id) {
              setLocalTurnSeconds(0);
            }
            updateGameState((prev) => {
              const players = (prev?.players || []).map((p) => (
                p.userId === packedId ? { ...p, status: 'PACKED' } : p
              ));
              const packedPlayerIds = [...new Set([...(prev?.packedPlayerIds || []), packedId])];
              const selfPacked = packedId === user?.id;
              return mergeGameState(prev, {
                players,
                packedPlayerIds,
                ...(selfPacked ? {
                  myTurn: false,
                  allowedActions: [],
                  turnSecondsRemaining: 0,
                  turnDeadlineAt: null,
                } : {}),
              }, user);
            });
          }
          break;
        }
        case RealTimeEventType.SIDE_SHOW_REQUESTED: {
          const targetId = payload?.targetUserId;
          const requesterId = payload?.requesterUserId;
          if (targetId && targetId === user?.id) {
            setWsError('Side Show requested — Accept or Reject');
            updateGameState((prev) => mergeGameState(prev, {
              pendingSideShow: { requesterId, targetId },
              allowedActions: ['SIDE_SHOW_ACCEPT', 'SIDE_SHOW_REJECT'],
              myTurn: true,
            }, user));
          } else if (requesterId === user?.id) {
            setWsError('Waiting for Side Show response…');
            updateGameState((prev) => mergeGameState(prev, {
              pendingSideShow: { requesterId, targetId },
              allowedActions: [],
              myTurn: false,
            }, user));
          }
          break;
        }
        case RealTimeEventType.SIDE_SHOW_ACCEPTED:
        case RealTimeEventType.SIDE_SHOW_REJECTED:
          setWsError('');
          updateGameState((prev) => mergeGameState(prev, {
            pendingSideShow: null,
          }, user));
          wsGameService.sendMessage('JOIN_TABLE', tableId, {});
          break;
        case RealTimeEventType.SHOW_REQUEST:
        case RealTimeEventType.SHOW_REQUESTED: {
          const targetId = payload?.targetUserId;
          const requesterId = payload?.requesterId || payload?.requesterUserId;
          const requesterName = payload?.requesterDisplayName || 'Opponent';
          if (targetId && targetId === user?.id) {
            setWsError('');
            updateGameState((prev) => mergeGameState(prev, {
              status: 'SHOW',
              pendingShow: {
                requesterId,
                targetId,
                requesterDisplayName: requesterName,
              },
              allowedActions: ['SHOW_ACCEPT'],
              myTurn: true,
              potPaise: payload?.potPaise ?? prev?.potPaise,
            }, user));
          } else if (requesterId === user?.id) {
            setWsError(`Waiting for ${payload?.targetDisplayName || 'opponent'} to accept Show…`);
            updateGameState((prev) => mergeGameState(prev, {
              status: 'SHOW',
              pendingShow: { requesterId, targetId },
              allowedActions: [],
              myTurn: false,
              potPaise: payload?.potPaise ?? prev?.potPaise,
            }, user));
          }
          break;
        }
        case RealTimeEventType.PLAYER_CARDS_REVEALED_TO_SELF: {
          const selfId = payload?.userId;
          const rawCards = payload?.cards;
          if (selfId !== user?.id || !Array.isArray(rawCards)) break;
          const cards = rawCards.map(normalizeCard).filter(Boolean);
          updateGameState((prev) => {
            const players = (prev?.players || []).map((p) => (
              p.userId === selfId
                ? { ...p, status: 'SEEN', cards, cardCount: cards.length || 3 }
                : p
            ));
            const seenPlayerIds = [...new Set([...(prev?.seenPlayerIds || []), selfId])];
            const blindPlayerIds = (prev?.blindPlayerIds || []).filter((id) => id !== selfId);
            return mergeGameState(prev, {
              players,
              seenPlayerIds,
              blindPlayerIds,
            }, user);
          });
          break;
        }
        case RealTimeEventType.SHOW_ACCEPTED:
          setWsError('');
          updateGameState((prev) => mergeGameState(prev, {
            pendingShow: null,
            allowedActions: [],
            myTurn: false,
          }, user));
          break;
        case RealTimeEventType.FINAL_HANDS_REVEALED: {
          const parsedHands = parseHandsMap(payload?.hands);
          const winnerId = payload?.winnerId;
          if (!Object.keys(parsedHands).length) break;
          updateGameState((prev) => {
            const players = (prev?.players || []).map((p) => {
              const revealed = parsedHands[p.userId];
              if (!revealed?.length) return p;
              return {
                ...p,
                status: 'SEEN',
                cards: revealed,
                cardCount: revealed.length,
                handRevealed: true,
                isWinner: p.userId === winnerId,
              };
            });
            return mergeGameState(prev, {
              players,
              revealedHands: parsedHands,
              pendingShow: null,
              status: 'ROUND_END',
              allowedActions: [],
              myTurn: false,
            }, user);
          });
          break;
        }
        case RealTimeEventType.POT_UPDATED: {
          const pot = typeof payload === 'number' ? payload : payload?.potPaise ?? payload?.potTotal;
          if (pot != null) {
            updateGameState((prev) => mergeGameState(prev, { potPaise: pot }, user));
          }
          break;
        }
        case RealTimeEventType.BET_UPDATED:
        case RealTimeEventType.CURRENT_BET_UPDATED:
          if (payload && typeof payload === 'object') {
            updateGameState((prev) => mergeGameState(prev, {
              currentBaseStakePaise: payload.currentBaseStakePaise,
              potPaise: payload.potPaise,
            }, user));
          }
          break;
        case RealTimeEventType.WALLET_UPDATED:
          if (typeof payload === 'number') {
            window.dispatchEvent(new CustomEvent('wallet:updated', {
              detail: { balancePaise: payload },
            }));
            updateGameState((prev) => mergeGameState(prev, { walletBalancePaise: payload }, user));
          }
          break;
        case RealTimeEventType.PLAYER_STATE_UPDATED:
        case RealTimeEventType.GAME_STATE_UPDATED:
          if (payload && typeof payload === 'object') {
            updateGameState((prev) => mergeGameState(prev, payload, user));
          }
          break;
        case RealTimeEventType.ACTION_REJECTED:
          setWsError(typeof payload === 'string' ? payload : payload?.reason || 'Action rejected');
          setTimeout(() => setWsError(''), 4000);
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
  }, [tableId, user?.id, updateGameState]);

  const sendPlayerAction = async (actionType, multiplier = 1) => {
    if (!tableId || actionLoading) return;
    setActionLoading(true);
    setWsError('');

    try {
      if (actionType === 'SEE_CARDS') {
        try {
          const res = await axiosClient.post(`/tables/${tableId}/see-cards`);
          const data = res.data?.data || res.data;
          if (data?.cards) {
            updateGameState((prev) => mergeGameState(prev, {
              status: prev?.status || 'RUNNING',
              seenPlayerIds: [...new Set([...(prev?.seenPlayerIds || []), user?.id].filter(Boolean))],
              blindPlayerIds: (prev?.blindPlayerIds || []).filter((id) => id !== user?.id),
              players: (prev?.players || []).map((p) => (
                p.userId === user?.id
                  ? { ...p, status: 'SEEN', cards: data.cards, cardCount: data.cards.length || 3 }
                  : p
              )),
            }, user));
          }
          // Refresh authoritative buttons/amounts after see.
          try {
            const betRes = await axiosClient.get(`/tables/${tableId}/betting-state`);
            const betting = betRes.data?.data || betRes.data;
            if (betting) {
              updateGameState((prev) => mergeGameState(prev, {
                ...betting,
                myTurn: betting.myTurn,
                allowedActions: betting.allowedActions,
              }, user));
            }
          } catch { /* ignore */ }
          wsGameService.sendMessage('JOIN_TABLE', tableId, {});
          return;
        } catch (err) {
          const msg = err.response?.data?.message || err.response?.data?.error || err.message;
          setWsError(msg || 'See Cards failed');
          setTimeout(() => setWsError(''), 5000);
        }
      }

      const amountPaise = actionType === 'RAISE'
        ? (Array.isArray(gameState?.raiseOptionsPaise) && gameState.raiseOptionsPaise.length
            ? gameState.raiseOptionsPaise[0]
            : (gameState?.minRaiseBetPaise || 0))
        : 0; // Blind/Chaal/Show/Pack/SideShow amounts are server-authoritative

      const restActionType = (actionType === 'BLIND') ? 'PLAY_BLIND' : actionType;

      // Prefer REST so Blind/Chaal/Raise/Show work even if WS action path fails.
      try {
        const res = await axiosClient.post(`/tables/${tableId}/actions`, {
          actionType: restActionType,
          amountPaise,
        });
        const betting = res.data?.data || res.data;
        if (betting && typeof betting === 'object') {
          const selfPacked = restActionType === 'PACK' || betting.playerState === 'PACKED';
          if (selfPacked) {
            setLocalTurnSeconds(0);
          }
          updateGameState((prev) => mergeGameState(prev, {
            potPaise: betting.potPaise ?? prev?.potPaise,
            currentBaseStakePaise: betting.currentBaseStakePaise ?? prev?.currentBaseStakePaise,
            requiredBetPaise: betting.requiredBetPaise,
            blindAmountPaise: betting.blindAmountPaise,
            chaalAmountPaise: betting.chaalAmountPaise,
            showCostPaise: betting.showCostPaise,
            sideShowCostPaise: betting.sideShowCostPaise,
            minRaiseBetPaise: betting.minRaiseBetPaise,
            raiseOptionsPaise: betting.raiseOptionsPaise,
            walletBalancePaise: betting.walletBalancePaise,
            playerState: betting.playerState,
            myTurn: selfPacked ? false : betting.myTurn,
            allowedActions: betting.allowedActions || [],
            ...(selfPacked ? {
              turnSecondsRemaining: 0,
              turnDeadlineAt: null,
            } : {}),
            players: (prev?.players || []).map((p) => (
              p.userId === user?.id && betting.playerState
                ? { ...p, status: betting.playerState }
                : p
            )),
          }, user));
          if (betting.walletBalancePaise != null) {
            window.dispatchEvent(new CustomEvent('wallet:updated', {
              detail: { balancePaise: betting.walletBalancePaise },
            }));
          }
        }
        wsGameService.sendMessage('JOIN_TABLE', tableId, {});
        return;
      } catch (err) {
        const msg = err.response?.data?.message || err.response?.data?.error || err.message;
        console.warn('REST action failed, trying WS:', msg);
        // Show friendly ₹ amounts when balance is insufficient
        const friendly = typeof msg === 'string'
          ? msg.replace(/(\d+)\s*paise/gi, (_, p) => `₹${(Number(p) / 100).toFixed(0)}`)
          : msg;
        setWsError(friendly || 'Action failed');
        setTimeout(() => setWsError(''), 6000);
        // Don't fall through to WS for balance errors — it will fail the same way
        if (String(msg || '').toLowerCase().includes('insufficient')) {
          return;
        }
      }

      const sent = wsGameService.sendMessage(restActionType, tableId, {
        amountPaise,
      });
      if (!sent) {
        setWsError('Not connected — reconnecting…');
        if (accessToken) wsGameService.connect(accessToken);
      }
    } finally {
      setTimeout(() => setActionLoading(false), 400);
    }
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
  const isMyTurn = Boolean(currentTurnUserId && user?.id && currentTurnUserId === user.id);
  const myPlayer = players.find((p) => p.userId === user?.id);
  const myStatus = myPlayer?.status || 'BLIND';
  const potRupees = (gameState?.potPaise || 0) / 100;
  const requiredBetRupees = (gameState?.requiredBetPaise || 0) / 100;
  const blindBetRupees = (gameState?.blindAmountPaise || 0) / 100;
  const chaalBetRupees = (gameState?.chaalAmountPaise || 0) / 100;
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
  // Status drives "hand over" — do not treat a stale winnerSnapshot as game-over while RUNNING.
  const handEnded = status === 'ROUND_END' || status === 'WAITING' || status === 'NEXT_ROUND' || status === 'CLOSED';
  const handInProgress = isActiveHandStatus(status);
  const revealedHands = gameState?.revealedHands || {};
  const showdownRevealed = handEnded && Object.keys(revealedHands).length > 0;
  const pendingShow = gameState?.pendingShow;
  const isShowTarget = pendingShow?.targetId === user?.id;
  const showWinnerBanner = Boolean((winnerSnapshot || handOutcome) && handEnded);
  const turnPlayer = players.find((p) => p.userId === currentTurnUserId);
  const turnPlayerName = gameState?.activeDisplayName
    || turnPlayer?.displayName
    || (currentTurnUserId === user?.id ? (user?.displayName || 'You') : null)
    || (currentTurnUserId ? `Player` : null);
  const canAct = (action) => {
    if (!handInProgress) return false;
    return allowedActions.includes(action);
  };
  const dealerSeatIndex = gameState?.dealerSeatIndex ?? -1;

  useEffect(() => {
    const deadline = gameState?.turnDeadlineAt;
    const amPacked = myStatus === 'PACKED';
    if (amPacked || !deadline || !handInProgress || !isMyTurn) {
      setLocalTurnSeconds(amPacked ? 0 : (gameState?.turnSecondsRemaining ?? 0));
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
  }, [gameState?.turnDeadlineAt, gameState?.turnSecondsRemaining, handInProgress, isMyTurn, myStatus]);

  const turnDisplaySeconds = (myStatus === 'PACKED' || !isMyTurn)
    ? 0
    : (localTurnSeconds || gameState?.turnSecondsRemaining || 0);

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
            {(status === 'COUNTDOWN' || status === 'NEXT_ROUND' || countdownSeconds > 0) && (
              <div className="flex flex-col items-center gap-1">
                {status === 'NEXT_ROUND' && (
                  <span className="text-xs text-emerald-300 font-black uppercase tracking-[0.2em]">
                    Next Round In
                  </span>
                )}
                <div className="text-5xl font-black text-amber-400 font-mono tabular-nums leading-none drop-shadow-[0_0_12px_rgba(251,191,36,0.45)]">
                  {countdownSeconds}
                </div>
                <span className="text-[10px] text-amber-200/80 font-semibold uppercase tracking-wider">
                  {status === 'NEXT_ROUND' ? 'seconds' : 'starting soon'}
                </span>
              </div>
            )}
            {handInProgress && currentTurnUserId && (
              <div className="mt-1 px-4 py-2 rounded-xl bg-amber-500/15 border border-amber-400/40 w-full">
                <p className="text-[10px] uppercase tracking-[0.25em] font-black text-amber-300/90">
                  {isMyTurn ? 'Your Turn' : 'Waiting for'}
                </p>
                <p className="text-lg sm:text-xl font-black text-amber-100 truncate">
                  {isMyTurn ? (user?.displayName || 'You') : turnPlayerName}
                </p>
                {turnDisplaySeconds > 0 && (
                  <p className="text-sm font-mono font-bold text-amber-300 tabular-nums mt-0.5">
                    {turnDisplaySeconds}s
                  </p>
                )}
              </div>
            )}
            {!isTableLoading && isPrivateTable && isHost && canStart && !handInProgress && status !== 'COUNTDOWN' && status !== 'NEXT_ROUND' && (
              <button
                onClick={handleStartGame}
                disabled={startLoading}
                className="mt-1 px-5 py-2 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/30 disabled:opacity-60 cursor-pointer"
              >
                {startLoading ? 'Starting…' : status === 'ROUND_END' ? 'Start Next Round' : 'Start Game'}
              </button>
            )}
            {!isTableLoading && isPrivateTable && !isHost && canStart && !handInProgress && status !== 'COUNTDOWN' && status !== 'NEXT_ROUND' && (
              <span className="text-[10px] text-amber-300/80 font-semibold">Waiting for host to start</span>
            )}
            {!isTableLoading && !isPrivateTable && canStart && !handInProgress && status !== 'COUNTDOWN' && status !== 'NEXT_ROUND' && (
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
            const isTableHost = hostId && player.userId === hostId;
            const isDisconnected = (gameState?.disconnectedPlayerIds || []).includes(player.userId)
              || player.connected === false;

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
                    {isTableHost && !handInProgress && (
                      <span className="px-1.5 py-0.5 rounded-full text-[8px] font-black uppercase bg-amber-500/20 text-amber-300 border border-amber-500/30">
                        Host
                      </span>
                    )}
                    {isDisconnected && player.status !== 'PACKED' && (
                      <span className="px-1.5 py-0.5 rounded-full text-[8px] font-black uppercase bg-rose-500/15 text-rose-300 border border-rose-500/30">
                        Reconnecting…
                      </span>
                    )}
                  </div>

                  {/* Player Display Name */}
                  <span className="font-bold text-xs text-slate-100 truncate max-w-[110px]">
                    {player.displayName || `Player ${index + 1}`} {isMe && '(You)'}
                  </span>

                  {/* 3-Card Hand Display */}
                  <div className={`flex gap-1.5 my-2 ${player.isWinner ? 'ring-2 ring-amber-400 rounded-xl p-1' : ''}`}>
                    {(() => {
                      const visibleCards = showdownRevealed
                        ? (revealedHands[player.userId] || player.cards || [])
                        : (isMe ? (player.cards || []) : []);
                      if (visibleCards.length > 0) {
                        return visibleCards.map((card, cIdx) => {
                          const { symbol, color } = renderCardSymbol(card.suit);
                          return (
                            <div
                              key={`${player.userId}-${cIdx}`}
                              className="w-8 h-12 rounded-lg bg-slate-100 border border-slate-300 shadow-md flex flex-col items-center justify-between p-1 font-bold text-xs"
                            >
                              <span className={`leading-none ${color}`}>{formatRankLabel(card.rank)}</span>
                              <span className={`text-sm ${color}`}>{symbol}</span>
                            </div>
                          );
                        });
                      }
                      return Array.from({ length: player.cardCount || 3 }).map((_, cIdx) => (
                        <div
                          key={cIdx}
                          className="w-7 h-11 rounded-lg bg-gradient-to-br from-amber-700 to-amber-900 border border-amber-500/40 shadow-sm flex items-center justify-center text-[10px] text-amber-300 font-extrabold"
                        >
                          ♠
                        </div>
                      ));
                    })()}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Show Request Modal — challenged player must accept */}
      <AnimatePresence>
        {pendingShow && isShowTarget && handInProgress && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          >
            <motion.div
              initial={{ scale: 0.9, y: 16 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.95, y: 8 }}
              className="w-full max-w-md rounded-2xl border border-amber-500/40 bg-slate-950 p-6 shadow-2xl text-center"
            >
              <Sparkles className="w-10 h-10 text-amber-400 mx-auto mb-4" />
              <h3 className="text-xl font-black text-amber-100 mb-2">Show Requested</h3>
              <p className="text-sm text-slate-300 mb-6">
                {pendingShow.requesterDisplayName || 'Your opponent'} has requested a Show.
                {myStatus === 'SEEN' ? '' : ' Your cards are now visible to you only.'}
              </p>
              <button
                onClick={() => sendPlayerAction('SHOW_ACCEPT')}
                disabled={actionLoading || !canAct('SHOW_ACCEPT')}
                className="w-full px-6 py-3 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-sm rounded-xl shadow-lg disabled:opacity-50 cursor-pointer"
              >
                Accept Show
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Big Winner Banner */}
      <AnimatePresence>
        {showWinnerBanner && (
          <motion.div
            initial={{ opacity: 0, scale: 0.85, y: 24 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, y: -12 }}
            transition={{ type: 'spring', stiffness: 260, damping: 22 }}
            className="relative z-40 mb-4 mx-auto w-full max-w-3xl overflow-hidden rounded-3xl border-2 border-amber-400/60 bg-gradient-to-b from-amber-500/25 via-slate-950/95 to-slate-950 shadow-[0_0_60px_rgba(245,158,11,0.35)] backdrop-blur-xl"
          >
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(251,191,36,0.25),transparent_55%)] pointer-events-none" />
            <div className="relative px-6 py-8 sm:px-10 sm:py-10 text-center">
              <div className="flex items-center justify-center gap-3 text-amber-300 mb-4">
                <Award className="w-8 h-8 sm:w-10 sm:h-10" />
                <span className="font-black text-sm sm:text-base uppercase tracking-[0.35em]">
                  Winner
                </span>
                <Award className="w-8 h-8 sm:w-10 sm:h-10" />
              </div>

              <h2 className="text-4xl sm:text-6xl md:text-7xl font-black uppercase tracking-wide text-transparent bg-clip-text bg-gradient-to-b from-amber-200 via-yellow-300 to-amber-500 drop-shadow-[0_4px_24px_rgba(251,191,36,0.45)] leading-tight break-words">
                {winnerDisplayName
                  || `Player #${(winnerSnapshot?.winnerUserId || handOutcome?.winnerId || 'Winner').slice(-6)}`}
              </h2>

              <p className="mt-4 text-2xl sm:text-3xl font-black text-slate-50">
                Won ₹{winnerPayoutRupees.toFixed(2)}
              </p>
              <p className="mt-2 text-sm sm:text-base font-bold uppercase tracking-[0.2em] text-amber-300/90">
                {winningCategoryLabel}
              </p>

              {status === 'NEXT_ROUND' && countdownSeconds > 0 && (
                <p className="mt-5 text-lg sm:text-xl font-black text-emerald-300 tabular-nums">
                  Next round in {countdownSeconds}s
                </p>
              )}

              {winnerSnapshot?.winnerUserId === user?.id && (
                <p className="mt-3 text-sm text-emerald-300 font-semibold">
                  Credited to your wallet
                </p>
              )}

              {winnerSnapshot?.participants?.length > 1 && (
                <div className="mt-5 flex flex-wrap justify-center gap-2">
                  {winnerSnapshot.participants.map((p) => (
                    <span
                      key={p.userId}
                      className={`px-3 py-1.5 rounded-xl text-xs font-bold border ${
                        p.winner
                          ? 'bg-amber-500/25 border-amber-400 text-amber-100'
                          : 'bg-slate-900/80 border-slate-700 text-slate-400'
                      }`}
                    >
                      {p.displayName}: {p.handDescription || p.handRank}
                    </span>
                  ))}
                </div>
              )}
            </div>
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
                ? 'Your turn — you are Blind (1×). See Cards anytime, or Chaal to play blind.'
                : 'Your turn — you are Seen (2×). Chaal to continue or Raise to increase stake.'
              : myStatus === 'BLIND'
              ? 'You are Blind — click See Cards to reveal your hand (does not use your turn).'
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
        {!isMyTurn && handInProgress && currentTurnUserId && (
          <div className="mb-3 text-center text-[10px] text-slate-400 font-semibold">
            Waiting for <span className="text-amber-300">{turnPlayerName}</span>
            {turnDisplaySeconds > 0 && (
              <> — <span className="text-amber-300 font-mono">{turnDisplaySeconds}s</span></>
            )}
          </div>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* See Cards Action */}
          <div>
            {canAct('SEE_CARDS') ? (
              <button
                onClick={() => sendPlayerAction('SEE_CARDS')}
                disabled={actionLoading || !canAct('SEE_CARDS')}
                className="px-4 py-2.5 bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-cyan-600/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
              >
                <Eye className="w-4 h-4" />
                <span>See Cards</span>
              </button>
            ) : myStatus === 'SEEN' ? (
              <div className="px-3 py-1.5 bg-slate-950 border border-cyan-500/30 rounded-xl text-cyan-400 text-xs font-bold flex items-center gap-1.5">
                <Eye className="w-3.5 h-3.5" />
                <span>Cards Seen</span>
              </div>
            ) : (
              <div className="px-3 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-slate-500 text-xs font-bold">
                Packed
              </div>
            )}
          </div>

          {/* Betting Action Buttons */}
          <div className="flex items-center gap-2">
            {canAct('PACK') && (
            <button
              onClick={() => sendPlayerAction('PACK')}
              disabled={!canAct('PACK') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 hover:bg-rose-950/80 border border-rose-500/40 text-rose-400 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Ban className="w-4 h-4" />
              <span>Pack (Fold)</span>
            </button>
            )}

            {(canAct('CHAAL') || canAct('PLAY_BLIND') || canAct('BLIND')) && (
            <button
              onClick={() => sendPlayerAction((canAct('PLAY_BLIND') || canAct('BLIND')) ? 'BLIND' : 'CHAAL')}
              disabled={(!canAct('CHAAL') && !canAct('PLAY_BLIND') && !canAct('BLIND')) || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-black text-xs rounded-xl shadow-lg shadow-emerald-600/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <DollarSign className="w-4 h-4" />
              <span>
                {canAct('PLAY_BLIND') || canAct('BLIND')
                  ? `Blind (₹${blindBetRupees.toFixed(2)})`
                  : `Chaal (₹${chaalBetRupees.toFixed(2) || requiredBetRupees.toFixed(2)})`}
              </span>
            </button>
            )}

            {canAct('RAISE') && (
            <button
              onClick={() => sendPlayerAction('RAISE')}
              disabled={!canAct('RAISE') || actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <ArrowUpRight className="w-4 h-4" />
              <span>Raise (₹{minRaiseBetRupees.toFixed(2)})</span>
            </button>
            )}

            {canAct('SHOW_ACCEPT') && (
            <button
              onClick={() => sendPlayerAction('SHOW_ACCEPT')}
              disabled={actionLoading}
              className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-amber-500/20 flex items-center gap-2 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>Accept Show</span>
            </button>
            )}

            {canAct('SIDE_SHOW_ACCEPT') && (
            <button
              onClick={() => sendPlayerAction('SIDE_SHOW_ACCEPT')}
              disabled={actionLoading}
              className="px-4 py-2.5 bg-emerald-600 text-white font-bold text-xs rounded-xl"
            >
              Accept Side Show
            </button>
            )}
            {canAct('SIDE_SHOW_REJECT') && (
            <button
              onClick={() => sendPlayerAction('SIDE_SHOW_REJECT')}
              disabled={actionLoading}
              className="px-4 py-2.5 bg-rose-600 text-white font-bold text-xs rounded-xl"
            >
              Reject Side Show
            </button>
            )}

            {canAct('SIDE_SHOW_REQUEST') && (
            <button
              onClick={() => sendPlayerAction('SIDE_SHOW_REQUEST')}
              disabled={!canAct('SIDE_SHOW_REQUEST') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 border border-cyan-500/40 text-cyan-400 hover:bg-cyan-500/10 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Eye className="w-4 h-4" />
              <span>Side Show (₹{((gameState?.sideShowCostPaise || gameState?.chaalAmountPaise || 0) / 100).toFixed(0)})</span>
            </button>
            )}

            {canAct('SHOW') && (
            <button
              onClick={() => sendPlayerAction('SHOW')}
              disabled={!canAct('SHOW') || actionLoading}
              className="px-4 py-2.5 bg-slate-950 border border-amber-500/40 text-amber-400 hover:bg-amber-500/10 font-bold text-xs rounded-xl flex items-center gap-1.5 transition-all disabled:opacity-40 cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>Show (₹{((gameState?.showCostPaise || gameState?.chaalAmountPaise || 0) / 100).toFixed(0)})</span>
            </button>
            )}
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
