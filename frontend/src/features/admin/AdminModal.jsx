import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Shield, CheckCircle, XCircle, UserX, UserCheck, AlertTriangle } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';

export default function AdminModal({ isOpen, onClose }) {
  const [withdrawals, setWithdrawals] = useState([]);
  const [users, setUsers] = useState([]);
  const [activeTab, setActiveTab] = useState('WITHDRAWALS');
  const [loading, setLoading] = useState(false);

  const fetchAdminData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'WITHDRAWALS') {
        const { data } = await axiosClient.get('/admin/withdrawals', {
          params: { status: 'PENDING_ADMIN_REVIEW', page: 0, size: 20 },
        });
        setWithdrawals(data.content || data || []);
      } else if (activeTab === 'USERS') {
        const { data } = await axiosClient.get('/admin/users', { params: { page: 0, size: 20 } });
        setUsers(data.content || data || []);
      }
    } catch (err) {
      console.error('Failed to fetch admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchAdminData();
    }
  }, [isOpen, activeTab]);

  const handleApproveWithdrawal = async (id) => {
    try {
      await axiosClient.post(`/admin/withdrawals/${id}/approve`, { notes: 'Approved by Admin' });
      fetchAdminData();
    } catch (err) {
      console.error('Approve withdrawal failed:', err);
    }
  };

  const handleRejectWithdrawal = async (id) => {
    try {
      await axiosClient.post(`/admin/withdrawals/${id}/reject`, { rejectionReason: 'Verification failed' });
      fetchAdminData();
    } catch (err) {
      console.error('Reject withdrawal failed:', err);
    }
  };

  const handleToggleSuspendUser = async (userId, currentStatus) => {
    try {
      if (currentStatus === 'SUSPENDED') {
        await axiosClient.post(`/admin/users/${userId}/unsuspend`);
      } else {
        await axiosClient.post(`/admin/users/${userId}/suspend`, { reason: 'Violation of T&C' });
      }
      fetchAdminData();
    } catch (err) {
      console.error('User suspension toggle failed:', err);
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
          className="w-full max-w-3xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative flex flex-col max-h-[85vh]"
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-amber-500 text-slate-950 flex items-center justify-center font-bold">
                <Shield className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-slate-100 text-lg">Admin Control Center</h3>
                <p className="text-xs text-slate-400">Withdrawal Approvals, Account Security & KYC Management</p>
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
          <div className="flex bg-slate-950 p-1 rounded-xl mb-4 border border-slate-800">
            {['WITHDRAWALS', 'USERS'].map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${
                  activeTab === tab ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {tab === 'WITHDRAWALS' ? 'Pending Withdrawals' : 'User Account Management'}
              </button>
            ))}
          </div>

          {/* Tab Contents */}
          <div className="flex-1 overflow-y-auto space-y-3 pr-1">
            {activeTab === 'WITHDRAWALS' && (
              withdrawals.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-12">No pending withdrawal requests found.</p>
              ) : (
                withdrawals.map((req) => (
                  <div key={req.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-100 text-sm block">User #{req.userId?.slice(-6)}</span>
                      <span className="text-emerald-400 font-mono font-bold text-base my-0.5 block">
                        ₹{((req.amountPaise || 0) / 100).toFixed(2)}
                      </span>
                      <span className="text-[10px] text-slate-500 block">Bank Details: {req.bankAccountDetails || 'N/A'}</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleApproveWithdrawal(req.id)}
                        className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl flex items-center gap-1 cursor-pointer"
                      >
                        <CheckCircle className="w-4 h-4" />
                        <span>Approve</span>
                      </button>
                      <button
                        onClick={() => handleRejectWithdrawal(req.id)}
                        className="px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs rounded-xl flex items-center gap-1 cursor-pointer"
                      >
                        <XCircle className="w-4 h-4" />
                        <span>Reject</span>
                      </button>
                    </div>
                  </div>
                ))
              )
            )}

            {activeTab === 'USERS' && (
              users.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-12">No user accounts found.</p>
              ) : (
                users.map((u) => (
                  <div key={u.id} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-100 text-sm block">{u.displayName || u.username}</span>
                      <span className="text-slate-400 block">{u.email}</span>
                      <span className="text-[10px] text-slate-500 font-mono">Role: {u.role}</span>
                    </div>

                    <button
                      onClick={() => handleToggleSuspendUser(u.id, u.accountStatus)}
                      className={`px-3 py-1.5 font-bold text-xs rounded-xl flex items-center gap-1 cursor-pointer ${
                        u.accountStatus === 'SUSPENDED'
                          ? 'bg-emerald-600 text-white hover:bg-emerald-500'
                          : 'bg-rose-600 text-white hover:bg-rose-500'
                      }`}
                    >
                      {u.accountStatus === 'SUSPENDED' ? <UserCheck className="w-4 h-4" /> : <UserX className="w-4 h-4" />}
                      <span>{u.accountStatus === 'SUSPENDED' ? 'Unsuspend' : 'Suspend'}</span>
                    </button>
                  </div>
                ))
              )
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
