import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Wallet, ArrowDownLeft, ArrowUpRight, History, ShieldCheck, AlertCircle } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';

export default function WalletModal({ isOpen, onClose }) {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('DEPOSIT');
  const [walletData, setWalletData] = useState(null);
  const [amountRupees, setAmountRupees] = useState('');
  const [bankAccount, setBankAccount] = useState('');
  const [ifscCode, setIfscCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [transactions, setTransactions] = useState([]);

  const fetchWalletDetails = async () => {
    try {
      const { data: res } = await axiosClient.get('/wallet/me');
      setWalletData(res?.data || res);

      const txRes = await axiosClient.get('/wallet/me/history');
      const list = txRes.data?.data?.content || txRes.data?.data || txRes.data?.content || txRes.data;
      setTransactions(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Wallet fetch error:', err);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchWalletDetails();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleQuickDemoDeposit = async () => {
    setLoading(true);
    setError('');
    setMessage('');
    try {
      await axiosClient.post('/wallet/deposit/demo?amountPaise=100000');
      setMessage('Successfully added +₹1,000 Demo Chips to your wallet!');
      fetchWalletDetails();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to add demo chips.');
    } finally {
      setLoading(false);
    }
  };

  const handleDepositSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);

    try {
      const paise = Math.round(Number(amountRupees) * 100);
      await axiosClient.post('/wallet/deposit/demo?amountPaise=' + paise);
      setMessage(`Successfully deposited ₹${amountRupees} Demo Chips!`);
      setAmountRupees('');
      fetchWalletDetails();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data || 'Deposit failed.');
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
      await axiosClient.post('/transactions/withdrawal/request', {
        amountPaise: paise,
        bankAccountDetails: `AC:${bankAccount} IFSC:${ifscCode}`,
      });

      setMessage(`Withdrawal request for ₹${amountRupees} submitted for review!`);
      setAmountRupees('');
      fetchWalletDetails();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data || 'Withdrawal request failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="w-full max-w-xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative overflow-hidden"
        >
          {/* Header Balance Banner */}
          <div className="bg-gradient-to-r from-amber-500/20 via-amber-500/10 to-amber-500/20 border border-amber-500/30 p-5 rounded-2xl mb-5 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center font-bold text-xl shadow-lg shadow-amber-500/20">
                <Wallet className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs text-slate-400 block font-medium">Real-Money Wallet Balance</span>
                <span className="text-2xl font-black text-amber-400 font-mono">
                  ₹{walletData ? (walletData.balancePaise / 100).toFixed(2) : '0.00'}
                </span>
              </div>
            </div>
            <button
              onClick={onClose}
              className="text-xs font-bold text-slate-400 hover:text-slate-200 bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800"
            >
              Close
            </button>
          </div>

          {/* Navigation Tabs */}
          <div className="flex bg-slate-950 p-1 rounded-xl mb-5 border border-slate-800">
            {['DEPOSIT', 'WITHDRAW', 'HISTORY'].map((tab) => (
              <button
                key={tab}
                onClick={() => { setActiveTab(tab); setError(''); setMessage(''); }}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${
                  activeTab === tab ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {tab === 'DEPOSIT' ? 'Add Cash (Deposit)' : tab === 'WITHDRAW' ? 'Withdraw Cash' : 'History'}
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

          {/* Deposit Form */}
          {activeTab === 'DEPOSIT' && (
            <form onSubmit={handleDepositSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Amount (₹)</label>
                <input
                  type="number"
                  min="100"
                  max="50000"
                  required
                  placeholder="Min ₹100 - Max ₹50,000"
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
                    className="flex-1 py-1.5 bg-slate-950 border border-slate-800 hover:border-amber-500/40 text-xs font-bold text-slate-300 rounded-xl"
                  >
                    +₹{quick}
                  </button>
                ))}
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-bold text-xs rounded-xl shadow-lg shadow-emerald-600/20 hover:from-emerald-500 hover:to-teal-500 transition-all cursor-pointer"
              >
                {loading ? 'Processing Payment...' : 'Proceed to Payment (Razorpay)'}
              </button>
            </form>
          )}

          {/* Withdraw Form */}
          {activeTab === 'WITHDRAW' && (
            <form onSubmit={handleWithdrawSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Withdrawal Amount (₹)</label>
                <input
                  type="number"
                  min="500"
                  max="100000"
                  required
                  placeholder="Min ₹500 - Max ₹100,000"
                  value={amountRupees}
                  onChange={(e) => setAmountRupees(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Bank Account Number</label>
                <input
                  type="text"
                  required
                  placeholder="Enter Bank Account Number"
                  value={bankAccount}
                  onChange={(e) => setBankAccount(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">IFSC Code</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. SBIN0001234"
                  value={ifscCode}
                  onChange={(e) => setIfscCode(e.target.value.toUpperCase())}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 hover:from-amber-400 hover:to-amber-500 transition-all cursor-pointer"
              >
                {loading ? 'Submitting...' : 'Submit Withdrawal Request'}
              </button>
            </form>
          )}

          {/* History List */}
          {activeTab === 'HISTORY' && (
            <div className="max-h-64 overflow-y-auto space-y-2 pr-1">
              {transactions.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-8">No transaction history found.</p>
              ) : (
                transactions.map((tx, idx) => (
                  <div key={idx} className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-200 block">{tx.type || tx.entryType || 'LEDGER'}</span>
                      <span className="text-[10px] text-slate-500 font-mono">{tx.createdAt ? new Date(tx.createdAt).toLocaleDateString() : 'Recent'}</span>
                    </div>
                    <span className={`font-mono font-bold ${
                      (tx.amountPaise || 0) >= 0 ? 'text-emerald-400' : 'text-rose-400'
                    }`}>
                      ₹{((tx.amountPaise || tx.amount || 0) / 100).toFixed(2)}
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
