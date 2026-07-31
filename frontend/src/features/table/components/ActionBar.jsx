import React from 'react';
import { Eye, EyeOff, X, Layers, Sparkles, ArrowUpRight } from 'lucide-react';

/** Teen Patti Gold–style solid action button */
function ActionBtn({
  label,
  onClick,
  disabled,
  bg,
  rim,
  glow,
  icon,
  large = false,
  dark = false,
}) {
  const text = dark ? 'text-[#1a1205]' : 'text-white';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`
        relative overflow-hidden flex flex-row items-center justify-center gap-1.5
        rounded-xl font-extrabold uppercase tracking-wide
        transition-all disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer
        active:scale-[0.97] hover:brightness-115
        ${text}
        ${large
          ? 'min-w-[130px] sm:min-w-[160px] h-[36px] sm:h-[40px] px-5 sm:px-7 text-[11px] sm:text-[12px]'
          : 'min-w-[96px] sm:min-w-[112px] h-[34px] sm:h-[38px] px-4 sm:px-5 text-[10px] sm:text-[11px]'}
      `}
      style={{
        background: bg,
        boxShadow: `
          0 0 0 1.5px ${rim || 'rgba(212,175,55,0.55)'},
          0 0 16px ${glow || 'rgba(212,175,55,0.35)'},
          0 0 28px ${glow || 'rgba(212,175,55,0.18)'},
          0 3px 10px rgba(0,0,0,0.45),
          inset 0 1px 0 rgba(255,255,255,0.2),
          inset 0 -2px 0 rgba(0,0,0,0.22)
        `,
      }}
    >
      <span className="relative drop-shadow-sm shrink-0">{icon}</span>
      <span className="relative drop-shadow-sm leading-none whitespace-nowrap">{label}</span>
    </button>
  );
}

function ChipIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="10" fill="#fff7d6" stroke="#7a5a12" strokeWidth="1" />
      <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.4" strokeDasharray="2 1.5" />
      <circle cx="12" cy="12" r="3" fill="#c9a227" />
    </svg>
  );
}

/** Classic Teen Patti Gold palette — brighter / shinier */
const THEME = {
  blind: {
    bg: 'linear-gradient(180deg, #c58cff 0%, #9b59d6 28%, #6b2d9b 70%, #4a1a70 100%)',
    rim: 'rgba(230,190,255,0.7)',
    glow: 'rgba(155,89,214,0.55)',
  },
  seen: {
    bg: 'linear-gradient(180deg, #6dffb0 0%, #2ecc71 28%, #1a8f4a 70%, #0f5c30 100%)',
    rim: 'rgba(160,255,200,0.65)',
    glow: 'rgba(46,204,113,0.5)',
  },
  pack: {
    bg: 'linear-gradient(180deg, #ff7a88 0%, #e74c5c 28%, #b71c2c 70%, #7a101c 100%)',
    rim: 'rgba(255,170,180,0.65)',
    glow: 'rgba(231,76,92,0.5)',
  },
  chaal: {
    bg: 'linear-gradient(180deg, #fff4c2 0%, #ffe9a0 20%, #e6c35c 50%, #d4af37 78%, #a67c00 100%)',
    rim: 'rgba(255,240,180,0.95)',
    glow: 'rgba(212,175,55,0.65)',
  },
  raise: {
    bg: 'linear-gradient(180deg, #ffe08a 0%, #ffc857 30%, #e8a317 70%, #b8770a 100%)',
    rim: 'rgba(255,220,140,0.85)',
    glow: 'rgba(245,158,11,0.55)',
  },
  side: {
    bg: 'linear-gradient(180deg, #8ec5ff 0%, #4d9fff 28%, #1e6fd9 70%, #0d4aa8 100%)',
    rim: 'rgba(170,210,255,0.7)',
    glow: 'rgba(77,159,255,0.5)',
  },
  show: {
    bg: 'linear-gradient(180deg, #fff4c2 0%, #ffe9a0 25%, #d4af37 65%, #a67c00 100%)',
    rim: 'rgba(255,236,180,0.9)',
    glow: 'rgba(212,175,55,0.6)',
  },
};

export default function ActionBar({
  canAct,
  sendPlayerAction,
  actionLoading,
  blindBetRupees,
  chaalBetRupees,
  requiredBetRupees,
  minRaiseBetRupees,
  sideShowCost,
  showCost,
  wsError,
  variantPhase,
  auctionHighBidPaise,
  auctionMinBidPaise,
  myCards,
  onDiscardCard,
}) {
  const blindEnabled = canAct('PLAY_BLIND') || canAct('BLIND');
  const chaalEnabled = canAct('CHAAL');
  const seeEnabled = canAct('SEE_CARDS');
  const packEnabled = canAct('PACK');
  const sideEnabled = canAct('SIDE_SHOW_REQUEST');
  const showEnabled = canAct('SHOW');
  const raiseEnabled = canAct('RAISE');
  const discardEnabled = canAct('DISCARD_CARD');
  const auctionBidEnabled = canAct('AUCTION_BID');
  const auctionPassEnabled = canAct('AUCTION_PASS');
  const showAccept = canAct('SHOW_ACCEPT');
  const showReject = canAct('SHOW_REJECT');
  const sideAccept = canAct('SIDE_SHOW_ACCEPT');
  const sideReject = canAct('SIDE_SHOW_REJECT');

  const auctionMinRupees = (auctionMinBidPaise || 0) / 100;
  const auctionHighRupees = (auctionHighBidPaise || 0) / 100;
  const suggestedBidRupees = auctionHighRupees > 0
    ? auctionHighRupees + (blindBetRupees || auctionMinRupees || 1)
    : (auctionMinRupees || blindBetRupees || 1);

  return (
    <div className="relative z-30 w-full">
      {wsError && (
        <div className="mb-2 text-center text-xs text-rose-300 font-semibold drop-shadow-md">{wsError}</div>
      )}

      {variantPhase === 'DISCARD' && discardEnabled && (
        <div className="mb-3 text-center">
          <p className="text-[11px] text-[#f5e6a8] font-bold uppercase tracking-wide mb-2 drop-shadow">
            Discard one card
          </p>
          {Array.isArray(myCards) && myCards.length > 3 ? (
            <div className="flex flex-wrap justify-center gap-2">
              {myCards.map((card, idx) => (
                <button
                  key={`discard-${idx}`}
                  type="button"
                  disabled={actionLoading}
                  onClick={() => onDiscardCard ? onDiscardCard(idx) : sendPlayerAction('DISCARD_CARD', 1, { cardIndex: idx })}
                  className="px-3 py-1.5 rounded-lg bg-black/50 text-white text-xs font-bold border border-[#d4af37]/60 hover:bg-[#d4af37]/20 disabled:opacity-40"
                >
                  {card?.rank || card?.value || `Card ${idx + 1}`}
                </button>
              ))}
            </div>
          ) : (
            <p className="text-[10px] text-white/70">See your cards, then pick one to discard</p>
          )}
        </div>
      )}

      {variantPhase === 'AUCTION' && (auctionBidEnabled || auctionPassEnabled) && (
        <div className="mb-3 flex flex-wrap justify-center gap-3 items-center">
          <p className="text-[10px] text-white/80 drop-shadow">
            Auction · High: ₹{auctionHighRupees.toFixed(0)} · Min bid: ₹{auctionMinRupees.toFixed(0)}
          </p>
          {auctionBidEnabled && (
            <ActionBtn
              label={`Bid ₹${Number(suggestedBidRupees).toFixed(0)}`}
              onClick={() => sendPlayerAction('AUCTION_BID', 1, { amountPaise: Math.round(suggestedBidRupees * 100) })}
              disabled={actionLoading}
              bg={THEME.chaal.bg}
              rim={THEME.chaal.rim}
              glow={THEME.chaal.glow}
              dark
              icon={<ChipIcon />}
            />
          )}
          {auctionPassEnabled && (
            <ActionBtn
              label="Pass"
              onClick={() => sendPlayerAction('AUCTION_PASS')}
              disabled={actionLoading}
              bg={THEME.pack.bg}
              rim={THEME.pack.rim}
              glow={THEME.pack.glow}
              icon={<X className="w-4 h-4" />}
            />
          )}
        </div>
      )}

      {(showAccept || showReject || sideAccept || sideReject) && (
        <div className="mb-3 flex flex-wrap justify-center gap-3">
          {showAccept && (
            <ActionBtn
              label="Accept Show"
              onClick={() => sendPlayerAction('SHOW_ACCEPT')}
              disabled={actionLoading}
              bg={THEME.show.bg}
              rim={THEME.show.rim}
              glow={THEME.show.glow}
              dark
              icon={<Sparkles className="w-4 h-4" />}
            />
          )}
          {showReject && (
            <ActionBtn
              label="Decline Show"
              onClick={() => sendPlayerAction('SHOW_REJECT')}
              disabled={actionLoading}
              bg={THEME.pack.bg}
              rim={THEME.pack.rim}
              glow={THEME.pack.glow}
              icon={<X className="w-4 h-4" />}
            />
          )}
          {sideAccept && (
            <ActionBtn
              label="Accept Side"
              onClick={() => sendPlayerAction('SIDE_SHOW_ACCEPT')}
              disabled={actionLoading}
              bg={THEME.seen.bg}
              rim={THEME.seen.rim}
              glow={THEME.seen.glow}
              icon={<Eye className="w-4 h-4" />}
            />
          )}
          {sideReject && (
            <ActionBtn
              label="Reject Side"
              onClick={() => sendPlayerAction('SIDE_SHOW_REJECT')}
              disabled={actionLoading}
              bg={THEME.pack.bg}
              rim={THEME.pack.rim}
              glow={THEME.pack.glow}
              icon={<X className="w-4 h-4" />}
            />
          )}
        </div>
      )}

      <div className="flex flex-nowrap items-center justify-center gap-3 sm:gap-4 px-2 overflow-x-auto pb-0.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {!(variantPhase === 'DISCARD' && discardEnabled) && !(variantPhase === 'AUCTION' && (auctionBidEnabled || auctionPassEnabled)) && (
        <>
        <ActionBtn
          label="Blind"
          onClick={() => sendPlayerAction('BLIND')}
          disabled={!blindEnabled || actionLoading}
          bg={THEME.blind.bg}
          rim={THEME.blind.rim}
          glow={THEME.blind.glow}
          icon={<EyeOff className="w-4 h-4" />}
        />
        <ActionBtn
          label="Seen"
          onClick={() => sendPlayerAction('SEE_CARDS')}
          disabled={!seeEnabled || actionLoading}
          bg={THEME.seen.bg}
          rim={THEME.seen.rim}
          glow={THEME.seen.glow}
          icon={<Eye className="w-4 h-4" />}
        />
        <ActionBtn
          label="Pack"
          onClick={() => sendPlayerAction('PACK')}
          disabled={!packEnabled || actionLoading}
          bg={THEME.pack.bg}
          rim={THEME.pack.rim}
          glow={THEME.pack.glow}
          icon={<X className="w-4 h-4 stroke-[3]" />}
        />
        <ActionBtn
          large
          label={`Chaal ₹${Number(
            blindEnabled ? blindBetRupees : (chaalBetRupees || requiredBetRupees || 0),
          ).toFixed(0)}`}
          onClick={() => sendPlayerAction(blindEnabled ? 'BLIND' : 'CHAAL')}
          disabled={(!blindEnabled && !chaalEnabled) || actionLoading}
          bg={THEME.chaal.bg}
          rim={THEME.chaal.rim}
          glow={THEME.chaal.glow}
          dark
          icon={<ChipIcon />}
        />
        {raiseEnabled && (
          <ActionBtn
            label={`Raise ₹${Number(minRaiseBetRupees || 0).toFixed(0)}`}
            onClick={() => sendPlayerAction('RAISE')}
            disabled={actionLoading}
            bg={THEME.raise.bg}
            rim={THEME.raise.rim}
            glow={THEME.raise.glow}
            dark
            icon={<ArrowUpRight className="w-4 h-4" />}
          />
        )}
        <ActionBtn
          label="Side Show"
          onClick={() => sendPlayerAction('SIDE_SHOW_REQUEST')}
          disabled={!sideEnabled || actionLoading}
          bg={THEME.side.bg}
          rim={THEME.side.rim}
          glow={THEME.side.glow}
          icon={<Layers className="w-4 h-4" />}
        />
        <ActionBtn
          label="Show"
          onClick={() => sendPlayerAction('SHOW')}
          disabled={!showEnabled || actionLoading}
          bg={THEME.show.bg}
          rim={THEME.show.rim}
          glow={THEME.show.glow}
          dark
          icon={<Sparkles className="w-4 h-4" />}
        />
        </>
        )}
      </div>

      {(sideEnabled || showEnabled) && (
        <p className="mt-1.5 text-center text-[9px] text-white/60 drop-shadow">
          {sideEnabled && <>Side Show ₹{Number(sideShowCost || 0).toFixed(0)} </>}
          {showEnabled && <>· Show ₹{Number(showCost || 0).toFixed(0)}</>}
        </p>
      )}
    </div>
  );
}
