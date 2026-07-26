import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Bell, CheckCheck, X, AlertCircle, CheckCircle2, ShieldAlert } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useGame } from '@/context/GameContext';

export default function NotificationDrawer({ isOpen, onClose }) {
  const { notifications, setUnreadNotificationsCount } = useGame();
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const { data: res } = await axiosClient.get('/notifications');
      const list = res?.data?.content || res?.data || res?.content || res;
      setList(Array.isArray(list) ? list : []);

      const countRes = await axiosClient.get('/notifications/unread-count');
      const countData = countRes.data?.data || countRes.data;
      setUnreadNotificationsCount(countData?.unreadCount ?? 0);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
    }
  }, [isOpen]);

  const handleMarkAllRead = async () => {
    try {
      await axiosClient.post('/notifications/read-all');
      setList((prev) => prev.map((item) => ({ ...item, isRead: true })));
      setUnreadNotificationsCount(0);
    } catch (err) {
      console.error('Failed to mark notifications as read:', err);
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/80 backdrop-blur-sm">
        <motion.div
          initial={{ x: '100%' }}
          animate={{ x: 0 }}
          exit={{ x: '100%' }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          className="w-full max-w-md bg-slate-900 border-l border-slate-800 h-full p-6 shadow-2xl flex flex-col justify-between"
        >
          {/* Header */}
          <div>
            <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
              <div className="flex items-center gap-2.5">
                <Bell className="w-5 h-5 text-amber-400" />
                <h3 className="font-bold text-slate-100 text-lg">Notifications</h3>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={handleMarkAllRead}
                  className="text-xs font-semibold text-amber-400 hover:text-amber-300 flex items-center gap-1 cursor-pointer"
                >
                  <CheckCheck className="w-4 h-4" />
                  <span>Mark all read</span>
                </button>
                <button onClick={onClose} className="p-1.5 rounded-xl bg-slate-950 text-slate-400 hover:text-slate-200">
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Notification List */}
            <div className="space-y-3 max-h-[75vh] overflow-y-auto pr-1">
              {loading ? (
                <p className="text-center text-xs text-slate-500 py-12">Loading notifications...</p>
              ) : list.length === 0 ? (
                <p className="text-center text-xs text-slate-500 py-12">No notifications found.</p>
              ) : (
                list.map((item) => (
                  <div
                    key={item.id}
                    className={`p-3.5 rounded-2xl border transition-all ${
                      item.isRead ? 'bg-slate-950/60 border-slate-800/60' : 'bg-slate-950 border-amber-500/30'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        {item.type === 'DEPOSIT_SUCCESS' || item.type === 'WITHDRAWAL_SUCCESS' ? (
                          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                        ) : item.type === 'ACCOUNT_ALERT' ? (
                          <ShieldAlert className="w-4 h-4 text-rose-400 shrink-0" />
                        ) : (
                          <AlertCircle className="w-4 h-4 text-amber-400 shrink-0" />
                        )}
                        <span className="font-bold text-xs text-slate-200">{item.type}</span>
                      </div>
                      <span className="text-[10px] text-slate-500 font-mono">
                        {item.createdAt ? new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Now'}
                      </span>
                    </div>

                    <p className="text-xs text-slate-300 mt-2 leading-relaxed">{item.message}</p>
                  </div>
                ))
              )}
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
