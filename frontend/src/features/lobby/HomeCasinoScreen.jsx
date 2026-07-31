import React from 'react';
import { motion } from 'framer-motion';
import {
  PlusCircle, Lock, Trophy, Gift, Home, ShoppingCart, History,
  User as UserIcon, AlertCircle, RefreshCw, Mail, Settings, Plus, Star,
  Users, UserPlus, Play,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import { isActiveHandStatus, getTableStatusLabel, isCountdownStatus, isJoinableStatus } from '@/features/table/tableUtils';
import { VARIANT_CARDS } from './variants';
import VariantArt, { BrandLogoAces } from './components/VariantArt';

const TABLE_HUES = [150, 280, 350, 40, 200, 310];

function TableFeltIcon({ hue = 150 }) {
  const id = `felt-${hue}`;
  return (
    <svg width="36" height="28" viewBox="0 0 40 30" aria-hidden className="shrink-0">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={`hsl(${hue} 55% 38%)`} />
          <stop offset="100%" stopColor={`hsl(${hue} 55% 16%)`} />
        </linearGradient>
      </defs>
      <ellipse cx="20" cy="15" rx="18" ry="12" fill={`url(#${id})`} stroke="#d4af37" strokeWidth="1.2" />
      <ellipse cx="20" cy="15" rx="12" ry="7" fill="none" stroke="#d4af37" strokeWidth="0.6" opacity="0.45" />
    </svg>
  );
}

function GoldCoinIcon({ size = 14 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="11" fill="#d4af37" stroke="#f5e6a8" strokeWidth="1.5" />
      <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.2" strokeDasharray="2 1.5" />
    </svg>
  );
}

function initials(name) {
  if (!name) return '?';
  const parts = String(name).trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

/**
 * Casino home — Teen Patti Gold style with SVG cards / variant art.
 */
export default function HomeCasinoScreen({
  tables = [],
  bootOptionsPaise = [1000],
  loading,
  error,
  setError,
  activeGame,
  privateInvitations = [],
  quickPlayLoading,
  selectedVariant,
  onRequestQuickPlay,
  onQuickPlay,
  onJoinTableClick,
  onOpenCreatePublic,
  onOpenCreatePrivate,
  onOpenJoinCode,
  onAcceptInvite,
  onRefresh,
  onOpenWallet,
  onOpenLeaderboard,
  onOpenProfile,
  onOpenNotifications,
  onOpenAuth,
  onResumeGame,
}) {
  const { user, isAuthenticated } = useAuth();
  const { unreadNotificationsCount } = useGame();
  const liveTables = (tables || []).slice(0, 8);
  const balance = ((user?.walletBalance ?? user?.balancePaise ?? 0) / 100).toFixed(2);
  const variantLabel = (selectedVariant || 'CLASSIC').replaceAll('_', ' ');

  return (
    <div className="relative min-h-full pb-28 text-white">
      {/* Atmosphere */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-[#2a0c0c] via-[#1a0808] to-[#0f0404]" />
        <div
          className="absolute inset-0 opacity-[0.12]"
          style={{
            backgroundImage:
              'radial-gradient(ellipse 80% 50% at 50% 0%, rgba(212,175,55,0.35), transparent 55%), radial-gradient(circle at 15% 40%, rgba(212,175,55,0.08) 0.5px, transparent 0.6px), radial-gradient(circle at 85% 70%, rgba(212,175,55,0.08) 0.5px, transparent 0.6px)',
            backgroundSize: '100% 100%, 26px 26px, 26px 26px',
          }}
        />
      </div>

      <div className="relative z-10 max-w-[1440px] mx-auto px-3 sm:px-5 pt-2 sm:pt-3 space-y-3 sm:space-y-4">
        {/* ── Header ── */}
        <header className="flex items-center justify-between gap-2 sm:gap-4">
          {/* Profile + wallet */}
          <div className="flex items-center gap-2 sm:gap-3 min-w-0">
            <button
              type="button"
              onClick={isAuthenticated ? onOpenProfile : onOpenAuth}
              className="flex items-center gap-2 min-w-0 cursor-pointer"
            >
              <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-full overflow-hidden border-2 border-[#d4af37] shadow-[0_0_14px_rgba(212,175,55,0.4)] bg-gradient-to-br from-[#5a2020] to-[#1a0505] flex items-center justify-center shrink-0">
                <span className="text-sm font-black text-[#f5e6a8]">{initials(user?.displayName)}</span>
              </div>
              <div className="text-left min-w-0 hidden xs:block sm:block">
                <p className="text-sm font-bold text-white truncate max-w-[110px] sm:max-w-[160px]">
                  {isAuthenticated ? (user?.displayName || 'Player') : 'Guest'}
                </p>
                <p className="flex items-center gap-1 text-[10px] text-[#d4af37] font-semibold">
                  <Star className="w-3 h-3 fill-[#d4af37]" />
                  VIP {user?.level || Math.max(1, Math.floor((user?.matchesPlayedCount || 0) / 10) + 1)}
                </p>
              </div>
            </button>

            <button
              type="button"
              onClick={onOpenWallet}
              className="flex items-center gap-1.5 pl-2 pr-1 py-1 rounded-full bg-black/50 border border-[#d4af37]/50 shadow-[0_0_12px_rgba(212,175,55,0.2)] cursor-pointer"
            >
              <GoldCoinIcon size={14} />
              <span className="text-[11px] sm:text-xs font-bold text-[#f5e6a8] tabular-nums">
                ₹ {isAuthenticated ? balance : '0.00'}
              </span>
              <span className="w-6 h-6 rounded-full bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] flex items-center justify-center">
                <Plus className="w-3.5 h-3.5 stroke-[3]" />
              </span>
            </button>
          </div>

          {/* Brand */}
          <div className="absolute left-1/2 -translate-x-1/2 text-center pointer-events-none hidden sm:block">
            <BrandLogoAces className="mb-1" />
            <h1 className="font-display text-xl md:text-3xl font-extrabold tracking-[0.18em] text-transparent bg-clip-text bg-gradient-to-b from-[#fff8d6] via-[#d4af37] to-[#8a6a12] drop-shadow-[0_2px_12px_rgba(212,175,55,0.35)]">
              TEEN PATTI
            </h1>
            <p className="flex items-center justify-center gap-2 text-[8px] md:text-[9px] tracking-[0.32em] text-[#c9a227] uppercase mt-0.5">
              <span className="h-px w-8 bg-[#d4af37]/45" />
              Real Fun · Real People
              <span className="h-px w-8 bg-[#d4af37]/45" />
            </p>
          </div>
          <div className="sm:hidden text-center pointer-events-none">
            <h1 className="font-display text-base font-extrabold tracking-[0.15em] text-[#d4af37]">TEEN PATTI</h1>
          </div>

          {/* Utilities */}
          <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
            <button type="button" onClick={onOpenLeaderboard} className="flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer min-w-[44px]">
              <span className="relative w-9 h-9 rounded-full border border-[#d4af37]/45 bg-black/40 flex items-center justify-center shadow-[0_0_10px_rgba(212,175,55,0.2)]">
                <Gift className="w-4 h-4" />
              </span>
              <span className="text-[8px] font-bold uppercase tracking-wide hidden md:block">Rewards</span>
            </button>
            <button type="button" onClick={onOpenNotifications} className="relative flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer min-w-[44px]">
              <span className="relative w-9 h-9 rounded-full border border-[#d4af37]/45 bg-black/40 flex items-center justify-center">
                <Mail className="w-4 h-4" />
                {unreadNotificationsCount > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 min-w-[15px] h-[15px] px-0.5 rounded-full bg-rose-500 text-white text-[8px] font-black flex items-center justify-center">
                    {unreadNotificationsCount > 9 ? '9+' : unreadNotificationsCount}
                  </span>
                )}
              </span>
              <span className="text-[8px] font-bold uppercase tracking-wide hidden md:block">Inbox</span>
            </button>
            <button type="button" onClick={onOpenLeaderboard} className="hidden sm:flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer min-w-[44px]">
              <span className="w-9 h-9 rounded-full border border-[#d4af37]/45 bg-black/40 flex items-center justify-center">
                <Trophy className="w-4 h-4" />
              </span>
              <span className="text-[8px] font-bold uppercase tracking-wide hidden md:block">Leaderboard</span>
            </button>
            <button type="button" onClick={onOpenProfile} className="flex flex-col items-center gap-0.5 text-[#f5e6a8]/90 hover:text-white cursor-pointer min-w-[44px]">
              <span className="w-9 h-9 rounded-full border border-[#d4af37]/45 bg-black/40 flex items-center justify-center">
                <Settings className="w-4 h-4" />
              </span>
              <span className="text-[8px] font-bold uppercase tracking-wide hidden md:block">Settings</span>
            </button>
          </div>
        </header>

        {/* Active / invites / errors */}
        {activeGame && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-col sm:flex-row items-center justify-between gap-2 p-3 rounded-2xl border border-[#d4af37]/45 bg-black/50"
          >
            <p className="text-sm text-white">
              Seated at <span className="text-[#d4af37] font-bold">{activeGame.tableName}</span>
              {' '}({activeGame.seatedCount}/{activeGame.maxPlayers})
            </p>
            <button
              type="button"
              onClick={() => onResumeGame(activeGame.tableId)}
              className="px-5 py-2 rounded-xl bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] text-xs font-extrabold uppercase cursor-pointer"
            >
              Resume Game
            </button>
          </motion.div>
        )}

        {privateInvitations.length > 0 && (
          <div className="p-3 rounded-2xl border border-[#d4af37]/30 bg-black/40 space-y-2">
            <p className="text-[11px] font-bold uppercase tracking-wide text-[#d4af37]">Private Invitations</p>
            {privateInvitations.map((invite) => (
              <div key={invite.notificationId || invite.tableId} className="flex items-center justify-between gap-3">
                <p className="text-xs text-white/85 truncate">
                  {invite.hostDisplayName || 'Host'} · {invite.inviteCode}
                </p>
                <button
                  type="button"
                  onClick={() => onAcceptInvite(invite)}
                  className="px-3 py-1.5 rounded-lg bg-[#d4af37] text-[#1a0505] text-[10px] font-extrabold cursor-pointer"
                >
                  Join
                </button>
              </div>
            ))}
          </div>
        )}

        {error && (
          <div className="p-3 rounded-xl bg-rose-950/60 border border-rose-500/40 text-rose-200 text-xs flex items-center justify-between gap-2">
            <span className="flex items-center gap-2"><AlertCircle className="w-4 h-4 shrink-0" />{error}</span>
            <button type="button" onClick={() => setError('')} className="underline text-[10px]">Dismiss</button>
          </div>
        )}

        {/* ── Main: variations + live tables ── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4 items-start">
          <section className="lg:col-span-9 order-2 lg:order-1">
            <div className="flex items-center justify-center gap-3 mb-3">
              <span className="h-px flex-1 max-w-[80px] bg-gradient-to-r from-transparent to-[#d4af37]/60" />
              <h2 className="font-display text-sm sm:text-base font-bold tracking-[0.2em] text-[#f5e6a8] uppercase">
                Select Variation
              </h2>
              <span className="h-px flex-1 max-w-[80px] bg-gradient-to-l from-transparent to-[#d4af37]/60" />
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-2 sm:gap-2.5">
              {VARIANT_CARDS.map((v) => {
                const selected = (selectedVariant || 'CLASSIC') === v.key;
                return (
                  <motion.button
                    key={v.key}
                    type="button"
                    whileHover={{ y: -2 }}
                    whileTap={{ scale: 0.98 }}
                    disabled={Boolean(quickPlayLoading)}
                    onClick={() => onRequestQuickPlay(v.key)}
                    className={`group relative flex flex-col items-center text-center p-2.5 sm:p-3 rounded-2xl border transition-all cursor-pointer disabled:opacity-55 disabled:cursor-wait ${
                      selected
                        ? 'border-[#d4af37] bg-gradient-to-b from-[#4a2010]/90 to-[#1a0808] shadow-[0_0_18px_rgba(212,175,55,0.28)]'
                        : 'border-[#d4af37]/25 bg-gradient-to-b from-[#2a1010]/85 to-[#120606] hover:border-[#d4af37]/55'
                    }`}
                  >
                    <p className="text-[11px] sm:text-[12px] font-extrabold text-[#f5e6a8] truncate w-full">
                      {v.name}
                    </p>
                    <p className="text-[8px] sm:text-[9px] text-white/50 leading-snug mt-0.5 min-h-[28px] line-clamp-2">
                      {v.description}
                    </p>
                    <div className="my-2 flex items-center justify-center min-h-[52px]">
                      <VariantArt variantKey={v.key} />
                    </div>
                    <span className="mt-auto inline-flex items-center justify-center gap-1 w-full py-1.5 rounded-full text-[9px] sm:text-[10px] font-black uppercase tracking-wide bg-gradient-to-b from-[#0f5c32] via-[#0a4a28] to-[#06351c] text-white border border-[#1a7a45]/50 shadow-[0_2px_10px_rgba(6,53,28,0.55)] group-hover:brightness-110">
                      <Play className="w-3 h-3 fill-white" />
                      Play
                    </span>
                  </motion.button>
                );
              })}
            </div>
          </section>

          {/* Live tables */}
          <aside className="lg:col-span-3 order-1 lg:order-2 rounded-2xl border border-[#d4af37]/30 bg-black/45 backdrop-blur-md p-3 sm:p-4 min-h-[200px] lg:min-h-[420px] flex flex-col">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-display text-sm font-bold tracking-[0.14em] text-[#f5e6a8]">LIVE TABLES</h3>
              <button type="button" onClick={onRefresh} className="text-[10px] text-[#d4af37] font-bold flex items-center gap-1 cursor-pointer uppercase">
                <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} />
                View All
              </button>
            </div>

            <div className="flex-1 space-y-2 overflow-y-auto max-h-[320px] lg:max-h-none pr-0.5">
              {liveTables.length === 0 ? (
                <p className="text-center text-xs text-white/45 py-10">No live tables yet. Create one!</p>
              ) : (
                liveTables.map((table, idx) => {
                  const id = table.tableId || table.id;
                  const boot = ((table.bootAmountPaise || table.bootAmount || 0) / 100).toFixed(0);
                  const seated = table.currentPlayerCount ?? table.seatedCount ?? 0;
                  const max = table.maxPlayers || 6;
                  const live = isActiveHandStatus(table.status) || isCountdownStatus(table.status, table.countdownSeconds);
                  const canJoin = isJoinableStatus(table.status);
                  const isPrivate = table.tableType === 'PRIVATE' || Boolean(table.inviteCode);
                  return (
                    <div
                      key={id}
                      className="flex items-center gap-2.5 p-2 rounded-xl border border-[#d4af37]/15 bg-black/35"
                    >
                      <TableFeltIcon hue={TABLE_HUES[idx % TABLE_HUES.length]} />
                      <div className="min-w-0 flex-1">
                        <p className="text-[11px] font-bold text-white truncate">
                          Boot ₹{boot}
                          {live && <span className="ml-1.5 text-[8px] text-emerald-300 uppercase">Live</span>}
                        </p>
                        <p className="text-[9px] text-white/55 truncate">
                          {(table.gameVariant || 'CLASSIC').replaceAll('_', ' ')} · {seated}/{max} · {getTableStatusLabel(table.status)}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={!canJoin}
                        onClick={() => onJoinTableClick(id)}
                        className="px-2.5 py-1.5 rounded-lg bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a0505] text-[9px] font-extrabold uppercase disabled:opacity-35 cursor-pointer flex items-center gap-1"
                      >
                        {isPrivate && <Lock className="w-3 h-3" />}
                        Join
                      </button>
                    </div>
                  );
                })
              )}
            </div>

            <div className="mt-3 pt-3 border-t border-[#d4af37]/15 flex flex-wrap gap-1.5">
              {bootOptionsPaise.slice(0, 4).map((boot) => (
                <button
                  key={boot}
                  type="button"
                  onClick={() => onRequestQuickPlay(selectedVariant || 'CLASSIC', boot)}
                  disabled={Boolean(quickPlayLoading)}
                  className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#d4af37]/12 text-[#f5e6a8] border border-[#d4af37]/35 cursor-pointer hover:bg-[#d4af37]/25"
                >
                  ₹{(boot / 100).toFixed(0)}
                </button>
              ))}
            </div>
          </aside>
        </div>

        {/* ── Action row ── */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 sm:gap-3">
          <button
            type="button"
            onClick={onOpenCreatePrivate}
            className="flex flex-col sm:flex-row items-center justify-center gap-1.5 sm:gap-2 py-3 px-2 rounded-2xl border border-[#d4af37]/35 bg-gradient-to-b from-[#5a3a08] to-[#2a1804] text-[#f5e6a8] cursor-pointer hover:brightness-110"
          >
            <Users className="w-5 h-5" />
            <span className="text-[10px] font-extrabold uppercase tracking-wide">Create Table</span>
          </button>
          <button
            type="button"
            onClick={onOpenJoinCode}
            className="flex flex-col sm:flex-row items-center justify-center gap-1.5 sm:gap-2 py-3 px-2 rounded-2xl border border-sky-400/40 bg-gradient-to-b from-[#0c3a5a] to-[#061828] text-sky-100 cursor-pointer hover:brightness-110"
          >
            <UserPlus className="w-5 h-5" />
            <span className="text-[10px] font-extrabold uppercase tracking-wide">Join By Code</span>
          </button>
          <button
            type="button"
            onClick={() => onQuickPlay(selectedVariant || 'CLASSIC')}
            disabled={Boolean(quickPlayLoading)}
            className="col-span-2 sm:col-span-1 relative flex flex-col items-center justify-center gap-1 py-3.5 sm:py-3 px-3 rounded-2xl border-2 border-[#f5e6a8]/70 bg-gradient-to-b from-[#fff4c2] via-[#d4af37] to-[#8a6a12] text-[#1a0505] shadow-[0_0_28px_rgba(212,175,55,0.45)] cursor-pointer hover:brightness-105 disabled:opacity-60 order-first sm:order-none"
          >
            <span className="font-display text-sm sm:text-base font-extrabold tracking-wide uppercase">Play Now</span>
            <span className="text-[9px] font-bold uppercase tracking-wider opacity-80">
              {quickPlayLoading ? 'Finding…' : `Quick Play · ${variantLabel}`}
            </span>
            <span className="absolute -top-1 right-3 text-[#1a0505]/50 text-xs">♠ ♦</span>
          </button>
          <button
            type="button"
            onClick={onOpenCreatePrivate}
            className="flex flex-col sm:flex-row items-center justify-center gap-1.5 sm:gap-2 py-3 px-2 rounded-2xl border border-violet-400/40 bg-gradient-to-b from-[#3a1a4a] to-[#1a0a22] text-violet-100 cursor-pointer hover:brightness-110"
          >
            <Lock className="w-5 h-5" />
            <span className="text-[10px] font-extrabold uppercase tracking-wide">Private Table</span>
          </button>
          <button
            type="button"
            onClick={onOpenCreatePublic}
            className="flex flex-col sm:flex-row items-center justify-center gap-1.5 sm:gap-2 py-3 px-2 rounded-2xl border border-emerald-400/40 bg-gradient-to-b from-[#0d3d28] to-[#061c12] text-emerald-100 cursor-pointer hover:brightness-110"
          >
            <PlusCircle className="w-5 h-5" />
            <span className="text-[10px] font-extrabold uppercase tracking-wide">Create Public</span>
          </button>
        </div>
      </div>

      {/* Bottom nav */}
      <nav className="fixed bottom-0 inset-x-0 z-40 border-t border-[#d4af37]/30 bg-gradient-to-t from-[#0f0404] via-[#1a0808]/98 to-[#1a0808]/90 backdrop-blur-md pb-[env(safe-area-inset-bottom)]">
        <div className="max-w-[900px] mx-auto px-2 h-[64px] flex items-end justify-between gap-1">
          <button type="button" className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-[#d4af37] cursor-pointer">
            <Home className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">Home</span>
          </button>
          <button type="button" onClick={onOpenWallet} className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-white/55 hover:text-[#d4af37] cursor-pointer">
            <ShoppingCart className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">Store</span>
          </button>
          <div className="relative -top-4 flex flex-col items-center">
            <button
              type="button"
              onClick={() => onQuickPlay(selectedVariant || 'CLASSIC')}
              className="w-14 h-14 rounded-2xl rotate-45 bg-gradient-to-br from-[#fff4c2] via-[#d4af37] to-[#8a6a12] shadow-[0_0_24px_rgba(212,175,55,0.55)] border-2 border-[#f5e6a8] flex items-center justify-center cursor-pointer"
            >
              <span className="-rotate-45 font-display text-[10px] font-extrabold text-[#1a0505]">PLAY</span>
            </button>
          </div>
          <button
            type="button"
            onClick={() => document.getElementById('home-history')?.scrollIntoView({ behavior: 'smooth' })}
            className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-white/55 hover:text-[#d4af37] cursor-pointer"
          >
            <History className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">History</span>
          </button>
          <button type="button" onClick={onOpenProfile} className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-white/55 hover:text-[#d4af37] cursor-pointer">
            <UserIcon className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">Profile</span>
          </button>
        </div>
      </nav>

      <div id="home-history" className="sr-only" aria-hidden />
    </div>
  );
}
