import { useEffect } from 'react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';
import { useGame } from '@/context/GameContext';

/**
 * Loads unread notification count when the user authenticates.
 */
export default function NotificationBootstrap() {
  const { isAuthenticated } = useAuth();
  const { setUnreadNotificationsCount } = useGame();

  useEffect(() => {
    if (!isAuthenticated) {
      setUnreadNotificationsCount(0);
      return;
    }

    let cancelled = false;
    const loadUnreadCount = async () => {
      try {
        const { data: res } = await axiosClient.get('/notifications/unread-count');
        const payload = res?.data ?? res;
        const count = payload?.unreadCount ?? payload?.data?.unreadCount ?? 0;
        if (!cancelled) {
          setUnreadNotificationsCount(count);
        }
      } catch (err) {
        console.debug('Unread notification count fetch skipped:', err?.message);
      }
    };

    loadUnreadCount();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, setUnreadNotificationsCount]);

  return null;
}
