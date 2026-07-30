import React, { useState } from 'react';
import HeaderNav from '@/components/HeaderNav';
import LobbyView from '@/features/lobby/LobbyView';
import TeenPattiTableUI from '@/features/table/TeenPattiTableUI';
import AuthModal from '@/features/auth/AuthModal';
import WalletModal from '@/features/wallet/WalletModal';
import LeaderboardModal from '@/features/leaderboard/LeaderboardModal';
import NotificationDrawer from '@/features/notifications/NotificationDrawer';
import AnnouncementBanner from '@/features/notifications/AnnouncementBanner';
import NotificationBootstrap from '@/features/notifications/NotificationBootstrap';
import AdminModal from '@/features/admin/AdminModal';
import ProfileModal from '@/features/user/ProfileModal';
import TutorialOverlay from '@/features/auth/TutorialOverlay';
import { useAuth } from '@/context/AuthContext';
import useLandscapeLock from '@/hooks/useLandscapeLock';

export default function App() {
  useLandscapeLock();
  const { user, isAuthenticated } = useAuth();
  const [activeTableId, setActiveTableId] = useState(() => {
    return localStorage.getItem('activeTableId') || null;
  });

  const [isAuthOpen, setIsAuthOpen] = useState(false);
  const [isWalletOpen, setIsWalletOpen] = useState(false);
  const [isLeaderboardOpen, setIsLeaderboardOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [isAdminOpen, setIsAdminOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  const handleJoinTable = (tableId) => {
    if (!isAuthenticated) {
      setIsAuthOpen(true);
      return;
    }
    setActiveTableId(tableId);
    if (tableId) {
      localStorage.setItem('activeTableId', tableId);
    } else {
      localStorage.removeItem('activeTableId');
    }
  };

  const handleLeaveTable = () => {
    setActiveTableId(null);
    localStorage.removeItem('activeTableId');
  };

  const openAuth = () => setIsAuthOpen(true);
  const openWallet = () => {
    if (!isAuthenticated) setIsAuthOpen(true);
    else setIsWalletOpen(true);
  };
  const openLeaderboard = () => setIsLeaderboardOpen(true);
  const openNotifications = () => {
    if (!isAuthenticated) setIsAuthOpen(true);
    else setIsNotificationsOpen(true);
  };
  const openProfile = () => {
    if (!isAuthenticated) setIsAuthOpen(true);
    else setIsProfileOpen(true);
  };

  return (
    <div className="h-full min-h-full w-full bg-[#1a0505] text-slate-100 flex flex-col font-sans selection:bg-amber-500 selection:text-slate-950 overflow-hidden">
      <NotificationBootstrap />
      {!activeTableId && <AnnouncementBanner />}
      {!activeTableId && (
        <HeaderNav
          onOpenAuth={openAuth}
          onOpenWallet={openWallet}
          onOpenLeaderboard={openLeaderboard}
          onOpenNotifications={openNotifications}
          onOpenAdmin={() => setIsAdminOpen(true)}
          onOpenProfile={openProfile}
        />
      )}

      <main
        className={
          activeTableId
            ? 'flex-1 min-h-0 p-0 overflow-hidden'
            : 'flex-1 min-h-0 overflow-y-auto p-0'
        }
      >
        {activeTableId ? (
          <TeenPattiTableUI tableId={activeTableId} onLeaveTable={handleLeaveTable} />
        ) : (
          <LobbyView
            onJoinTable={handleJoinTable}
            onOpenAuth={openAuth}
            onOpenWallet={openWallet}
            onOpenLeaderboard={openLeaderboard}
            onOpenProfile={openProfile}
          />
        )}
      </main>

      <AuthModal isOpen={isAuthOpen} onClose={() => setIsAuthOpen(false)} />
      <WalletModal isOpen={isWalletOpen} onClose={() => setIsWalletOpen(false)} />
      <LeaderboardModal isOpen={isLeaderboardOpen} onClose={() => setIsLeaderboardOpen(false)} />
      <NotificationDrawer isOpen={isNotificationsOpen} onClose={() => setIsNotificationsOpen(false)} />
      <AdminModal isOpen={isAdminOpen} onClose={() => setIsAdminOpen(false)} />
      <ProfileModal isOpen={isProfileOpen} onClose={() => setIsProfileOpen(false)} />

      <TutorialOverlay user={user} />
    </div>
  );
}
