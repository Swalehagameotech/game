import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Wallet, ShieldCheck, AlertCircle, ArrowDownLeft, ArrowUpRight, History } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchWalletSummary,
  fetchWalletHistory,
  depositToWallet,
  requestWithdrawal,
  completeGatewayDeposit,
} from '@/features/wallet/walletApi';

export default function WalletModal({ isOpen, onClose }) {
  const { user, updateUserProfile, refreshWalletBalance } = useAuth();
  const [activeTab, setActiveTab] = useState('DEPOSIT');
  const [summary, setSummary] = useState(null);
  const [amountRupees, setAmountRupees] = useState('');
  const [bankAccount, setBankAccount] = useState('');
  const [ifscCode, setIfscCode] = useState('');
  const [accountHolderName, setAccountHolderName] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [transactions, setTransactions] = useState([]);
  const [useGateway, setUseGateway] = useState(false);

  const syncBalance = (balancePaise) => {
    if (balancePaise != null) {
      updateUserProfile({ balancePaise, walletBalance: balancePaise });
    }
    if (refreshWalletBalance) refreshWalletBalance();
  };

  const loadWallet = async () => {
    try {
      const [sum, history] = await Promise.all([
        fetchWalletSummary(),
        fetchWalletHistory(0, 30),
      ]);
      setSummary(sum);
      setTransactions(history);
      syncBalance(sum?.balancePaise);
    } catch (err) {
      console.error('Wallet fetch error:', err);
    }
  };

  useEffect(() => {
    if (isOpen) {
      setAccountHolderName(user?.displayName || '');
      loadWallet();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const minDeposit = summary ? summary.minDepositPaise / 100 : 100;
  const maxDeposit = summary ? summary.maxDepositPaise / 100 : 50000;
  const minWithdraw = summary ? summary.minWithdrawalPaise / 100 : 500;
  const maxWithdraw = summary ? summary.maxWithdrawalPaise / 100 : 100000;

  const handleDepositSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);

    try {
      const paise = Math.round(Number(amountRupees) * 100);

      if (useGateway) {
        const result = await depositToWallet(paise, false);
        if (result.gatewayOrder?.depositRequestId) {
          const completed = await completeGatewayDeposit(result.gatewayOrder.depositRequestId);
          setMessage(`Deposited ${completed.formattedBalance ? completed.formattedBalance : '₹' + amountRupees} successfully!`);
        } else {
          setMessage(`Payment order created. Order ID: ${result.gatewayOrder?.gatewayOrderId || 'pending'}`);
        }
      } else {
        const result = await depositToWallet(paise, true);
        setMessage(`Added ${result.formattedBalance || '₹' + amountRupees} to your wallet!`);
      }

      setAmountRupees('');
      await loadWallet();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Deposit failed.');
    } finally {
      setLoading(false);
    }
  };

  const handleWithdrawSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);

    try {
      const paise = Math.round(Number(amountRupees) * 100);
      await requestWithdrawal({
        amountPaise: paise,
        accountNumber: bankAccount.replace(/\s/g, ''),
        ifscCode: ifscCode.trim().toUpperCase(),
        accountHolderName: accountHolderName.trim() || user?.displayName || 'Account Holder',
      });

      setMessage(`Withdrawal request for ₹${amountRupees} submitted. Funds held pending admin review.`);
      setAmountRupees('');
      setBankAccount('');
      setIfscCode('');
      await loadWallet();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Withdrawal request failed.');
    } finally {
      setLoading(false);
    }
  };

  const balanceDisplay = summary?.formattedBalance
    || (summary?.balancePaise != null ? `₹${(summary.balancePaise / 100).toFixed(2)}` : '₹0.00');

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="w-full max-w-xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative overflow-hidden"
        >
          <div className="bg-gradient-to-r from-amber-500/20 via-amber-500/10 to-amber-500/20 border border-amber-500/30 p-5 rounded-2xl mb-5">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center shadow-lg shadow-amber-500/20">
                  <Wallet className="w-6 h-6" />
                </div>
                <div>
                  <span className="text-xs text-slate-400 block font-medium">Wallet Balance</span>
                  <span className="text-2xl font-black text-amber-400 font-mono">{balanceDisplay}</span>
                </div>
              </div>
              <button
                onClick={onClose}
                className="text-xs font-bold text-slate-400 hover:text-slate-200 bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800 cursor-pointer"
              >
                Close
              </button>
            </div>
            {summary && (
              <div className="grid grid-cols-3 gap-2 text-[10px]">
                <div className="bg-slate-950/60 rounded-lg p-2 border border-slate-800/50">
                  <span className="text-slate-500 uppercase font-bold block">Deposited</span>
                  <span className="text-emerald-400 font-mono font-bold">₹{(summary.totalDepositedPaise / 100).toFixed(0)}</span>
                </div>
                <div className="bg-slate-950/60 rounded-lg p-2 border border-slate-800/50">
                  <span className="text-slate-500 uppercase font-bold block">Withdrawn</span>
                  <span className="text-rose-400 font-mono font-bold">₹{(summary.totalWithdrawnPaise / 100).toFixed(0)}</span>
                </div>
                <div className="bg-slate-950/60 rounded-lg p-2 border border-slate-800/50">
                  <span className="text-slate-500 uppercase font-bold block">Pending</span>
                  <span className="text-amber-400 font-mono font-bold">₹{(summary.pendingWithdrawalPaise / 100).toFixed(0)}</span>
                </div>
              </div>
            )}
          </div>

          <div className="flex bg-slate-950 p-1 rounded-xl mb-5 border border-slate-800">
            {[
              { id: 'DEPOSIT', label: 'Deposit', icon: ArrowDownLeft },
              { id: 'WITHDRAW', label: 'Withdraw', icon: ArrowUpRight },
              { id: 'HISTORY', label: 'History', icon: History },
            ].map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => { setActiveTab(id); setError(''); setMessage(''); }}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1 cursor-pointer ${
                  activeTab === id ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
              </button>
            ))}
          </div>

          {error && (
            <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-rose-400 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {message && (
            <div className="mb-4 p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400 text-xs flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 shrink-0" />
              <span>{message}</span>
            </div>
          )}

          {activeTab === 'DEPOSIT' && (
            <form onSubmit={handleDepositSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Amount (₹)</label>
                <input
                  type="number"
                  min={minDeposit}
                  max={maxDeposit}
                  step="1"
                  required
                  placeholder={`Min ₹${minDeposit} — Max ₹${maxDeposit.toLocaleString()}`}
                  value={amountRupees}
                  onChange={(e) => setAmountRupees(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div className="flex gap-2">
                {[100, 500, 1000, 5000].map((quick) => (
                  <button
                    key={quick}
                    type="button"
                    onClick={() => setAmountRupees(quick.toString())}
                    className="flex-1 py-1.5 bg-slate-950 border border-slate-800 hover:border-amber-500/40 text-xs font-bold text-slate-300 rounded-xl cursor-pointer"
                  >
                    +₹{quick}
                  </button>
                ))}
              </div>

              <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer">
                <input
                  type="checkbox"
                  checked={useGateway}
                  onChange={(e) => setUseGateway(e.target.checked)}
                  className="rounded border-slate-600"
                />
                Use payment gateway flow (mock Razorpay in dev)
              </label>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-bold text-xs rounded-xl shadow-lg hover:from-emerald-500 hover:to-teal-500 transition-all cursor-pointer disabled:opacity-60"
              >
                {loading ? 'Processing...' : useGateway ? 'Pay via Gateway' : 'Add Cash (Instant Demo)'}
              </button>
            </form>
          )}

          {activeTab === 'WITHDRAW' && (
            <form onSubmit={handleWithdrawSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Withdrawal Amount (₹)</label>
                <input
                  type="number"
                  min={minWithdraw}
                  max={maxWithdraw}
                  step="1"
                  required
                  placeholder={`Min ₹${minWithdraw} — Max ₹${maxWithdraw.toLocaleString()}`}
                  value={amountRupees}
                  onChange={(e) => setAmountRupees(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Account Holder Name</label>
                <input
                  type="text"
                  required
                  value={accountHolderName}
                  onChange={(e) => setAccountHolderName(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Bank Account Number</label>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{9,18}"
                  required
                  placeholder="9–18 digit account number"
                  value={bankAccount}
                  onChange={(e) => setBankAccount(e.target.value.replace(/\D/g, ''))}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">IFSC Code</label>
                <input
                  type="text"
                  required
                  pattern="[A-Z]{4}0[A-Z0-9]{6}"
                  placeholder="e.g. SBIN0001234"
                  value={ifscCode}
                  onChange={(e) => setIfscCode(e.target.value.toUpperCase())}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <p className="text-[10px] text-slate-500">Funds are held immediately and released after admin approval.</p>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold text-xs rounded-xl shadow-lg hover:from-amber-400 hover:to-amber-500 transition-all cursor-pointer disabled:opacity-60"
              >
                {loading ? 'Submitting...' : 'Submit Withdrawal Request'}
              </button>
            </form>
          )}

          {activeTab === 'HISTORY' && (
            <div className="max-h-64 overflow-y-auto space-y-2 pr-1">
              {transactions.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-8">No transaction history yet.</p>
              ) : (
                transactions.map((tx) => (
                  <div key={tx.id || tx.referenceId} className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-200 block">{tx.typeLabel || tx.type || 'Transaction'}</span>
                      <span className="text-[10px] text-slate-500 font-mono">
                        {tx.createdAt ? new Date(tx.createdAt).toLocaleString() : 'Recent'}
                      </span>
                    </div>
                    <span className={`font-mono font-bold ${(tx.amountPaise || 0) >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {tx.formattedAmount || `₹${((tx.amountPaise || 0) / 100).toFixed(2)}`}
                    </span>
                  </div>
                ))
              )}
            </div>
          )}
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
