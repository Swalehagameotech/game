import axios from 'axios';

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

const clearAuthStorage = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  localStorage.removeItem('dashboard');
};

axiosClient.interceptors.request.use(
  (config) => {
    const rawToken = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (rawToken && typeof rawToken === 'string' && rawToken.split('.').length === 3) {
      config.headers.Authorization = `Bearer ${rawToken}`;
    } else if (rawToken) {
      clearAuthStorage();
    }
    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (!originalRequest || !error.response || error.response.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // Never try to refresh auth endpoints themselves
    const url = originalRequest.url || '';
    if (url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/refresh')) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return axiosClient(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      isRefreshing = false;
      clearAuthStorage();
      window.dispatchEvent(new Event('auth:unauthorized'));
      return Promise.reject(error);
    }

    try {
      const { data: res } = await axios.post(
        `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'}/auth/refresh`,
        { refreshToken }
      );
      // Backend wraps payload as { success, message, data: {...tokens...} }
      const payload = res?.data?.data ?? res?.data ?? res;
      const newAccessToken = payload?.accessToken;
      const newRefreshToken = payload?.refreshToken;

      if (!newAccessToken) {
        throw new Error('Refresh response missing accessToken');
      }

      localStorage.setItem('accessToken', newAccessToken);
      if (newRefreshToken) {
        localStorage.setItem('refreshToken', newRefreshToken);
      }

      window.dispatchEvent(
        new CustomEvent('auth:token-refreshed', {
          detail: { accessToken: newAccessToken, refreshToken: newRefreshToken },
        })
      );

      axiosClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

      processQueue(null, newAccessToken);
      return axiosClient(originalRequest);
    } catch (refreshErr) {
      clearAuthStorage();
      processQueue(refreshErr, null);
      window.dispatchEvent(new Event('auth:unauthorized'));
      return Promise.reject(refreshErr);
    } finally {
      isRefreshing = false;
    }
  }
);

export default axiosClient;
