import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  PlusCircle, Key, Users, Trophy, Play, Lock, RefreshCw, AlertCircle,
  ShieldCheck, Coins, Flame, Crown, Zap, Activity, Clock, History, Bell,
  ArrowUpRight, ArrowDownRight, Radio, Sparkles
} from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { LOBBY_REFRESH_EVENTS } from '@/shared/api/realtimeEvents';
import stompService from '@/shared/api/stompService';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import { getNotificationDisplayLabel } from '@/features/notifications/notificationUtils';
import { isActiveHandStatus, getTableStatusLabel, normalizeGameState, isCountdownStatus, isJoinableStatus } from '@/features/table/tableUtils';

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

export default function LobbyView({ onJoinTable, onOpenAuth, onOpenWallet }) {
  const { user, isAuthenticated, refreshWalletBalance } = useAuth();
  const { updateTableState, notifications: liveNotifications } = useGame();

  // Aggregate Home Dashboard State (100% Backend-Driven)
  const [dashboardData, setDashboardData] = useState(null);
  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(false);
  const [quickPlayLoading, setQuickPlayLoading] = useState(null);
  const [selectedStake, setSelectedStake] = useState('ALL');
  const [error, setError] = useState('');

  // Private/Public Table Creation Modal State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createTableType, setCreateTableType] = useState('PUBLIC');
  const [createStakeTier, setCreateStakeTier] = useState('LOW');
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

  useEffect(() => {
    if (!isAuthenticated) {
      setDashboardData(null);
      return undefined;
    }

    fetchHomeDashboard();

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

  // 2. Quick Play Single-Click Matchmaking Handler
  const handleQuickPlay = async (bootAmountPaise) => {
    if (!isAuthenticated) {
      if (onOpenAuth) onOpenAuth();
      return;
    }
    setQuickPlayLoading(bootAmountPaise);
    setError('');
    try {
      const { data: res } = await axiosClient.post('/tables/quick-play', { bootAmountPaise });
      const joinData = res?.data || res;
      if (refreshWalletBalance) refreshWalletBalance();
      updateTableState(joinData);
      const targetId = joinData.tableId || joinData.id;
      if (targetId) {
        onJoinTable(targetId);
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
    if (!inviteCodeInput || inviteCodeInput.trim().length < 4) {
      setError('Please enter a valid 6-character private invite code.');
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
    <div className="space-y-6 pb-12">
      {/* 1. Real-Time Platform Live Stats Header Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-3.5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center">
            <Radio className="w-5 h-5 animate-pulse" />
          </div>
          <div>
            <span className="text-[10px] text-slate-500 uppercase font-bold block">ONLINE PLAYERS</span>
            <span className="text-base font-black text-slate-100 font-mono">{liveStats.onlinePlayers} Live</span>
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-3.5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center">
            <Flame className="w-5 h-5" />
          </div>
          <div>
            <span className="text-[10px] text-slate-500 uppercase font-bold block">RUNNING TABLES</span>
            <span className="text-base font-black text-cyan-400 font-mono">{liveStats.runningTablesCount} Active</span>
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-3.5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center">
            <Users className="w-5 h-5" />
          </div>
          <div>
            <span className="text-[10px] text-slate-500 uppercase font-bold block">WAITING ROOMS</span>
            <span className="text-base font-black text-amber-400 font-mono">{liveStats.waitingTablesCount} Waiting</span>
          </div>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-3.5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center">
            <Trophy className="w-5 h-5" />
          </div>
          <div>
            <span className="text-[10px] text-slate-500 uppercase font-bold block">TOTAL GAMES</span>
            <span className="text-base font-black text-slate-100 font-mono">{liveStats.totalActiveGames} Total</span>
          </div>
        </div>
      </div>

      {/* 2. Active Game Resume Banner (Detected automatically if user is seated) */}
      {activeGame && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-gradient-to-r from-amber-500/20 via-amber-600/20 to-emerald-600/20 border-2 border-amber-500/60 rounded-3xl p-5 shadow-2xl flex flex-col sm:flex-row items-center justify-between gap-4"
        >
          <div className="flex items-center gap-4 text-center sm:text-left">
            <div className="w-12 h-12 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center font-black text-2xl shadow-lg shrink-0">
              ♠
            </div>
            <div>
              <div className="flex items-center justify-center sm:justify-start gap-2 mb-1">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping" />
                <span className="text-xs font-black uppercase text-amber-400 tracking-wider">ACTIVE MATCH IN PROGRESS</span>
              </div>
              <h3 className="text-lg font-bold text-slate-100">
                You are currently seated at <span className="text-amber-400">{activeGame.tableName}</span> ({activeGame.seatedCount}/{activeGame.maxPlayers} Players)
              </h3>
            </div>
          </div>

          <button
            onClick={() => onJoinTable(activeGame.tableId)}
            className="px-6 py-3 bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 font-black text-sm rounded-2xl shadow-xl hover:from-amber-300 hover:to-amber-400 flex items-center gap-2 cursor-pointer transition-all shrink-0"
          >
            <Play className="w-4 h-4 fill-current" />
            <span>Resume Game Now</span>
          </button>
        </motion.div>
      )}

      {/* 2b. Private Table Invitations */}
      {privateInvitations.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-slate-900/90 border border-amber-500/30 rounded-3xl p-5 shadow-xl"
        >
          <div className="flex items-center gap-2 mb-4">
            <Lock className="w-5 h-5 text-amber-400" />
            <h3 className="text-base font-black text-slate-100">Private Table Invitations</h3>
            <span className="ml-auto text-[10px] font-bold uppercase tracking-wider text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded-full border border-amber-500/20">
              {privateInvitations.length} Pending
            </span>
          </div>
          <div className="space-y-3">
            {privateInvitations.map((invite) => (
              <div
                key={invite.notificationId || invite.tableId}
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 bg-slate-950/60 border border-slate-800 rounded-2xl"
              >
                <div>
                  <p className="text-sm font-bold text-slate-100">
                    {invite.hostDisplayName || 'Host'} invited you to{' '}
                    <span className="text-amber-400">{invite.tableName || 'Private Table'}</span>
                  </p>
                  <p className="text-[11px] text-slate-400 mt-1">
                    Code: <span className="font-mono text-amber-300">{invite.inviteCode}</span>
                    {' · '}
                    {invite.currentPlayerCount}/{invite.maxPlayers} players
                    {' · '}
                    Boot ₹{((invite.bootAmountPaise || 0) / 100).toFixed(0)}
                  </p>
                </div>
                <button
                  onClick={() => handleAcceptPrivateInvite(invite)}
                  className="px-4 py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl hover:bg-amber-400 cursor-pointer shrink-0"
                >
                  Accept & Join
                </button>
              </div>
            ))}
          </div>
        </motion.div>
      )}

      {/* 3. Hero & Control Header Bar */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-900 to-emerald-950/40 border border-slate-800 rounded-3xl p-6 shadow-2xl relative overflow-hidden">
        <div className="absolute -right-12 -top-12 w-64 h-64 bg-amber-500/5 rounded-full blur-3xl pointer-events-none" />

        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-bold mb-3">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Real-Money Teen Patti Room Lobby</span>
            </div>
            <h2 className="text-2xl md:text-3xl font-black text-slate-100 tracking-tight">
              Welcome Back, <span className="text-amber-400">{user?.displayName || 'Player'}</span>
            </h2>
            <p className="text-xs md:text-sm text-slate-400 mt-1 max-w-xl">
              Join active public rooms, create private invite tables, or click Quick Play for instant matchmaking!
            </p>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => {
                setCreateTableType('PUBLIC');
                setCreatedPrivateCode(null);
                setShowCreateModal(true);
              }}
              className="px-5 py-3 bg-gradient-to-r from-emerald-500 to-teal-500 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-teal-400 flex items-center gap-2 transition-all cursor-pointer"
            >
              <PlusCircle className="w-4 h-4 text-slate-950" />
              <span>Create Public Table</span>
            </button>

            <button
              onClick={() => {
                setCreateTableType('PRIVATE');
                setCreatedPrivateCode(null);
                setShowCreateModal(true);
              }}
              className="px-4 py-3 bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 hover:from-amber-400 hover:to-amber-500 flex items-center gap-2 transition-all cursor-pointer"
            >
              <Lock className="w-4 h-4 text-slate-950" />
              <span>Create Private Room</span>
            </button>

            <button
              onClick={() => setShowJoinPrivateModal(true)}
              className="px-4 py-3 bg-slate-950 border border-slate-700 text-slate-200 font-bold text-xs rounded-xl hover:border-amber-500/50 flex items-center gap-2 transition-all cursor-pointer"
            >
              <Key className="w-4 h-4 text-amber-400" />
              <span>Join with Code</span>
            </button>
          </div>
        </div>
      </div>

      {/* 4. Quick Play Single-Click Matchmaking Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-3xl p-5 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Zap className="w-5 h-5 text-amber-400 animate-bounce" />
            <h3 className="text-base font-black text-slate-100">Quick Play (1-Click Instant Match)</h3>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">Auto-Finds Open Room or Creates Table</span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          {[
            { label: '₹10', bootPaise: 1000, desc: 'Casual Match' },
            { label: '₹20', bootPaise: 2000, desc: 'Standard Match' },
            { label: '₹50', bootPaise: 5000, desc: 'Popular Room' },
            { label: '₹100', bootPaise: 10000, desc: 'High Stakes' },
            { label: '₹500', bootPaise: 50000, desc: 'VIP Roller' },
          ].map((option) => (
            <button
              key={option.bootPaise}
              onClick={() => handleQuickPlay(option.bootPaise)}
              disabled={quickPlayLoading === option.bootPaise}
              className="p-4 rounded-2xl bg-gradient-to-b from-slate-950 to-slate-900 border border-slate-800 hover:border-amber-500/60 transition-all text-center group cursor-pointer shadow-md relative overflow-hidden"
            >
              <span className="text-[10px] text-amber-400 font-extrabold uppercase block mb-1">{option.desc}</span>
              <span className="text-xl font-black text-slate-100 group-hover:text-amber-400 transition-colors">
                {option.label}
              </span>
              <span className="text-[9px] text-slate-500 block mt-1 font-mono">BOOT STAKE</span>
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-500/10 border border-rose-500/30 rounded-2xl text-rose-400 text-sm flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError('')} className="text-xs underline text-rose-300 hover:text-rose-100">Dismiss</button>
        </div>
      )}

      {/* 5. Public Tables Section Header & Filter Tabs */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-2">
          <Trophy className="w-5 h-5 text-amber-400" />
          <h3 className="text-lg font-black text-slate-100">Live Active Public Tables</h3>
        </div>

        <button
          onClick={fetchHomeDashboard}
          disabled={loading}
          className="text-xs font-semibold text-slate-400 hover:text-slate-200 flex items-center gap-1.5 cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh List</span>
        </button>
      </div>

      {/* 6. Active Public Tables Grid (Driven 100% by Backend Data & WebSockets) */}
      {tables.length === 0 ? (
        <div className="text-center py-16 bg-slate-900/50 border border-slate-800/80 rounded-3xl p-8">
          <Trophy className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-lg font-bold text-slate-300">No Active Public Tables Found</h3>
          <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
            Be the first to create a public table or private room to start playing!
          </p>
          <button
            onClick={() => { setShowCreateModal(true); setCreatedPrivateCode(null); }}
            className="mt-4 px-5 py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 hover:bg-amber-400 transition-all cursor-pointer"
          >
            Create Table Now
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {tables.map((table) => {
            const bootRupees = ((table.bootAmountPaise || table.bootAmount || 1000) / 100).toFixed(0);
            const currentPlayers = table.seatedPlayerIds ? table.seatedPlayerIds.length : table.currentPlayerCount || 0;
            const maxPlayers = table.maxPlayers || 6;
            const isFull = currentPlayers >= maxPlayers;
            const countdownSeconds = table.countdownSeconds ?? 0;
            const isCountdown = isCountdownStatus(table.status, countdownSeconds);
            const canJoin = !isFull && !isActiveHandStatus(table.status) && !isCountdown && isJoinableStatus(table.status);

            return (
              <motion.div
                key={table.id || table.tableId}
                whileHover={{ y: -4 }}
                className="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col justify-between relative overflow-hidden group hover:border-amber-500/40 transition-all"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      BOOT ₹{bootRupees}
                    </span>

                    <span className={`text-xs font-mono flex items-center gap-1.5 px-2 py-0.5 rounded-full border transition-all ${
                      currentPlayers > 0
                        ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40 font-black animate-pulse'
                        : 'text-slate-500 border-transparent font-semibold'
                    }`}>
                      <Users className={`w-3.5 h-3.5 ${currentPlayers > 0 ? 'text-emerald-400' : 'text-slate-400'}`} />
                      <span>{currentPlayers}/{maxPlayers} Players</span>
                    </span>
                  </div>

                  <h4 className="font-bold text-slate-100 text-lg flex items-center justify-between">
                    <span>{table.tableName || `Table #${(table.tableId || table.id || 'PUBLIC').slice(-6).toUpperCase()}`}</span>
                    {table.privateTable && <Lock className="w-4 h-4 text-amber-400" />}
                  </h4>

                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/60">
                    <div>
                      <span className="text-slate-500 block text-[10px] font-bold uppercase">BOOT AMOUNT</span>
                      <span className="font-bold text-amber-400 text-sm">₹{bootRupees}</span>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px] font-bold uppercase">STATUS</span>
                      <span className={`font-extrabold text-xs uppercase ${
                        isCountdown ? 'text-rose-400 animate-pulse'
                          : isActiveHandStatus(table.status) ? 'text-cyan-400' : 'text-amber-400'
                      }`}>
                        {isCountdown ? `STARTING ${countdownSeconds}s` : getTableStatusLabel(table.status)}
                      </span>
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => handleJoinTableClick(table.id || table.tableId)}
                  disabled={!canJoin}
                  className={`mt-5 w-full py-2.5 px-4 font-bold text-xs rounded-xl flex items-center justify-center gap-2 transition-all cursor-pointer ${
                    isActiveHandStatus(table.status)
                      ? 'bg-cyan-950/40 text-cyan-400 border border-cyan-500/30 cursor-not-allowed'
                      : isCountdown
                      ? 'bg-rose-950/40 text-rose-300 border border-rose-500/30 cursor-not-allowed'
                      : isFull
                      ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                      : 'bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-lg shadow-emerald-600/20 hover:from-emerald-500 hover:to-teal-500'
                  }`}
                >
                  <Play className="w-4 h-4 fill-current" />
                  <span>
                    {isActiveHandStatus(table.status)
                      ? 'Game Running'
                      : isCountdown
                      ? `Starting in ${countdownSeconds}s`
                      : isFull
                      ? `Table Full (${maxPlayers}/${maxPlayers})`
                      : 'Join Table'}
                  </span>
                </button>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* 7. Game History & Live Notifications Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-8">
        {/* Game History List */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-3xl p-5 shadow-xl">
          <div className="flex items-center gap-2 mb-4">
            <History className="w-5 h-5 text-amber-400" />
            <h3 className="text-base font-black text-slate-100">Recent Game History</h3>
          </div>

          {recentHistory.length === 0 ? (
            <div className="text-center py-8 text-xs text-slate-500">No game history items recorded yet. Join a match to record games!</div>
          ) : (
            <div className="space-y-2.5">
              {recentHistory.map((item) => (
                <div key={item.id || item.gameId} className="bg-slate-950/70 p-3 rounded-2xl border border-slate-800/80 flex items-center justify-between gap-3 text-xs">
                  <div className="min-w-0">
                    <span className="font-bold text-slate-200 block truncate">{item.tableName}</span>
                    <span className="text-[10px] text-slate-500 font-mono block truncate">
                      {item.gameId}{item.variant ? ` · ${item.variant}` : ''}{item.playerCount ? ` · ${item.playerCount} players` : ''}
                    </span>
                    <span className="text-[10px] text-slate-400 block mt-0.5">
                      {formatWinningCategory(item.winningCategory, item.winningHandDescription, item.foldWin)}
                    </span>
                    <span className="text-[9px] text-slate-600 block">{formatHistoryDate(item.playedAt)}</span>
                  </div>
                  <div className="text-right shrink-0">
                    {item.result === 'WON' ? (
                      <>
                        <span className="font-extrabold block text-emerald-400">
                          +₹{((item.winningAmountPaise ?? item.winnerPayoutPaise ?? 0) / 100).toFixed(2)}
                        </span>
                        <span className="text-[9px] text-slate-500">Pot ₹{((item.potAmountPaise ?? 0) / 100).toFixed(2)}</span>
                      </>
                    ) : (
                      <>
                        <span className="font-extrabold block text-rose-400">LOST</span>
                        <span className="text-[9px] text-slate-500">Pot ₹{((item.potAmountPaise ?? 0) / 100).toFixed(2)}</span>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Live STOMP Notifications Feed */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-3xl p-5 shadow-xl">
          <div className="flex items-center gap-2 mb-4">
            <Bell className="w-5 h-5 text-amber-400" />
            <h3 className="text-base font-black text-slate-100">Live System Notifications</h3>
          </div>

          {notifications.length === 0 ? (
            <div className="text-center py-8 text-xs text-slate-500">No recent notifications. Live events will display here automatically!</div>
          ) : (
            <div className="space-y-2.5">
              {notifications.slice(0, 5).map((n) => (
                <div key={n.id || n.message} className="bg-slate-950/70 p-3 rounded-2xl border border-slate-800/80 text-xs flex items-start gap-2.5">
                  <div className="w-2 h-2 rounded-full bg-amber-400 mt-1 shrink-0" />
                  <div>
                    <span className="font-bold text-slate-200 block">{getNotificationDisplayLabel(n)}</span>
                    <p className="text-slate-400 text-[11px] mt-0.5">{n.message}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

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
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Boot Amount (₹)</label>
                    <select
                      value={createStakeTier}
                      onChange={(e) => setCreateStakeTier(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200 focus:outline-none focus:border-amber-500/60"
                    >
                      <option value="LOW">Boot ₹10 (Min Buy-In ₹10)</option>
                      <option value="MEDIUM">Boot ₹50 (Min Buy-In ₹50)</option>
                      <option value="HIGH">Boot ₹100 / ₹250 (High Stakes)</option>
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
              <p className="text-xs text-slate-400 mb-5">Enter the 6-character invite code provided by the host</p>

              <form onSubmit={handleJoinPrivateSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Invite Code</label>
                  <input
                    type="text"
                    maxLength={6}
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
    </div>
  );
}
