import { describe, it, expect } from 'vitest';
import {
  getNotificationDisplayLabel,
  getNotificationIconKey,
  formatNotificationTime,
} from './notificationUtils';

describe('notificationUtils', () => {
  it('prefers displayLabel and title', () => {
    expect(getNotificationDisplayLabel({ displayLabel: 'Custom' })).toBe('Custom');
    expect(getNotificationDisplayLabel({ title: 'Title Win' })).toBe('Title Win');
  });

  it('maps known notification types', () => {
    expect(getNotificationDisplayLabel({ type: 'DEPOSIT_SUCCESS' })).toBe('Deposit Successful');
    expect(getNotificationDisplayLabel({ type: 'GAME' })).toBe('Game Result');
  });

  it('returns icon keys by type', () => {
    expect(getNotificationIconKey('DEPOSIT_SUCCESS')).toBe('success');
    expect(getNotificationIconKey('ACCOUNT_ALERT')).toBe('alert');
    expect(getNotificationIconKey('GAME_INVITE')).toBe('invite');
  });

  it('formats valid ISO timestamps', () => {
    const formatted = formatNotificationTime('2026-07-27T10:30:00.000Z');
    expect(formatted).not.toBe('Now');
    expect(formatted.length).toBeGreaterThan(0);
  });
});
