import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  PlusCircle, Key, Users, Trophy, Play, Lock, RefreshCw, AlertCircle,
  ShieldCheck, Coins, Flame, Crown, Zap, Activity, Clock, History, Bell,
  ArrowUpRight, ArrowDownRight, Radio, Sparkles, Home, ShoppingCart,
  HelpCircle, Gift, UserPlus, User as UserIcon,
} from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { LOBBY_REFRESH_EVENTS } from '@/shared/api/realtimeEvents';
import stompService from '@/shared/api/stompService';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import { getNotificationDisplayLabel } from '@/features/notifications/notificationUtils';
import { isActiveHandStatus, getTableStatusLabel, normalizeGameState, isCountdownStatus, isJoinableStatus } from '@/features/table/tableUtils';
import HomeCasinoScreen from './HomeCasinoScreen';
import { VARIANT_CARDS } from './variants';

function formatHistoryDate(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatWinningCategory(category, description, foldWin) {
  if (description) return description;
  if (foldWin || category === 'FOLD_WIN') return 'Fold Win';
  if (!category) return 'Completed';
  return category.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function LobbyView({
  onJoinTable,
  onOpenAuth,
  onOpenWallet,
  onOpenLeaderboard,
  onOpenProfile,
  onOpenNotifications,
}) {
  const { user, isAuthenticated, refreshWalletBalance } = useAuth();
  const { updateTableState, notifications: liveNotifications } = useGame();

  // Aggregate Home Dashboard State (100% Backend-Driven)
  const [dashboardData, setDashboardData] = useState(null);
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(false);
  const [quickPlayLoading, setQuickPlayLoading] = useState(null);
  const [selectedStake, setSelectedStake] = useState('ALL');
  const [error, setError] = useState('');
  const [bootOptionsPaise, setBootOptionsPaise] = useState([1000]);
  const [selectedVariant, setSelectedVariant] = useState('CLASSIC');
  const [selectedBootPaise, setSelectedBootPaise] = useState(1000);
  const [showQuickPlayModal, setShowQuickPlayModal] = useState(false);
  const [quickPlayModalVariant, setQuickPlayModalVariant] = useState('CLASSIC');

  // Private/Public Table Creation Modal State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createTableType, setCreateTableType] = useState('PUBLIC');
  const [createStakeTier, setCreateStakeTier] = useState('LOW');
  const [createBootAmountPaise, setCreateBootAmountPaise] = useState(1000);
  const [createMaxPlayers, setCreateMaxPlayers] = useState(3);
  const [createMinPlayers, setCreateMinPlayers] = useState(3);
  const [createTableName, setCreateTableName] = useState('');
  const [createdPrivateCode, setCreatedPrivateCode] = useState(null);
  const [pendingCreatedTableId, setPendingCreatedTableId] = useState(null);

  // Private Table Code Join Modal State
  const [showJoinPrivateModal, setShowJoinPrivateModal] = useState(false);
  const [inviteCodeInput, setInviteCodeInput] = useState('');

  // 1. Fetch Complete Aggregate Home Dashboard from Backend REST API
  const fetchHomeDashboard = async () => {
    setLoading(true);
    try {
      const { data: res } = await axiosClient.get('/home/dashboard');
      const data = res?.data || res;
      setDashboardData(data);
      if (data?.publicTables) {
        setTables(data.publicTables);
      }
    } catch (err) {
      console.error('Failed to fetch home dashboard:', err);
      // Fallback fetch public tables
      try {
        const { data: res } = await axiosClient.get('/lobby/tables');
        const rawList = res?.data?.content || res?.data || res;
        setTables(Array.isArray(rawList) ? rawList : []);
      } catch (e) {}
    } finally {
      setLoading(false);
    }
  };

  const fetchBootOptions = async () => {
    try {
      const { data: res } = await axiosClient.get('/lobby/boot-options');
      const data = res?.data || res;
      const options = Array.isArray(data?.bootAmountOptionsPaise) && data.bootAmountOptionsPaise.length
        ? data.bootAmountOptionsPaise
        : [1000];
      setBootOptionsPaise(options);
      setCreateBootAmountPaise(options[0]);
      setSelectedBootPaise(options[0]);
      setCreateMinPlayers(data?.minimumPlayers ?? 3);
      setCreateMaxPlayers(data?.maximumPlayers ?? 6);
    } catch {
      setBootOptionsPaise([1000]);
      setCreateBootAmountPaise(1000);
    }
  };

  useEffect(() => {
    if (!isAuthenticated) {
      setDashboardData(null);
      return undefined;
    }

    fetchHomeDashboard();
    fetchBootOptions();

    const onRealtime = (e) => {
      const event = e.detail;
      if (event?.eventType && LOBBY_REFRESH_EVENTS.has(event.eventType)) {
        fetchHomeDashboard();
        if (refreshWalletBalance) refreshWalletBalance();
      }
    };

    window.addEventListener('realtime', onRealtime);

    // Fallback poll when STOMP is disconnected (30s)
    const interval = setInterval(() => {
      if (!stompService.isConnected()) {
        fetchHomeDashboard();
      }
    }, 30000);

    return () => {
      window.removeEventListener('realtime', onRealtime);
      clearInterval(interval);
    };
  }, [selectedStake, isAuthenticated]);

  const handleSelectVariant = (variantKey) => {
    setSelectedVariant(variantKey);
  };

  const openQuickPlayModal = (variantKey = selectedVariant, bootPaise = null) => {
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setQuickPlayModalVariant(variantKey || 'CLASSIC');
    setSelectedVariant(variantKey || 'CLASSIC');
    if (bootPaise != null && Number.isFinite(Number(bootPaise))) {
      setSelectedBootPaise(Number(bootPaise));
    }
    setShowQuickPlayModal(true);
    setError('');
  };

  const confirmQuickPlay = async () => {
    setShowQuickPlayModal(false);
    await handleQuickPlay(selectedBootPaise, quickPlayModalVariant);
  };

  // 2. Quick Play Single-Click Matchmaking Handler
  const handleQuickPlay = async (bootAmountPaise, variant = selectedVariant) => {
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setQuickPlayLoading(bootAmountPaise);
    setError('');
    try {
      const { data: res } = await axiosClient.post('/tables/quick-play', {
        bootAmountPaise,
        gameVariant: variant || 'CLASSIC',
      });
      const joinData = res?.data || res;
      if (refreshWalletBalance) refreshWalletBalance();
      const tablePayload = joinData.tableDetail || joinData;
      updateTableState(normalizeGameState(tablePayload, user) || joinData);
      const targetId = joinData.tableId
        || joinData.id
        || joinData.tableDetail?.tableId
        || joinData.tableDetail?.id;
      if (targetId) {
        onJoinTable(targetId);
      } else {
        setError('Joined table but could not resolve table id. Try refreshing.');
      }
    } catch (err) {
      console.error('Quick Play error:', err);
      const errMsg = err.response?.data?.message || err.response?.data?.error || 'Quick play failed. Please try again.';
      setError(errMsg);
    } finally {
      setQuickPlayLoading(null);
    }
  };

  // 3. Join Existing Table Handler
  const handleJoinTableClick = async (tableId) => {
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setError('');
    try {
      const checkRes = await axiosClient.post(`/lobby/tables/${tableId}/check-eligibility`);
      const checkData = checkRes.data?.data || checkRes.data;
      if (!checkData.eligible) {
        if (checkData.reason === 'INSUFFICIENT_BALANCE') {
          const reqRs = checkData.minRequiredPaise ? (checkData.minRequiredPaise / 100) : 10;
          const curRs = checkData.currentBalancePaise ? (checkData.currentBalancePaise / 100).toFixed(2) : '0.00';
          setError(`Insufficient balance to join! Min required is ₹${reqRs} (Your balance: ₹${curRs}). Add Cash to continue.`);
        } else {
          setError(`Not eligible to join: ${checkData.reason}`);
        }
        return;
      }

      const joinRes = await axiosClient.post(`/tables/${tableId}/join`);
      const joinData = joinRes.data?.data || joinRes.data;
      if (refreshWalletBalance) refreshWalletBalance();
      const tablePayload = joinData.tableDetail || joinData;
      updateTableState(normalizeGameState(tablePayload, user) || joinData);
      onJoinTable(tableId);
    } catch (err) {
      if (err.response?.status === 401) {
        if (onOpenAuth) onOpenAuth();
        setError('Session expired. Please log in again to take a seat.');
        return;
      }
      const errMsg = err.response?.data?.message || err.response?.data?.error || 'Failed to join table.';
      setError(errMsg);
    }
  };

  // 4. Create Public/Private Table Handler
  const handleCreateTableSubmit = async (e) => {
    e.preventDefault();
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setError('');
    try {
      const isPrivate = createTableType === 'PRIVATE';
      const endpoint = isPrivate ? '/lobby/tables/private' : '/lobby/tables/public';
      const { data: res } = await axiosClient.post(endpoint, {
        tableName: createTableName || undefined,
        stakeTier: createStakeTier,
        bootAmount: createBootAmountPaise,
        gameVariant: selectedVariant,
        maxPlayers: createMaxPlayers,
        minPlayers: createMinPlayers,
      });

      const data = res?.data || res;
      const targetTableId = data.tableId || data.id;
      setPendingCreatedTableId(targetTableId);

      const tableMeta = {
        tableId: targetTableId,
        hostId: user?.id,
        minPlayers: createMinPlayers,
        maxPlayers: createMaxPlayers,
        status: 'WAITING',
        currentPlayerCount: 1,
      };

      if (isPrivate) {
        setCreatedPrivateCode(data.inviteCode);
      } else {
        setShowCreateModal(false);
        if (targetTableId) {
          updateTableState(tableMeta);
          onJoinTable(targetTableId);
        }
      }
      fetchHomeDashboard();
    } catch (err) {
      if (err.response?.status === 401) {
        setShowCreateModal(false);
        if (onOpenAuth) onOpenAuth();
        setError('Session expired. Please log in again to create a table.');
        return;
      }
      const errMsg = err.response?.data?.message || err.response?.data?.error || 'Failed to create table.';
      setError(errMsg);
    }
  };

  // 5. Join Private Table with Code Handler
  const handleJoinPrivateSubmit = async (e) => {
    e.preventDefault();
    if (!inviteCodeInput || inviteCodeInput.trim().length < 6) {
      setError('Please enter a valid private invite code (6 or 7 characters).');
      return;
    }
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setError('');
    try {
      const code = inviteCodeInput.trim().toUpperCase();
      const { data: res } = await axiosClient.post(`/lobby/tables/private/join?inviteCode=${encodeURIComponent(code)}`);
      const joinData = res?.data || res;
      setShowJoinPrivateModal(false);
      setInviteCodeInput('');
      if (refreshWalletBalance) refreshWalletBalance();
      const tablePayload = joinData.tableDetail || joinData;
      updateTableState(normalizeGameState(tablePayload, user) || joinData);
      const targetId = joinData.tableId || tablePayload?.tableId;
      if (targetId) {
        onJoinTable(targetId);
      }
      fetchHomeDashboard();
    } catch (err) {
      if (err.response?.status === 401) {
        if (onOpenAuth) onOpenAuth();
        setError('Session expired. Please log in again.');
        return;
      }
      const errMsg = err.response?.data?.message || err.response?.data?.error || 'Invalid or expired private table code.';
      setError(errMsg);
    }
  };

  const handleAcceptPrivateInvite = async (invite) => {
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setError('');
    try {
      const code = invite.inviteCode;
      const { data: res } = await axiosClient.post(`/lobby/tables/private/join?inviteCode=${encodeURIComponent(code)}`);
      const joinData = res?.data || res;
      if (refreshWalletBalance) refreshWalletBalance();
      const tablePayload = joinData.tableDetail || joinData;
      updateTableState(normalizeGameState(tablePayload, user) || joinData);
      const targetId = joinData.tableId || invite.tableId;
      if (targetId) {
        onJoinTable(targetId);
      }
      fetchHomeDashboard();
    } catch (err) {
      const errMsg = err.response?.data?.message || err.response?.data?.error || 'Could not join private table.';
      setError(errMsg);
    }
  };

  const liveStats = dashboardData?.liveStats || {
    onlinePlayers: 1,
    runningTablesCount: tables.filter((t) => isActiveHandStatus(t.status)).length,
    waitingTablesCount: tables.filter((t) => t.status === 'WAITING' || t.status === 'ROUND_END').length,
    totalActiveGames: tables.length,
  };

  const activeGame = dashboardData?.activeGame;
  const privateInvitations = dashboardData?.privateInvitations || [];
  const recentHistory = dashboardData?.recentHistory || [];
  const dashboardNotifications = dashboardData?.recentNotifications || [];
  const notifications = [
    ...liveNotifications,
    ...dashboardNotifications.filter((d) => !liveNotifications.some((l) => l.id === d.id)),
  ].slice(0, 5);

  return (
    <div>
      <HomeCasinoScreen
        tables={tables}
        bootOptionsPaise={bootOptionsPaise}
        loading={loading}
        error={error}
        setError={setError}
        activeGame={activeGame}
        privateInvitations={privateInvitations}
        quickPlayLoading={quickPlayLoading}
        selectedVariant={selectedVariant}
        onRequestQuickPlay={openQuickPlayModal}
        onQuickPlay={openQuickPlayModal}
        onJoinTableClick={handleJoinTableClick}
        onOpenCreatePublic={() => {
          setCreateTableType('PUBLIC');
          setCreatedPrivateCode(null);
          setShowCreateModal(true);
        }}
        onOpenCreatePrivate={() => {
          setCreateTableType('PRIVATE');
          setCreatedPrivateCode(null);
          setShowCreateModal(true);
        }}
        onOpenJoinCode={() => setShowJoinPrivateModal(true)}
        onAcceptInvite={handleAcceptPrivateInvite}
        onRefresh={fetchHomeDashboard}
        onOpenWallet={onOpenWallet}
        onOpenLeaderboard={onOpenLeaderboard}
        onOpenProfile={onOpenProfile}
        onOpenNotifications={onOpenNotifications}
        onOpenAuth={onOpenAuth}
        onResumeGame={onJoinTable}
      />

      {/* Create Table Modal */}
      <AnimatePresence>
        {showCreateModal && (
          <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl relative"
            >
              <h3 className="text-xl font-bold text-slate-100 mb-1">
                {createTableType === 'PUBLIC' ? 'Create Public Table' : 'Create Private Table'}
              </h3>
              <p className="text-xs text-slate-400 mb-5">
                {createTableType === 'PUBLIC' ? 'Create an open table for all players to join' : 'Generates a shareable 6-character code for private matches'}
              </p>

              {createdPrivateCode ? (
                <div className="text-center py-6 bg-slate-950 border border-slate-800 rounded-xl p-4">
                  <ShieldCheck className="w-12 h-12 text-emerald-400 mx-auto mb-2" />
                  <span className="text-xs text-slate-400 block">PRIVATE INVITE CODE</span>
                  <span className="text-3xl font-black text-amber-400 tracking-widest my-2 block font-mono">
                    {createdPrivateCode}
                  </span>
                  <p className="text-xs text-slate-400">Share this code with your friends to join your private room</p>
                  <button
                    onClick={() => {
                      setShowCreateModal(false);
                      setCreatedPrivateCode(null);
                      if (pendingCreatedTableId) {
                        updateTableState({
                          tableId: pendingCreatedTableId,
                          hostId: user?.id,
                          minPlayers: createMinPlayers,
                          maxPlayers: createMaxPlayers,
                          status: 'WAITING',
                          currentPlayerCount: 1,
                        });
                        onJoinTable(pendingCreatedTableId);
                      }
                    }}
                    className="mt-5 px-6 py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl hover:bg-amber-400 cursor-pointer shadow-lg shadow-amber-500/20"
                  >
                    Enter Game Table Now
                  </button>
                </div>
              ) : (
                <form onSubmit={handleCreateTableSubmit} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Variation</label>
                    <select
                      value={selectedVariant}
                      onChange={(e) => setSelectedVariant(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200 focus:outline-none focus:border-amber-500/60"
                    >
                      {VARIANT_CARDS.map((v) => (
                        <option key={v.key} value={v.key}>{v.name}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Table Access</label>
                    <div className="grid grid-cols-2 gap-2 p-1 bg-slate-950 border border-slate-800 rounded-xl">
                      <button
                        type="button"
                        onClick={() => setCreateTableType('PUBLIC')}
                        className={`py-2 text-xs font-bold rounded-lg transition-all ${
                          createTableType === 'PUBLIC' ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        Public (Open)
                      </button>
                      <button
                        type="button"
                        onClick={() => setCreateTableType('PRIVATE')}
                        className={`py-2 text-xs font-bold rounded-lg transition-all ${
                          createTableType === 'PRIVATE' ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        Private (Code)
                      </button>
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Boot Amount (from Admin Settings)</label>
                    <select
                      value={createBootAmountPaise}
                      onChange={(e) => setCreateBootAmountPaise(Number(e.target.value))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200 focus:outline-none focus:border-amber-500/60"
                    >
                      {bootOptionsPaise.map((boot) => (
                        <option key={boot} value={boot}>Boot ₹{(boot / 100).toFixed(0)}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Minimum Players</label>
                    <select
                      value={createMinPlayers}
                      onChange={(e) => setCreateMinPlayers(Number(e.target.value))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200 focus:outline-none focus:border-amber-500/60"
                    >
                      {[2, 3, 4, 5, 6].filter((n) => n <= createMaxPlayers).map((n) => (
                        <option key={n} value={n}>{n} Players (min to start)</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Maximum Players (3 to 6)</label>
                    <select
                      value={createMaxPlayers}
                      onChange={(e) => {
                        const max = Number(e.target.value);
                        setCreateMaxPlayers(max);
                        if (createMinPlayers > max) setCreateMinPlayers(max);
                      }}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200 focus:outline-none focus:border-amber-500/60"
                    >
                      <option value={3}>3 Players (Minimum)</option>
                      <option value={4}>4 Players</option>
                      <option value={5}>5 Players</option>
                      <option value={6}>6 Players (Maximum)</option>
                    </select>
                  </div>

                  <div className="flex items-center justify-end gap-3 pt-3">
                    <button
                      type="button"
                      onClick={() => setShowCreateModal(false)}
                      className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="px-5 py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl hover:bg-amber-400 cursor-pointer shadow-lg shadow-amber-500/20"
                    >
                      Create Table
                    </button>
                  </div>
                </form>
              )}
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Private Table Code Join Modal */}
      <AnimatePresence>
        {showJoinPrivateModal && (
          <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="w-full max-w-sm bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl relative"
            >
              <h3 className="text-xl font-bold text-slate-100 mb-1 flex items-center gap-2">
                <Key className="w-5 h-5 text-amber-400" />
                <span>Join Private Room</span>
              </h3>
              <p className="text-xs text-slate-400 mb-5">Enter the private invite code provided by the host</p>

              <form onSubmit={handleJoinPrivateSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Invite Code</label>
                  <input
                    type="text"
                    maxLength={7}
                    placeholder="e.g. AB12CD"
                    value={inviteCodeInput}
                    onChange={(e) => setInviteCodeInput(e.target.value.toUpperCase())}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-center text-xl font-black text-amber-400 uppercase tracking-widest font-mono focus:outline-none focus:border-amber-500/60"
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowJoinPrivateModal(false)}
                    className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-5 py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl hover:bg-amber-400 cursor-pointer shadow-lg shadow-amber-500/20"
                  >
                    Join Private Table
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Quick Play — boot amount picker */}
      <AnimatePresence>
        {showQuickPlayModal && (
          <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4">
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="w-full max-w-sm bg-slate-900 border border-[#d4af37]/35 rounded-2xl p-6 shadow-2xl"
            >
              <h3 className="text-xl font-bold text-[#f5e6a8] mb-1">Choose Boot Amount</h3>
              <p className="text-xs text-slate-400 mb-5">
                Variation: <span className="text-[#d4af37] font-semibold">
                  {(quickPlayModalVariant || 'CLASSIC').replaceAll('_', ' ')}
                </span>
                {' '}· Same boot + same variant matchmaking
              </p>

              <p className="text-xs font-semibold text-slate-300 mb-2">Boot amount (per player)</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-5">
                {bootOptionsPaise.map((boot) => {
                  const selected = selectedBootPaise === boot;
                  return (
                    <button
                      key={boot}
                      type="button"
                      onClick={() => setSelectedBootPaise(boot)}
                      className={`py-3 rounded-xl border text-sm font-extrabold transition-all cursor-pointer ${
                        selected
                          ? 'border-[#d4af37] bg-[#d4af37]/20 text-[#f5e6a8] shadow-[0_0_12px_rgba(212,175,55,0.25)]'
                          : 'border-slate-700 bg-slate-950 text-slate-300 hover:border-[#d4af37]/45'
                      }`}
                    >
                      ₹{(boot / 100).toFixed(0)}
                    </button>
                  );
                })}
              </div>

              <p className="text-[10px] text-slate-500 mb-4">
                Minimum chips needed at table: boot × players (usually 3+). Your wallet will be checked on join.
              </p>

              <div className="flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowQuickPlayModal(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={Boolean(quickPlayLoading)}
                  onClick={confirmQuickPlay}
                  className="px-5 py-2.5 bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] font-bold text-xs rounded-xl hover:brightness-110 cursor-pointer disabled:opacity-50"
                >
                  {quickPlayLoading ? 'Joining…' : `Play · ₹${(selectedBootPaise / 100).toFixed(0)} boot`}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
