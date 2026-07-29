import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield,
  CheckCircle,
  XCircle,
  UserX,
  UserCheck,
  LayoutDashboard,
  Users,
  Wallet,
  Table2,
  Megaphone,
  AlertTriangle,
  Ban,
  RefreshCw,
} from 'lucide-react';
import {
  fetchAdminDashboard,
  fetchBettingConfig,
  updateBettingConfig,
  fetchAdminUsers,
  fetchAdminUser,
  fetchAdminUserWalletHistory,
  adminAddWallet,
  adminDeductWallet,
  suspendUser,
  reinstateUser,
  banUser,
  fetchAdminWithdrawals,
  approveWithdrawal,
  rejectWithdrawal,
  fetchAdminTables,
  forceCloseTable,
  broadcastAnnouncement,
  formatPaise,
} from './adminApi';

const TABS = [
  { id: 'DASHBOARD', label: 'Dashboard', icon: LayoutDashboard },
  { id: 'USERS', label: 'Users', icon: Users },
  { id: 'SETTINGS', label: 'Game Settings', icon: Shield },
  { id: 'WALLET', label: 'Wallet', icon: Wallet },
  { id: 'TABLES', label: 'Tables', icon: Table2 },
  { id: 'WITHDRAWALS', label: 'Withdrawals', icon: AlertTriangle },
  { id: 'ANNOUNCE', label: 'Announce', icon: Megaphone },
];

function StatCard({ label, value, accent = 'text-amber-400' }) {
  return (
    <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl">
      <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold">{label}</p>
      <p className={`text-lg font-bold font-mono mt-1 ${accent}`}>{value}</p>
    </div>
  );
}

function paiseToInrText(value) {
  const n = Number(value || 0);
  return `₹${(n / 100).toFixed(2)}`;
}

function paiseToInrNumber(value) {
  const n = Number(value || 0);
  return Math.round(n / 100);
}

function inrToPaise(value) {
  const n = parseInt(value, 10);
  return (Number.isFinite(n) ? n : 0) * 100;
}

export default function AdminPage({ onLogout }) {
  const [activeTab, setActiveTab] = useState('DASHBOARD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [dashboard, setDashboard] = useState(null);
  const [users, setUsers] = useState([]);
  const [userQuery, setUserQuery] = useState('');
  const [withdrawals, setWithdrawals] = useState([]);
  const [tables, setTables] = useState([]);
  const [tableGroup, setTableGroup] = useState('active');
  const [walletUserId, setWalletUserId] = useState('');
  const [walletUser, setWalletUser] = useState(null);
  const [walletHistory, setWalletHistory] = useState([]);
  const [walletAmount, setWalletAmount] = useState('');
  const [walletReason, setWalletReason] = useState('');
  const [announceTitle, setAnnounceTitle] = useState('');
  const [announceMessage, setAnnounceMessage] = useState('');
  const [settings, setSettings] = useState({
    bootAmount: 10,
    bootAmountOptions: [],
    minimumPlayers: 3,
    maximumPlayers: 6,
    turnTimer: 20,
    blindBetAmount: 10,
    blindRaiseOptions: '20,40',
    seenChaalAmount: 20,
    seenRaiseOptions: '40,80',
    showCost: 20,
    sideShowCost: 20,
    sideShowEnabled: true,
    showEnabled: true,
  });

  const applyBootPreset = (bootInr) => {
    const blind = bootInr;
    const seen = blind * 2;
    setSettings((prev) => ({
      ...prev,
      bootAmount: bootInr,
      blindBetAmount: blind,
      blindRaiseOptions: `${(blind * 2)},${(blind * 4)},${(blind * 8)}`,
      seenChaalAmount: seen,
      seenRaiseOptions: `${(seen * 2)},${(seen * 4)},${(seen * 8)}`,
      showCost: seen,
      sideShowCost: seen,
    }));
  };

  const fetchAdminData = useCallback(async () => {
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      if (activeTab === 'DASHBOARD') setDashboard(await fetchAdminDashboard());
      if (activeTab === 'USERS') setUsers(await fetchAdminUsers(userQuery));
      if (activeTab === 'SETTINGS') {
        const cfg = await fetchBettingConfig();
        setSettings({
          bootAmount: paiseToInrNumber(cfg.bootAmount ?? 1000),
          bootAmountOptions: (cfg.bootAmountOptions && cfg.bootAmountOptions.length
            ? cfg.bootAmountOptions
            : [cfg.bootAmount ?? 1000]
          ).map(paiseToInrNumber),
          minimumPlayers: Math.max(3, cfg.minimumPlayers ?? 3),
          maximumPlayers: Math.max(3, cfg.maximumPlayers ?? 6),
          turnTimer: cfg.turnTimer ?? 20,
          blindBetAmount: paiseToInrNumber(cfg.blindBetAmount ?? 1000),
          blindRaiseOptions: (cfg.blindRaiseOptions || []).map(paiseToInrNumber).join(','),
          seenChaalAmount: paiseToInrNumber(cfg.seenChaalAmount ?? 2000),
          seenRaiseOptions: (cfg.seenRaiseOptions || []).map(paiseToInrNumber).join(','),
          showCost: paiseToInrNumber(cfg.showCost ?? 2000),
          sideShowCost: paiseToInrNumber(cfg.sideShowCost ?? 2000),
          sideShowEnabled: Boolean(cfg.sideShowEnabled),
          showEnabled: Boolean(cfg.showEnabled),
        });
      }
      if (activeTab === 'WITHDRAWALS') setWithdrawals(await fetchAdminWithdrawals());
      if (activeTab === 'TABLES') setTables(await fetchAdminTables(tableGroup));
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load admin data');
    } finally {
      setLoading(false);
    }
  }, [activeTab, userQuery, tableGroup]);

  useEffect(() => {
    fetchAdminData();
  }, [fetchAdminData]);

  const handleLookupWalletUser = async () => {
    if (!walletUserId.trim()) return;
    setLoading(true);
    setError('');
    try {
      const user = await fetchAdminUser(walletUserId.trim());
      const history = await fetchAdminUserWalletHistory(walletUserId.trim());
      setWalletUser(user);
      setWalletHistory(history.slice(0, 10));
    } catch {
      setError('User not found');
      setWalletUser(null);
      setWalletHistory([]);
    } finally {
      setLoading(false);
    }
  };

  const handleWalletAdjust = async (mode) => {
    if (!walletUser?.id) {
      setError('Lookup a user before adjusting their wallet');
      return;
    }
    if (!walletAmount || Number.isNaN(parseFloat(walletAmount))) {
      setError('Enter a valid amount in ₹');
      return;
    }
    if (!walletReason.trim()) {
      setError('Reason is required for wallet adjustments');
      return;
    }
    const paise = Math.round(parseFloat(walletAmount) * 100);
    if (!paise || paise <= 0) {
      setError('Amount must be greater than 0');
      return;
    }
    setLoading(true);
    setError('');
    try {
      if (mode === 'add') await adminAddWallet(walletUser.id, paise, walletReason.trim());
      else await adminDeductWallet(walletUser.id, paise, walletReason.trim());
      await handleLookupWalletUser();
      setWalletAmount('');
      setWalletReason('');
    } catch (err) {
      setError(err?.response?.data?.message || err?.response?.data?.error || 'Wallet adjustment failed');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickAddMoney = async (user) => {
    const amountInr = window.prompt(`Enter amount in INR to add for ${user.displayName}:`, '100');
    if (!amountInr) return;
    const reason = window.prompt('Enter reason for wallet credit:', 'Admin credit');
    if (!reason) return;
    const paise = Math.round(Number(amountInr) * 100);
    if (!paise || paise <= 0) {
      setError('Invalid amount');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await adminAddWallet(user.id, paise, reason);
      await fetchAdminData();
    } catch (err) {
      setError(err?.response?.data?.message || 'Add money failed');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveSettings = async () => {
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const minPlayers = Math.max(3, parseInt(settings.minimumPlayers, 10) || 3);
      const maxPlayers = Math.max(minPlayers, parseInt(settings.maximumPlayers, 10) || minPlayers);
      const turnTimer = Math.max(5, parseInt(settings.turnTimer, 10) || 20);

      const payload = {
        bootAmount: inrToPaise(settings.bootAmount),
        bootAmountOptions: Array.from(
          new Set([
            ...((Array.isArray(settings.bootAmountOptions) ? settings.bootAmountOptions : [])
              .map((v) => Number(v))
              .filter((v) => Number.isFinite(v) && v > 0)
              .map((v) => Math.round(v))),
            Number(settings.bootAmount),
          ])
        )
          .filter((v) => Number.isFinite(v) && v > 0)
          .sort((a, b) => a - b)
          .map((v) => inrToPaise(v)),
        minimumPlayers: minPlayers,
        maximumPlayers: maxPlayers,
        turnTimer: turnTimer,
        blindBetAmount: inrToPaise(settings.blindBetAmount),
        blindRaiseOptions: String(settings.blindRaiseOptions)
          .split(',')
          .map((v) => inrToPaise(v.trim()))
          .filter((v) => Number.isFinite(v) && v > 0),
        seenChaalAmount: inrToPaise(settings.seenChaalAmount),
        seenRaiseOptions: String(settings.seenRaiseOptions)
          .split(',')
          .map((v) => inrToPaise(v.trim()))
          .filter((v) => Number.isFinite(v) && v > 0),
        showCost: inrToPaise(settings.showCost),
        sideShowCost: inrToPaise(settings.sideShowCost),
        sideShowEnabled: Boolean(settings.sideShowEnabled),
        showEnabled: Boolean(settings.showEnabled),
      };
      await updateBettingConfig(payload);
      await fetchAdminData();
      setSuccess('Game settings saved successfully.');
    } catch (err) {
      const details = err?.response?.data?.details;
      const msg = err?.response?.data?.message || 'Saving settings failed';
      setError(Array.isArray(details) && details.length ? `${msg}: ${details.join(', ')}` : msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen p-4 md:p-8 bg-slate-950 text-slate-100">
      <div className="max-w-6xl mx-auto bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center font-bold">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <h1 className="font-bold text-lg">Admin Control Center</h1>
              <p className="text-xs text-slate-400">Standalone admin frontend</p>
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={fetchAdminData} className="text-xs font-bold bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800 flex items-center gap-1">
              <RefreshCw className="w-3 h-3" /> Refresh
            </button>
            <button onClick={onLogout} className="text-xs font-bold bg-rose-900/40 text-rose-200 px-3 py-1.5 rounded-xl border border-rose-800">
              Logout
            </button>
          </div>
        </div>

        <div className="flex flex-wrap gap-1 bg-slate-950 p-1 rounded-xl mb-4 border border-slate-800">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => setActiveTab(id)} className={`flex items-center gap-1.5 px-3 py-2 text-[10px] font-bold rounded-lg ${activeTab === id ? 'bg-amber-500 text-slate-950' : 'text-slate-400'}`}>
              <Icon className="w-3.5 h-3.5" /> {label}
            </button>
          ))}
        </div>

        {error && <div className="mb-3 px-3 py-2 bg-rose-950/50 border border-rose-800 rounded-xl text-xs text-rose-300">{error}</div>}
        {success && <div className="mb-3 px-3 py-2 bg-emerald-950/50 border border-emerald-800 rounded-xl text-xs text-emerald-300">{success}</div>}
        {loading && <p className="text-center text-xs text-slate-500 py-8">Loading...</p>}

        {!loading && activeTab === 'DASHBOARD' && dashboard && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="Total Users" value={dashboard.totalUsers} />
            <StatCard label="Online Now" value={dashboard.onlineUsers} accent="text-emerald-400" />
            <StatCard label="Running Games" value={dashboard.runningGames} accent="text-sky-400" />
            <StatCard label="Waiting Games" value={dashboard.waitingGames} />
            <StatCard label="Closed Tables" value={dashboard.closedGames} />
            <StatCard label="Pending Withdrawals" value={dashboard.pendingWithdrawals} accent="text-rose-400" />
            <StatCard label="Total Wallet" value={formatPaise(dashboard.totalWalletBalance)} accent="text-emerald-400" />
            <StatCard label="Transactions" value={dashboard.totalWalletTransactions} />
          </div>
        )}

        {!loading && activeTab === 'USERS' && (
          <div className="space-y-3">
            <div className="flex gap-2">
              <input value={userQuery} onChange={(e) => setUserQuery(e.target.value)} placeholder="Search username, email, mobile..." className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs" />
              <button onClick={fetchAdminData} className="px-4 py-2 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl">Search</button>
            </div>
            {users.map((u) => (
              <div key={u.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold text-sm">{u.displayName}</div>
                  <div className="text-slate-400">{u.email}</div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleQuickAddMoney(u)}
                    className="px-3 py-1.5 bg-emerald-600 text-white rounded-xl flex items-center gap-1"
                  >
                    <Wallet className="w-4 h-4" /> Add Money
                  </button>
                  <button onClick={() => (u.accountStatus === 'SUSPENDED' ? reinstateUser(u.id) : suspendUser(u.id, 'Violation of T&C')).then(fetchAdminData)} className="px-3 py-1.5 bg-amber-600 text-white rounded-xl flex items-center gap-1">
                    {u.accountStatus === 'SUSPENDED' ? <UserCheck className="w-4 h-4" /> : <UserX className="w-4 h-4" />}
                    {u.accountStatus === 'SUSPENDED' ? 'Reinstate' : 'Suspend'}
                  </button>
                  {u.accountStatus !== 'BANNED' && (
                    <button onClick={() => banUser(u.id, 'Permanent ban by admin').then(fetchAdminData)} className="px-3 py-1.5 bg-rose-600 text-white rounded-xl flex items-center gap-1">
                      <Ban className="w-4 h-4" /> Ban
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && activeTab === 'SETTINGS' && (
          <div className="space-y-4">
            <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/60">
              <p className="text-xs font-bold text-slate-200 mb-2">Quick Boot Presets</p>
              <div className="flex gap-2">
                {[10, 50, 60].map((inr) => (
                  <button
                    key={inr}
                    type="button"
                    onClick={() => applyBootPreset(inr)}
                    className="px-3 py-1.5 rounded-lg bg-amber-500/15 text-amber-300 border border-amber-500/40 text-xs font-bold"
                  >
                    Boot ₹{inr}
                  </button>
                ))}
              </div>
              <p className="text-[11px] text-slate-400 mt-2">
                Optional helper. You can type any custom ₹ values below.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-2">
                <label className="block text-[11px] text-slate-400 mb-1">Boot Amount (₹)</label>
                <input type="number" min="1" step="1" value={settings.bootAmount} onChange={(e) => setSettings((p) => ({ ...p, bootAmount: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
              </div>
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-2">
                <label className="block text-[11px] text-slate-400 mb-1">Saved Boot Options (₹)</label>
                <div className="w-full min-h-[38px] bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs text-slate-200">
                  {(settings.bootAmountOptions || []).length
                    ? settings.bootAmountOptions.join(', ')
                    : 'No saved options yet'}
                </div>
              </div>
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-2">
                <label className="block text-[11px] text-slate-400 mb-1">Turn Timer (seconds)</label>
                <input type="number" min="5" step="1" value={settings.turnTimer} onChange={(e) => setSettings((p) => ({ ...p, turnTimer: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
              </div>
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-2">
                <label className="block text-[11px] text-slate-400 mb-1">Minimum Players</label>
                <input type="number" min="3" step="1" value={settings.minimumPlayers} onChange={(e) => setSettings((p) => ({ ...p, minimumPlayers: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
              </div>
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-2">
                <label className="block text-[11px] text-slate-400 mb-1">Maximum Players</label>
                <input type="number" min="3" step="1" value={settings.maximumPlayers} onChange={(e) => setSettings((p) => ({ ...p, maximumPlayers: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-3 space-y-2">
                <p className="text-xs font-bold text-amber-300">Blind Player Settings</p>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Blind Bet (₹)</label>
                  <input type="number" min="1" step="1" value={settings.blindBetAmount} onChange={(e) => setSettings((p) => ({ ...p, blindBetAmount: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                </div>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Blind Raise Options (₹, comma)</label>
                  <input value={settings.blindRaiseOptions} onChange={(e) => setSettings((p) => ({ ...p, blindRaiseOptions: e.target.value }))} placeholder="20,40,80" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                </div>
              </div>

              <div className="bg-slate-950 border border-slate-800 rounded-xl p-3 space-y-2">
                <p className="text-xs font-bold text-cyan-300">Seen Player Settings</p>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Seen Chaal (₹)</label>
                  <input type="number" min="1" step="1" value={settings.seenChaalAmount} onChange={(e) => setSettings((p) => ({ ...p, seenChaalAmount: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                </div>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Seen Raise Options (₹, comma)</label>
                  <input value={settings.seenRaiseOptions} onChange={(e) => setSettings((p) => ({ ...p, seenRaiseOptions: e.target.value }))} placeholder="40,80,160" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Show Cost (₹)</label>
                    <input type="number" min="1" step="1" value={settings.showCost} onChange={(e) => setSettings((p) => ({ ...p, showCost: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                  </div>
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Side Show Cost (₹)</label>
                    <input type="number" min="1" step="1" value={settings.sideShowCost} onChange={(e) => setSettings((p) => ({ ...p, sideShowCost: e.target.value }))} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs" />
                  </div>
                </div>
              </div>
            </div>
            <div className="flex gap-6 text-xs">
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={settings.showEnabled} onChange={(e) => setSettings((p) => ({ ...p, showEnabled: e.target.checked }))} />
                Show Enabled
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={settings.sideShowEnabled} onChange={(e) => setSettings((p) => ({ ...p, sideShowEnabled: e.target.checked }))} />
                Side Show Enabled
              </label>
            </div>
            <button
              onClick={handleSaveSettings}
              disabled={loading}
              className="w-full py-2.5 bg-amber-500 text-slate-950 font-bold rounded-xl disabled:opacity-60"
            >
              {loading ? 'Saving...' : 'Save Game Settings'}
            </button>
          </div>
        )}

        {!loading && activeTab === 'WALLET' && (
          <div className="space-y-4">
            <div className="flex gap-2">
              <input value={walletUserId} onChange={(e) => setWalletUserId(e.target.value)} placeholder="User ID..." className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs" />
              <button onClick={handleLookupWalletUser} className="px-4 py-2 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl">Lookup</button>
            </div>
            {walletUser && (
              <>
                <div className="p-4 bg-slate-950 border border-slate-800 rounded-2xl">
                  <p className="font-bold">{walletUser.displayName}</p>
                  <p className="text-emerald-400 font-mono text-lg font-bold">{formatPaise(walletUser.walletBalancePaise)}</p>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <input value={walletAmount} onChange={(e) => setWalletAmount(e.target.value)} placeholder="Amount (₹)" type="number" min="0" step="0.01" className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs" />
                  <input value={walletReason} onChange={(e) => setWalletReason(e.target.value)} placeholder="Reason" className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs" />
                </div>
                <div className="flex gap-2">
                  <button onClick={() => handleWalletAdjust('add')} className="flex-1 py-2 bg-emerald-600 text-white rounded-xl">Credit</button>
                  <button onClick={() => handleWalletAdjust('deduct')} className="flex-1 py-2 bg-rose-600 text-white rounded-xl">Debit</button>
                </div>
              </>
            )}
          </div>
        )}

        {!loading && activeTab === 'TABLES' && (
          <div className="space-y-3">
            <div className="flex gap-2">
              {['active', 'running', 'waiting', 'closed'].map((g) => (
                <button key={g} onClick={() => setTableGroup(g)} className={`px-3 py-1.5 text-[10px] font-bold rounded-lg capitalize ${tableGroup === g ? 'bg-amber-500 text-slate-950' : 'bg-slate-950 text-slate-400 border border-slate-800'}`}>
                  {g}
                </button>
              ))}
            </div>
            {tables.map((t) => (
              <div key={t.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold">{t.tableName || `Table ${t.id?.slice(-6)}`}</div>
                  <div className="text-slate-400">{t.tableType} · {t.status} · {t.seatedCount}/{t.maxPlayers}</div>
                </div>
                {t.status !== 'CLOSED' && (
                  <button onClick={() => forceCloseTable(t.id, 'Admin force close').then(fetchAdminData)} className="px-3 py-1.5 bg-rose-600 text-white rounded-xl">
                    Force Close
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        {!loading && activeTab === 'WITHDRAWALS' && (
          <div className="space-y-3">
            {withdrawals.map((req) => (
              <div key={req.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold">{req.userDisplayName || req.userId}</div>
                  <div className="text-emerald-400 font-mono font-bold">{formatPaise(req.amountPaise)}</div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => approveWithdrawal(req.id).then(fetchAdminData)} className="px-3 py-1.5 bg-emerald-600 text-white rounded-xl flex items-center gap-1">
                    <CheckCircle className="w-4 h-4" /> Approve
                  </button>
                  <button onClick={() => rejectWithdrawal(req.id, 'Verification failed').then(fetchAdminData)} className="px-3 py-1.5 bg-rose-600 text-white rounded-xl flex items-center gap-1">
                    <XCircle className="w-4 h-4" /> Reject
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && activeTab === 'ANNOUNCE' && (
          <div className="space-y-3">
            <input value={announceTitle} onChange={(e) => setAnnounceTitle(e.target.value)} placeholder="Announcement title" className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs" />
            <textarea value={announceMessage} onChange={(e) => setAnnounceMessage(e.target.value)} placeholder="Message to all users..." rows={4} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs resize-none" />
            <button
              onClick={async () => {
                if (!announceTitle.trim() || !announceMessage.trim()) return;
                await broadcastAnnouncement(announceTitle, announceMessage);
                setAnnounceTitle('');
                setAnnounceMessage('');
              }}
              className="w-full py-3 bg-amber-500 text-slate-950 font-bold text-sm rounded-xl flex items-center justify-center gap-2"
            >
              <Megaphone className="w-4 h-4" /> Broadcast to All Users
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
