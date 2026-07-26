import React, { createContext, useContext, useState, useEffect } from 'react';
import axiosClient from '@/shared/api/axiosClient';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem('user');
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const [accessToken, setAccessToken] = useState(() => localStorage.getItem('accessToken') || null);

  useEffect(() => {
    if (user) {
      localStorage.setItem('user', JSON.stringify(user));
    }
  }, [user]);

  useEffect(() => {
    if (accessToken) {
      localStorage.setItem('accessToken', accessToken);
    }
  }, [accessToken]);

  // Auto-create demo player session on first visit if not logged in
  useEffect(() => {
    if (!accessToken) {
      const autoGuestLogin = async () => {
        try {
          const rand = Math.floor(100000 + Math.random() * 900000);
          const demoEmail = `player_${rand}@example.com`;
          const demoPhone = `+919${Math.floor(100000000 + Math.random() * 899999999)}`;
          const demoPass = 'Password123';
          const demoDisplay = `Player_${rand.toString().slice(-4)}`;

          try {
            await axiosClient.post('/auth/register', {
              email: demoEmail,
              phoneNumber: demoPhone,
              password: demoPass,
              displayName: demoDisplay,
            });
          } catch (e) {
            // User might already exist
          }

          const { data: res } = await axiosClient.post('/auth/login', {
            loginId: demoEmail,
            password: demoPass,
          });

          const authData = res?.data || res;
          const userObj = authData.user || {};

          login(
            {
              id: userObj.id || authData.userId,
              email: userObj.email,
              displayName: userObj.displayName || authData.displayName,
              role: userObj.role || authData.role || 'PLAYER',
            },
            authData.accessToken,
            authData.refreshToken
          );
        } catch (err) {
          console.error('Auto guest login failed:', err);
        }
      };

      autoGuestLogin();
    }
  }, []);

  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);
      setAccessToken(null);
      localStorage.removeItem('user');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    };
    window.addEventListener('auth:expired', handleAuthExpired);
    window.addEventListener('auth:unauthorized', handleAuthExpired);
    return () => {
      window.removeEventListener('auth:expired', handleAuthExpired);
      window.removeEventListener('auth:unauthorized', handleAuthExpired);
    };
  }, []);

  const login = (userData, token, refreshToken = null) => {
    setUser(userData);
    setAccessToken(token);
    localStorage.setItem('user', JSON.stringify(userData));
    localStorage.setItem('accessToken', token);
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken);
    }
  };

  const logout = () => {
    setUser(null);
    setAccessToken(null);
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  };

  const refreshWalletBalance = async () => {
    if (!accessToken) return;
    try {
      const { data: res } = await axiosClient.get('/wallet/me');
      const data = res?.data || res;
      if (data && (data.balancePaise !== undefined || data.walletBalance !== undefined)) {
        const bal = data.balancePaise !== undefined ? data.balancePaise : data.walletBalance;
        setUser((prev) => (prev ? { ...prev, walletBalance: bal, balancePaise: bal } : prev));
      }
    } catch (err) {
      // Ignore wallet fetch errors if unauthenticated
    }
  };

  useEffect(() => {
    if (accessToken) {
      refreshWalletBalance();
    }
  }, [accessToken]);

  const updateUserProfile = (updatedFields) => {
    setUser((prev) => (prev ? { ...prev, ...updatedFields } : null));
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: !!accessToken,
        login,
        logout,
        updateUserProfile,
        refreshWalletBalance,
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
