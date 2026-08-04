import { describe, it, expect } from 'vitest';
import {
  isActiveHandStatus,
  isJoinableStatus,
  isCountdownStatus,
  getTableStatusLabel,
  mergeGameState,
  mergePlayers,
} from './tableUtils';

describe('tableUtils', () => {
  it('detects active hand statuses', () => {
    expect(isActiveHandStatus('IN_PROGRESS')).toBe(true);
    expect(isActiveHandStatus('WAITING')).toBe(false);
  });

  it('detects joinable table statuses', () => {
    expect(isJoinableStatus('WAITING')).toBe(true);
    expect(isJoinableStatus('ROUND_END')).toBe(true);
    expect(isJoinableStatus('IN_PROGRESS')).toBe(false);
  });

  it('detects countdown state', () => {
    expect(isCountdownStatus('COUNTDOWN', 0)).toBe(true);
    expect(isCountdownStatus('WAITING', 5)).toBe(true);
    expect(isCountdownStatus('NEXT_ROUND', 3)).toBe(true);
    expect(isCountdownStatus('WAITING', 0)).toBeFalsy();
  });

  it('normalizes status labels', () => {
    expect(getTableStatusLabel('IN_PROGRESS')).toBe('RUNNING');
    expect(getTableStatusLabel('ROUND_END')).toBe('ROUND END');
    expect(getTableStatusLabel('NEXT_ROUND')).toBe('NEXT ROUND');
    expect(getTableStatusLabel(undefined)).toBe('WAITING');
  });

  it('keeps SEEN cards across shell table updates and turn changes', () => {
    const user = { id: 'u1', displayName: 'Alice' };
    const withCards = {
      status: 'RUNNING',
      currentTurnPlayerId: 'u1',
      seenPlayerIds: ['u1'],
      players: [
        {
          userId: 'u1',
          displayName: 'Alice',
          status: 'SEEN',
          cards: [{ suit: 'HEARTS', rank: 'Q' }, { suit: 'SPADES', rank: 'A' }, { suit: 'CLUBS', rank: '7' }],
          cardCount: 3,
        },
        { userId: 'u2', displayName: 'Bob', status: 'BLIND', cards: [], cardCount: 3 },
      ],
    };

    const afterSee = mergeGameState(null, withCards, user);
    expect(afterSee.players[0].cards).toHaveLength(3);

    const shell = mergeGameState(afterSee, {
      status: 'RUNNING',
      seatedPlayerIds: ['u1', 'u2'],
      currentTurnUserId: 'u2',
      seenPlayerIds: ['u1'],
    }, user);

    expect(shell.currentTurnPlayerId).toBe('u2');
    expect(shell.players.find((p) => p.userId === 'u1').status).toBe('SEEN');
    expect(shell.players.find((p) => p.userId === 'u1').cards).toHaveLength(3);

    const afterTurn = mergeGameState(shell, {
      activeUserId: 'u2',
      currentTurnUserId: 'u2',
      durationSeconds: 20,
      myTurn: false,
    }, user);

    expect(afterTurn.players.find((p) => p.userId === 'u1').cards).toHaveLength(3);
    expect(afterTurn.currentTurnPlayerId).toBe('u2');
  });

  it('does not wipe NEXT_ROUND countdown with ROUND_END countdownSeconds 0', () => {
    const user = { id: 'u1' };
    const withCountdown = mergeGameState(null, {
      status: 'NEXT_ROUND',
      countdownSeconds: 45,
      winnerSnapshot: { winnerUserId: 'u1', payoutPaise: 1000 },
      players: [{ userId: 'u1', displayName: 'Alice', status: 'SEEN', cards: [], cardCount: 3 }],
    }, user);

    expect(withCountdown.status).toBe('NEXT_ROUND');
    expect(withCountdown.countdownSeconds).toBe(45);

    const wiped = mergeGameState(withCountdown, {
      status: 'ROUND_END',
      countdownSeconds: 0,
      winnerSnapshot: { winnerUserId: 'u1', payoutPaise: 1000 },
    }, user);

    expect(wiped.status).toBe('NEXT_ROUND');
    expect(wiped.countdownSeconds).toBe(45);
  });
});
