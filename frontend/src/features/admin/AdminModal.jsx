import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
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

export default function AdminModal({ isOpen, onClose }) {
  const [activeTab, setActiveTab] = useState('DASHBOARD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

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

  const fetchAdminData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      switch (activeTab) {
        case 'DASHBOARD':
          setDashboard(await fetchAdminDashboard());
          break;
        case 'USERS':
          setUsers(await fetchAdminUsers(userQuery));
          break;
        case 'WITHDRAWALS':
          setWithdrawals(await fetchAdminWithdrawals());
          break;
        case 'TABLES':
          setTables(await fetchAdminTables(tableGroup));
          break;
        default:
          break;
      }
    } catch (err) {
      console.error('Failed to fetch admin data:', err);
      setError(err?.response?.data?.message || 'Failed to load admin data');
    } finally {
      setLoading(false);
    }
  }, [activeTab, userQuery, tableGroup]);

  useEffect(() => {
    if (isOpen) {
      fetchAdminData();
    }
  }, [isOpen, fetchAdminData]);

  const handleSearchUsers = () => fetchAdminData();

  const handleLookupWalletUser = async () => {
    if (!walletUserId.trim()) return;
    setLoading(true);
    setError('');
    try {
      const user = await fetchAdminUser(walletUserId.trim());
      const history = await fetchAdminUserWalletHistory(walletUserId.trim());
      setWalletUser(user);
      setWalletHistory(history.slice(0, 10));
    } catch (err) {
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
      if (mode === 'add') {
        await adminAddWallet(walletUser.id, paise, walletReason.trim());
      } else {
        await adminDeductWallet(walletUser.id, paise, walletReason.trim());
      }
      await handleLookupWalletUser();
      setWalletAmount('');
      setWalletReason('');
    } catch (err) {
      setError(err?.response?.data?.message || err?.response?.data?.error || 'Wallet adjustment failed');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSuspend = async (userId, status) => {
    try {
      if (status === 'SUSPENDED') {
        await reinstateUser(userId);
      } else {
        await suspendUser(userId, 'Violation of T&C');
      }
      fetchAdminData();
    } catch (err) {
      setError('User action failed');
    }
  };

  const handleBanUser = async (userId) => {
    try {
      await banUser(userId, 'Permanent ban by admin');
      fetchAdminData();
    } catch (err) {
      setError('Ban failed');
    }
  };

  const handleApproveWithdrawal = async (id) => {
    try {
      await approveWithdrawal(id);
      fetchAdminData();
    } catch (err) {
      setError('Approve failed');
    }
  };

  const handleRejectWithdrawal = async (id) => {
    try {
      await rejectWithdrawal(id, 'Verification failed');
      fetchAdminData();
    } catch (err) {
      setError('Reject failed');
    }
  };

  const handleForceClose = async (tableId) => {
    if (!window.confirm('Force close this table? Active hand state will be cleared.')) return;
    try {
      await forceCloseTable(tableId, 'Admin force close');
      fetchAdminData();
    } catch (err) {
      setError('Force close failed');
    }
  };

  const handleBroadcast = async () => {
    if (!announceTitle.trim() || !announceMessage.trim()) return;
    setLoading(true);
    try {
      const result = await broadcastAnnouncement(announceTitle, announceMessage);
      setAnnounceTitle('');
      setAnnounceMessage('');
      setError('');
      alert(`Announcement sent to ${result?.recipientCount ?? 'all'} users`);
    } catch (err) {
      setError('Broadcast failed');
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="w-full max-w-4xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative flex flex-col max-h-[90vh]"
        >
          <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center font-bold">
                <Shield className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-slate-100 text-lg">Admin Control Center</h3>
                <p className="text-xs text-slate-400">Dashboard, users, wallet, tables & announcements</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={fetchAdminData}
                className="text-xs font-bold text-slate-400 hover:text-slate-200 bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800 flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" />
                Refresh
              </button>
              <button
                onClick={onClose}
                className="text-xs font-bold text-slate-400 hover:text-slate-200 bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800"
              >
                Close
              </button>
            </div>
          </div>

          <div className="flex flex-wrap gap-1 bg-slate-950 p-1 rounded-xl mb-4 border border-slate-800">
            {TABS.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => setActiveTab(id)}
                className={`flex items-center gap-1.5 px-3 py-2 text-[10px] font-bold rounded-lg transition-all ${
                  activeTab === id ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
              </button>
            ))}
          </div>

          {error && (
            <div className="mb-3 px-3 py-2 bg-rose-950/50 border border-rose-800 rounded-xl text-xs text-rose-300">
              {error}
            </div>
          )}

          <div className="flex-1 overflow-y-auto space-y-3 pr-1">
            {loading && activeTab !== 'WALLET' && activeTab !== 'ANNOUNCE' && (
              <p className="text-center text-xs text-slate-500 py-8">Loading...</p>
            )}

            {activeTab === 'DASHBOARD' && dashboard && (
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

            {activeTab === 'USERS' && (
              <>
                <div className="flex gap-2 mb-2">
                  <input
                    value={userQuery}
                    onChange={(e) => setUserQuery(e.target.value)}
                    placeholder="Search email or display name..."
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200"
                  />
                  <button
                    onClick={handleSearchUsers}
                    className="px-4 py-2 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl"
                  >
                    Search
                  </button>
                </div>
                {users.length === 0 ? (
                  <p className="text-center text-xs text-slate-500 py-12">No users found.</p>
                ) : (
                  users.map((u) => (
                    <div key={u.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs gap-3">
                      <div className="min-w-0">
                        <span className="font-bold text-slate-100 text-sm block truncate">{u.displayName}</span>
                        <span className="text-slate-400 block truncate">{u.email}</span>
                        <span className="text-[10px] text-slate-500 block">
                          {u.role} · {u.accountStatus} · {formatPaise(u.walletBalancePaise)}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <button
                          onClick={() => handleToggleSuspend(u.id, u.accountStatus)}
                          className={`px-3 py-1.5 font-bold text-xs rounded-xl flex items-center gap-1 ${
                            u.accountStatus === 'SUSPENDED'
                              ? 'bg-emerald-600 text-white hover:bg-emerald-500'
                              : 'bg-amber-600 text-white hover:bg-amber-500'
                          }`}
                        >
                          {u.accountStatus === 'SUSPENDED' ? <UserCheck className="w-4 h-4" /> : <UserX className="w-4 h-4" />}
                          {u.accountStatus === 'SUSPENDED' ? 'Reinstate' : 'Suspend'}
                        </button>
                        {u.accountStatus !== 'BANNED' && (
                          <button
                            onClick={() => handleBanUser(u.id)}
                            className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs rounded-xl flex items-center gap-1"
                          >
                            <Ban className="w-4 h-4" />
                            Ban
                          </button>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </>
            )}

            {activeTab === 'WALLET' && (
              <div className="space-y-4">
                <div className="flex gap-2">
                  <input
                    value={walletUserId}
                    onChange={(e) => setWalletUserId(e.target.value)}
                    placeholder="User ID..."
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200"
                  />
                  <button
                    onClick={handleLookupWalletUser}
                    className="px-4 py-2 bg-amber-500 text-slate-950 text-xs font-bold rounded-xl"
                  >
                    Lookup
                  </button>
                </div>

                {walletUser && (
                  <>
                    <div className="p-4 bg-slate-950 border border-slate-800 rounded-2xl">
                      <p className="font-bold text-slate-100">{walletUser.displayName}</p>
                      <p className="text-emerald-400 font-mono text-lg font-bold">{formatPaise(walletUser.walletBalancePaise)}</p>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <input
                        value={walletAmount}
                        onChange={(e) => setWalletAmount(e.target.value)}
                        placeholder="Amount (₹)"
                        type="number"
                        min="0"
                        step="0.01"
                        className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200"
                      />
                      <input
                        value={walletReason}
                        onChange={(e) => setWalletReason(e.target.value)}
                        placeholder="Reason (required)"
                        className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200"
                      />
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleWalletAdjust('add')}
                        className="flex-1 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-xl"
                      >
                        Credit
                      </button>
                      <button
                        onClick={() => handleWalletAdjust('deduct')}
                        className="flex-1 py-2 bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold rounded-xl"
                      >
                        Debit
                      </button>
                    </div>

                    {walletHistory.length > 0 && (
                      <div className="space-y-2">
                        <p className="text-[10px] uppercase text-slate-500 font-bold">Recent Transactions</p>
                        {walletHistory.map((tx) => (
                          <div key={tx.id} className="p-2 bg-slate-950 border border-slate-800 rounded-xl text-[10px] flex justify-between">
                            <span className="text-slate-400">{tx.type || tx.transactionType}</span>
                            <span className={tx.amountPaise >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                              {formatPaise(Math.abs(tx.amountPaise || tx.amount || 0))}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>
            )}

            {activeTab === 'TABLES' && (
              <>
                <div className="flex gap-2 mb-2">
                  {['active', 'running', 'waiting', 'closed'].map((g) => (
                    <button
                      key={g}
                      onClick={() => setTableGroup(g)}
                      className={`px-3 py-1.5 text-[10px] font-bold rounded-lg capitalize ${
                        tableGroup === g ? 'bg-amber-500 text-slate-950' : 'bg-slate-950 text-slate-400 border border-slate-800'
                      }`}
                    >
                      {g}
                    </button>
                  ))}
                </div>
                {tables.length === 0 ? (
                  <p className="text-center text-xs text-slate-500 py-12">No tables in this group.</p>
                ) : (
                  tables.map((t) => (
                    <div key={t.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                      <div>
                        <span className="font-bold text-slate-100 text-sm block">{t.tableName || `Table ${t.id?.slice(-6)}`}</span>
                        <span className="text-slate-400 block">
                          {t.tableType} · {t.status} · {t.seatedCount}/{t.maxPlayers} players
                        </span>
                        <span className="text-[10px] text-slate-500 font-mono">Boot: {formatPaise(t.bootAmountPaise)}</span>
                      </div>
                      {t.status !== 'CLOSED' && (
                        <button
                          onClick={() => handleForceClose(t.id)}
                          className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs rounded-xl"
                        >
                          Force Close
                        </button>
                      )}
                    </div>
                  ))
                )}
              </>
            )}

            {activeTab === 'WITHDRAWALS' && (
              withdrawals.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-12">No pending withdrawal requests.</p>
              ) : (
                withdrawals.map((req) => (
                  <div key={req.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-100 text-sm block">
                        {req.userDisplayName || `User #${req.userId?.slice(-6)}`}
                      </span>
                      <span className="text-emerald-400 font-mono font-bold text-base my-0.5 block">
                        {formatPaise(req.amountPaise)}
                      </span>
                      <span className="text-[10px] text-slate-500 block">{req.userEmail}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleApproveWithdrawal(req.id)}
                        className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl flex items-center gap-1"
                      >
                        <CheckCircle className="w-4 h-4" />
                        Approve
                      </button>
                      <button
                        onClick={() => handleRejectWithdrawal(req.id)}
                        className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs rounded-xl flex items-center gap-1"
                      >
                        <XCircle className="w-4 h-4" />
                        Reject
                      </button>
                    </div>
                  </div>
                ))
              )
            )}

            {activeTab === 'ANNOUNCE' && (
              <div className="space-y-3">
                <input
                  value={announceTitle}
                  onChange={(e) => setAnnounceTitle(e.target.value)}
                  placeholder="Announcement title"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200"
                />
                <textarea
                  value={announceMessage}
                  onChange={(e) => setAnnounceMessage(e.target.value)}
                  placeholder="Message to all users..."
                  rows={4}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 resize-none"
                />
                <button
                  onClick={handleBroadcast}
                  disabled={loading}
                  className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-sm rounded-xl flex items-center justify-center gap-2"
                >
                  <Megaphone className="w-4 h-4" />
                  Broadcast to All Users
                </button>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}


