import React, { useState } from 'react';
import AdminPage from '@/features/admin/AdminPage';
import { adminLogin, adminRegister } from '@/features/admin/adminApi';

export default function App() {
  const [isLogin, setIsLogin] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [registerData, setRegisterData] = useState({
    email: '',
    phoneNumber: '',
    displayName: '',
    password: '',
    role: 'ADMIN',
  });
  const [adminUser, setAdminUser] = useState(() => {
    const raw = localStorage.getItem('adminUser');
    return raw ? JSON.parse(raw) : null;
  });

  const clearLocalAuth = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('adminUser');
    setAdminUser(null);
  };

  const persistAuth = (auth) => {
    localStorage.setItem('accessToken', auth.accessToken);
    localStorage.setItem('refreshToken', auth.refreshToken || '');
    localStorage.setItem('adminUser', JSON.stringify(auth.user));
    setAdminUser(auth.user);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const auth = await adminLogin(loginId, password);
      if (auth?.user?.role !== 'ADMIN') {
        throw new Error('Only ADMIN can access admin panel');
      }
      persistAuth(auth);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const auth = await adminRegister(registerData);
      if (auth?.user?.role !== 'ADMIN') {
        throw new Error('Registered user is not ADMIN');
      }
      persistAuth(auth);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  if (adminUser) {
    return <AdminPage onLogout={clearLocalAuth} />;
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl">
        <h1 className="text-xl font-bold mb-1">Teen Patti Admin</h1>
        <p className="text-xs text-slate-400 mb-5">Login or register</p>

        <div className="flex bg-slate-950 p-1 rounded-xl mb-4 border border-slate-800">
          <button
            type="button"
            onClick={() => setIsLogin(true)}
            className={`flex-1 py-2 text-xs font-bold rounded-lg ${isLogin ? 'bg-amber-500 text-slate-950' : 'text-slate-400'}`}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => setIsLogin(false)}
            className={`flex-1 py-2 text-xs font-bold rounded-lg ${!isLogin ? 'bg-amber-500 text-slate-950' : 'text-slate-400'}`}
          >
            Register
          </button>
        </div>

        {error && <div className="mb-3 text-xs text-rose-300 bg-rose-950/40 border border-rose-800 rounded-xl px-3 py-2">{error}</div>}

        {isLogin ? (
          <form onSubmit={handleLogin} className="space-y-3">
            <input
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              placeholder="Email or phone"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <button type="submit" disabled={loading} className="w-full py-2.5 rounded-xl bg-amber-500 text-slate-950 font-bold disabled:opacity-60">
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="space-y-3">
            <input
              value={registerData.email}
              onChange={(e) => setRegisterData((p) => ({ ...p, email: e.target.value }))}
              placeholder="Email"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <input
              value={registerData.phoneNumber}
              onChange={(e) => setRegisterData((p) => ({ ...p, phoneNumber: e.target.value }))}
              placeholder="+91XXXXXXXXXX"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <input
              value={registerData.displayName}
              onChange={(e) => setRegisterData((p) => ({ ...p, displayName: e.target.value }))}
              placeholder="Display name"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <input
              type="password"
              value={registerData.password}
              onChange={(e) => setRegisterData((p) => ({ ...p, password: e.target.value }))}
              placeholder="Password"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              required
            />
            <div>
              <label className="block text-xs text-slate-400 mb-1">Role</label>
              <select
                value={registerData.role}
                onChange={(e) => setRegisterData((p) => ({ ...p, role: e.target.value }))}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm"
              >
                <option value="ADMIN">Admin</option>
                <option value="PLAYER">Player</option>
              </select>
            </div>
            <button type="submit" disabled={loading} className="w-full py-2.5 rounded-xl bg-amber-500 text-slate-950 font-bold disabled:opacity-60">
              {loading ? 'Creating...' : 'Register'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
