export const ACTIVE_HAND_STATUSES = ['RUNNING', 'IN_PROGRESS', 'PLAYING', 'SHOW', 'STARTING'];

export function isActiveHandStatus(status) {
  return ACTIVE_HAND_STATUSES.includes(status);
}

export function isJoinableStatus(status) {
  return status === 'WAITING' || status === 'ROUND_END';
}

export function isCountdownStatus(status, countdownSeconds = 0) {
  return status === 'COUNTDOWN' || (countdownSeconds && countdownSeconds > 0);
}

export function getTableStatusLabel(status) {
  switch (status) {
    case 'RUNNING':
    case 'IN_PROGRESS':
    case 'PLAYING':
      return 'RUNNING';
    case 'STARTING':
      return 'STARTING';
    case 'ROUND_END':
      return 'ROUND END';
    case 'WAITING':
      return 'WAITING';
    case 'COUNTDOWN':
      return 'COUNTDOWN';
    default:
      return status || 'WAITING';
  }
}

export function mergeGameState(prev, incoming, user) {
  const next = normalizeGameState(incoming, user);
  if (!next) return prev ?? null;
  if (!prev) return next;

  const merged = {
    ...prev,
    ...next,
    hostId: next.hostId ?? prev.hostId,
    minPlayers: next.minPlayers ?? prev.minPlayers,
    maxPlayers: next.maxPlayers ?? prev.maxPlayers,
    bootAmountPaise: next.bootAmountPaise ?? prev.bootAmountPaise,
    seatedPlayerIds: next.seatedPlayerIds?.length ? next.seatedPlayerIds : prev.seatedPlayerIds,
    seatedPlayers: next.seatedPlayers?.length ? next.seatedPlayers : prev.seatedPlayers,
    status: next.status ?? prev.status,
    players: next.players?.length ? next.players : prev.players,
    currentTurnPlayerId: next.currentTurnPlayerId ?? (
      isActiveHandStatus(next.status ?? prev.status) ? prev.currentTurnPlayerId : null
    ),
    dealerSeatIndex: next.dealerSeatIndex ?? prev.dealerSeatIndex,
    currentTurnSeatIndex: next.currentTurnSeatIndex ?? prev.currentTurnSeatIndex,
    turnTimeoutSeconds: next.turnTimeoutSeconds ?? prev.turnTimeoutSeconds,
    turnSecondsRemaining: next.turnSecondsRemaining ?? prev.turnSecondsRemaining,
    turnDeadlineAt: next.turnDeadlineAt ?? prev.turnDeadlineAt,
    activePlayerIds: next.activePlayerIds?.length ? next.activePlayerIds : prev.activePlayerIds,
    blindPlayerIds: next.blindPlayerIds?.length ? next.blindPlayerIds : prev.blindPlayerIds,
    seenPlayerIds: next.seenPlayerIds?.length ? next.seenPlayerIds : prev.seenPlayerIds,
    packedPlayerIds: next.packedPlayerIds?.length ? next.packedPlayerIds : prev.packedPlayerIds,
    potPaise: next.potPaise ?? prev.potPaise,
    requiredBetPaise: next.requiredBetPaise ?? prev.requiredBetPaise,
    minRaiseBetPaise: next.minRaiseBetPaise ?? prev.minRaiseBetPaise,
    maxBetPaise: next.maxBetPaise ?? prev.maxBetPaise,
    playerContributedPaise: next.playerContributedPaise ?? prev.playerContributedPaise,
    blindSeenRatio: next.blindSeenRatio ?? prev.blindSeenRatio,
    myTurn: next.myTurn ?? prev.myTurn,
    allowedActions: next.allowedActions?.length ? next.allowedActions : prev.allowedActions,
    winnerSnapshot: next.winnerSnapshot ?? prev.winnerSnapshot,
    handOutcome: next.handOutcome ?? prev.handOutcome,
    countdownSeconds: next.countdownSeconds ?? prev.countdownSeconds,
    tableType: next.tableType ?? prev.tableType,
    inviteCode: next.inviteCode ?? prev.inviteCode,
  };

  if (merged.status === 'ROUND_END' || merged.status === 'WAITING') {
    merged.currentTurnPlayerId = null;
    merged.requiredBetPaise = 0;
    merged.turnSecondsRemaining = 0;
    merged.turnDeadlineAt = null;
  }

  // Skip re-render if nothing meaningful changed
  if (
    prev.status === merged.status
    && prev.potPaise === merged.potPaise
    && prev.currentTurnPlayerId === merged.currentTurnPlayerId
    && prev.turnSecondsRemaining === merged.turnSecondsRemaining
    && prev.dealerSeatIndex === merged.dealerSeatIndex
    && prev.hostId === merged.hostId
    && prev.countdownSeconds === merged.countdownSeconds
    && JSON.stringify(prev.players) === JSON.stringify(merged.players)
  ) {
    return prev;
  }

  return merged;
}

export function normalizeGameState(data, user) {
  if (!data) return null;

  const seatedPlayers = data.seatedPlayers || [];
  const seatedIds = data.seatedPlayerIds
    || seatedPlayers.map((p) => p.userId)
    || [];

  const players = (data.players && data.players.length > 0)
    ? data.players
    : seatedPlayers.map((p, index) => ({
        userId: p.userId,
        displayName: p.displayName || `Player ${index + 1}`,
        status: 'BLIND',
        cards: [],
        cardCount: 0,
      }));

  if (players.length === 0 && seatedIds.length > 0) {
    seatedIds.forEach((id, index) => {
      players.push({
        userId: id,
        displayName: id === user?.id ? (user?.displayName || 'You') : `Player ${index + 1}`,
        status: 'BLIND',
        cards: [],
        cardCount: 0,
      });
    });
  }

  return {
    ...data,
    tableId: data.tableId || data.id,
    hostId: data.hostId,
    tableType: data.tableType,
    inviteCode: data.inviteCode,
    countdownSeconds: data.countdownSeconds ?? 0,
    minPlayers: data.minPlayers ?? 3,
    maxPlayers: data.maxPlayers ?? 6,
    currentPlayerCount: data.currentPlayerCount ?? players.length,
    seatedPlayerIds: seatedIds,
    seatedPlayers,
    players,
    currentTurnPlayerId: data.currentTurnPlayerId || data.currentTurnUserId,
    dealerSeatIndex: data.dealerSeatIndex ?? -1,
    currentTurnSeatIndex: data.currentTurnSeatIndex ?? -1,
    turnTimeoutSeconds: data.turnTimeoutSeconds ?? 20,
    turnSecondsRemaining: data.turnSecondsRemaining ?? 0,
    turnDeadlineAt: data.turnDeadlineAt ?? null,
    activePlayerIds: data.activePlayerIds || [],
    blindPlayerIds: data.blindPlayerIds || [],
    seenPlayerIds: data.seenPlayerIds || [],
    packedPlayerIds: data.packedPlayerIds || [],
    requiredBetPaise: data.requiredBetPaise ?? 0,
    minRaiseBetPaise: data.minRaiseBetPaise ?? 0,
    maxBetPaise: data.maxBetPaise ?? 0,
    playerContributedPaise: data.playerContributedPaise ?? 0,
    blindSeenRatio: data.blindSeenRatio ?? 2,
    myTurn: data.myTurn ?? false,
    allowedActions: data.allowedActions || [],
    winnerSnapshot: data.winnerSnapshot ?? null,
  };
}

export function getWaitingBannerText({
  status,
  playerCount,
  minPlayers,
  maxPlayers,
  isHost,
  canStart,
  tableType,
  countdownSeconds,
}) {
  const isPrivate = tableType === 'PRIVATE';

  if (status === 'COUNTDOWN' || (countdownSeconds && countdownSeconds > 0)) {
    return `Game starts in ${countdownSeconds || '?'}…`;
  }
  if (status === 'STARTING') {
    return 'Starting game… shuffling and dealing cards';
  }
  if (isActiveHandStatus(status)) {
    return `♠ Hand in progress (${playerCount}/${maxPlayers} players) ♠`;
  }
  if (status === 'ROUND_END') {
    if (isPrivate) {
      return isHost
        ? 'Round finished — click Start Game for next round'
        : 'Round finished — waiting for host to start next round';
    }
    return playerCount >= minPlayers
      ? 'Round finished — auto countdown starting soon'
      : `${playerCount}/${maxPlayers} seated • waiting for ${minPlayers - playerCount} more player(s)`;
  }
  if (playerCount < minPlayers) {
    return `${playerCount}/${maxPlayers} seated • waiting for ${minPlayers - playerCount} more player(s)`;
  }
  if (isPrivate) {
    if (canStart && isHost) {
      return `${playerCount}/${maxPlayers} seated • you can start the game`;
    }
    return `${playerCount}/${maxPlayers} seated • waiting for host to start`;
  }
  return `${playerCount}/${maxPlayers} seated • game will start automatically`;
}
