import React from 'react';
import { motion } from 'framer-motion';
import {
  PlusCircle, Key, Lock, Play, Trophy, Gift, Crown, HelpCircle,
  UserPlus, Home, ShoppingCart, History, User as UserIcon, AlertCircle,
  RefreshCw, Zap,
} from 'lucide-react';
import { isActiveHandStatus, getTableStatusLabel, isCountdownStatus, isJoinableStatus } from '@/features/table/tableUtils';

const HERO_IMG =
  'https://res.cloudinary.com/dsafvwkrf/image/upload/v1785347801/Untitled_1920_x_1080_px_1_1_qgc3s6.webp';

const ROOM_BG =
  'https://res.cloudinary.com/dsafvwkrf/image/upload/v1785347148/8fdf3e41-a727-4785-b6db-48b163f39f69.png';

function ModeBtn({ color, icon, title, subtitle, onClick }) {
  const tones = {
    gold: 'from-[#5a3a08] via-[#3d2505] to-[#2a1804] border-[#d4af37]/55 shadow-[0_0_18px_rgba(212,175,55,0.2)]',
    green: 'from-[#0d3d28] via-[#0a2e1e] to-[#061c12] border-emerald-500/45 shadow-[0_0_18px_rgba(16,185,129,0.18)]',
    purple: 'from-[#3a1a4a] via-[#2a1035] to-[#1a0a22] border-purple-400/45 shadow-[0_0_18px_rgba(168,85,247,0.18)]',
  };
  const iconBg = {
    gold: 'from-[#f5e6a8] to-[#d4af37] text-[#1a0505]',
    green: 'from-emerald-300 to-emerald-600 text-white',
    purple: 'from-purple-300 to-purple-700 text-white',
  };
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full flex items-center gap-3 p-3 rounded-2xl border bg-gradient-to-r ${tones[color]} text-left cursor-pointer hover:brightness-110 transition-all`}
    >
      <div className={`w-12 h-12 rounded-full bg-gradient-to-b ${iconBg[color]} flex items-center justify-center shrink-0 shadow-lg`}>
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-[12px] font-extrabold uppercase tracking-wide text-[#f5e6a8]">{title}</p>
        <p className="text-[10px] text-white/65 leading-snug mt-0.5">{subtitle}</p>
      </div>
    </button>
  );
}

function FeatureTile({ icon, title, subtitle, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-col items-center justify-center gap-1.5 p-3 rounded-2xl bg-gradient-to-b from-[#5a1212] to-[#2a0808] border border-[#d4af37]/25 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] hover:border-[#d4af37]/55 cursor-pointer transition-all"
    >
      <div className="w-10 h-10 rounded-xl bg-black/35 border border-[#d4af37]/30 text-[#d4af37] flex items-center justify-center">
        {icon}
      </div>
      <p className="text-[10px] font-extrabold uppercase tracking-wide text-[#f5e6a8]">{title}</p>
      <p className="text-[9px] text-white/55">{subtitle}</p>
    </button>
  );
}

/**
 * Casino home layout matching Teen Patti Gold–style reference.
 * Presentation only — all actions come from LobbyView handlers.
 */
export default function HomeCasinoScreen({
  user,
  tables = [],
  bootOptionsPaise = [1000],
  loading,
  error,
  setError,
  activeGame,
  privateInvitations = [],
  quickPlayLoading,
  onQuickPlay,
  onJoinTable,
  onJoinTableClick,
  onOpenCreatePublic,
  onOpenCreatePrivate,
  onOpenJoinCode,
  onAcceptInvite,
  onRefresh,
  onOpenWallet,
  onOpenLeaderboard,
  onOpenProfile,
  onResumeGame,
}) {
  const liveTables = tables.slice(0, 6);
  const defaultBoot = bootOptionsPaise[0] || 1000;

  return (
    <div className="relative min-h-full pb-24">
      {/* Damask / casino room atmosphere */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <img src={ROOM_BG} alt="" className="absolute inset-0 w-full h-full object-cover opacity-35" draggable={false} />
        <div className="absolute inset-0 bg-gradient-to-b from-[#1a0505]/75 via-[#1a0505]/88 to-[#120303]" />
        <div
          className="absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, #d4af37 0.6px, transparent 0.7px), radial-gradient(circle at 80% 60%, #d4af37 0.6px, transparent 0.7px)',
            backgroundSize: '28px 28px',
          }}
        />
      </div>

      <div className="relative z-10 max-w-[1400px] mx-auto px-3 sm:px-5 pt-3 sm:pt-4 space-y-4">
        {/* Active match / invites */}
        {activeGame && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3.5 rounded-2xl border border-[#d4af37]/50 bg-black/50 backdrop-blur-md"
          >
            <div className="flex items-center gap-3 text-center sm:text-left">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
              <p className="text-sm text-white">
                Seated at <span className="text-[#d4af37] font-bold">{activeGame.tableName}</span>
                {' '}({activeGame.seatedCount}/{activeGame.maxPlayers})
              </p>
            </div>
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
          <div className="p-3.5 rounded-2xl border border-[#d4af37]/35 bg-black/45 space-y-2">
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

        {/* Main 3-column stage */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4 items-stretch">
          {/* Left modes */}
          <div className="lg:col-span-3 flex flex-col gap-2.5 order-2 lg:order-1">
            <ModeBtn
              color="gold"
              icon={<PlusCircle className="w-6 h-6" />}
              title="Create Table"
              subtitle="Create a new private table and invite friends."
              onClick={onOpenCreatePrivate}
            />
            <ModeBtn
              color="green"
              icon={<UserPlus className="w-6 h-6" />}
              title="Join By Code"
              subtitle="Enter table code and join private table."
              onClick={onOpenJoinCode}
            />
            <ModeBtn
              color="purple"
              icon={<Lock className="w-6 h-6" />}
              title="Private Table"
              subtitle="Create invite-only private room."
              onClick={onOpenCreatePrivate}
            />
            <button
              type="button"
              onClick={onOpenCreatePublic}
              className="mt-1 w-full py-2.5 rounded-xl border border-emerald-500/40 bg-emerald-950/40 text-emerald-300 text-[11px] font-bold uppercase tracking-wide cursor-pointer hover:bg-emerald-900/50"
            >
              + Create Public Table
            </button>
          </div>

          {/* Center Play Now hero */}
          <div className="lg:col-span-5 order-1 lg:order-2">
            <button
              type="button"
              onClick={() => onQuickPlay(defaultBoot)}
              disabled={Boolean(quickPlayLoading)}
              className="relative w-full h-full min-h-[220px] sm:min-h-[280px] rounded-3xl overflow-hidden border-2 border-[#d4af37]/55 shadow-[0_0_40px_rgba(212,175,55,0.25)] cursor-pointer group text-left"
            >
              <img
                src={HERO_IMG}
                alt="Play Teen Patti"
                className="absolute inset-0 w-full h-full object-cover object-top group-hover:scale-[1.03] transition-transform duration-700"
                draggable={false}
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/25 to-transparent" />
              <div className="absolute inset-x-0 bottom-0 p-5 sm:p-7">
                <p className="font-display text-2xl sm:text-3xl md:text-4xl font-extrabold tracking-wide text-transparent bg-clip-text bg-gradient-to-b from-[#fff4c2] via-[#d4af37] to-[#a67c00] drop-shadow">
                  PLAY NOW
                </p>
                <p className="font-display text-lg sm:text-xl text-[#f5e6a8]/90 tracking-[0.15em]">TEEN PATTI</p>
                <p className="mt-2 text-[11px] text-white/70 flex items-center gap-1.5">
                  <Zap className="w-3.5 h-3.5 text-[#d4af37]" />
                  {quickPlayLoading ? 'Finding table…' : `Quick play · Boot ₹${(defaultBoot / 100).toFixed(0)}`}
                </p>
              </div>
            </button>
          </div>

          {/* Right live tables */}
          <div className="lg:col-span-4 order-3 rounded-3xl border border-[#d4af37]/30 bg-black/45 backdrop-blur-md p-3 sm:p-4 flex flex-col min-h-[220px]">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-display text-sm sm:text-base font-bold tracking-[0.12em] text-[#f5e6a8]">LIVE TABLES</h3>
              <button type="button" onClick={onRefresh} className="text-[10px] text-[#d4af37] font-bold flex items-center gap-1 cursor-pointer">
                <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} />
                View All
              </button>
            </div>

            <div className="flex-1 space-y-2 overflow-y-auto max-h-[280px] pr-0.5">
              {liveTables.length === 0 ? (
                <p className="text-center text-xs text-white/50 py-10">No live tables yet. Create one!</p>
              ) : (
                liveTables.map((table) => {
                  const id = table.tableId || table.id;
                  const boot = ((table.bootAmountPaise || table.bootAmount || 0) / 100).toFixed(0);
                  const seated = table.currentPlayerCount ?? table.seatedCount ?? 0;
                  const max = table.maxPlayers || 6;
                  const live = isActiveHandStatus(table.status) || isCountdownStatus(table.status, table.countdownSeconds);
                  const canJoin = isJoinableStatus(table.status);
                  return (
                    <div
                      key={id}
                      className="flex items-center gap-2.5 p-2.5 rounded-xl bg-gradient-to-r from-[#3a0c0c]/90 to-black/50 border border-[#d4af37]/20"
                    >
                      <div className={`w-9 h-9 rounded-lg flex items-center justify-center text-sm shrink-0 ${live ? 'bg-emerald-900/60 text-emerald-300' : 'bg-rose-900/50 text-rose-300'}`}>
                        ♠
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <p className="text-[11px] font-bold text-white truncate">Table #{String(id).slice(-5)}</p>
                          {live && (
                            <span className="text-[8px] font-black uppercase px-1 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">Live</span>
                          )}
                        </div>
                        <p className="text-[9px] text-white/55 truncate">
                          Boot ₹{boot} · {seated}/{max} Players · {getTableStatusLabel(table.status)}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={!canJoin}
                        onClick={() => onJoinTableClick(id)}
                        className="px-3 py-1.5 rounded-lg bg-gradient-to-b from-[#8b1e2d] to-[#5a1018] border border-rose-400/30 text-[10px] font-extrabold text-white uppercase disabled:opacity-35 cursor-pointer hover:brightness-110"
                      >
                        Join
                      </button>
                    </div>
                  );
                })
              )}
            </div>

            {/* Quick boot chips */}
            <div className="mt-3 pt-3 border-t border-[#d4af37]/15 flex flex-wrap gap-1.5">
              {bootOptionsPaise.slice(0, 4).map((boot) => (
                <button
                  key={boot}
                  type="button"
                  onClick={() => onQuickPlay(boot)}
                  disabled={quickPlayLoading === boot}
                  className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#d4af37]/15 text-[#f5e6a8] border border-[#d4af37]/35 cursor-pointer hover:bg-[#d4af37]/25"
                >
                  ₹{(boot / 100).toFixed(0)}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Feature grid */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 sm:gap-3">
          <FeatureTile icon={<Trophy className="w-5 h-5" />} title="Tournament" subtitle="Win Big Prizes" onClick={onOpenLeaderboard} />
          <FeatureTile icon={<Gift className="w-5 h-5" />} title="Daily Bonus" subtitle="Claim Now" onClick={onOpenWallet} />
          <FeatureTile icon={<UserPlus className="w-5 h-5" />} title="Refer & Earn" subtitle="Invite & Earn" onClick={onOpenProfile} />
          <FeatureTile icon={<Crown className="w-5 h-5" />} title="Leaderboard" subtitle="Top Players" onClick={onOpenLeaderboard} />
          <FeatureTile icon={<HelpCircle className="w-5 h-5" />} title="How To Play" subtitle="Learn Teen Patti" onClick={() => {}} />
        </div>
      </div>

      {/* Bottom nav */}
      <nav className="fixed bottom-0 inset-x-0 z-40 border-t border-[#d4af37]/35 bg-gradient-to-t from-[#120303] via-[#1a0505]/98 to-[#1a0505]/92 backdrop-blur-md pb-[env(safe-area-inset-bottom)]">
        <div className="max-w-[900px] mx-auto px-4 h-[64px] flex items-end justify-between gap-1">
          <button type="button" className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-[#d4af37] cursor-pointer">
            <Home className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">Home</span>
          </button>
          <button type="button" onClick={onOpenWallet} className="flex-1 flex flex-col items-center gap-0.5 pb-2.5 text-white/55 hover:text-[#d4af37] cursor-pointer">
            <ShoppingCart className="w-5 h-5" />
            <span className="text-[9px] font-bold uppercase">Store</span>
          </button>

          <div className="relative -top-5 flex flex-col items-center">
            <div className="absolute -top-5 flex gap-0.5 text-[#d4af37] text-sm opacity-90 pointer-events-none">
              <span className="-rotate-12">A♠</span>
              <span>A♥</span>
              <span className="rotate-12">A♦</span>
            </div>
            <button
              type="button"
              onClick={() => onQuickPlay(defaultBoot)}
              className="w-[68px] h-[68px] rounded-2xl rotate-45 bg-gradient-to-br from-[#fff4c2] via-[#d4af37] to-[#8a6a12] shadow-[0_0_28px_rgba(212,175,55,0.55)] border-2 border-[#f5e6a8] flex items-center justify-center cursor-pointer hover:brightness-110"
            >
              <span className="-rotate-45 font-display text-[11px] font-extrabold text-[#1a0505] tracking-wide">PLAY</span>
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
