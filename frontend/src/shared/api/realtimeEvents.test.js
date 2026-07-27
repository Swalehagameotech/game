import { describe, it, expect } from 'vitest';
import { RealTimeEventType, LOBBY_REFRESH_EVENTS, StompDestinations } from './realtimeEvents';

describe('realtimeEvents', () => {
  it('mirrors backend notification and history events', () => {
    expect(RealTimeEventType.NOTIFICATION).toBe('NOTIFICATION');
    expect(RealTimeEventType.GAME_HISTORY_RECORDED).toBe('GAME_HISTORY_RECORDED');
  });

  it('refreshes lobby on notification events', () => {
    expect(LOBBY_REFRESH_EVENTS.has(RealTimeEventType.NOTIFICATION)).toBe(true);
    expect(LOBBY_REFRESH_EVENTS.has(RealTimeEventType.SYSTEM_ANNOUNCEMENT)).toBe(true);
  });

  it('builds per-user STOMP destinations', () => {
    expect(StompDestinations.queueNotifications('user123')).toBe('/queue/notifications/user123');
    expect(StompDestinations.queueWallet('user123')).toBe('/queue/wallet/user123');
  });
});
