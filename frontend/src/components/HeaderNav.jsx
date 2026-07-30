import React from 'react';
import { Gift, Mail, Settings, Plus, Star } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';

function initials(name) {
  if (!name) return '?';
  const parts = String(name).trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

export default function HeaderNav({
  onOpenAuth,
  onOpenWallet,
  onOpenLeaderboard,
  onOpenNotifications,
  onOpenAdmin,
  onOpenProfile,
}) {
  const { user, isAuthenticated } = useAuth();
  const { unreadNotificationsCount } = useGame();
  const balance = ((user?.walletBalance ?? user?.balancePaise ?? 0) / 100).toFixed(2);

  if (!isAuthenticated) {
    return (
      <header className="relative z-40 shrink-0 px-4 py-3 border-b border-[#d4af37]/25 bg-gradient-to-b from-[#2a0a0a] to-[#1a0505]">
        <div className="max-w-[1400px] mx-auto flex items-center justify-between">
          <div className="font-display text-[#d4af37] text-xl font-extrabold tracking-[0.2em]">TEEN PATTI</div>
          <button
            type="button"
            onClick={onOpenAuth}
            className="px-5 py-2 rounded-xl bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] text-xs font-extrabold uppercase tracking-wide cursor-pointer"
          >
            Sign In / Register
          </button>
        </div>
      </header>
    );
  }

  return (
    <header className="relative z-40 shrink-0 px-3 sm:px-5 py-2.5 border-b border-[#d4af37]/20 bg-gradient-to-b from-[#3a0c0c] via-[#2a0808] to-[#1a0505] shadow-[0_4px_24px_rgba(0,0,0,0.45)]">
      <div className="max-w-[1400px] mx-auto flex items-center justify-between gap-3">
        {/* Left — profile */}
        <button
          type="button"
          onClick={onOpenProfile}
          className="flex items-center gap-2.5 min-w-0 cursor-pointer group"
        >
          <div className="w-11 h-11 rounded-full overflow-hidden border-2 border-[#d4af37] shadow-[0_0_12px_rgba(212,175,55,0.35)] bg-gradient-to-br from-[#4a2020] to-[#1a0505] flex items-center justify-center shrink-0">
            <span className="text-sm font-black text-[#f5e6a8]">{initials(user?.displayName)}</span>
          </div>
          <div className="text-left min-w-0 hidden sm:block">
            <p className="text-sm font-bold text-white truncate group-hover:text-[#f5e6a8] transition-colors">
              {user?.displayName || 'Player'}
            </p>
            <p className="flex items-center gap-1 text-[10px] text-[#d4af37] font-semibold">
              <Star className="w-3 h-3 fill-[#d4af37]" />
              Lv. {user?.level || user?.matchesPlayedCount || 1}
            </p>
          </div>
        </button>

        {/* Center — brand */}
        <div className="absolute left-1/2 -translate-x-1/2 text-center pointer-events-none">
          <div className="text-[#d4af37] text-lg leading-none mb-0.5">♠</div>
          <h1 className="font-display text-[15px] sm:text-xl md:text-2xl font-extrabold tracking-[0.22em] text-transparent bg-clip-text bg-gradient-to-b from-[#fff4c2] via-[#d4af37] to-[#a67c00]">
            TEEN PATTI
          </h1>
          <p className="hidden sm:flex items-center justify-center gap-2 text-[8px] sm:text-[9px] tracking-[0.35em] text-[#c9a227]/90 uppercase mt-0.5">
            <span className="h-px w-6 bg-[#d4af37]/40" />
            Real · Fun · Trust
            <span className="h-px w-6 bg-[#d4af37]/40" />
          </p>
        </div>

        {/* Right — wallet + utilities */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          <button
            type="button"
            onClick={onOpenWallet}
            className="flex items-center gap-1.5 pl-2.5 pr-1 py-1 rounded-full bg-black/45 border border-[#d4af37]/45 shadow-[0_0_12px_rgba(212,175,55,0.15)] cursor-pointer"
          >
            <span className="text-[#d4af37] text-sm">●</span>
            <span className="text-[11px] sm:text-xs font-bold text-[#f5e6a8] tabular-nums">₹{balance}</span>
            <span className="w-6 h-6 rounded-full bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] flex items-center justify-center">
              <Plus className="w-3.5 h-3.5 stroke-[3]" />
            </span>
          </button>

          <button type="button" onClick={onOpenLeaderboard} className="hidden md:flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer">
            <Gift className="w-4 h-4" />
            <span className="text-[8px] font-bold uppercase tracking-wide">Rewards</span>
          </button>

          <button type="button" onClick={onOpenNotifications} className="relative flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer">
            <Mail className="w-4 h-4" />
            <span className="text-[8px] font-bold uppercase tracking-wide hidden sm:block">Inbox</span>
            {unreadNotificationsCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-[14px] h-[14px] px-0.5 rounded-full bg-rose-500 text-white text-[8px] font-black flex items-center justify-center">
                {unreadNotificationsCount > 9 ? '9+' : unreadNotificationsCount}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={user?.role === 'ADMIN' ? onOpenAdmin : onOpenProfile}
            className="flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer"
          >
            <Settings className="w-4 h-4" />
            <span className="text-[8px] font-bold uppercase tracking-wide hidden sm:block">Settings</span>
          </button>
        </div>
      </div>
    </header>
  );
}
