import axiosClient from '@/shared/api/axiosClient';

function unwrap(res) {
  return res?.data?.data ?? res?.data ?? res;
}

export async function fetchWalletSummary() {
  const { data: res } = await axiosClient.get('/wallet/summary');
  return unwrap(res);
}

export async function fetchWalletBalance() {
  const { data: res } = await axiosClient.get('/wallet/me');
  return unwrap(res);
}

export async function fetchWalletHistory(page = 0, size = 20) {
  const { data: res } = await axiosClient.get('/wallet/me/history', { params: { page, size } });
  const payload = unwrap(res);
  return payload?.content ?? (Array.isArray(payload) ? payload : []);
}

export async function depositToWallet(amountPaise, demo = true) {
  const { data: res } = await axiosClient.post('/wallet/deposit', { amountPaise, demo });
  return unwrap(res);
}

export async function depositDemo(amountPaise) {
  const { data: res } = await axiosClient.post(`/wallet/deposit/demo?amountPaise=${amountPaise}`);
  return unwrap(res);
}

export async function initiateGatewayDeposit(amountPaise) {
  const { data: res } = await axiosClient.post('/wallet/deposit', { amountPaise, demo: false });
  return unwrap(res);
}

export async function completeGatewayDeposit(depositRequestId) {
  const { data: res } = await axiosClient.post(`/wallet/deposit/${depositRequestId}/complete`);
  return unwrap(res);
}

export async function requestWithdrawal({ amountPaise, accountNumber, ifscCode, accountHolderName }) {
  const { data: res } = await axiosClient.post('/wallet/withdraw', {
    amountPaise,
    accountNumber,
    ifscCode,
    accountHolderName,
  });
  return unwrap(res);
}
