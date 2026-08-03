import React, { useMemo } from 'react';
import { Eye, EyeOff, X, Layers, Sparkles, ArrowUpRight } from 'lucide-react';
import { evaluateHandLabel } from '../handRank';

/** Gold / burgundy casino theme — matches table felt UI */
const THEME = {
  gold: {
    bg: 'linear-gradient(180deg, #fff4c2 0%, #f5e6a8 18%, #d4af37 55%, #a67c00 100%)',
    rim: 'rgba(245,230,168,0.95)',
    glow: 'rgba(212,175,55,0.45)',
    dark: true,
  },
  goldSoft: {
    bg: 'linear-gradient(180deg, #3a2a12 0%, #2a1c0a 45%, #1a1006 100%)',
    rim: 'rgba(212,175,55,0.65)',
    glow: 'rgba(212,175,55,0.25)',
    dark: false,
  },
  burgundy: {
    bg: 'linear-gradient(180deg, #8b1a28 0%, #5c1018 55%, #3a0a10 100%)',
    rim: 'rgba(212,175,55,0.45)',
    glow: 'rgba(139,26,40,0.35)',
    dark: false,
  },
  bronze: {
    bg: 'linear-gradient(180deg, #e8c878 0%, #c9a227 50%, #8a6a1a 100%)',
    rim: 'rgba(245,230,168,0.8)',
    glow: 'rgba(201,162,39,0.4)',
    dark: true,
  },
};

function ActionBtn({
  label,
  onClick,
  disabled,
  tone = 'gold',
  icon,
  large = false,
}) {
  const t = THEME[tone] || THEME.gold;
  const text = t.dark ? 'text-[#1a1205]' : 'text-[#f5e6a8]';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`
        relative overflow-hidden flex flex-row items-center justify-center gap-1.5
        rounded-xl font-extrabold uppercase tracking-wide
        transition-all disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer
        active:scale-[0.97] hover:brightness-110
        ${text}
        ${large
          ? 'min-w-[96px] sm:min-w-[110px] h-[28px] sm:h-[30px] px-3 sm:px-3.5 text-[9px] sm:text-[10px]'
          : 'min-w-[72px] sm:min-w-[82px] h-[26px] sm:h-[28px] px-2.5 sm:px-3 text-[8px] sm:text-[9px]'}
      `}
      style={{
        background: t.bg,
        boxShadow: `
          0 0 0 1px ${t.rim},
          0 0 8px ${t.glow},
          0 2px 6px rgba(0,0,0,0.45),
          inset 0 1px 0 rgba(255,255,255,0.16),
          inset 0 -1px 0 rgba(0,0,0,0.22)
        `,
      }}
    >
      <span className="relative drop-shadow-sm shrink-0 scale-90">{icon}</span>
      <span className="relative drop-shadow-sm leading-none whitespace-nowrap">{label}</span>
    </button>
  );
}

function ChipIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="10" fill="#fff7d6" stroke="#7a5a12" strokeWidth="1" />
      <circle cx="12" cy="12" r="6" fill="none" stroke="#7a5a12" strokeWidth="1.4" strokeDasharray="2 1.5" />
      <circle cx="12" cy="12" r="3" fill="#c9a227" />
    </svg>
  );
}

export default function ActionBar({
  canAct,
  sendPlayerAction,
  actionLoading,
  myStatus,
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
  const isBlind = myStatus === 'BLIND';
  const isSeen = myStatus === 'SEEN';
  const isPacked = myStatus === 'PACKED';

  const blindEnabled = isBlind && (canAct('PLAY_BLIND') || canAct('BLIND'));
  const chaalEnabled = isSeen && canAct('CHAAL');
  const seeEnabled = isBlind && canAct('SEE_CARDS');
  const packEnabled = canAct('PACK');
  const sideEnabled = isSeen && canAct('SIDE_SHOW_REQUEST');
  const showEnabled = canAct('SHOW');
  const raiseEnabled = canAct('RAISE');
  const discardEnabled = canAct('DISCARD_CARD');
  const auctionBidEnabled = canAct('AUCTION_BID');
  const auctionPassEnabled = canAct('AUCTION_PASS');
  const showAccept = canAct('SHOW_ACCEPT');
  const showReject = canAct('SHOW_REJECT');
  const sideAccept = canAct('SIDE_SHOW_ACCEPT');
  const sideReject = canAct('SIDE_SHOW_REJECT');

  const handInfo = useMemo(() => {
    if (!isSeen || isPacked) return null;
    return evaluateHandLabel(myCards);
  }, [isSeen, isPacked, myCards]);

  const auctionMinRupees = (auctionMinBidPaise || 0) / 100;
  const auctionHighRupees = (auctionHighBidPaise || 0) / 100;
  const suggestedBidRupees = auctionHighRupees > 0
    ? auctionHighRupees + (blindBetRupees || auctionMinRupees || 1)
    : (auctionMinRupees || blindBetRupees || 1);

  if (isPacked) return null;

  return (
    <div className="relative z-30 w-full">
      {wsError && (
        <div className="mb-2 text-center text-xs text-rose-300 font-semibold drop-shadow-md">{wsError}</div>
      )}

      {/* Only the SEEN player sees their hand category */}
      {handInfo && (
        <div className="mb-2.5 flex justify-center">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/65 border border-[#d4af37]/55 text-[11px] font-extrabold uppercase tracking-wider text-[#f5e6a8] shadow-[0_0_16px_rgba(212,175,55,0.25)]">
            <Sparkles className="w-3.5 h-3.5 text-[#d4af37]" />
            Your hand · {handInfo.label}
          </span>
        </div>
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
                  onClick={() => (onDiscardCard ? onDiscardCard(idx) : sendPlayerAction('DISCARD_CARD', 1, { cardIndex: idx }))}
                  className="px-3 py-1.5 rounded-lg bg-black/55 text-[#f5e6a8] text-xs font-bold border border-[#d4af37]/55 hover:bg-[#d4af37]/15 disabled:opacity-40"
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
          <p className="text-[10px] text-[#f5e6a8]/80 drop-shadow">
            Auction · High: ₹{auctionHighRupees.toFixed(0)} · Min bid: ₹{auctionMinRupees.toFixed(0)}
          </p>
          {auctionBidEnabled && (
            <ActionBtn
              label={`Bid ₹${Number(suggestedBidRupees).toFixed(0)}`}
              onClick={() => sendPlayerAction('AUCTION_BID', 1, { amountPaise: Math.round(suggestedBidRupees * 100) })}
              disabled={actionLoading}
              tone="gold"
              icon={<ChipIcon />}
            />
          )}
          {auctionPassEnabled && (
            <ActionBtn
              label="Pass"
              onClick={() => sendPlayerAction('AUCTION_PASS')}
              disabled={actionLoading}
              tone="burgundy"
              icon={<X className="w-3 h-3" />}
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
              tone="gold"
              icon={<Sparkles className="w-3 h-3" />}
            />
          )}
          {showReject && (
            <ActionBtn
              label="Decline Show"
              onClick={() => sendPlayerAction('SHOW_REJECT')}
              disabled={actionLoading}
              tone="burgundy"
              icon={<X className="w-3 h-3" />}
            />
          )}
          {sideAccept && (
            <ActionBtn
              label="Accept Side"
              onClick={() => sendPlayerAction('SIDE_SHOW_ACCEPT')}
              disabled={actionLoading}
              tone="bronze"
              icon={<Eye className="w-3 h-3" />}
            />
          )}
          {sideReject && (
            <ActionBtn
              label="Reject Side"
              onClick={() => sendPlayerAction('SIDE_SHOW_REJECT')}
              disabled={actionLoading}
              tone="burgundy"
              icon={<X className="w-3 h-3" />}
            />
          )}
        </div>
      )}

      <div className="flex flex-nowrap items-center justify-center gap-1.5 sm:gap-2 px-2 overflow-x-auto pb-0.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {!(variantPhase === 'DISCARD' && discardEnabled) && !(variantPhase === 'AUCTION' && (auctionBidEnabled || auctionPassEnabled)) && (
          <>
            {/* Blind only while still BLIND — never show Chaal yet */}
            {isBlind && (
              <ActionBtn
                large
                label={`Blind ₹${Number(blindBetRupees || requiredBetRupees || 0).toFixed(0)}`}
                onClick={() => sendPlayerAction('BLIND')}
                disabled={!blindEnabled || actionLoading}
                tone="gold"
                icon={<EyeOff className="w-3 h-3" />}
              />
            )}

            {/* Chaal only after SEEN — never while Blind */}
            {isSeen && (
              <ActionBtn
                large
                label={`Chaal ₹${Number(chaalBetRupees || requiredBetRupees || 0).toFixed(0)}`}
                onClick={() => sendPlayerAction('CHAAL')}
                disabled={!chaalEnabled || actionLoading}
                tone="gold"
                icon={<ChipIcon />}
              />
            )}

            {seeEnabled && (
              <ActionBtn
                label="See Cards"
                onClick={() => sendPlayerAction('SEE_CARDS')}
                disabled={actionLoading}
                tone="goldSoft"
                icon={<Eye className="w-3 h-3" />}
              />
            )}

            <ActionBtn
              label="Pack"
              onClick={() => sendPlayerAction('PACK')}
              disabled={!packEnabled || actionLoading}
              tone="burgundy"
              icon={<X className="w-3 h-3 stroke-[3]" />}
            />

            {raiseEnabled && (
              <ActionBtn
                label={`Raise ₹${Number(minRaiseBetRupees || 0).toFixed(0)}`}
                onClick={() => sendPlayerAction('RAISE')}
                disabled={actionLoading}
                tone="bronze"
                icon={<ArrowUpRight className="w-3 h-3" />}
              />
            )}

            {sideEnabled && (
              <ActionBtn
                label="Side Show"
                onClick={() => sendPlayerAction('SIDE_SHOW_REQUEST')}
                disabled={actionLoading}
                tone="goldSoft"
                icon={<Layers className="w-3 h-3" />}
              />
            )}

            {showEnabled && (
              <ActionBtn
                label="Show"
                onClick={() => sendPlayerAction('SHOW')}
                disabled={actionLoading}
                tone="gold"
                icon={<Sparkles className="w-3 h-3" />}
              />
            )}
          </>
        )}
      </div>

      {(sideEnabled || showEnabled) && (
        <p className="mt-1.5 text-center text-[9px] text-[#f5e6a8]/55 drop-shadow">
          {sideEnabled && <>Side Show ₹{Number(sideShowCost || 0).toFixed(0)} </>}
          {showEnabled && <>· Show ₹{Number(showCost || 0).toFixed(0)}</>}
        </p>
      )}
    </div>
  );
}
