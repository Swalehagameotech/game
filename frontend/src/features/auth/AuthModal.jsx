import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Shield, Mail, Lock, User as UserIcon, Phone, AlertCircle, Sparkles } from 'lucide-react';
import axiosClient from '@/shared/api/axiosClient';
import { useAuth } from '@/context/AuthContext';

export default function AuthModal({ isOpen, onClose }) {
  const [isLogin, setIsLogin] = useState(true);
  const [formData, setFormData] = useState({
    usernameOrEmail: '',
    password: '',
    displayName: '',
    phoneNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (isLogin) {
        const { data: res } = await axiosClient.post('/auth/login', {
          loginId: formData.usernameOrEmail.trim(),
          password: formData.password,
        });

        const authData = res?.data || res;
        const userObj = authData.user || {};

        login(
          {
            id: userObj.id || authData.userId,
            email: userObj.email,
            displayName: userObj.displayName || authData.displayName,
            role: userObj.role || authData.role,
          },
          authData.accessToken
        );
        if (authData.refreshToken) {
          localStorage.setItem('refreshToken', authData.refreshToken);
        }
        onClose();
      } else {
        const rawEmail = formData.usernameOrEmail.trim();
        const email = rawEmail.includes('@') ? rawEmail : `${rawEmail}@example.com`;
        const displayName = (formData.displayName || rawEmail).trim();
        const phoneNumber = (formData.phoneNumber || '+919876543210').trim();

        if (displayName.length < 3 || displayName.length > 20) {
          setError('Display name must be between 3 and 20 characters.');
          setLoading(false);
          return;
        }

        const { data: res } = await axiosClient.post('/auth/register', {
          email,
          phoneNumber,
          password: formData.password,
          displayName,
        });

        const authData = res?.data || res;
        const userObj = authData.user || {};

        login(
          {
            id: userObj.id || authData.userId,
            email: userObj.email,
            displayName: userObj.displayName || authData.displayName,
            role: userObj.role || authData.role,
          },
          authData.accessToken
        );
        localStorage.setItem('refreshToken', authData.refreshToken || authData.accessToken);
        onClose();
      }
    } catch (err) {
      const resp = err.response?.data;
      if (resp?.details && Array.isArray(resp.details) && resp.details.length > 0) {
        setError(resp.details.join('. '));
      } else if (resp?.message) {
        setError(resp.message);
      } else {
        setError(typeof resp === 'string' ? resp : 'Authentication failed. Please check credentials and password requirements.');
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
      const demoEmail = isDemoAdmin ? `admin_${randomId}@example.com` : `player_${randomId}@example.com`;
      const demoPass = 'Password123';
      const random9Digits = Math.floor(100000000 + Math.random() * 899999999);
      const demoPhone = `+919${random9Digits}`;
      const demoDisplay = isDemoAdmin ? `Admin Manager ${randomId.toString().slice(-3)}` : `Player ${randomId.toString().slice(-4)}`;

      try {
        await axiosClient.post('/auth/register', {
          email: demoEmail,
          phoneNumber: demoPhone,
          password: demoPass,
          displayName: demoDisplay,
        });
      } catch (regErr) {
        // Fallback in case user exists
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
          role: isDemoAdmin ? 'ADMIN' : (userObj.role || authData.role),
        },
        authData.accessToken
      );
      localStorage.setItem('refreshToken', authData.refreshToken || authData.accessToken);
      onClose();
    } catch (err) {
      const resp = err.response?.data;
      setError('Guest login failed: ' + (resp?.message || err.message || 'Make sure backend server on port 8080 is running.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-6 relative overflow-hidden"
        >
          {/* Header Glow */}
          <div className="absolute -top-16 -right-16 w-36 h-36 bg-amber-500/10 rounded-full blur-2xl pointer-events-none" />

          {/* Title */}
          <div className="text-center mb-6">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500 to-amber-300 text-slate-950 flex items-center justify-center mx-auto mb-3 font-extrabold text-2xl shadow-lg shadow-amber-500/20">
              ♠
            </div>
            <h2 className="text-2xl font-black text-slate-100 tracking-wide">
              {isLogin ? 'Welcome Back' : 'Create Player Account'}
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              {isLogin ? 'Log in to join real-money Teen Patti tables' : 'Register to get 10,000 Paise welcome bonus'}
            </p>
          </div>

          {/* Tab Switcher */}
          <div className="flex bg-slate-950 p-1 rounded-xl mb-6 border border-slate-800">
            <button
              type="button"
              onClick={() => { setIsLogin(true); setError(''); }}
              className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${
                isLogin ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { setIsLogin(false); setError(''); }}
              className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all ${
                !isLogin ? 'bg-amber-500 text-slate-950 shadow-md' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Register
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-rose-400 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Username or Email</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  type="text"
                  required
                  placeholder="enter username or email"
                  value={formData.usernameOrEmail}
                  onChange={(e) => setFormData({ ...formData, usernameOrEmail: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500/60"
                />
              </div>
            </div>

            {!isLogin && (
              <>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Display Name</label>
                  <div className="relative">
                    <UserIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                    <input
                      type="text"
                      required
                      placeholder="Your poker handle"
                      value={formData.displayName}
                      onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500/60"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Phone Number</label>
                  <div className="relative">
                    <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                    <input
                      type="text"
                      placeholder="+91 9876543210"
                      value={formData.phoneNumber}
                      onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500/60"
                    />
                  </div>
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  type="password"
                  required
                  placeholder="••••••••"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-amber-500/60"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 hover:from-amber-400 hover:to-amber-500 transition-all disabled:opacity-50 cursor-pointer"
            >
              {loading ? 'Processing...' : isLogin ? 'Sign In to Play' : 'Register Account'}
            </button>
          </form>

          {/* Quick Demo Login Divider */}
          <div className="relative my-5 text-center">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-slate-800" /></div>
            <span className="relative bg-slate-900 px-3 text-[10px] uppercase font-bold tracking-widest text-slate-500">Instant Demo Login</span>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => handleDemoGuestLogin('USER')}
              disabled={loading}
              className="py-2.5 px-3 bg-slate-950 border border-slate-800 hover:border-emerald-500/50 rounded-xl text-xs font-semibold text-emerald-400 flex items-center justify-center gap-1.5 transition-all cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Demo Player</span>
            </button>

            <button
              type="button"
              onClick={() => handleDemoGuestLogin('ADMIN')}
              disabled={loading}
              className="py-2.5 px-3 bg-slate-950 border border-slate-800 hover:border-amber-500/50 rounded-xl text-xs font-semibold text-amber-400 flex items-center justify-center gap-1.5 transition-all cursor-pointer"
            >
              <Shield className="w-3.5 h-3.5" />
              <span>Demo Admin</span>
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
