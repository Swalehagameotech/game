import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LogOut, ShieldAlert, Sparkles, BookOpen, Trash2,
  ArrowLeft, MessageCircle, Settings, Loader2,
} from 'lucide-react';
import { wsGameService } from '@/shared/api/websocketService';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import RulebookModal from './RulebookModal';
import axiosClient from '@/shared/api/axiosClient';
import {
  normalizeGameState,
  mergeGameState,
  isActiveHandStatus,
  isDealableHandStatus,
  getWaitingBannerText,
} from './tableUtils';
import { RealTimeEventType } from '@/shared/api/realtimeEvents';
import stompService from '@/shared/api/stompService';
import TableArena from './components/TableArena';
import WinnerEffects from './components/WinnerEffects';
import ActionBar from './components/ActionBar';
import { formatChipAmount } from './components/seatLayout';

// Card Helper Function (logic / WS parsing only — rendering uses SVG PlayingCard)
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

    const applyShowRequestPayload = (payload) => {
      const targetId = String(payload?.targetUserId || payload?.targetId || '');
      const requesterId = String(payload?.requesterId || payload?.requesterUserId || '');
      const me = String(user?.id || '');
      const requesterName = payload?.requesterDisplayName || 'Opponent';
      if (!targetId && !requesterId) return;
      if (targetId && me && targetId === me) {
        setWsError('');
        updateGameState((prev) => mergeGameState(prev, {
          status: 'SHOW',
          pendingShow: {
            requesterId,
            targetId,
            requesterDisplayName: requesterName,
          },
          allowedActions: ['SHOW_ACCEPT', 'SHOW_REJECT'],
          myTurn: true,
          potPaise: payload?.potPaise ?? prev?.potPaise,
        }, user));
      } else if (requesterId && me && requesterId === me) {
        setWsError(`Waiting for ${payload?.targetDisplayName || 'opponent'} to accept or decline Show…`);
        updateGameState((prev) => mergeGameState(prev, {
          status: 'SHOW',
          pendingShow: { requesterId, targetId, requesterDisplayName: requesterName },
          allowedActions: [],
          myTurn: false,
          potPaise: payload?.potPaise ?? prev?.potPaise,
        }, user));
      } else {
        updateGameState((prev) => mergeGameState(prev, {
          status: 'SHOW',
          pendingShow: {
            requesterId,
            targetId,
            requesterDisplayName: requesterName,
          },
          potPaise: payload?.potPaise ?? prev?.potPaise,
        }, user));
      }
    };

    const handleWsMessage = (message) => {
      if (!message?.type) return;

      if (message.type === 'GAME_STATE_UPDATE' || message.type === 'STATE_UPDATE') {
        const state = message.payload || message.state;
        updateGameState((prev) => {
          const merged = mergeGameState(prev, state, user);
          // Always honor pendingShow / winner from live projection
          if (state?.pendingShow) {
            const targetId = state.pendingShow.targetId || state.pendingShow.targetUserId;
            const isTarget = targetId && user?.id && String(targetId) === String(user.id);
            return {
              ...merged,
              status: 'SHOW',
              pendingShow: {
                requesterId: state.pendingShow.requesterId || state.pendingShow.requesterUserId,
                targetId,
                requesterDisplayName: state.pendingShow.requesterDisplayName || 'Opponent',
              },
              allowedActions: isTarget
                ? [...new Set([...(merged.allowedActions || []), ...(state.allowedActions || []), 'SHOW_ACCEPT', 'SHOW_REJECT'])]
                : (merged.allowedActions || []),
              myTurn: isTarget ? true : merged.myTurn,
            };
          }
          if (state?.winnerSnapshot && (state.status === 'ROUND_END' || state.status === 'NEXT_ROUND' || merged.status === 'ROUND_END' || merged.status === 'NEXT_ROUND')) {
            return {
              ...merged,
              winnerSnapshot: state.winnerSnapshot,
              handOutcome: state.handOutcome || merged.handOutcome,
              status: state.status === 'NEXT_ROUND' && (state.countdownSeconds > 0)
                ? 'NEXT_ROUND'
                : (merged.winnerSnapshot || state.winnerSnapshot ? (state.status || 'ROUND_END') : merged.status),
              pendingShow: null,
              // Don't invent a countdown during winner-display window
              countdownSeconds: state.countdownSeconds > 0
                ? state.countdownSeconds
                : (merged.countdownSeconds || 0),
            };
          }
          return merged;
        });
        return;
      }

      // Apply Show challenge instantly on raw WS — don't wait for STOMP / refresh.
      if (message.type === 'SHOW_REQUEST' || message.type === 'SHOW_REQUESTED') {
        applyShowRequestPayload(message.payload || message);
        return;
      }
      if (message.type === 'SHOW_ACCEPTED') {
        updateGameState((prev) => mergeGameState(prev, {
          pendingShow: null,
          allowedActions: [],
          myTurn: false,
        }, user, { clearPendingShow: true }));
        // Still fan-out so STOMP listeners can process winner/reveal events
      }
      if (message.type === 'FINAL_HANDS_REVEALED' || message.type === 'FinalHandsRevealed') {
        const payload = message.payload || message;
        const parsedHands = parseHandsMap(payload?.hands);
        const winnerId = payload?.winnerId || payload?.winnerUserId;
        if (Object.keys(parsedHands).length) {
          updateGameState((prev) => {
            const nextPlayers = (prev?.players || []).map((p) => {
              const revealed = parsedHands[p.userId];
              if (!revealed?.length) {
                return winnerId && p.userId === winnerId ? { ...p, isWinner: true } : p;
              }
              return {
                ...p,
                status: 'SEEN',
                cards: revealed,
                cardCount: revealed.length,
                handRevealed: true,
                isWinner: String(p.userId) === String(winnerId),
              };
            });
            return mergeGameState(prev, {
              players: nextPlayers,
              revealedHands: parsedHands,
              pendingShow: null,
              status: 'ROUND_END',
              allowedActions: [],
              myTurn: false,
            }, user, { clearPendingShow: true });
          });
        }
      }
      if (message.type === 'SHOW_REJECTED') {
        updateGameState((prev) => mergeGameState(prev, {
          pendingShow: null,
          status: 'RUNNING',
          myTurn: false,
          allowedActions: [],
        }, user, { clearPendingShow: true }));
      }
      if (message.type === 'WINNER_DECLARED' && message.payload) {
        const payload = message.payload;
        setLocalTurnSeconds(0);
        const payout = Number(
          payload.payoutPaise
          ?? payload.winnerPayoutPaise
          ?? payload.potPaise
          ?? 0,
        );
        updateGameState((prev) => mergeGameState(prev, {
          winnerSnapshot: {
            ...payload,
            payoutPaise: payout,
            winnerPayoutPaise: payout,
            winnerUserId: payload.winnerUserId || payload.winnerId,
            winnerId: payload.winnerId || payload.winnerUserId,
          },
          handOutcome: {
            winnerId: payload.winnerUserId || payload.winnerId,
            winnerPayoutPaise: payout,
            winningCategory: payload.winningCategory,
            notes: payload.winningHandDescription || payload.notes,
          },
          status: 'ROUND_END',
          countdownSeconds: 0,
          nextRoundSeconds: prev?.nextRoundSeconds > 0 ? prev.nextRoundSeconds : 60,
          myTurn: false,
          allowedActions: [],
          turnSecondsRemaining: 0,
          turnDeadlineAt: null,
          currentTurnPlayerId: null,
          pendingShow: null,
        }, user, { clearPendingShow: true }));
      }
      if (message.type === 'ROUND_FINISHED') {
        const payload = message.payload;
        const planned = typeof payload === 'number'
          ? payload
          : (payload?.nextRoundInSeconds ?? 60);
        updateGameState((prev) => mergeGameState(prev, {
          status: 'ROUND_END',
          countdownSeconds: 0,
          nextRoundSeconds: planned > 0 ? planned : (prev?.nextRoundSeconds || 60),
          myTurn: false,
          allowedActions: [],
          pendingShow: null,
        }, user, { clearPendingShow: true }));
      }
      if (message.type === 'NEXT_ROUND_COUNTDOWN') {
        const payload = message.payload;
        const seconds = typeof payload === 'number'
          ? payload
          : (payload?.secondsRemaining ?? payload?.countdownSeconds ?? 0);
        updateGameState((prev) => mergeGameState(prev, {
          status: seconds > 0 ? 'NEXT_ROUND' : prev?.status,
          countdownSeconds: seconds,
          nextRoundSeconds: seconds,
          winnerSnapshot: prev?.winnerSnapshot,
          handOutcome: prev?.handOutcome,
        }, user));
      }
      if (message.type === 'NEXT_ROUND_STARTED' || message.type === 'GAME_STARTED' || message.type === 'GAME_RUNNING') {
        // Fan-out to realtime handler below for full reset
      }
      if (message.type === 'BETTING_STATE' && message.payload) {
        const payload = message.payload;
        const isMine = !payload.userId || String(payload.userId) === String(user?.id);
        const actions = Array.isArray(payload.allowedActions) ? payload.allowedActions : [];
        const isShowRespond = actions.includes('SHOW_ACCEPT') || actions.includes('SHOW_REJECT');
        if (isMine && isShowRespond) {
          updateGameState((prev) => mergeGameState(prev, {
            status: 'SHOW',
            myTurn: true,
            allowedActions: [...new Set([...actions, 'SHOW_ACCEPT', 'SHOW_REJECT'])],
            potPaise: payload.potPaise ?? prev?.potPaise,
            pendingShow: prev?.pendingShow || {
              targetId: user?.id,
              requesterId: '',
              requesterDisplayName: 'Opponent',
            },
          }, user));
          axiosClient.get(`/tables/${tableId}/live`).then((res) => {
            const data = res.data?.data || res.data;
            if (data?.pendingShow) {
              applyShowRequestPayload({
                ...data.pendingShow,
                targetUserId: data.pendingShow.targetId || data.pendingShow.targetUserId,
                potPaise: data.potPaise,
              });
            }
          }).catch(() => {});
        }
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
      const payloadTableId = typeof payload === 'object' && payload !== null
        ? (payload.tableId || payload.table?.id)
        : null;
      const isUserQueueEvent = [
        RealTimeEventType.SHOW_REQUEST,
        RealTimeEventType.SHOW_REQUESTED,
        RealTimeEventType.PLAYER_CARDS_REVEALED_TO_SELF,
        RealTimeEventType.BETTING_STATE,
      ].includes(event.eventType);
      const isHandResultEvent = [
        RealTimeEventType.WINNER_DECLARED,
        RealTimeEventType.FINAL_HANDS_REVEALED,
        RealTimeEventType.SHOW_ACCEPTED,
        RealTimeEventType.SHOW_REJECTED,
        RealTimeEventType.ROUND_FINISHED,
        RealTimeEventType.SHOW_REQUEST,
        RealTimeEventType.SHOW_REQUESTED,
      ].includes(event.eventType);
      const matchesTable =
        event.destination?.includes(`/tables/${tableId}`)
        || event.destination?.includes(`/queue/game/`)
        || payloadTableId === tableId
        || payload === tableId
        || (isUserQueueEvent && (payloadTableId == null || payloadTableId === tableId))
        || (isHandResultEvent && (payloadTableId == null || payloadTableId === tableId));

      if (!matchesTable) return;

      switch (event.eventType) {
        case RealTimeEventType.STATE_UPDATE:
        case 'GAME_STATE_UPDATE': {
          const state = payload;
          if (!state || typeof state !== 'object') break;
          updateGameState((prev) => {
            const merged = mergeGameState(prev, state, user);
            if (state?.pendingShow) {
              const targetId = state.pendingShow.targetId || state.pendingShow.targetUserId;
              const isTarget = targetId && user?.id && String(targetId) === String(user.id);
              return {
                ...merged,
                status: 'SHOW',
                pendingShow: {
                  requesterId: state.pendingShow.requesterId || state.pendingShow.requesterUserId,
                  targetId,
                  requesterDisplayName: state.pendingShow.requesterDisplayName || 'Opponent',
                },
                allowedActions: isTarget
                  ? [...new Set([...(merged.allowedActions || []), ...(state.allowedActions || []), 'SHOW_ACCEPT', 'SHOW_REJECT'])]
                  : (merged.allowedActions || []),
                myTurn: isTarget ? true : merged.myTurn,
              };
            }
            if (state?.winnerSnapshot && (state.status === 'ROUND_END' || state.status === 'NEXT_ROUND'
              || merged.status === 'ROUND_END' || merged.status === 'NEXT_ROUND')) {
              return {
                ...merged,
                winnerSnapshot: {
                  ...state.winnerSnapshot,
                  payoutPaise: state.winnerSnapshot.payoutPaise
                    ?? state.winnerSnapshot.winnerPayoutPaise
                    ?? state.handOutcome?.winnerPayoutPaise
                    ?? merged.winnerSnapshot?.payoutPaise,
                  winnerPayoutPaise: state.winnerSnapshot.winnerPayoutPaise
                    ?? state.winnerSnapshot.payoutPaise,
                },
                handOutcome: state.handOutcome || merged.handOutcome,
                status: state.status || merged.status || 'ROUND_END',
              };
            }
            return merged;
          });
          break;
        }
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
            myTurn: Boolean(turnUserId && user?.id && turnUserId === user.id),
          };
          if (payload?.seatIndex != null || payload?.currentTurnSeatIndex != null) {
            turnPatch.currentTurnSeatIndex = payload.seatIndex ?? payload.currentTurnSeatIndex;
          }
          if (payload?.durationSeconds != null || payload?.turnTimeoutSeconds != null) {
            const dur = Number(payload.durationSeconds ?? payload.turnTimeoutSeconds);
            turnPatch.turnTimeoutSeconds = dur;
            turnPatch.turnDurationSeconds = dur;
          }
          const secs = payload?.turnSecondsRemaining ?? payload?.durationSeconds ?? payload?.turnTimeoutSeconds;
          if (secs != null && Number(secs) >= 0) {
            turnPatch.turnSecondsRemaining = Number(secs);
          }
          // Keep previous deadline unless backend sends one — or derive from seconds
          if (payload?.turnDeadlineAt) {
            turnPatch.turnDeadlineAt = payload.turnDeadlineAt;
          } else if (secs != null && Number(secs) > 0) {
            turnPatch.turnDeadlineAt = new Date(Date.now() + Number(secs) * 1000).toISOString();
          }
          if (payload?.dealerSeatIndex != null) turnPatch.dealerSeatIndex = payload.dealerSeatIndex;
          if (Array.isArray(payload?.activePlayerIds)) turnPatch.activePlayerIds = payload.activePlayerIds;
          if (Array.isArray(payload?.blindPlayerIds)) turnPatch.blindPlayerIds = payload.blindPlayerIds;
          if (Array.isArray(payload?.seenPlayerIds)) turnPatch.seenPlayerIds = payload.seenPlayerIds;
          if (Array.isArray(payload?.packedPlayerIds)) turnPatch.packedPlayerIds = payload.packedPlayerIds;
          updateGameState((prev) => mergeGameState(prev, turnPatch, user));
          if (turnPatch.turnSecondsRemaining != null) {
            setLocalTurnSeconds(turnPatch.turnSecondsRemaining);
          }
          // When it becomes your turn with two active, refresh Show / action buttons live.
          const myTurnNow = Boolean(turnUserId && user?.id && turnUserId === user.id);
          if (myTurnNow) {
            axiosClient.get(`/tables/${tableId}/betting-state`).then((res) => {
              const betting = res.data?.data || res.data;
              if (!betting) return;
              updateGameState((prev) => {
                const activeCount = (betting.activePlayerIds || prev?.activePlayerIds
                  || (prev?.players || []).filter((p) => p.status !== 'PACKED').map((p) => p.userId)).length;
                const actions = new Set(betting.allowedActions || []);
                if (activeCount === 2 && betting.myTurn) actions.add('SHOW');
                return mergeGameState(prev, {
                  ...betting,
                  myTurn: betting.myTurn,
                  allowedActions: [...actions],
                }, user);
              });
            }).catch(() => {});
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
            const isMine = !payload.userId || String(payload.userId) === String(user?.id);
            updateGameState((prev) => {
              const actions = Array.isArray(payload.allowedActions) ? [...payload.allowedActions] : [];
              const isShowRespond = actions.includes('SHOW_ACCEPT') || actions.includes('SHOW_REJECT');
              const showTarget = isShowRespond || (prev?.pendingShow?.targetId
                && user?.id
                && String(prev.pendingShow.targetId) === String(user.id));
              if (showTarget && !actions.includes('SHOW_ACCEPT')) actions.push('SHOW_ACCEPT');
              if (showTarget && !actions.includes('SHOW_REJECT')) actions.push('SHOW_REJECT');
              return mergeGameState(prev, {
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
                  myTurn: showTarget ? true : payload.myTurn,
                  allowedActions: actions,
                  ...(showTarget ? {
                    status: 'SHOW',
                    pendingShow: prev?.pendingShow || {
                      targetId: user?.id,
                      requesterId: '',
                      requesterDisplayName: 'Opponent',
                    },
                  } : {}),
                } : {}),
              }, user);
            });
            const actions = Array.isArray(payload.allowedActions) ? payload.allowedActions : [];
            if (isMine && (actions.includes('SHOW_ACCEPT') || actions.includes('SHOW_REJECT'))) {
              axiosClient.get(`/tables/${tableId}/live`).then((res) => {
                const data = res.data?.data || res.data;
                if (!data?.pendingShow) return;
                const tid = data.pendingShow.targetId || data.pendingShow.targetUserId;
                if (String(tid) !== String(user?.id)) return;
                updateGameState((prev) => mergeGameState(prev, {
                  status: 'SHOW',
                  pendingShow: {
                    requesterId: data.pendingShow.requesterId || data.pendingShow.requesterUserId,
                    targetId: tid,
                    requesterDisplayName: data.pendingShow.requesterDisplayName || 'Opponent',
                  },
                  allowedActions: ['SHOW_ACCEPT', 'SHOW_REJECT'],
                  myTurn: true,
                  potPaise: data.potPaise ?? prev?.potPaise,
                }, user));
              }).catch(() => {});
            }
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
            const payout = Number(
              payload.payoutPaise
              ?? payload.winnerPayoutPaise
              ?? payload.potPaise
              ?? 0,
            );
            updateGameState((prev) => mergeGameState(prev, {
              winnerSnapshot: {
                ...payload,
                payoutPaise: payout,
                winnerPayoutPaise: payout,
                winnerUserId: payload.winnerUserId || payload.winnerId,
                winnerId: payload.winnerId || payload.winnerUserId,
              },
              handOutcome: {
                winnerId: payload.winnerUserId || payload.winnerId,
                winnerPayoutPaise: payout,
                winningCategory: payload.winningCategory,
                notes: payload.winningHandDescription || payload.notes,
              },
              status: 'ROUND_END',
              countdownSeconds: 0,
              nextRoundSeconds: prev?.nextRoundSeconds > 0
                ? prev.nextRoundSeconds
                : (payload.nextRoundInSeconds || prev?.nextRoundInSeconds || 60),
              myTurn: false,
              allowedActions: [],
              turnSecondsRemaining: 0,
              turnDeadlineAt: null,
              currentTurnPlayerId: null,
              pendingShow: null,
            }, user, { clearPendingShow: true }));
          }
          break;
        case RealTimeEventType.JOKER_REVEALED:
          if (payload && typeof payload === 'object' && payload.jokerRank) {
            updateGameState((prev) => mergeGameState(prev, {
              jokerRank: payload.jokerRank,
            }, user));
          }
          break;
        case RealTimeEventType.DISCARD_PHASE_STARTED:
          updateGameState((prev) => mergeGameState(prev, {
            variantPhase: 'DISCARD',
          }, user));
          break;
        case RealTimeEventType.CARD_DISCARDED:
          updateGameState((prev) => {
            const selfId = user?.id;
            const discardedId = payload?.userId;
            const players = (prev?.players || []).map((p) => {
              if (p.userId === discardedId && discardedId === selfId) {
                const cards = Array.isArray(p.cards) ? p.cards : [];
                const idx = payload?.cardIndex;
                const nextCards = typeof idx === 'number' && idx >= 0 && idx < cards.length
                  ? cards.filter((_, i) => i !== idx)
                  : cards;
                return { ...p, cards: nextCards, cardCount: nextCards.length || Math.max(0, (p.cardCount || 0) - 1) };
              }
              if (p.userId === discardedId) {
                return { ...p, cardCount: Math.max(3, (p.cardCount || 4) - 1) };
              }
              return p;
            });
            return mergeGameState(prev, { players }, user);
          });
          break;
        case RealTimeEventType.AUCTION_STARTED:
          updateGameState((prev) => mergeGameState(prev, {
            variantPhase: 'AUCTION',
            auctionMinBidPaise: payload?.minBidPaise ?? prev?.auctionMinBidPaise ?? prev?.bootAmountPaise,
            auctionHighBidPaise: 0,
            auctionHighBidderId: null,
          }, user));
          break;
        case RealTimeEventType.AUCTION_BID:
          if (payload && typeof payload === 'object') {
            updateGameState((prev) => mergeGameState(prev, {
              auctionHighBidPaise: payload.highBidPaise ?? payload.amountPaise ?? prev?.auctionHighBidPaise,
              auctionHighBidderId: payload.highBidderId ?? payload.userId ?? prev?.auctionHighBidderId,
              potPaise: payload.potPaise ?? prev?.potPaise,
            }, user));
          }
          break;
        case RealTimeEventType.AUCTION_ENDED:
          updateGameState((prev) => mergeGameState(prev, {
            variantPhase: null,
            jokerRank: payload?.jokerRank ?? prev?.jokerRank,
            auctionHighBidPaise: payload?.highBidPaise ?? prev?.auctionHighBidPaise,
            potPaise: payload?.potPaise ?? prev?.potPaise,
          }, user));
          break;
        case RealTimeEventType.SHOW_ENABLED: {
          // Force-enable Show when exactly two remain (don't wait solely on racing BETTING_STATE).
          const activeIds = Array.isArray(payload?.activePlayerIds) ? payload.activePlayerIds : null;
          const count = payload?.activePlayerCount ?? activeIds?.length ?? 2;
          updateGameState((prev) => {
            const activePlayerIds = activeIds
              || (prev?.activePlayerIds || []).filter((id) => !(prev?.packedPlayerIds || []).includes(id));
            const myId = user?.id;
            const isActive = myId && (activePlayerIds.length
              ? activePlayerIds.some((id) => String(id) === String(myId))
              : true);
            const onTurn = Boolean(
              myId
              && prev?.currentTurnPlayerId
              && String(prev.currentTurnPlayerId) === String(myId),
            );
            const actions = new Set(prev?.allowedActions || []);
            if (count === 2 && isActive && onTurn && prev?.playerState !== 'PACKED') {
              actions.add('SHOW');
            }
            return mergeGameState(prev, {
              activePlayerIds: activePlayerIds.length ? activePlayerIds : prev?.activePlayerIds,
              ...(actions.has('SHOW') ? {
                allowedActions: [...actions],
                myTurn: true,
              } : {}),
            }, user);
          });
          // Refresh authoritative buttons for the player now on turn.
          axiosClient.get(`/tables/${tableId}/betting-state`).then((res) => {
            const betting = res.data?.data || res.data;
            if (!betting) return;
            updateGameState((prev) => mergeGameState(prev, {
              ...betting,
              myTurn: betting.myTurn,
              allowedActions: betting.allowedActions || prev?.allowedActions,
              activePlayerIds: activeIds || betting.activePlayerIds || prev?.activePlayerIds,
            }, user));
          }).catch(() => {});
          break;
        }
        case RealTimeEventType.ROUND_FINISHED: {
          // Keep ROUND_END + winner visible; countdown arrives later via NEXT_ROUND_COUNTDOWN
          const planned = typeof payload === 'number'
            ? payload
            : (payload?.nextRoundInSeconds ?? 60);
          updateGameState((prev) => mergeGameState(prev, {
            status: 'ROUND_END',
            countdownSeconds: 0,
            nextRoundSeconds: planned > 0 ? planned : (prev?.nextRoundSeconds || 60),
            myTurn: false,
            allowedActions: [],
            pendingShow: null,
          }, user, { clearPendingShow: true }));
          break;
        }
        case RealTimeEventType.NEXT_ROUND_COUNTDOWN: {
          const seconds = typeof payload === 'number'
            ? payload
            : (payload?.secondsRemaining ?? payload?.countdownSeconds ?? 0);
          updateGameState((prev) => mergeGameState(prev, {
            status: seconds > 0 ? 'NEXT_ROUND' : (prev?.status === 'NEXT_ROUND' ? 'NEXT_ROUND' : 'ROUND_END'),
            countdownSeconds: seconds,
            nextRoundSeconds: seconds,
            // Keep winner banner visible during countdown
            winnerSnapshot: prev?.winnerSnapshot,
            handOutcome: prev?.handOutcome,
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
              cardCount: 0, // seat cards appear after deal animation
            }));
            const extra = (typeof payload === 'object' && payload !== null) ? { ...payload } : {};
            // Don't let payload re-inject cards before deal finishes
            delete extra.players;
            delete extra.revealedHands;
            return mergeGameState(prev, {
              ...extra,
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
            }, user, { forceResetHands: true });
          });
          break;
        case RealTimeEventType.CARDS_DISTRIBUTED:
          updateGameState((prev) => mergeGameState(prev, {
            status: prev?.status || 'RUNNING',
            variantPhase: prev?.gameVariant === 'DISCARD_ONE' ? 'DISCARD' : prev?.variantPhase,
            players: (prev?.players || []).map((p) => ({
              ...p,
              cardCount: p.cardCount > 0 ? p.cardCount : (prev?.gameVariant === 'DISCARD_ONE' ? 4 : 3),
              cards: Array.isArray(p.cards) ? p.cards : [],
            })),
          }, user));
          break;
        case RealTimeEventType.TABLE_WAITING_FOR_PLAYERS:
          updateGameState((prev) => mergeGameState(prev, {
            status: 'WAITING',
            countdownSeconds: 0,
            nextRoundSeconds: 0,
            winnerSnapshot: null,
          }, user));
          break;
        case RealTimeEventType.PLAYER_JOINED:
        case RealTimeEventType.PLAYER_LEFT:
        case RealTimeEventType.PLAYER_COUNT_CHANGED: {
          // Refresh seated players live without full page reload
          axiosClient.get(`/tables/${tableId}`).then((res) => {
            const data = res.data?.data || res.data;
            if (data) updateGameState((prev) => mergeGameState(prev, data, user));
          }).catch(() => {});
          axiosClient.get(`/tables/${tableId}/live`).then((res) => {
            const data = res.data?.data || res.data;
            if (data) updateGameState((prev) => mergeGameState(prev, data, user));
          }).catch(() => {});
          break;
        }
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
              const activePlayerIds = (prev?.activePlayerIds || [])
                .filter((id) => String(id) !== String(packedId));
              const selfPacked = packedId === user?.id;
              return mergeGameState(prev, {
                players,
                packedPlayerIds,
                activePlayerIds,
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
          const targetId = String(payload?.targetUserId || payload?.targetId || '');
          const requesterId = String(payload?.requesterId || payload?.requesterUserId || '');
          const me = String(user?.id || '');
          const requesterName = payload?.requesterDisplayName || 'Opponent';
          if (targetId && me && targetId === me) {
            setWsError('');
            updateGameState((prev) => mergeGameState(prev, {
              status: 'SHOW',
              pendingShow: {
                requesterId,
                targetId,
                requesterDisplayName: requesterName,
              },
              allowedActions: ['SHOW_ACCEPT', 'SHOW_REJECT'],
              myTurn: true,
              potPaise: payload?.potPaise ?? prev?.potPaise,
            }, user));
          } else if (requesterId && me && requesterId === me) {
            setWsError(`Waiting for ${payload?.targetDisplayName || 'opponent'} to accept or decline Show…`);
            updateGameState((prev) => mergeGameState(prev, {
              status: 'SHOW',
              pendingShow: { requesterId, targetId, requesterDisplayName: requesterName },
              allowedActions: [],
              myTurn: false,
              potPaise: payload?.potPaise ?? prev?.potPaise,
            }, user));
          } else {
            updateGameState((prev) => mergeGameState(prev, {
              status: 'SHOW',
              pendingShow: {
                requesterId,
                targetId,
                requesterDisplayName: requesterName,
              },
              potPaise: payload?.potPaise ?? prev?.potPaise,
            }, user));
          }
          // If we are the target but somehow missed names, hydrate from live projection.
          if (targetId && me && targetId === me) {
            axiosClient.get(`/tables/${tableId}/live`).then((res) => {
              const data = res.data?.data || res.data;
              if (!data?.pendingShow) return;
              const tid = data.pendingShow.targetId || data.pendingShow.targetUserId;
              if (String(tid) !== me) return;
              updateGameState((prev) => mergeGameState(prev, {
                status: 'SHOW',
                pendingShow: {
                  requesterId: data.pendingShow.requesterId || data.pendingShow.requesterUserId,
                  targetId: tid,
                  requesterDisplayName: data.pendingShow.requesterDisplayName || requesterName,
                },
                allowedActions: ['SHOW_ACCEPT', 'SHOW_REJECT'],
                myTurn: true,
                potPaise: data.potPaise ?? prev?.potPaise,
              }, user));
            }).catch(() => {});
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
          }, user, { clearPendingShow: true }));
          break;
        case RealTimeEventType.SHOW_REJECTED:
          setWsError('');
          updateGameState((prev) => mergeGameState(prev, {
            pendingShow: null,
            status: 'RUNNING',
            myTurn: false,
            allowedActions: [],
          }, user, { clearPendingShow: true }));
          break;
        case RealTimeEventType.FINAL_HANDS_REVEALED: {
          const parsedHands = parseHandsMap(payload?.hands);
          const winnerId = payload?.winnerId || payload?.winnerUserId;
          updateGameState((prev) => {
            const players = (prev?.players || []).map((p) => {
              const revealed = parsedHands[p.userId];
              if (!revealed?.length) {
                return winnerId && p.userId === winnerId ? { ...p, isWinner: true } : p;
              }
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
              revealedHands: Object.keys(parsedHands).length ? parsedHands : prev?.revealedHands,
              pendingShow: null,
              status: 'ROUND_END',
              allowedActions: [],
              myTurn: false,
              ...(winnerId ? {
                winnerSnapshot: prev?.winnerSnapshot?.winnerUserId === winnerId
                  ? prev.winnerSnapshot
                  : {
                      ...(prev?.winnerSnapshot || {}),
                      tableId,
                      winnerUserId: winnerId,
                      winnerDisplayName: prev?.winnerSnapshot?.winnerDisplayName
                        || players.find((p) => p.userId === winnerId)?.displayName
                        || 'Winner',
                    },
                handOutcome: {
                  ...(prev?.handOutcome || {}),
                  winnerId,
                },
              } : {}),
            }, user, { clearPendingShow: true });
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

  const sendPlayerAction = async (actionType, multiplier = 1, extra = {}) => {
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
        : actionType === 'AUCTION_BID'
          ? (extra.amountPaise || gameState?.auctionMinBidPaise || gameState?.bootAmountPaise || 0)
          : 0; // Blind/Chaal/Show/Pack/SideShow amounts are server-authoritative

      const restActionType = (actionType === 'BLIND') ? 'PLAY_BLIND' : actionType;

      // Prefer REST so Blind/Chaal/Raise/Show work even if WS action path fails.
      try {
        const res = await axiosClient.post(`/tables/${tableId}/actions`, {
          actionType: restActionType,
          amountPaise,
          ...(extra.cardIndex != null ? { cardIndex: extra.cardIndex } : {}),
        });
        const betting = res.data?.data || res.data;
        if (betting && typeof betting === 'object') {
          const selfPacked = restActionType === 'PACK' || betting.playerState === 'PACKED';
          if (selfPacked) {
            setLocalTurnSeconds(0);
          }
          const showRespond = Array.isArray(betting.allowedActions)
            && (betting.allowedActions.includes('SHOW_ACCEPT')
              || betting.allowedActions.includes('SHOW_REJECT'));
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
            myTurn: selfPacked ? false : (showRespond ? true : betting.myTurn),
            allowedActions: betting.allowedActions || [],
            ...(restActionType === 'SHOW' ? {
              status: 'SHOW',
              pendingShow: prev?.pendingShow || {
                requesterId: user?.id,
                targetId: '',
                requesterDisplayName: user?.displayName || 'You',
              },
            } : {}),
            ...(showRespond ? {
              status: 'SHOW',
              pendingShow: prev?.pendingShow || {
                targetId: user?.id,
                requesterId: '',
                requesterDisplayName: 'Opponent',
              },
            } : {}),
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
          // After Show request, hydrate pendingShow for Accept/Decline UI immediately.
          if (restActionType === 'SHOW' || showRespond) {
            axiosClient.get(`/tables/${tableId}/live`).then((liveRes) => {
              const data = liveRes.data?.data || liveRes.data;
              if (!data?.pendingShow) return;
              updateGameState((prev) => mergeGameState(prev, {
                status: 'SHOW',
                pendingShow: {
                  requesterId: data.pendingShow.requesterId || data.pendingShow.requesterUserId,
                  targetId: data.pendingShow.targetId || data.pendingShow.targetUserId,
                  requesterDisplayName: data.pendingShow.requesterDisplayName || 'Opponent',
                },
                potPaise: data.potPaise ?? prev?.potPaise,
                allowedActions: showRespond ? ['SHOW_ACCEPT', 'SHOW_REJECT'] : (prev?.allowedActions || []),
                myTurn: showRespond,
              }, user));
            }).catch(() => {});
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
        ...(extra.cardIndex != null ? { cardIndex: extra.cardIndex } : {}),
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
  const isHost = Boolean(hostId && user?.id && hostId === user.id);

  // Tick next-round countdown locally so it updates every second without waiting for server ticks
  useEffect(() => {
    if (status !== 'NEXT_ROUND' && status !== 'ROUND_END') return undefined;
    if (!countdownSeconds || countdownSeconds <= 0) return undefined;
    const id = setInterval(() => {
      let hitZero = false;
      updateGameState((prev) => {
        const cur = prev?.countdownSeconds ?? 0;
        if (cur <= 0) return prev;
        const next = Math.max(0, cur - 1);
        if (next === cur) return prev;
        if (next <= 0) hitZero = true;
        return {
          ...prev,
          countdownSeconds: next,
          nextRoundSeconds: next,
          status: next > 0 ? 'NEXT_ROUND' : (prev?.status || 'NEXT_ROUND'),
        };
      });
      if (hitZero) {
        axiosClient.get(`/tables/${tableId}/live`).then((res) => {
          const data = res.data?.data || res.data;
          if (data) updateGameState((p) => mergeGameState(p, data, user));
        }).catch(() => {});
        wsGameService.sendMessage('JOIN_TABLE', tableId, {});
      }
    }, 1000);
    return () => clearInterval(id);
  }, [status, countdownSeconds > 0, tableId, user, updateGameState]);

  // Live sync bridge: poll /live so Show Accept/Decline + Winner never wait for a refresh.
  useEffect(() => {
    if (!tableId || !user?.id) return undefined;
    const liveStatuses = ['RUNNING', 'IN_PROGRESS', 'PLAYING', 'SHOW', 'ROUND_END', 'NEXT_ROUND', 'STARTING'];
    if (!liveStatuses.includes(status)) return undefined;

    const amSeated = (gameState?.seatedPlayerIds || [])
      .some((id) => String(id) === String(user.id))
      || (gameState?.players || []).some((p) => String(p.userId) === String(user.id));
    if (!amSeated) return undefined;

    const syncLive = () => {
      axiosClient.get(`/tables/${tableId}/live`).then((res) => {
        const data = res.data?.data || res.data;
        if (!data) return;
        updateGameState((prev) => {
          const next = mergeGameState(prev, data, user);
          if (data.pendingShow) {
            const tid = data.pendingShow.targetId || data.pendingShow.targetUserId;
            const isTarget = tid && user?.id && String(tid) === String(user.id);
            return {
              ...next,
              status: 'SHOW',
              pendingShow: {
                requesterId: data.pendingShow.requesterId || data.pendingShow.requesterUserId,
                targetId: tid,
                requesterDisplayName: data.pendingShow.requesterDisplayName || 'Opponent',
              },
              allowedActions: isTarget
                ? [...new Set([...(next.allowedActions || []), ...(data.allowedActions || []), 'SHOW_ACCEPT', 'SHOW_REJECT'])]
                : (next.allowedActions || []),
              myTurn: isTarget ? true : next.myTurn,
            };
          }
          if (data.winnerSnapshot && (data.status === 'ROUND_END' || data.status === 'NEXT_ROUND'
            || prev?.status === 'ROUND_END' || prev?.status === 'NEXT_ROUND')) {
            const snap = data.winnerSnapshot;
            const payout = Number(
              snap.payoutPaise ?? snap.winnerPayoutPaise ?? data.handOutcome?.winnerPayoutPaise ?? snap.potPaise ?? 0,
            );
            return {
              ...next,
              status: data.status || prev?.status || 'ROUND_END',
              winnerSnapshot: {
                ...snap,
                payoutPaise: payout,
                winnerPayoutPaise: payout,
                winnerUserId: snap.winnerUserId || snap.winnerId,
              },
              handOutcome: data.handOutcome || {
                winnerId: snap.winnerUserId || snap.winnerId,
                winnerPayoutPaise: payout,
                winningCategory: snap.winningCategory,
              },
              countdownSeconds: data.countdownSeconds ?? next.countdownSeconds,
            };
          }
          return next;
        });
      }).catch(() => {});
    };

    syncLive();
    const id = setInterval(syncLive, 2000);
    return () => clearInterval(id);
  }, [
    tableId,
    status,
    user?.id,
    updateGameState,
    (gameState?.seatedPlayerIds || []).join(','),
    (gameState?.players || []).map((p) => p.userId).join(','),
  ]);

  // After winner banner (~5s), ensure next-round countdown starts even if server event was missed.
  useEffect(() => {
    if (status !== 'ROUND_END') return undefined;
    if (!gameState?.winnerSnapshot && !gameState?.handOutcome) return undefined;

    const winnerDisplayMs = 5200;
    const timer = setTimeout(() => {
      axiosClient.get(`/tables/${tableId}/live`).then((res) => {
        const data = res.data?.data || res.data;
        if (!data) return;
        updateGameState((prev) => {
          if (prev?.status === 'RUNNING' || prev?.status === 'STARTING') return prev;
          const serverStatus = data.status;
          const secs = data.countdownSeconds
            ?? data.nextRoundSeconds
            ?? prev?.nextRoundSeconds
            ?? 60;
          if (serverStatus === 'NEXT_ROUND' || (secs > 0 && serverStatus === 'ROUND_END')) {
            return mergeGameState(prev, {
              ...data,
              status: 'NEXT_ROUND',
              countdownSeconds: secs > 0 ? secs : (prev?.nextRoundSeconds || 60),
              nextRoundSeconds: secs > 0 ? secs : (prev?.nextRoundSeconds || 60),
              winnerSnapshot: data.winnerSnapshot || prev?.winnerSnapshot,
              handOutcome: data.handOutcome || prev?.handOutcome,
            }, user);
          }
          if (serverStatus === 'WAITING' || serverStatus === 'CLOSED') {
            return mergeGameState(prev, data, user);
          }
          // Server still ROUND_END — start local countdown from planned delay
          const planned = prev?.nextRoundSeconds > 0 ? prev.nextRoundSeconds : 60;
          return mergeGameState(prev, {
            status: 'NEXT_ROUND',
            countdownSeconds: planned,
            nextRoundSeconds: planned,
            winnerSnapshot: prev?.winnerSnapshot,
            handOutcome: prev?.handOutcome,
          }, user);
        });
      }).catch(() => {
        updateGameState((prev) => {
          if (prev?.status !== 'ROUND_END') return prev;
          const planned = prev?.nextRoundSeconds > 0 ? prev.nextRoundSeconds : 60;
          return {
            ...prev,
            status: 'NEXT_ROUND',
            countdownSeconds: planned,
            nextRoundSeconds: planned,
          };
        });
      });
    }, winnerDisplayMs);

    return () => clearTimeout(timer);
  }, [
    status,
    tableId,
    user,
    updateGameState,
    gameState?.winnerSnapshot?.winnerUserId || gameState?.winnerSnapshot?.winnerId,
    gameState?.handOutcome?.winnerId,
  ]);
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
  const isMyTurn = (gameState != null && 'myTurn' in gameState)
    ? Boolean(gameState.myTurn)
    : Boolean(currentTurnUserId && user?.id && currentTurnUserId === user.id);
  const myPlayer = players.find((p) => p.userId === user?.id);
  const myStatus = myPlayer?.status || 'BLIND';
  const myCards = myPlayer?.cards || [];
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
  const winnerPayoutRupees = (
    Number(
      winnerSnapshot?.payoutPaise
      ?? winnerSnapshot?.winnerPayoutPaise
      ?? handOutcome?.winnerPayoutPaise
      ?? handOutcome?.payoutPaise
      ?? winnerSnapshot?.potPaise
      ?? 0,
    ) / 100
  );
  const winningCategoryLabel = winnerSnapshot?.winningHandDescription
    || winnerSnapshot?.winningCategory
    || handOutcome?.winningCategory
    || 'FOLD WIN';
  // Status drives "hand over" — do not treat a stale winnerSnapshot as game-over while RUNNING.
  const handEnded = status === 'ROUND_END' || status === 'WAITING' || status === 'NEXT_ROUND' || status === 'CLOSED';
  const handInProgress = isActiveHandStatus(status);
  const cardsLive = isDealableHandStatus(status) && players.length >= minPlayers;
  const revealedHands = gameState?.revealedHands || {};
  const showdownRevealed = handEnded && Object.keys(revealedHands).length > 0;
  const pendingShow = gameState?.pendingShow;
  const isShowTarget = Boolean(
    pendingShow?.targetId && user?.id && String(pendingShow.targetId) === String(user.id),
  );
  const showWinnerBanner = Boolean((winnerSnapshot || handOutcome) && handEnded);
  const turnPlayer = players.find((p) => p.userId === currentTurnUserId);
  const turnPlayerName = gameState?.activeDisplayName
    || turnPlayer?.displayName
    || (currentTurnUserId === user?.id ? (user?.displayName || 'You') : null)
    || (currentTurnUserId ? `Player` : null);
  const canAct = (action) => {
    if ((action === 'SHOW_ACCEPT' || action === 'SHOW_REJECT') && isShowTarget) return true;
    if (!handInProgress && status !== 'SHOW') return false;
    if (allowedActions.includes(action)) return true;
    // Live fallback: Show must light up whenever exactly two active players remain on your turn.
    if (action === 'SHOW' && isMyTurn && myStatus !== 'PACKED') {
      const fromIds = (gameState?.activePlayerIds || []).filter(Boolean);
      const activeCount = fromIds.length > 0
        ? fromIds.length
        : players.filter((p) => p.status && p.status !== 'PACKED').length;
      return activeCount === 2;
    }
    return false;
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

  const bootRupees = (gameState?.bootAmountPaise || 0) / 100;
  const iconBtn =
    'w-8 h-8 sm:w-10 sm:h-10 rounded-full bg-black/40 backdrop-blur-sm text-[#d4af37] flex items-center justify-center cursor-pointer transition-all hover:bg-black/55 shadow-[0_0_0_1.5px_rgba(212,175,55,0.65),0_0_14px_rgba(212,175,55,0.25)]';

  return (
    <div className="relative w-full h-full min-h-0 overflow-hidden flex flex-col">
      {/* Full-page casino room background */}
      <img
        src="https://res.cloudinary.com/dsafvwkrf/image/upload/v1785347148/8fdf3e41-a727-4785-b6db-48b163f39f69.png"
        alt=""
        className="absolute inset-0 w-full h-full object-cover object-center select-none pointer-events-none"
        draggable={false}
      />

      {/* Top-left — compact in landscape phone height */}
      <div className="absolute top-2 left-2 sm:top-4 sm:left-4 z-40 flex flex-row items-start gap-2 sm:gap-3">
        <div className="flex items-center gap-1.5 sm:gap-2.5">
          <button type="button" onClick={() => setShowLeaveModal(true)} className={iconBtn} title="Back">
            <ArrowLeft className="w-4 h-4" />
          </button>
          <button type="button" className={iconBtn} title="Chat" onClick={() => {}}>
            <MessageCircle className="w-4 h-4" />
          </button>
          <button type="button" className={iconBtn} title="Settings" onClick={() => setShowRulebook(true)}>
            <Settings className="w-4 h-4" />
          </button>
        </div>
        <div className="text-[10px] sm:text-[12px] text-white leading-snug drop-shadow-[0_2px_8px_rgba(0,0,0,0.95)]">
          <div>
            Table ID:{' '}
            <button
              type="button"
              className="font-mono text-[#f5e6a8] hover:underline cursor-pointer font-semibold"
              onClick={() => {
                const code = gameState?.inviteCode || tableId;
                if (code) navigator.clipboard.writeText(code);
              }}
            >
              {gameState?.inviteCode || tableId?.slice(-8) || '—'}
            </button>
          </div>
          <div className="hidden sm:block">
            Variant: {(gameState?.gameVariant || 'CLASSIC').replaceAll('_', ' ')}
          </div>
          {gameState?.jokerRank && (
            <div className="hidden sm:block">
              Joker: {String(gameState.jokerRank).replaceAll('_', ' ')}
            </div>
          )}
          <div className="hidden sm:block">Boot: {formatChipAmount(gameState?.bootAmountPaise || 0)}</div>
          <div className="hidden sm:block">Max Players: {maxPlayers}</div>
        </div>
      </div>

      {/* Top-right: username + Rules + leave */}
      <div className="absolute top-2 right-2 sm:top-4 sm:right-4 z-40 flex items-center gap-1.5 sm:gap-2">
        {user && (
          <div className="px-2.5 sm:px-3.5 py-1.5 sm:py-2 rounded-full bg-black/45 backdrop-blur-sm text-[#f5e6a8] text-[10px] sm:text-xs font-bold max-w-[140px] sm:max-w-[180px] truncate shadow-[0_0_0_1.5px_rgba(212,175,55,0.55)]">
            {user.displayName || user.username || user.name || 'You'}
          </div>
        )}
        <button
          type="button"
          onClick={() => setShowRulebook(true)}
          className="px-2.5 sm:px-3.5 py-1.5 sm:py-2 rounded-full bg-black/35 backdrop-blur-sm text-[#f5e6a8] text-[10px] sm:text-xs font-bold flex items-center gap-1.5 cursor-pointer shadow-[0_0_0_1.5px_rgba(212,175,55,0.55),0_0_12px_rgba(212,175,55,0.2)]"
        >
          <BookOpen className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Rules</span>
        </button>
        {hostId === user?.id && (
          <button
            type="button"
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
            className="p-1.5 sm:p-2 rounded-full bg-black/35 backdrop-blur-sm text-rose-300 cursor-pointer shadow-[0_0_0_1.5px_rgba(244,63,94,0.45)]"
            title="Delete Table"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
        <button
          type="button"
          onClick={() => setShowLeaveModal(true)}
          className="px-2.5 sm:px-3.5 py-1.5 sm:py-2 rounded-full bg-black/35 backdrop-blur-sm text-rose-300 text-[10px] sm:text-xs font-bold flex items-center gap-1.5 cursor-pointer shadow-[0_0_0_1.5px_rgba(244,63,94,0.5)]"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Leave Table</span>
        </button>
      </div>

      {/* Main row (landscape): table + actions packed for short height */}
      <div className="relative z-10 flex-1 flex flex-col items-center justify-start px-2 sm:px-3 pt-4 sm:pt-8 pb-2 min-h-0">
        <div className="relative w-full max-w-[1200px] flex justify-center shrink min-h-0 max-h-[min(52dvh,360px)] sm:max-h-[min(60dvh,520px)] md:max-h-[min(66dvh,620px)] -translate-y-0 sm:-translate-y-2">
          <TableArena
            players={players}
            myUserId={user?.id}
            currentTurnUserId={currentTurnUserId}
            dealerSeatIndex={dealerSeatIndex}
            hostId={hostId}
            handInProgress={handInProgress}
            cardsLive={cardsLive}
            minPlayers={minPlayers}
            handEnded={handEnded}
            showdownRevealed={showdownRevealed}
            revealedHands={revealedHands}
            disconnectedIds={gameState?.disconnectedPlayerIds || []}
            potRupees={potRupees}
            potPaise={gameState?.potPaise || 0}
            walletBalancePaise={gameState?.walletBalancePaise}
            turnDeadlineAt={gameState?.turnDeadlineAt}
            turnSecondsRemaining={gameState?.turnSecondsRemaining}
            turnDurationSeconds={
              gameState?.turnDurationSeconds
              || gameState?.turnTimeoutSeconds
              || gameState?.turnTimerSeconds
              || 30
            }
            winnerUserId={winnerSnapshot?.winnerUserId || handOutcome?.winnerId}
          />

          {!handInProgress && (
            <div className="absolute top-[18%] left-1/2 -translate-x-1/2 z-30 w-[min(90%,280px)] text-center pointer-events-auto">
              {(status === 'COUNTDOWN' || status === 'NEXT_ROUND' || countdownSeconds > 0) && (
                <div className="hidden sm:block text-5xl font-black text-[#d4af37] font-mono drop-shadow-[0_0_16px_rgba(212,175,55,0.55)] mb-2">
                  {countdownSeconds}
                </div>
              )}
              {!isTableLoading && isPrivateTable && isHost && canStart && status !== 'COUNTDOWN' && status !== 'NEXT_ROUND' && (
                <button
                  type="button"
                  onClick={handleStartGame}
                  disabled={startLoading}
                  className="px-5 sm:px-8 py-1.5 sm:py-2.5 rounded-full bg-black/40 backdrop-blur-sm text-[#f5e6a8] font-black text-xs sm:text-sm cursor-pointer disabled:opacity-60 shadow-[0_0_0_1.5px_rgba(212,175,55,0.7),0_0_24px_rgba(212,175,55,0.35)]"
                >
                  {startLoading ? 'Starting…' : status === 'ROUND_END' ? 'Start Next Round' : 'Start Game'}
                </button>
              )}
              {startError && <p className="mt-1 text-[10px] text-rose-300 drop-shadow">{startError}</p>}
            </div>
          )}
        </div>

        {/* Action buttons — one horizontal row */}
        <div className="w-full max-w-5xl mt-2 sm:mt-6 shrink-0 px-1">
          {handInProgress ? (
            <ActionBar
              canAct={canAct}
              sendPlayerAction={sendPlayerAction}
              actionLoading={actionLoading}
              myStatus={myStatus}
              blindBetRupees={blindBetRupees}
              chaalBetRupees={chaalBetRupees}
              requiredBetRupees={requiredBetRupees}
              minRaiseBetRupees={minRaiseBetRupees}
              sideShowCost={(gameState?.sideShowCostPaise || gameState?.chaalAmountPaise || 0) / 100}
              showCost={(gameState?.showCostPaise || gameState?.chaalAmountPaise || 0) / 100}
              wsError={wsError}
              variantPhase={gameState?.variantPhase}
              auctionHighBidPaise={gameState?.auctionHighBidPaise}
              auctionMinBidPaise={gameState?.auctionMinBidPaise}
              myCards={myCards}
              onDiscardCard={(idx) => sendPlayerAction('DISCARD_CARD', 1, { cardIndex: idx })}
            />
          ) : null}
        </div>

        {/* Boot — desktop only under buttons; phone uses right-side badge */}
        <div className="hidden sm:flex mt-2.5 shrink-0 items-center gap-1.5 px-4 py-1.5 rounded-full bg-black/55 backdrop-blur-sm text-[12px] text-white shadow-[0_0_0_1px_rgba(212,175,55,0.35)]">
          <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden>
            <circle cx="12" cy="12" r="11" fill="#d4af37" stroke="#f5e6a8" strokeWidth="1.5" />
            <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.2" strokeDasharray="2 1.5" />
          </svg>
          <span>Boot Amount: <strong className="text-[#f5e6a8]">{formatChipAmount(bootRupees * 100)}</strong></span>
        </div>

      </div>

      {/* Phone: Game status / starting — left of table */}
      <div className="absolute bottom-2 left-1.5 sm:bottom-4 sm:left-4 z-40 max-w-[42vw] sm:max-w-[260px] px-2 sm:px-3 py-1 sm:py-2 rounded-xl sm:rounded-2xl bg-black/50 backdrop-blur-md">
        <div className="flex items-center gap-1.5 sm:gap-2 text-[9px] sm:text-[11px] text-white">
          {(isTableLoading || status === 'COUNTDOWN' || dealActiveLike(status, handInProgress, countdownSeconds)) && (
            <Loader2 className="w-3 h-3 sm:w-4 sm:h-4 text-[#d4af37] animate-spin shrink-0" />
          )}
          <div className="min-w-0">
            <div className="text-[8px] sm:text-[10px] text-emerald-400 font-semibold">Game Status</div>
            <div className="text-white/90 truncate text-[9px] sm:text-[11px]">{statusBannerText}</div>
          </div>
        </div>
        {handInProgress && currentTurnUserId && (
          <p className="mt-0.5 sm:mt-1 text-[8px] sm:text-[10px] text-[#f5e6a8] font-semibold truncate">
            {isMyTurn ? `Your turn${turnDisplaySeconds > 0 ? ` · ${turnDisplaySeconds}s` : ''}` : `Waiting · ${turnPlayerName}`}
          </p>
        )}
        {!handInProgress && (status === 'WAITING' || status === 'COUNTDOWN') && players.length < (minPlayers || 3) && (
          <p className="mt-0.5 sm:mt-1 text-[8px] sm:text-[10px] text-[#f5e6a8]/90 font-semibold">
            Finding… {players.length}/{minPlayers || 3}
          </p>
        )}
        {!handInProgress && (status === 'COUNTDOWN' || countdownSeconds > 0) && (
          <p className="mt-0.5 text-[10px] sm:text-[11px] font-black text-[#d4af37] tabular-nums sm:hidden">
            Starting {countdownSeconds}s
          </p>
        )}
      </div>

      {/* Phone: Boot amount — right of table */}
      <div className="absolute bottom-2 right-1.5 z-40 sm:hidden max-w-[42vw] px-2 py-1 rounded-xl bg-black/50 backdrop-blur-md shadow-[0_0_0_1px_rgba(212,175,55,0.4)]">
        <div className="flex items-center gap-1 text-[9px] text-white">
          <svg width="11" height="11" viewBox="0 0 24 24" aria-hidden className="shrink-0">
            <circle cx="12" cy="12" r="11" fill="#d4af37" stroke="#f5e6a8" strokeWidth="1.5" />
            <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.2" strokeDasharray="2 1.5" />
          </svg>
          <span className="truncate">Boot <strong className="text-[#f5e6a8]">{formatChipAmount(bootRupees * 100)}</strong></span>
        </div>
      </div>

      <AnimatePresence>
        {pendingShow && isShowTarget && (handInProgress || status === 'SHOW') && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
          >
            <motion.div
              initial={{ scale: 0.9, y: 16 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.95, y: 8 }}
              className="w-full max-w-md rounded-2xl bg-black/70 backdrop-blur-xl p-6 shadow-[0_0_0_1px_rgba(212,175,55,0.4)] text-center"
            >
              <Sparkles className="w-10 h-10 text-[#d4af37] mx-auto mb-4" />
              <h3 className="text-xl font-black text-amber-100 mb-2">Show Requested</h3>
              <p className="text-sm text-slate-300 mb-6">
                {pendingShow.requesterDisplayName || 'Your opponent'} has requested a Show.
                Accept to reveal both hands and declare the winner, or Decline to continue betting.
              </p>
              <div className="flex flex-col sm:flex-row gap-3">
                <button
                  type="button"
                  onClick={() => sendPlayerAction('SHOW_ACCEPT')}
                  disabled={actionLoading || !canAct('SHOW_ACCEPT')}
                  className="flex-1 px-6 py-3 rounded-full bg-black/40 text-[#f5e6a8] font-black text-sm cursor-pointer disabled:opacity-50 shadow-[0_0_0_1.5px_rgba(212,175,55,0.7),0_0_20px_rgba(212,175,55,0.35)]"
                >
                  Accept Show
                </button>
                <button
                  type="button"
                  onClick={() => sendPlayerAction('SHOW_REJECT')}
                  disabled={actionLoading || !canAct('SHOW_REJECT')}
                  className="flex-1 px-6 py-3 rounded-full bg-rose-950/50 text-rose-200 font-black text-sm cursor-pointer disabled:opacity-50 shadow-[0_0_0_1.5px_rgba(244,63,94,0.55)]"
                >
                  Decline
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="absolute inset-x-4 top-[16%] z-40 pointer-events-none flex justify-center">
        <div className="pointer-events-auto max-w-3xl w-full">
          <WinnerEffects
            show={showWinnerBanner}
            winnerDisplayName={
              winnerDisplayName
              || (winnerSnapshot?.winnerUserId || handOutcome?.winnerId
                ? `Player #${(winnerSnapshot?.winnerUserId || handOutcome?.winnerId).slice(-6)}`
                : 'Winner')
            }
            winnerPayoutRupees={winnerPayoutRupees}
            winningCategoryLabel={winningCategoryLabel}
            countdownSeconds={countdownSeconds}
            status={status}
            isSelfWinner={
              Boolean(user?.id) && (
                String(winnerSnapshot?.winnerUserId || '') === String(user.id)
                || String(winnerSnapshot?.winnerId || '') === String(user.id)
                || String(handOutcome?.winnerId || '') === String(user.id)
              )
            }
          />
        </div>
      </div>

      <AnimatePresence>
        {showLeaveModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-sm rounded-2xl bg-black/75 backdrop-blur-xl p-6 text-center shadow-[0_0_0_1px_rgba(255,255,255,0.08)]"
            >
              <ShieldAlert className="w-12 h-12 text-rose-400 mx-auto mb-2" />
              <h3 className="text-xl font-bold text-white">Leave Teen Patti Table?</h3>
              <p className="text-xs text-white/60 my-2">
                Leaving mid-hand will automatically fold your current hand and forfeit any bets placed.
              </p>
              <div className="flex gap-2 mt-5">
                <button
                  type="button"
                  onClick={() => setShowLeaveModal(false)}
                  className="flex-1 py-2.5 bg-white/10 text-white text-xs font-bold rounded-xl hover:bg-white/15"
                >
                  Stay in Game
                </button>
                <button
                  type="button"
                  onClick={handleConfirmLeave}
                  className="flex-1 py-2.5 bg-rose-600/90 text-white text-xs font-bold rounded-xl hover:bg-rose-500"
                >
                  Leave Table
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <RulebookModal isOpen={showRulebook} onClose={() => setShowRulebook(false)} />
    </div>
  );
}

function dealActiveLike(status, handInProgress, countdownSeconds) {
  if (handInProgress) return false;
  return status === 'WAITING' || status === 'COUNTDOWN' || countdownSeconds > 0;
}
