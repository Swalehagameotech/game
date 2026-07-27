import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, User, Lock, Save, Loader2 } from 'lucide-react';
import { fetchMyProfile, updateMyProfile, changePassword } from '@/features/user/userApi';
import { useAuth } from '@/context/AuthContext';

export default function ProfileModal({ isOpen, onClose }) {
  const { updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [profile, setProfile] = useState(null);
  const [displayName, setDisplayName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    setError('');
    setSuccess('');
    setLoading(true);
    fetchMyProfile()
      .then((data) => {
        setProfile(data);
        setDisplayName(data.displayName || '');
        setAvatarUrl(data.avatarUrl || '');
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Could not load profile.');
      })
      .finally(() => setLoading(false));
  }, [isOpen]);

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const updated = await updateMyProfile({
        displayName: displayName.trim(),
        avatarUrl: avatarUrl.trim(),
      });
      setProfile(updated);
      updateUserProfile({
        displayName: updated.displayName,
        avatarUrl: updated.avatarUrl,
        balancePaise: updated.walletBalancePaise,
        walletBalance: updated.walletBalancePaise,
        matchesPlayedCount: updated.matchesPlayedCount,
        firstLoginTutorialCompleted: updated.firstLoginTutorialCompleted,
      });
      setSuccess('Profile updated.');
    } catch (err) {
      setError(err.response?.data?.message || 'Profile update failed.');
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (!currentPassword || !newPassword) return;
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setSuccess('Password changed successfully.');
    } catch (err) {
      setError(err.response?.data?.message || 'Password change failed.');
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
        <motion.div
          initial={{ opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.96 }}
          className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl shadow-2xl overflow-hidden"
        >
          <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
            <div className="flex items-center gap-2">
              <User className="w-5 h-5 text-amber-400" />
              <h2 className="text-lg font-black text-slate-100">My Profile</h2>
            </div>
            <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-200 cursor-pointer">
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="p-6 space-y-6 max-h-[75vh] overflow-y-auto">
            {loading ? (
              <div className="flex justify-center py-12">
                <Loader2 className="w-8 h-8 text-amber-400 animate-spin" />
              </div>
            ) : (
              <>
                {error && (
                  <div className="text-sm text-rose-400 bg-rose-500/10 border border-rose-500/20 rounded-xl px-4 py-2">
                    {error}
                  </div>
                )}
                {success && (
                  <div className="text-sm text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 rounded-xl px-4 py-2">
                    {success}
                  </div>
                )}

                {profile && (
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div className="bg-slate-950 rounded-xl p-3 border border-slate-800">
                      <span className="text-[10px] uppercase text-slate-500 font-bold block">Wallet</span>
                      <span className="font-mono font-bold text-amber-400">{profile.formattedWalletBalance}</span>
                    </div>
                    <div className="bg-slate-950 rounded-xl p-3 border border-slate-800">
                      <span className="text-[10px] uppercase text-slate-500 font-bold block">Games Played</span>
                      <span className="font-mono font-bold text-slate-100">{profile.matchesPlayedCount ?? 0}</span>
                    </div>
                    <div className="bg-slate-950 rounded-xl p-3 border border-slate-800 col-span-2">
                      <span className="text-[10px] uppercase text-slate-500 font-bold block">Email</span>
                      <span className="text-slate-300 truncate block">{profile.email}</span>
                    </div>
                  </div>
                )}

                <form onSubmit={handleSaveProfile} className="space-y-4">
                  <div>
                    <label className="text-xs font-bold text-slate-400 uppercase">Display Name</label>
                    <input
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      minLength={3}
                      maxLength={20}
                      className="mt-1 w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-slate-100 focus:border-amber-500 outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-slate-400 uppercase">Avatar URL</label>
                    <input
                      value={avatarUrl}
                      onChange={(e) => setAvatarUrl(e.target.value)}
                      placeholder="https://..."
                      className="mt-1 w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-slate-100 focus:border-amber-500 outline-none"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={saving}
                    className="w-full py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60"
                  >
                    {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                    Save Profile
                  </button>
                </form>

                <form onSubmit={handleChangePassword} className="space-y-4 pt-4 border-t border-slate-800">
                  <div className="flex items-center gap-2 text-slate-300 font-bold text-sm">
                    <Lock className="w-4 h-4 text-amber-400" />
                    Change Password
                  </div>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="Current password"
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-slate-100 focus:border-amber-500 outline-none"
                  />
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="New password (8+ chars, letter + number)"
                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2.5 text-slate-100 focus:border-amber-500 outline-none"
                  />
                  <button
                    type="submit"
                    disabled={saving || !currentPassword || !newPassword}
                    className="w-full py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-100 font-bold rounded-xl cursor-pointer disabled:opacity-60"
                  >
                    Update Password
                  </button>
                </form>
              </>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
