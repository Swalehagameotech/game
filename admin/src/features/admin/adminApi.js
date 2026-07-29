import axiosClient from '@/shared/api/axiosClient';

function unwrap(res) {
  return res?.data?.data ?? res?.data ?? res;
}

export async function adminLogin(loginId, password) {
  const { data: res } = await axiosClient.post('/auth/login', { loginId, password });
  return unwrap(res);
}

export async function adminRegister({ email, phoneNumber, password, displayName, role }) {
  const { data: res } = await axiosClient.post('/auth/register', {
    email,
    phoneNumber,
    password,
    displayName,
    role,
  });
  return unwrap(res);
}

export async function fetchAdminDashboard() {
  const { data: res } = await axiosClient.get('/admin/dashboard');
  return unwrap(res);
}

export async function fetchBettingConfig() {
  const { data: res } = await axiosClient.get('/admin/betting-config/active');
  return unwrap(res);
}

export async function updateBettingConfig(payload) {
  const { data: res } = await axiosClient.put('/admin/betting-config/active', payload);
  return unwrap(res);
}

export async function fetchAdminUsers(query = '', page = 0, size = 20) {
  const { data: res } = await axiosClient.get('/admin/users', { params: { query: query || undefined, page, size } });
  const payload = unwrap(res);
  return payload?.content ?? (Array.isArray(payload) ? payload : []);
}

export async function fetchAdminUser(userId) {
  const { data: res } = await axiosClient.get(`/admin/users/${userId}`);
  return unwrap(res);
}

export async function fetchAdminUserWalletHistory(userId) {
  const { data: res } = await axiosClient.get(`/admin/users/${userId}/wallet/history`);
  const payload = unwrap(res);
  return Array.isArray(payload) ? payload : [];
}

export async function adminAddWallet(userId, amountPaise, reason) {
  const { data: res } = await axiosClient.post(`/admin/users/${userId}/wallet/add`, { amount: amountPaise, reason });
  return unwrap(res);
}

export async function adminDeductWallet(userId, amountPaise, reason) {
  const { data: res } = await axiosClient.post(`/admin/users/${userId}/wallet/deduct`, { amount: amountPaise, reason });
  return unwrap(res);
}

export async function suspendUser(userId, reason) {
  const { data: res } = await axiosClient.post(`/admin/users/${userId}/suspend`, { reason });
  return unwrap(res);
}

export async function reinstateUser(userId) {
  const { data: res } = await axiosClient.post(`/admin/users/${userId}/reinstate`);
  return unwrap(res);
}

export async function banUser(userId, reason) {
  const { data: res } = await axiosClient.post(`/admin/users/${userId}/ban`, { reason });
  return unwrap(res);
}

export async function fetchAdminWithdrawals(status = 'PENDING_ADMIN_REVIEW', page = 0, size = 20) {
  const { data: res } = await axiosClient.get('/admin/withdrawals', { params: { status, page, size } });
  const payload = unwrap(res);
  return payload?.content ?? (Array.isArray(payload) ? payload : []);
}

export async function approveWithdrawal(requestId) {
  const { data: res } = await axiosClient.post(`/admin/withdrawals/${requestId}/approve`);
  return unwrap(res);
}

export async function rejectWithdrawal(requestId, rejectionReason) {
  const { data: res } = await axiosClient.post(`/admin/withdrawals/${requestId}/reject`, { reason: rejectionReason });
  return unwrap(res);
}

export async function fetchAdminTables(group = 'active', page = 0, size = 20) {
  const { data: res } = await axiosClient.get('/admin/tables', { params: { group, page, size } });
  const payload = unwrap(res);
  return payload?.content ?? (Array.isArray(payload) ? payload : []);
}

export async function forceCloseTable(tableId, reason) {
  const { data: res } = await axiosClient.post(`/admin/tables/${tableId}/force-close`, null, {
    params: { reason },
  });
  return unwrap(res);
}

export async function broadcastAnnouncement(title, message) {
  const { data: res } = await axiosClient.post('/admin/announcements', { title, message });
  return unwrap(res);
}

export function formatPaise(paise) {
  return `₹${((paise || 0) / 100).toFixed(2)}`;
}
