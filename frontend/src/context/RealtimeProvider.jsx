import React, { useEffect, useRef } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';
import stompService from '@/shared/api/stompService';
import { RealTimeEventType } from '@/shared/api/realtimeEvents';

/**
 * Maintains authenticated STOMP connection for lobby/wallet/announcement events.
 */
export function RealtimeProvider({ children }) {
  const { accessToken, user, refreshWalletBalance } = useAuth();
  const { addNotification } = useGame();
  const refreshWalletBalanceRef = useRef(refreshWalletBalance);
  const addNotificationRef = useRef(addNotification);

  useEffect(() => {
    refreshWalletBalanceRef.current = refreshWalletBalance;
  }, [refreshWalletBalance]);

  useEffect(() => {
    addNotificationRef.current = addNotification;
  }, [addNotification]);

  useEffect(() => {
    if (!accessToken) {
      stompService.disconnect();
      return undefined;
    }

    stompService.connect(accessToken, user?.id);

    const unsub = stompService.subscribe((event) => {
      if (event.eventType === RealTimeEventType.WALLET_UPDATED) {
        const balance = typeof event.payload === 'number' ? event.payload : event.payload?.balancePaise;
        if (balance != null) {
          window.dispatchEvent(
            new CustomEvent('wallet:updated', { detail: { balancePaise: balance } })
          );
        }
        refreshWalletBalanceRef.current?.();
      }

      if (event.eventType === RealTimeEventType.SYSTEM_ANNOUNCEMENT) {
        window.dispatchEvent(new CustomEvent('announcement', { detail: event.payload }));
      }

      if (event.eventType === RealTimeEventType.NOTIFICATION && event.payload) {
        addNotificationRef.current?.(event.payload);
        window.dispatchEvent(new CustomEvent('notification:received', { detail: event.payload }));
      }

      window.dispatchEvent(new CustomEvent('realtime', { detail: event }));
    });

    return () => {
      unsub();
      // Do not disconnect on token refresh / callback identity changes — only on unmount
      // or when accessToken becomes null (handled above). Keep session alive across
      // React re-renders; reconnect only when token/userId actually change.
    };
  }, [accessToken, user?.id]);

  useEffect(() => {
    return () => {
      stompService.disconnect();
    };
  }, []);

  return children;
}
