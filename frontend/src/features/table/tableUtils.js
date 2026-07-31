export const ACTIVE_HAND_STATUSES = ['RUNNING', 'IN_PROGRESS', 'PLAYING', 'SHOW', 'STARTING'];

/** Statuses where cards are dealt / visible (not STARTING / waiting). */
export const DEALABLE_HAND_STATUSES = ['RUNNING', 'IN_PROGRESS', 'PLAYING', 'SHOW'];

export function isActiveHandStatus(status) {
  return ACTIVE_HAND_STATUSES.includes(status);
}

export function isDealableHandStatus(status) {
  return DEALABLE_HAND_STATUSES.includes(status);
}

export function isJoinableStatus(status) {
  return status === 'WAITING' || status === 'ROUND_END';
}

export function isCountdownStatus(status, countdownSeconds = 0) {
  return status === 'COUNTDOWN' || status === 'NEXT_ROUND' || (countdownSeconds && countdownSeconds > 0);
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
    case 'NEXT_ROUND':
      return 'NEXT ROUND';
    case 'WAITING':
      return 'WAITING';
    case 'COUNTDOWN':
      return 'COUNTDOWN';
    default:
      return status || 'WAITING';
  }
}

function hasOwn(obj, key) {
  return Object.prototype.hasOwnProperty.call(obj || {}, key);
}

function resolveSyntheticStatus(userId, data) {
  if (data.packedPlayerIds?.includes(userId)) return 'PACKED';
  if (data.seenPlayerIds?.includes(userId)) return 'SEEN';
  if (data.blindPlayerIds?.includes(userId)) return 'BLIND';
  return 'BLIND';
}

/**
 * Merge seat projections without wiping SEEN card values on incomplete updates
 * (e.g. TABLE_UPDATED Mongo entity, TURN_CHANGED patches).
 */
export function mergePlayers(prevPlayers = [], nextPlayers = [], user, options = {}) {
  const { forceResetHands = false, seenPlayerIds, packedPlayerIds, blindPlayerIds } = options;

  if (forceResetHands && nextPlayers?.length) {
    return nextPlayers.map((p) => ({
      ...p,
      cards: p.cards || [],
      cardCount: p.cardCount ?? 3,
      status: p.status || 'BLIND',
    }));
  }

  if (!nextPlayers?.length) {
    return prevPlayers;
  }

  const prevById = new Map((prevPlayers || []).map((p) => [p.userId, p]));
  const incomingLooksLikeProjection = nextPlayers.some(
    (p) => Array.isArray(p.cards) || p.cardCount != null || p.status === 'SEEN' || p.status === 'PACKED' || p.status === 'BLIND'
  );

  // Incomplete lobby/table payloads that only carry seat ids — keep prior hand views.
  const looksLikeShellOnly = nextPlayers.every(
    (p) => (!p.cards || p.cards.length === 0)
      && (p.status === 'BLIND' || !p.status)
      && (p.cardCount === 0 || p.cardCount == null)
  ) && (prevPlayers || []).some((p) => (p.cards && p.cards.length > 0) || p.status === 'SEEN');

  if (looksLikeShellOnly) {
    return (prevPlayers || []).map((prev) => {
      let status = prev.status;
      if (packedPlayerIds?.includes(prev.userId)) status = 'PACKED';
      else if (seenPlayerIds?.includes(prev.userId)) status = 'SEEN';
      else if (blindPlayerIds?.includes(prev.userId)) status = 'BLIND';
      return { ...prev, status };
    });
  }

  if (!incomingLooksLikeProjection && prevPlayers?.length) {
    return prevPlayers;
  }

  return nextPlayers.map((incoming) => {
    const prev = prevById.get(incoming.userId);
    let status = incoming.status || prev?.status || 'BLIND';
    if (packedPlayerIds?.includes(incoming.userId)) status = 'PACKED';
    else if (seenPlayerIds?.includes(incoming.userId)) status = 'SEEN';
    else if (blindPlayerIds?.includes(incoming.userId) && status !== 'SEEN' && status !== 'PACKED') {
      status = 'BLIND';
    }

    const incomingCards = Array.isArray(incoming.cards) ? incoming.cards : null;
    const prevCards = Array.isArray(prev?.cards) ? prev.cards : [];
    const isSelf = user?.id && incoming.userId === user.id;

    // Keep own SEEN cards visible across turn/table patches that omit card values.
    let cards = incomingCards;
    if ((!cards || cards.length === 0) && status === 'SEEN' && isSelf && prevCards.length > 0) {
      cards = prevCards;
    }
    if (!cards) {
      cards = [];
    }

    return {
      ...prev,
      ...incoming,
      status,
      cards,
      cardCount: incoming.cardCount ?? prev?.cardCount ?? (cards.length || 3),
      displayName: incoming.displayName || prev?.displayName || 'Player',
    };
  });
}

export function mergeGameState(prev, incoming, user, options = {}) {
  const next = normalizeGameState(incoming, user);
  if (!next) return prev ?? null;
  if (!prev) return next;

  const status = next.status ?? prev.status;
  const turnFromIncoming = hasOwn(incoming, 'currentTurnPlayerId')
    || hasOwn(incoming, 'currentTurnUserId')
    || hasOwn(incoming, 'activeUserId');

  let currentTurnPlayerId = turnFromIncoming
    ? (next.currentTurnPlayerId ?? null)
    : (next.currentTurnPlayerId ?? (
      isActiveHandStatus(status) ? prev.currentTurnPlayerId : null
    ));

  if (status === 'ROUND_END' || status === 'WAITING' || status === 'NEXT_ROUND' || status === 'CLOSED') {
    currentTurnPlayerId = null;
  }

  const packedPlayerIds = hasOwn(incoming, 'packedPlayerIds')
    ? (next.packedPlayerIds || [])
    : (next.packedPlayerIds?.length ? next.packedPlayerIds : prev.packedPlayerIds);

  const userIsPacked = user?.id && packedPlayerIds?.includes(user.id);

  const myTurnExplicit = hasOwn(incoming, 'myTurn');
  const derivedMyTurn = userIsPacked
    ? false
    : Boolean(currentTurnPlayerId && user?.id && currentTurnPlayerId === user.id);

  const forceResetHands = Boolean(options.forceResetHands);
  const seenPlayerIds = next.seenPlayerIds?.length
    ? next.seenPlayerIds
    : (isActiveHandStatus(status) ? prev.seenPlayerIds : next.seenPlayerIds);
  const blindPlayerIds = hasOwn(incoming, 'blindPlayerIds')
    ? (next.blindPlayerIds || [])
    : (next.blindPlayerIds?.length ? next.blindPlayerIds : prev.blindPlayerIds);

  // Prefer explicit seen list; never drop SEEN ids on empty shell patches.
  const mergedSeenIds = hasOwn(incoming, 'seenPlayerIds')
    ? (next.seenPlayerIds?.length ? next.seenPlayerIds : (
      // Empty array on turn patch without the field meaning — normalize always sets [].
      // Only trust empty if incoming actually had the key as empty from a projection.
      (Array.isArray(incoming.seenPlayerIds) ? incoming.seenPlayerIds : prev.seenPlayerIds)
    ))
    : (prev.seenPlayerIds || []);

  const players = mergePlayers(prev.players, next.players, user, {
    forceResetHands,
    seenPlayerIds: mergedSeenIds,
    packedPlayerIds,
    blindPlayerIds,
  });

  const handEndedStatus = status === 'ROUND_END' || status === 'WAITING' || status === 'NEXT_ROUND' || status === 'CLOSED';
  const incomingWinnerDefined = hasOwn(incoming, 'winnerSnapshot');
  const preserveWinner = handEndedStatus
    && prev?.winnerSnapshot
    && incomingWinnerDefined
    && (next.winnerSnapshot == null);

  const merged = {
    ...prev,
    ...next,
    hostId: next.hostId ?? prev.hostId,
    minPlayers: next.minPlayers ?? prev.minPlayers,
    maxPlayers: next.maxPlayers ?? prev.maxPlayers,
    bootAmountPaise: next.bootAmountPaise ?? prev.bootAmountPaise,
    seatedPlayerIds: next.seatedPlayerIds?.length ? next.seatedPlayerIds : prev.seatedPlayerIds,
    seatedPlayers: next.seatedPlayers?.length ? next.seatedPlayers : prev.seatedPlayers,
    status,
    players,
    currentTurnPlayerId,
    dealerSeatIndex: next.dealerSeatIndex ?? prev.dealerSeatIndex,
    currentTurnSeatIndex: next.currentTurnSeatIndex ?? prev.currentTurnSeatIndex,
    turnTimeoutSeconds: next.turnTimeoutSeconds ?? prev.turnTimeoutSeconds,
    turnSecondsRemaining: userIsPacked
      ? 0
      : (hasOwn(incoming, 'turnSecondsRemaining')
      || hasOwn(incoming, 'durationSeconds')
      ? (next.turnSecondsRemaining ?? 0)
      : (next.turnSecondsRemaining ?? prev.turnSecondsRemaining)),
    turnDeadlineAt: userIsPacked
      ? null
      : (hasOwn(incoming, 'turnDeadlineAt')
      ? next.turnDeadlineAt
      : (next.turnDeadlineAt ?? prev.turnDeadlineAt)),
    activePlayerIds: hasOwn(incoming, 'activePlayerIds')
      ? (next.activePlayerIds || [])
      : (next.activePlayerIds?.length ? next.activePlayerIds : prev.activePlayerIds),
    blindPlayerIds: hasOwn(incoming, 'blindPlayerIds')
      ? (next.blindPlayerIds || [])
      : (next.blindPlayerIds?.length ? next.blindPlayerIds : prev.blindPlayerIds),
    seenPlayerIds: mergedSeenIds,
    packedPlayerIds: hasOwn(incoming, 'packedPlayerIds')
      ? (next.packedPlayerIds || [])
      : (next.packedPlayerIds?.length ? next.packedPlayerIds : prev.packedPlayerIds),
    potPaise: next.potPaise ?? prev.potPaise,
    requiredBetPaise: next.requiredBetPaise ?? prev.requiredBetPaise,
    minRaiseBetPaise: next.minRaiseBetPaise ?? prev.minRaiseBetPaise,
    maxBetPaise: next.maxBetPaise ?? prev.maxBetPaise,
    playerContributedPaise: next.playerContributedPaise ?? prev.playerContributedPaise,
    blindSeenRatio: next.blindSeenRatio ?? prev.blindSeenRatio,
    myTurn: myTurnExplicit ? Boolean(next.myTurn) : derivedMyTurn,
    allowedActions: hasOwn(incoming, 'allowedActions')
      ? (next.allowedActions || [])
      : (next.allowedActions?.length ? next.allowedActions : prev.allowedActions),
    winnerSnapshot: preserveWinner
      ? prev.winnerSnapshot
      : (hasOwn(incoming, 'winnerSnapshot')
      ? next.winnerSnapshot
      : (next.winnerSnapshot ?? prev.winnerSnapshot)),
    handOutcome: hasOwn(incoming, 'handOutcome')
      ? next.handOutcome
      : (next.handOutcome ?? prev.handOutcome),
    pendingShow: hasOwn(incoming, 'pendingShow')
      ? next.pendingShow
      : (next.pendingShow ?? prev.pendingShow),
    pendingSideShow: hasOwn(incoming, 'pendingSideShow')
      ? next.pendingSideShow
      : (next.pendingSideShow ?? prev.pendingSideShow),
    revealedHands: hasOwn(incoming, 'revealedHands')
      ? next.revealedHands
      : (next.revealedHands ?? prev.revealedHands),
    disconnectedPlayerIds: hasOwn(incoming, 'disconnectedPlayerIds')
      ? (next.disconnectedPlayerIds || [])
      : (next.disconnectedPlayerIds?.length ? next.disconnectedPlayerIds : prev.disconnectedPlayerIds),
    hostId: hasOwn(incoming, 'hostId') ? next.hostId : (next.hostId ?? prev.hostId),
    countdownSeconds: next.countdownSeconds ?? prev.countdownSeconds,
    nextRoundSeconds: next.nextRoundSeconds ?? prev.nextRoundSeconds,
    tableType: next.tableType ?? prev.tableType,
    inviteCode: next.inviteCode ?? prev.inviteCode,
  };

  // Only clear winner on a true new-hand reset — never on every RUNNING tick
  if (options.forceResetHands) {
    if (!hasOwn(incoming, 'winnerSnapshot')) merged.winnerSnapshot = null;
    if (!hasOwn(incoming, 'handOutcome')) merged.handOutcome = null;
    if (!hasOwn(incoming, 'pendingShow')) merged.pendingShow = null;
    if (!hasOwn(incoming, 'revealedHands')) merged.revealedHands = null;
  } else if (isActiveHandStatus(merged.status) && hasOwn(incoming, 'winnerSnapshot')) {
    merged.winnerSnapshot = next.winnerSnapshot;
  }

  if (isActiveHandStatus(merged.status) && !myTurnExplicit) {
    merged.myTurn = Boolean(merged.currentTurnPlayerId && user?.id && merged.currentTurnPlayerId === user.id)
      || (merged.pendingShow?.targetId === user?.id
        && (merged.allowedActions || []).includes('SHOW_ACCEPT'))
      || (merged.pendingSideShow?.targetId === user?.id);
  }

  const endStatuses = ['ROUND_END', 'WAITING', 'NEXT_ROUND', 'CLOSED'];

  // Keep pending Show across racing STATE_UPDATE (often sends pendingShow: null) / BETTING patches
  if (prev?.pendingShow && !options.clearPendingShow && !options.forceResetHands) {
    const incomingClearedShow = hasOwn(incoming, 'pendingShow') && incoming.pendingShow == null;
    const incomingOmittedShow = !hasOwn(incoming, 'pendingShow');
    if (endStatuses.includes(merged.status)) {
      merged.pendingShow = null;
    } else if (incomingClearedShow || incomingOmittedShow) {
      // Ignore null/omitted pendingShow from projections — only clearPendingShow ends it
      merged.pendingShow = prev.pendingShow;
      if (prev.status === 'SHOW' && (!hasOwn(incoming, 'status')
        || incoming.status === 'RUNNING'
        || incoming.status === 'SHOW')) {
        merged.status = 'SHOW';
      }
    }
  }

  if (options.clearPendingShow) {
    merged.pendingShow = null;
  }

  // Target must always be able to Accept while Show is pending
  if (merged.pendingShow?.targetId && user?.id
    && String(merged.pendingShow.targetId) === String(user.id)
    && !endStatuses.includes(merged.status)) {
    const actions = new Set(merged.allowedActions || []);
    actions.add('SHOW_ACCEPT');
    actions.add('SHOW_REJECT');
    merged.allowedActions = [...actions];
    merged.myTurn = true;
    if (merged.status !== 'SHOW') merged.status = 'SHOW';
  }

  // Preserve winner across ROUND_END → NEXT_ROUND until next hand reset
  if (prev?.winnerSnapshot && !options.forceResetHands) {
    const keepStatuses = ['ROUND_END', 'WAITING', 'NEXT_ROUND', 'CLOSED', 'SHOW'];
    const incomingClearedWinner = hasOwn(incoming, 'winnerSnapshot') && incoming.winnerSnapshot == null;
    const incomingOmittedWinner = !hasOwn(incoming, 'winnerSnapshot');
    if ((incomingClearedWinner || incomingOmittedWinner)
      && (keepStatuses.includes(merged.status) || keepStatuses.includes(prev.status))) {
      merged.winnerSnapshot = prev.winnerSnapshot;
      if (prev.handOutcome && !merged.handOutcome) merged.handOutcome = prev.handOutcome;
    }
  }

  if (
    prev.status === merged.status
    && prev.potPaise === merged.potPaise
    && prev.currentTurnPlayerId === merged.currentTurnPlayerId
    && prev.myTurn === merged.myTurn
    && prev.turnSecondsRemaining === merged.turnSecondsRemaining
    && prev.turnDeadlineAt === merged.turnDeadlineAt
    && prev.requiredBetPaise === merged.requiredBetPaise
    && prev.minRaiseBetPaise === merged.minRaiseBetPaise
    && prev.walletBalancePaise === merged.walletBalancePaise
    && prev.blindAmountPaise === merged.blindAmountPaise
    && prev.chaalAmountPaise === merged.chaalAmountPaise
    && prev.currentBaseStakePaise === merged.currentBaseStakePaise
    && prev.gameVariant === merged.gameVariant
    && prev.jokerRank === merged.jokerRank
    && prev.dealerSeatIndex === merged.dealerSeatIndex
    && prev.hostId === merged.hostId
    && prev.countdownSeconds === merged.countdownSeconds
    && JSON.stringify(prev.allowedActions) === JSON.stringify(merged.allowedActions)
    && JSON.stringify(prev.players) === JSON.stringify(merged.players)
    && JSON.stringify(prev.seenPlayerIds) === JSON.stringify(merged.seenPlayerIds)
    && JSON.stringify(prev.blindPlayerIds) === JSON.stringify(merged.blindPlayerIds)
    && JSON.stringify(prev.packedPlayerIds) === JSON.stringify(merged.packedPlayerIds)
    && JSON.stringify(prev.pendingShow) === JSON.stringify(merged.pendingShow)
    && JSON.stringify(prev.pendingSideShow) === JSON.stringify(merged.pendingSideShow)
    && JSON.stringify(prev.winnerSnapshot) === JSON.stringify(merged.winnerSnapshot)
    && JSON.stringify(prev.revealedHands) === JSON.stringify(merged.revealedHands)
    && JSON.stringify(prev.raiseOptionsPaise) === JSON.stringify(merged.raiseOptionsPaise)
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

  let players = (data.players && data.players.length > 0)
    ? data.players.map((p) => ({
        ...p,
        status: p.status || resolveSyntheticStatus(p.userId, data),
        cards: Array.isArray(p.cards) ? p.cards : [],
        cardCount: p.cardCount ?? (Array.isArray(p.cards) && p.cards.length ? p.cards.length : 3),
      }))
    : seatedPlayers.map((p, index) => ({
        userId: p.userId,
        displayName: p.displayName || `Player ${index + 1}`,
        status: resolveSyntheticStatus(p.userId, data),
        cards: [],
        cardCount: 3,
      }));

  if (players.length === 0 && seatedIds.length > 0) {
    players = seatedIds.map((id, index) => ({
      userId: id,
      displayName: id === user?.id ? (user?.displayName || 'You') : `Player ${index + 1}`,
      status: resolveSyntheticStatus(id, data),
      cards: [],
      cardCount: 3,
    }));
  }

  const currentTurnPlayerId = data.currentTurnPlayerId
    || data.currentTurnUserId
    || data.activeUserId
    || null;

  const myTurn = hasOwn(data, 'myTurn')
    ? Boolean(data.myTurn)
    : Boolean(currentTurnPlayerId && user?.id && currentTurnPlayerId === user.id);

  return {
    ...data,
    tableId: data.tableId || data.id,
    hostId: data.hostId,
    tableType: data.tableType,
    gameVariant: data.gameVariant || 'CLASSIC',
    jokerRank: data.jokerRank ?? null,
    variantPhase: data.variantPhase ?? null,
    auctionHighBidPaise: data.auctionHighBidPaise ?? 0,
    auctionHighBidderId: data.auctionHighBidderId ?? null,
    auctionMinBidPaise: data.auctionMinBidPaise ?? 0,
    inviteCode: data.inviteCode,
    countdownSeconds: data.countdownSeconds ?? 0,
    minPlayers: data.minPlayers ?? 3,
    maxPlayers: data.maxPlayers ?? 6,
    currentPlayerCount: data.currentPlayerCount ?? players.length,
    seatedPlayerIds: seatedIds,
    seatedPlayers,
    players,
    currentTurnPlayerId,
    dealerSeatIndex: data.dealerSeatIndex ?? -1,
    currentTurnSeatIndex: data.currentTurnSeatIndex ?? -1,
    turnTimeoutSeconds: data.turnTimeoutSeconds ?? data.durationSeconds ?? 20,
    turnSecondsRemaining: data.turnSecondsRemaining ?? data.durationSeconds ?? 0,
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
    myTurn,
    allowedActions: data.allowedActions || [],
    winnerSnapshot: data.winnerSnapshot ?? null,
    handOutcome: data.handOutcome ?? null,
    pendingShow: hasOwn(data, 'pendingShow') ? data.pendingShow : undefined,
    disconnectedPlayerIds: data.disconnectedPlayerIds || [],
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

  if (status === 'COUNTDOWN' || status === 'NEXT_ROUND' || (countdownSeconds && countdownSeconds > 0)) {
    if (status === 'NEXT_ROUND') {
      return `Next round starting in ${countdownSeconds || '?'}…`;
    }
    return `Game starts in ${countdownSeconds || '?'}…`;
  }
  if (status === 'STARTING') {
    return 'Starting game… shuffling and dealing cards';
  }
  if (isActiveHandStatus(status)) {
    return `♠ Hand in progress (${playerCount}/${maxPlayers} players) ♠`;
  }
  if (status === 'ROUND_END') {
    return playerCount >= minPlayers
      ? 'Round finished — next round countdown starting…'
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
