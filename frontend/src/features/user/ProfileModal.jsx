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
      <div className="tp-modal-backdrop fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
        <motion.div
          initial={{ opacity: 0, y: 24, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 16, scale: 0.98 }}
          className="tp-modal-panel w-full sm:max-w-lg max-h-[92dvh] overflow-hidden rounded-t-3xl sm:rounded-2xl"
        >
          <div className="flex items-center justify-between px-5 sm:px-6 py-4 border-b border-[#d4af37]/25">
            <div className="flex items-center gap-2">
              <User className="w-5 h-5 text-[#d4af37]" />
              <h2 className="font-display text-lg font-extrabold tracking-wide text-[#f5e6a8]">My Profile</h2>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="p-2 rounded-full text-[#f5e6a8]/70 hover:text-[#f5e6a8] hover:bg-black/30 cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="p-5 sm:p-6 space-y-5 max-h-[min(75dvh,640px)] overflow-y-auto pb-[max(1.25rem,env(safe-area-inset-bottom))]">
            {loading ? (
              <div className="flex justify-center py-12">
                <Loader2 className="w-8 h-8 text-[#d4af37] animate-spin" />
              </div>
            ) : (
              <>
                {error && (
                  <div className="text-sm text-[#ffb4b4] bg-[#8b1a28]/25 border border-[#d4af37]/25 rounded-xl px-4 py-2">
                    {error}
                  </div>
                )}
                {success && (
                  <div className="text-sm text-emerald-200 bg-emerald-950/40 border border-emerald-500/30 rounded-xl px-4 py-2">
                    {success}
                  </div>
                )}

                {profile && (
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div className="rounded-xl p-3 border border-[#d4af37]/25 bg-black/35">
                      <span className="text-[10px] uppercase text-[#d4af37]/70 font-bold block">Wallet</span>
                      <span className="font-mono font-bold text-[#f5e6a8]">{profile.formattedWalletBalance}</span>
                    </div>
                    <div className="rounded-xl p-3 border border-[#d4af37]/25 bg-black/35">
                      <span className="text-[10px] uppercase text-[#d4af37]/70 font-bold block">Games Played</span>
                      <span className="font-mono font-bold text-[#f5e6a8]">{profile.matchesPlayedCount ?? 0}</span>
                    </div>
                    <div className="rounded-xl p-3 border border-[#d4af37]/25 bg-black/35 col-span-2">
                      <span className="text-[10px] uppercase text-[#d4af37]/70 font-bold block">Username</span>
                      <span className="text-[#f5e6a8] truncate block font-semibold">{profile.displayName}</span>
                    </div>
                  </div>
                )}

                <form onSubmit={handleSaveProfile} className="space-y-3.5">
                  <div>
                    <label className="text-[11px] font-bold text-[#d4af37] uppercase tracking-wider">Display Name</label>
                    <input
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      minLength={3}
                      maxLength={20}
                      className="tp-input mt-1.5 px-4 py-3 text-sm"
                    />
                  </div>
                  <div>
                    <label className="text-[11px] font-bold text-[#d4af37] uppercase tracking-wider">Avatar URL</label>
                    <input
                      value={avatarUrl}
                      onChange={(e) => setAvatarUrl(e.target.value)}
                      placeholder="https://..."
                      className="tp-input mt-1.5 px-4 py-3 text-sm"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={saving}
                    className="tp-btn-gold w-full py-3 text-sm flex items-center justify-center gap-2 cursor-pointer"
                  >
                    {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                    Save Profile
                  </button>
                </form>

                <form onSubmit={handleChangePassword} className="space-y-3.5 pt-4 border-t border-[#d4af37]/20">
                  <div className="flex items-center gap-2 text-[#f5e6a8] font-bold text-sm">
                    <Lock className="w-4 h-4 text-[#d4af37]" />
                    Change Password
                  </div>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="Current password"
                    className="tp-input px-4 py-3 text-sm"
                  />
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="New password (4+ characters)"
                    className="tp-input px-4 py-3 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={saving || !currentPassword || !newPassword}
                    className="tp-btn-ghost w-full py-3 text-sm font-bold cursor-pointer disabled:opacity-50"
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
