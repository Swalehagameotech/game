import React from 'react';
import { Wallet, Trophy, Bell, Shield, LogOut, User as UserIcon, Plus } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';

export default function HeaderNav({
  onOpenAuth,
  onOpenWallet,
  onOpenLeaderboard,
  onOpenNotifications,
  onOpenAdmin,
  onOpenProfile,
}) {
  const { user, isAuthenticated, logout } = useAuth();
  const { unreadNotificationsCount } = useGame();

  return (
    <header className="bg-slate-900/90 backdrop-blur-md border-b border-slate-800 sticky top-0 z-40 px-4 md:px-8 py-3.5 shadow-xl">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Brand Logo */}
        <div className="flex items-center gap-3 cursor-pointer" onClick={() => window.location.reload()}>
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-amber-400 via-amber-500 to-amber-600 text-slate-950 flex items-center justify-center font-black text-xl shadow-lg shadow-amber-500/20">
            ♠
          </div>
          <div>
            <h1 className="text-lg font-black text-slate-100 tracking-tight flex items-center gap-1.5">
              <span>Teen Patti</span>
              <span className="text-[10px] uppercase font-bold tracking-widest bg-amber-500/10 text-amber-400 px-2 py-0.5 rounded border border-amber-500/20">
                PRO
              </span>
            </h1>
            <p className="text-[10px] text-slate-400 font-mono">Real-Money Poker Platform</p>
          </div>
        </div>

        {/* Right Navigation Options */}
        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <>
              {/* Wallet Balance Widget */}
              <div className="flex items-center gap-2 bg-slate-950 p-1.5 pl-3 rounded-2xl border border-slate-800">
                <div className="flex flex-col">
                  <span className="text-[9px] uppercase font-bold text-slate-500 leading-none">Wallet</span>
                  <span className="text-xs font-mono font-extrabold text-amber-400">
                    ₹{((user?.walletBalance ?? user?.balancePaise ?? 10000) / 100).toFixed(2)}
                  </span>
                </div>
                <button
                  onClick={onOpenWallet}
                  className="px-2.5 py-1.5 bg-amber-500 hover:bg-amber-400 text-slate-950 rounded-xl font-bold text-xs flex items-center gap-1 shadow-md cursor-pointer transition-all"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add</span>
                </button>
              </div>

              {/* Leaderboard Button */}
              <button
                onClick={onOpenLeaderboard}
                className="p-2.5 bg-slate-950 hover:bg-slate-800 text-slate-300 rounded-2xl border border-slate-800 transition-all cursor-pointer"
                title="Leaderboard"
              >
                <Trophy className="w-4 h-4 text-amber-400" />
              </button>

              {/* Notifications Bell */}
              <button
                onClick={onOpenNotifications}
                className="p-2.5 bg-slate-950 hover:bg-slate-800 text-slate-300 rounded-2xl border border-slate-800 transition-all relative cursor-pointer"
                title="Notifications"
              >
                <Bell className="w-4 h-4 text-slate-300" />
                {unreadNotificationsCount > 0 && (
                  <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-rose-500 text-white font-bold text-[9px] flex items-center justify-center animate-pulse">
                    {unreadNotificationsCount}
                  </span>
                )}
              </button>

              {/* Admin Button (If Admin Role) */}
              {user?.role === 'ADMIN' && (
                <button
                  onClick={onOpenAdmin}
                  className="px-3 py-2 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 rounded-2xl font-bold text-xs flex items-center gap-1.5 transition-all cursor-pointer"
                >
                  <Shield className="w-4 h-4" />
                  <span>Admin</span>
                </button>
              )}

              {/* User Avatar & Logout */}
              <div className="flex items-center gap-2 pl-2 border-l border-slate-800">
                <button
                  onClick={onOpenProfile}
                  className="w-8 h-8 rounded-xl bg-slate-800 text-slate-200 flex items-center justify-center font-bold text-xs border border-slate-700 hover:border-amber-500/50 cursor-pointer transition-all"
                  title="My Profile"
                >
                  {user?.displayName ? user.displayName.charAt(0).toUpperCase() : 'U'}
                </button>
                <button
                  onClick={logout}
                  className="p-2 text-slate-400 hover:text-rose-400 transition-all cursor-pointer"
                  title="Sign Out"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            </>
          ) : (
            <button
              onClick={onOpenAuth}
              className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 transition-all cursor-pointer"
            >
              Sign In / Register
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
