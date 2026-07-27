/**
 * Human-readable labels and icon keys for notification types.
 */
export function getNotificationDisplayLabel(item) {
  if (item?.displayLabel) return item.displayLabel;
  if (item?.title) return item.title;
  const labels = {
    GAME_INVITE: 'Game Invite',
    DEPOSIT_SUCCESS: 'Deposit Successful',
    DEPOSIT_FAILED: 'Deposit Failed',
    WITHDRAWAL_SUCCESS: 'Withdrawal Successful',
    WITHDRAWAL_FAILED: 'Withdrawal Failed',
    ACCOUNT_ALERT: 'Account Alert',
    SYSTEM_ANNOUNCEMENT: 'Announcement',
    GAME: 'Game Result',
    TRANSACTION: 'Transaction',
    FRIEND: 'Friend',
    SYSTEM: 'System',
  };
  return labels[item?.type] || item?.type || 'Notification';
}

export function getNotificationIconKey(type) {
  if (type === 'DEPOSIT_SUCCESS' || type === 'WITHDRAWAL_SUCCESS') return 'success';
  if (type === 'DEPOSIT_FAILED' || type === 'WITHDRAWAL_FAILED' || type === 'ACCOUNT_ALERT') return 'alert';
  if (type === 'GAME_INVITE') return 'invite';
  if (type === 'GAME') return 'game';
  if (type === 'SYSTEM_ANNOUNCEMENT') return 'announcement';
  return 'default';
}

export function formatNotificationTime(isoString) {
  if (!isoString) return 'Now';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
