import { describe, it, expect } from 'vitest';
import {
  isActiveHandStatus,
  isJoinableStatus,
  isCountdownStatus,
  getTableStatusLabel,
} from './tableUtils';

describe('tableUtils', () => {
  it('detects active hand statuses', () => {
    expect(isActiveHandStatus('IN_PROGRESS')).toBe(true);
    expect(isActiveHandStatus('WAITING')).toBe(false);
  });

  it('detects joinable table statuses', () => {
    expect(isJoinableStatus('WAITING')).toBe(true);
    expect(isJoinableStatus('ROUND_END')).toBe(true);
    expect(isJoinableStatus('IN_PROGRESS')).toBe(false);
  });

  it('detects countdown state', () => {
    expect(isCountdownStatus('COUNTDOWN', 0)).toBe(true);
    expect(isCountdownStatus('WAITING', 5)).toBe(true);
    expect(isCountdownStatus('WAITING', 0)).toBeFalsy();
  });

  it('normalizes status labels', () => {
    expect(getTableStatusLabel('IN_PROGRESS')).toBe('RUNNING');
    expect(getTableStatusLabel('ROUND_END')).toBe('ROUND END');
    expect(getTableStatusLabel(undefined)).toBe('WAITING');
  });
});
