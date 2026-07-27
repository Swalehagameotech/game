import axiosClient from '@/shared/api/axiosClient';

function unwrap(res) {
  return res?.data?.data ?? res?.data ?? res;
}

export async function fetchMyProfile() {
  const { data: res } = await axiosClient.get('/users/me');
  return unwrap(res);
}

export async function updateMyProfile(payload) {
  const { data: res } = await axiosClient.patch('/users/me', payload);
  return unwrap(res);
}

export async function changePassword(currentPassword, newPassword) {
  const { data: res } = await axiosClient.post('/users/me/password', {
    currentPassword,
    newPassword,
  });
  return unwrap(res);
}

export async function sendPresenceHeartbeat() {
  const { data: res } = await axiosClient.post('/users/me/presence/heartbeat');
  return unwrap(res);
}

export async function fetchPublicProfile(userId) {
  const { data: res } = await axiosClient.get(`/users/${userId}/public`);
  return unwrap(res);
}

export async function fetchOnlineCount() {
  const { data: res } = await axiosClient.get('/users/online/count');
  return unwrap(res);
}
