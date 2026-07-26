import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy, Medal, Crown, Star, RefreshCw } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';

export default function LeaderboardModal({ isOpen, onClose }) {
  const { user } = useAuth();
  const [windowType, setWindowType] = useState('DAILY');
  const [metricType, setMetricType] = useState('WINNINGS');
  const [entries, setEntries] = useState([]);
  const [myRank, setMyRank] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchLeaderboard = async () => {
    setLoading(true);
    try {
      const { data: res } = await axiosClient.get('/leaderboard', {
        params: { window: windowType, metric: metricType, page: 0, size: 20 },
      });
      const list = res?.data?.content || res?.data || res?.content || res;
      setEntries(Array.isArray(list) ? list : []);

      if (user) {
        const rankRes = await axiosClient.get('/leaderboard/me', {
          params: { window: windowType, metric: metricType },
        });
        setMyRank(rankRes.data?.data || rankRes.data);
      }
    } catch (err) {
      console.error('Failed to fetch leaderboard:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchLeaderboard();
    }
  }, [isOpen, windowType, metricType]);

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative overflow-hidden flex flex-col max-h-[85vh]"
        >
          {/* Header */}
          <div className="flex items-center justify-between mb-4 border-b border-slate-800 pb-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-amber-400 to-amber-600 text-slate-950 flex items-center justify-center font-bold shadow-lg shadow-amber-500/20">
                <Trophy className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-slate-100 text-lg">Platform Leaderboard</h3>
                <p className="text-xs text-slate-400">Precomputed time-windowed rankings</p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="text-xs font-bold text-slate-400 hover:text-slate-200 bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800"
            >
              Close
            </button>
          </div>

          {/* Time Window Tabs */}
          <div className="flex bg-slate-950 p-1 rounded-xl mb-4 border border-slate-800">
            {['DAILY', 'WEEKLY', 'ALL_TIME'].map((w) => (
              <button
                key={w}
                onClick={() => setWindowType(w)}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${
                  windowType === w ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {w === 'DAILY' ? 'Today' : w === 'WEEKLY' ? 'This Week' : 'All Time'}
              </button>
            ))}
          </div>

          {/* Leaderboard Table List */}
          <div className="flex-1 overflow-y-auto space-y-2 pr-1 my-2">
            {loading ? (
              <p className="text-center text-xs text-slate-500 py-12">Loading precomputed rankings...</p>
            ) : entries.length === 0 ? (
              <p className="text-center text-xs text-slate-500 py-12">No leaderboard entries found for this window yet.</p>
            ) : (
              entries.map((item, idx) => {
                const rankNum = idx + 1;
                return (
                  <div
                    key={item.userId || idx}
                    className={`p-3.5 rounded-2xl border flex items-center justify-between text-xs transition-all ${
                      item.userId === user?.id
                        ? 'bg-amber-500/10 border-amber-500/40'
                        : 'bg-slate-950 border-slate-800/80'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      {/* Rank Badge */}
                      <div className="w-8 h-8 rounded-xl flex items-center justify-center font-black text-sm shrink-0">
                        {rankNum === 1 ? (
                          <div className="w-full h-full bg-amber-400 text-slate-950 rounded-xl flex items-center justify-center shadow-lg shadow-amber-400/30">
                            <Crown className="w-4 h-4 fill-current" />
                          </div>
                        ) : rankNum === 2 ? (
                          <div className="w-full h-full bg-slate-300 text-slate-950 rounded-xl flex items-center justify-center">
                            <Medal className="w-4 h-4" />
                          </div>
                        ) : rankNum === 3 ? (
                          <div className="w-full h-full bg-amber-700 text-amber-100 rounded-xl flex items-center justify-center">
                            <Medal className="w-4 h-4" />
                          </div>
                        ) : (
                          <span className="text-slate-500 font-mono">#{rankNum}</span>
                        )}
                      </div>

                      <div>
                        <span className="font-bold text-slate-100 block text-sm">
                          {item.displayName || `Player ${item.userId?.slice(-4)}`} {item.userId === user?.id && '(You)'}
                        </span>
                        <span className="text-[10px] text-slate-500 font-mono">
                          {item.handsWon || 0} Wins / {item.handsPlayed || 0} Played
                        </span>
                      </div>
                    </div>

                    <div className="text-right">
                      <span className="font-mono font-black text-emerald-400 text-base block">
                        ₹{((item.totalWinningsPaise || 0) / 100).toFixed(2)}
                      </span>
                      <span className="text-[10px] text-slate-500">Gross Winnings</span>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Sticky User Position Banner */}
          {myRank && myRank.ranked && (
            <div className="mt-3 p-3 bg-gradient-to-r from-amber-500/20 to-amber-500/10 border border-amber-500/30 rounded-2xl flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Star className="w-4 h-4 text-amber-400 fill-amber-400" />
                <span className="font-bold text-slate-200">Your Current Position:</span>
                <span className="font-black text-amber-400 font-mono text-sm">#{myRank.rank}</span>
              </div>
              <span className="font-mono text-emerald-400 font-bold">₹{((myRank.totalWinningsPaise || 0) / 100).toFixed(2)}</span>
            </div>
          )}
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
