import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Shield, Lock, User as UserIcon, AlertCircle, Sparkles, X } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';

export default function AuthModal({ isOpen, onClose }) {
  const [isLogin, setIsLogin] = useState(true);
  const [formData, setFormData] = useState({
    username: '',
    password: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();

  if (!isOpen) return null;

  const applyAuth = (authData, roleOverride = null) => {
    const userObj = authData.user || {};
    login(
      {
        id: userObj.id || authData.userId,
        displayName: userObj.displayName || authData.displayName,
        role: roleOverride || userObj.role || authData.role,
      },
      authData.accessToken,
      authData.refreshToken
    );
    if (authData.refreshToken) {
      localStorage.setItem('refreshToken', authData.refreshToken);
    }
    onClose();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const username = formData.username.trim();
    try {
      if (username.length < 3 || username.length > 20) {
        setError('Username must be between 3 and 20 characters.');
        setLoading(false);
        return;
      }

      if (isLogin) {
        const { data: res } = await axiosClient.post('/auth/login', {
          loginId: username,
          password: formData.password,
        });
        applyAuth(res?.data || res);
      } else {
        const { data: res } = await axiosClient.post('/auth/register', {
          displayName: username,
          password: formData.password,
        });
        applyAuth(res?.data || res);
      }
    } catch (err) {
      const resp = err.response?.data;
      if (resp?.details && Array.isArray(resp.details) && resp.details.length > 0) {
        setError(resp.details.join('. '));
      } else if (resp?.message) {
        setError(resp.message);
      } else {
        setError(typeof resp === 'string' ? resp : 'Authentication failed. Check username and password.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleDemoGuestLogin = async (role = 'USER') => {
    setLoading(true);
    setError('');
    try {
      const isDemoAdmin = role === 'ADMIN';
      const randomId = Math.floor(100000 + Math.random() * 900000);
      const demoPass = 'Pass1234';
      const demoDisplay = isDemoAdmin
        ? `Admin${randomId.toString().slice(-4)}`
        : `Player${randomId.toString().slice(-4)}`;

      try {
        await axiosClient.post('/auth/register', {
          displayName: demoDisplay,
          password: demoPass,
          role: isDemoAdmin ? 'ADMIN' : 'PLAYER',
        });
      } catch {
        // Fallback if username somehow collides
      }

      const { data: res } = await axiosClient.post('/auth/login', {
        loginId: demoDisplay,
        password: demoPass,
      });

      applyAuth(res?.data || res, isDemoAdmin ? 'ADMIN' : null);
    } catch (err) {
      const resp = err.response?.data;
      setError('Guest login failed: ' + (resp?.message || err.message || 'Make sure backend server on port 8080 is running.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      <div className="tp-modal-backdrop fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
        <motion.div
          initial={{ opacity: 0, y: 28, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 16, scale: 0.98 }}
          className="tp-modal-panel relative w-full sm:max-w-md max-h-[92dvh] overflow-y-auto rounded-t-3xl sm:rounded-2xl p-5 sm:p-6 pb-[max(1.25rem,env(safe-area-inset-bottom))]"
        >
          <button
            type="button"
            onClick={onClose}
            className="absolute top-3 right-3 p-2 rounded-full text-[#f5e6a8]/70 hover:text-[#f5e6a8] hover:bg-black/30 cursor-pointer"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="text-center mb-5 sm:mb-6 pt-1">
            <div className="w-12 h-12 sm:w-14 sm:h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center font-display text-2xl font-extrabold text-[#1a1205] shadow-[0_0_24px_rgba(212,175,55,0.35)] bg-gradient-to-b from-[#fff4c2] via-[#d4af37] to-[#a67c00]">
              ♠
            </div>
            <h2 className="font-display text-xl sm:text-2xl font-extrabold tracking-[0.08em] text-transparent bg-clip-text bg-gradient-to-b from-[#fff8d6] via-[#d4af37] to-[#8a6a12]">
              {isLogin ? 'Welcome Back' : 'Join The Table'}
            </h2>
            <p className="text-[11px] sm:text-xs text-[#f5e6a8]/65 mt-1.5">
              {isLogin ? 'Sign in with your unique username' : 'Pick a unique username to start playing'}
            </p>
          </div>

          <div className="flex bg-black/40 p-1 rounded-xl mb-5 border border-[#d4af37]/25">
            <button
              type="button"
              onClick={() => { setIsLogin(true); setError(''); }}
              className={`flex-1 py-2.5 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                isLogin
                  ? 'bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a1205] shadow-md'
                  : 'text-[#f5e6a8]/60 hover:text-[#f5e6a8]'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { setIsLogin(false); setError(''); }}
              className={`flex-1 py-2.5 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                !isLogin
                  ? 'bg-gradient-to-b from-[#f5e6a8] to-[#d4af37] text-[#1a1205] shadow-md'
                  : 'text-[#f5e6a8]/60 hover:text-[#f5e6a8]'
              }`}
            >
              Register
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-[#8b1a28]/25 border border-[#d4af37]/25 rounded-xl text-[#ffb4b4] text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-[#d4af37]" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-3.5 sm:space-y-4">
            <div>
              <label className="block text-[11px] font-bold uppercase tracking-wider text-[#d4af37] mb-1.5">Username</label>
              <div className="relative">
                <UserIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#d4af37]/70" />
                <input
                  type="text"
                  required
                  minLength={3}
                  maxLength={20}
                  autoComplete="username"
                  placeholder="unique username"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  className="tp-input pl-9 pr-3 py-3 text-sm"
                />
              </div>
            </div>

            <div>
              <label className="block text-[11px] font-bold uppercase tracking-wider text-[#d4af37] mb-1.5">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#d4af37]/70" />
                <input
                  type="password"
                  required
                  minLength={4}
                  autoComplete={isLogin ? 'current-password' : 'new-password'}
                  placeholder="••••••••"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="tp-input pl-9 pr-3 py-3 text-sm"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="tp-btn-gold w-full py-3.5 text-sm uppercase tracking-wide cursor-pointer"
            >
              {loading ? 'Processing...' : isLogin ? 'Sign In to Play' : 'Create Account'}
            </button>
          </form>

          <div className="relative my-5 text-center">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-[#d4af37]/20" /></div>
            <span className="relative px-3 text-[10px] uppercase font-bold tracking-widest text-[#d4af37]/70 bg-[#1a0808]">
              Instant Demo
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => handleDemoGuestLogin('USER')}
              disabled={loading}
              className="tp-btn-ghost py-2.5 px-3 text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer hover:border-[#d4af37]/70"
            >
              <Sparkles className="w-3.5 h-3.5 text-[#d4af37]" />
              <span>Demo Player</span>
            </button>

            <button
              type="button"
              onClick={() => handleDemoGuestLogin('ADMIN')}
              disabled={loading}
              className="tp-btn-ghost py-2.5 px-3 text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer hover:border-[#d4af37]/70"
            >
              <Shield className="w-3.5 h-3.5 text-[#d4af37]" />
              <span>Demo Admin</span>
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
