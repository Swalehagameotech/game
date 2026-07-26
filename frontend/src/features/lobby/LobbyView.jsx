import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { PlusCircle, Key, Users, Trophy, Play, Lock, RefreshCw, AlertCircle, ShieldCheck } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';

export default function LobbyView({ onJoinTable }) {
  const { user, isAuthenticated } = useAuth();
  const { updateTableState } = useGame();

  const [tables, setTables] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedStake, setSelectedStake] = useState('ALL');
  const [error, setError] = useState('');

  // Private Table Creation Modal State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createStakeTier, setCreateStakeTier] = useState('LOW');
  const [createMaxPlayers, setCreateMaxPlayers] = useState(6);
  const [createdPrivateCode, setCreatedPrivateCode] = useState(null);

  // Private Table Code Join Modal State
  const [showJoinPrivateModal, setShowJoinPrivateModal] = useState(false);
  const [inviteCodeInput, setInviteCodeInput] = useState('');

  const fetchTables = async () => {
    setLoading(true);
    setError('');
    try {
      const params = {};
      if (selectedStake !== 'ALL') {
        params.stakeTier = selectedStake;
      }
      const { data } = await axiosClient.get('/tables', { params });
      setTables(data.content || data || []);
    } catch (err) {
      console.error('Failed to fetch lobby tables:', err);
      setError('Could not load active lobby tables. Make sure backend is running.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTables();
    // Only start polling interval when user is authenticated
    if (!isAuthenticated) return;
    const interval = setInterval(fetchTables, 10000);
    return () => clearInterval(interval);
  }, [selectedStake, isAuthenticated]);

  const handleJoinTableClick = async (tableId) => {
    setError('');
    try {
      // Step 1: Pre-check eligibility
      const checkRes = await axiosClient.get(`/lobby/tables/${tableId}/check-eligibility`);
      if (!checkRes.data.eligible) {
        setError(`Not eligible to join: ${checkRes.data.reason}`);
        return;
      }

      // Step 2: Perform atomic join
      const joinRes = await axiosClient.post(`/tables/${tableId}/join`);
      updateTableState(joinRes.data);
      onJoinTable(tableId);
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data || 'Failed to join table.');
    }
  };

  const handleCreatePrivateTable = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const { data } = await axiosClient.post('/lobby/tables/private', {
        stakeTier: createStakeTier,
        maxPlayers: createMaxPlayers,
      });

      setCreatedPrivateCode(data.inviteCode);
      fetchTables();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data || 'Failed to create private table.');
    }
  };

  const handleJoinPrivateByCode = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const { data: tableData } = await axiosClient.get(`/lobby/tables/private/${inviteCodeInput.trim().toUpperCase()}`);
      handleJoinTableClick(tableData.tableId || tableData.id);
      setShowJoinPrivateModal(false);
    } catch (err) {
      setError('Invalid or expired private invite code.');
    }
  };

  return (
    <div className="max-w-7xl mx-auto p-4 md:p-6 space-y-6">
      {/* Lobby Hero Banner */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-slate-900 via-slate-900 to-amber-950/40 border border-slate-800 p-6 md:p-8 shadow-2xl">
        <div className="absolute top-0 right-0 w-96 h-96 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-widest bg-amber-500/10 text-amber-400 border border-amber-500/20">
                Live Multiplayer Lobby
              </span>
              <span className="flex items-center gap-1 text-xs text-emerald-400 font-semibold">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                Real-Time Tables
              </span>
            </div>
            <h1 className="text-3xl md:text-4xl font-black text-slate-100 tracking-tight">
              Select Your <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-300 via-amber-400 to-amber-500">Teen Patti</span> Table
            </h1>
            <p className="text-slate-400 text-sm mt-1 max-w-xl">
              Choose a public stake tier or create a private room to invite your friends. Real-money wallet protection & optimistic seat claiming guaranteed.
            </p>
          </div>

          {/* Lobby Action Buttons */}
          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => { setShowCreateModal(true); setCreatedPrivateCode(null); }}
              className="px-4 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 hover:from-amber-400 hover:to-amber-500 flex items-center gap-2 transition-all cursor-pointer"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Create Private Table</span>
            </button>

            <button
              onClick={() => setShowJoinPrivateModal(true)}
              className="px-4 py-2.5 bg-slate-900 border border-slate-700 text-slate-200 font-bold text-xs rounded-xl hover:border-amber-500/50 flex items-center gap-2 transition-all cursor-pointer"
            >
              <Key className="w-4 h-4 text-amber-400" />
              <span>Join with Code</span>
            </button>
          </div>
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

      {/* Stake Filter Tabs & Header Controls */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-1.5 bg-slate-950 p-1 rounded-2xl border border-slate-800">
          {['ALL', 'LOW', 'MEDIUM', 'HIGH'].map((tier) => (
            <button
              key={tier}
              onClick={() => setSelectedStake(tier)}
              className={`px-4 py-2 text-xs font-bold rounded-xl transition-all ${
                selectedStake === tier
                  ? 'bg-amber-500 text-slate-950 shadow-md'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {tier === 'ALL' ? 'All Tiers' : tier === 'LOW' ? 'Low (₹10)' : tier === 'MEDIUM' ? 'Medium (₹50)' : 'High (₹250)'}
            </button>
          ))}
        </div>

        <button
          onClick={fetchTables}
          disabled={loading}
          className="text-xs font-semibold text-slate-400 hover:text-slate-200 flex items-center gap-1.5 cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh Tables</span>
        </button>
      </div>

      {/* Tables Grid */}
      {tables.length === 0 ? (
        <div className="text-center py-16 bg-slate-900/50 border border-slate-800/80 rounded-3xl p-8">
          <Trophy className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-lg font-bold text-slate-300">No Active Public Tables Found</h3>
          <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
            Be the first to create a public table or private invite room to start playing!
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
            const minBuyInRupees = (table.minBuyInPaise || table.stakeTier === 'LOW' ? 1000 : table.stakeTier === 'MEDIUM' ? 5000 : 25000) / 100;
            const currentPlayers = table.seatedPlayerIds ? table.seatedPlayerIds.length : table.currentPlayerCount || 0;
            const maxPlayers = table.maxPlayers || 6;
            const isFull = currentPlayers >= maxPlayers;

            return (
              <motion.div
                key={table.id || table.tableId}
                whileHover={{ y: -4 }}
                className="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col justify-between relative overflow-hidden group hover:border-amber-500/40 transition-all"
              >
                {/* Table Header */}
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider ${
                      table.stakeTier === 'HIGH' ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20' :
                      table.stakeTier === 'MEDIUM' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' :
                      'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                    }`}>
                      {table.stakeTier || 'LOW'} STAKE
                    </span>

                    <span className="text-xs font-mono text-slate-500 flex items-center gap-1">
                      <Users className="w-3.5 h-3.5 text-slate-400" />
                      <span className="font-bold text-slate-200">{currentPlayers}</span>/{maxPlayers}
                    </span>
                  </div>

                  <h4 className="font-bold text-slate-100 text-lg flex items-center justify-between">
                    <span>Table #{table.id ? table.id.slice(-6) : 'PUBLIC'}</span>
                    {table.privateTable && <Lock className="w-4 h-4 text-amber-400" />}
                  </h4>

                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs bg-slate-950/60 p-2.5 rounded-xl border border-slate-800/60">
                    <div>
                      <span className="text-slate-500 block text-[10px]">MIN BUY-IN</span>
                      <span className="font-bold text-emerald-400 text-sm">₹{minBuyInRupees}</span>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px]">STATUS</span>
                      <span className={`font-semibold text-xs ${table.status === 'IN_PROGRESS' ? 'text-cyan-400' : 'text-amber-400'}`}>
                        {table.status === 'IN_PROGRESS' ? 'In Game' : 'Waiting'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Join Table Button */}
                <button
                  onClick={() => handleJoinTableClick(table.id || table.tableId)}
                  disabled={isFull}
                  className={`mt-5 w-full py-2.5 px-4 font-bold text-xs rounded-xl flex items-center justify-center gap-2 transition-all cursor-pointer ${
                    isFull
                      ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                      : 'bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-lg shadow-emerald-600/20 hover:from-emerald-500 hover:to-teal-500'
                  }`}
                >
                  <Play className="w-4 h-4 fill-current" />
                  <span>{isFull ? 'Table Full' : 'Take Seat'}</span>
                </button>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* Create Private Table Modal */}
      <AnimatePresence>
        {showCreateModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl relative"
            >
              <h3 className="text-xl font-bold text-slate-100 mb-1">Create Private Table</h3>
              <p className="text-xs text-slate-400 mb-5">Generates a shareable 6-character code for private matches</p>

              {createdPrivateCode ? (
                <div className="text-center py-6 bg-slate-950 border border-slate-800 rounded-xl p-4">
                  <ShieldCheck className="w-12 h-12 text-emerald-400 mx-auto mb-2" />
                  <span className="text-xs text-slate-400 block">PRIVATE INVITE CODE</span>
                  <span className="font-mono text-3xl font-black text-amber-400 tracking-wider my-2 block">
                    {createdPrivateCode}
                  </span>
                  <p className="text-xs text-slate-400">Share this code with your friends to join this table</p>
                  <button
                    onClick={() => setShowCreateModal(false)}
                    className="mt-5 px-6 py-2 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl hover:bg-amber-400"
                  >
                    Done
                  </button>
                </div>
              ) : (
                <form onSubmit={handleCreatePrivateTable} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Stake Tier</label>
                    <select
                      value={createStakeTier}
                      onChange={(e) => setCreateStakeTier(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200"
                    >
                      <option value="LOW">LOW (Min Buy-In ₹10)</option>
                      <option value="MEDIUM">MEDIUM (Min Buy-In ₹50)</option>
                      <option value="HIGH">HIGH (Min Buy-In ₹250)</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Max Players</label>
                    <select
                      value={createMaxPlayers}
                      onChange={(e) => setCreateMaxPlayers(Number(e.target.value))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-sm text-slate-200"
                    >
                      <option value={2}>2 Players (Heads Up)</option>
                      <option value={4}>4 Players</option>
                      <option value={6}>6 Players (Full Ring)</option>
                    </select>
                  </div>

                  <div className="flex gap-2 pt-2">
                    <button
                      type="button"
                      onClick={() => setShowCreateModal(false)}
                      className="flex-1 py-2.5 bg-slate-800 text-slate-300 text-xs font-bold rounded-xl hover:bg-slate-700"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="flex-1 py-2.5 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl hover:bg-amber-400 shadow-lg shadow-amber-500/20"
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

      {/* Join Private Table Modal */}
      <AnimatePresence>
        {showJoinPrivateModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-sm bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl relative"
            >
              <h3 className="text-xl font-bold text-slate-100 mb-1">Join Private Room</h3>
              <p className="text-xs text-slate-400 mb-4">Enter the 6-character private invite code</p>

              <form onSubmit={handleJoinPrivateByCode} className="space-y-4">
                <input
                  type="text"
                  maxLength={6}
                  required
                  placeholder="e.g. ABC123"
                  value={inviteCodeInput}
                  onChange={(e) => setInviteCodeInput(e.target.value.toUpperCase())}
                  className="w-full text-center font-mono uppercase tracking-widest text-2xl font-black bg-slate-950 border border-slate-800 rounded-xl p-3 text-amber-400 placeholder-slate-700 focus:outline-none focus:border-amber-500"
                />

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowJoinPrivateModal(false)}
                    className="flex-1 py-2.5 bg-slate-800 text-slate-300 text-xs font-bold rounded-xl hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="flex-1 py-2.5 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl hover:bg-amber-400 shadow-lg shadow-amber-500/20"
                  >
                    Join Table
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
