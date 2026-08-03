import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import axiosClient from '@/shared/api/axiosClient';
import { sendPresenceHeartbeat } from '@/features/user/userApi';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem('user');
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const [dashboard, setDashboard] = useState(() => {
    const saved = localStorage.getItem('dashboard');
    return saved ? JSON.parse(saved) : null;
  });

  const [accessToken, setAccessToken] = useState(() => localStorage.getItem('accessToken') || null);

  useEffect(() => {
    if (user) {
      localStorage.setItem('user', JSON.stringify(user));
    }
  }, [user]);

  useEffect(() => {
    if (dashboard) {
      localStorage.setItem('dashboard', JSON.stringify(dashboard));
    }
  }, [dashboard]);

  useEffect(() => {
    if (accessToken) {
      localStorage.setItem('accessToken', accessToken);
    }
  }, [accessToken]);

  // Optional demo mode: auto-create a guest player on first visit.
  // Disabled by default to prevent unwanted account creation in real usage.
  useEffect(() => {
    const autoGuestEnabled = String(import.meta.env.VITE_ENABLE_AUTO_GUEST_LOGIN || '').toLowerCase() === 'true';
    if (!accessToken && autoGuestEnabled) {
      const autoGuestLogin = async () => {
        try {
          const rand = Math.floor(100000 + Math.random() * 900000);
          const demoPass = 'Password123';
          const demoDisplay = `Player_${rand.toString().slice(-4)}`;

          try {
            await axiosClient.post('/auth/register', {
              password: demoPass,
              displayName: demoDisplay,
            });
          } catch (e) {
            // User might already exist
          }

          const { data: res } = await axiosClient.post('/auth/login', {
            loginId: demoDisplay,
            password: demoPass,
          });

          const authData = res?.data || res;
          const userObj = authData.user || {};
          const dash = authData.dashboard || null;
          const wallet = dash?.wallet;

          login(
            {
              id: userObj.id || authData.userId,
              displayName: userObj.displayName || authData.displayName,
              role: userObj.role || authData.role || 'PLAYER',
              balancePaise: wallet?.balancePaise,
              walletBalance: wallet?.balancePaise,
            },
            authData.accessToken,
            authData.refreshToken,
            dash
          );
        } catch (err) {
          console.error('Auto guest login failed:', err);
        }
      };

      autoGuestLogin();
    }
  }, [accessToken]);

  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);
      setDashboard(null);
      setAccessToken(null);
      localStorage.removeItem('user');
      localStorage.removeItem('dashboard');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    };
    const handleTokenRefreshed = (event) => {
      const nextToken = event.detail?.accessToken;
      if (nextToken) {
        setAccessToken(nextToken);
      }
    };
    window.addEventListener('auth:expired', handleAuthExpired);
    window.addEventListener('auth:unauthorized', handleAuthExpired);
    window.addEventListener('auth:token-refreshed', handleTokenRefreshed);
    return () => {
      window.removeEventListener('auth:expired', handleAuthExpired);
      window.removeEventListener('auth:unauthorized', handleAuthExpired);
      window.removeEventListener('auth:token-refreshed', handleTokenRefreshed);
    };
  }, []);

  const login = (userData, token, refreshToken = null, sessionDashboard = null) => {
    setUser(userData);
    setAccessToken(token);
    if (sessionDashboard) {
      setDashboard(sessionDashboard);
    }
    localStorage.setItem('user', JSON.stringify(userData));
    localStorage.setItem('accessToken', token);
    if (sessionDashboard) {
      localStorage.setItem('dashboard', JSON.stringify(sessionDashboard));
    }
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken);
    }
  };

  const logout = () => {
    setUser(null);
    setDashboard(null);
    setAccessToken(null);
    localStorage.removeItem('user');
    localStorage.removeItem('dashboard');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  };

  const refreshWalletBalance = useCallback(async () => {
    if (!localStorage.getItem('accessToken')) return;
    try {
      const { data: res } = await axiosClient.get('/wallet/summary');
      const data = res?.data?.data ?? res?.data ?? res;
      if (data?.balancePaise !== undefined) {
        setUser((prev) => (prev ? { ...prev, walletBalance: data.balancePaise, balancePaise: data.balancePaise } : prev));
      }
    } catch {
      try {
        const { data: res } = await axiosClient.get('/wallet/me');
        const data = res?.data?.data ?? res?.data ?? res;
        if (data?.balancePaise !== undefined) {
          setUser((prev) => (prev ? { ...prev, walletBalance: data.balancePaise, balancePaise: data.balancePaise } : prev));
        }
      } catch {
        // ignore
      }
    }
  }, []);

  const refreshProfile = async () => {
    if (!accessToken) return null;
    try {
      const profile = await sendPresenceHeartbeat();
      if (profile) {
        setUser((prev) =>
          prev
            ? {
                ...prev,
                displayName: profile.displayName ?? prev.displayName,
                avatarUrl: profile.avatarUrl ?? prev.avatarUrl,
                balancePaise: profile.walletBalancePaise,
                walletBalance: profile.walletBalancePaise,
                matchesPlayedCount: profile.matchesPlayedCount,
                firstLoginTutorialCompleted: profile.firstLoginTutorialCompleted,
                isOnline: profile.isOnline,
              }
            : prev
        );
      }
      return profile;
    } catch {
      return null;
    }
  };

  useEffect(() => {
    const onWallet = (e) => {
      const balance = e.detail?.balancePaise;
      if (balance != null) {
        setUser((prev) => (prev ? { ...prev, walletBalance: balance, balancePaise: balance } : prev));
      }
    };
    window.addEventListener('wallet:updated', onWallet);
    return () => window.removeEventListener('wallet:updated', onWallet);
  }, []);

  useEffect(() => {
    if (!accessToken) return undefined;
    refreshProfile();
    const heartbeat = setInterval(() => {
      refreshProfile();
    }, 60000);
    return () => clearInterval(heartbeat);
  }, [accessToken]);

  const updateUserProfile = (updatedFields) => {
    setUser((prev) => (prev ? { ...prev, ...updatedFields } : null));
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        dashboard,
        accessToken,
        isAuthenticated: !!accessToken,
        login,
        logout,
        setDashboard,
        updateUserProfile,
        refreshWalletBalance,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
