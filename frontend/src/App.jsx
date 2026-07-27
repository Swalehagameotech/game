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

export default function App() {
  const { user, isAuthenticated } = useAuth();
  const [activeTableId, setActiveTableId] = useState(() => {
    return localStorage.getItem('activeTableId') || null;
  });

  // Modals state
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

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-amber-500 selection:text-slate-950">
      <NotificationBootstrap />
      <AnnouncementBanner />
      {/* Top Header Navigation */}
      <HeaderNav
        onOpenAuth={() => setIsAuthOpen(true)}
        onOpenWallet={() => {
          if (!isAuthenticated) setIsAuthOpen(true);
          else setIsWalletOpen(true);
        }}
        onOpenLeaderboard={() => setIsLeaderboardOpen(true)}
        onOpenNotifications={() => {
          if (!isAuthenticated) setIsAuthOpen(true);
          else setIsNotificationsOpen(true);
        }}
        onOpenAdmin={() => setIsAdminOpen(true)}
        onOpenProfile={() => {
          if (!isAuthenticated) setIsAuthOpen(true);
          else setIsProfileOpen(true);
        }}
      />

      {/* Main View Body */}
      <main className="flex-1 p-4 md:p-6">
        {activeTableId ? (
          <TeenPattiTableUI tableId={activeTableId} onLeaveTable={handleLeaveTable} />
        ) : (
          <LobbyView onJoinTable={handleJoinTable} onOpenAuth={() => setIsAuthOpen(true)} />
        )}
      </main>

      {/* Modals & Drawers */}
      <AuthModal isOpen={isAuthOpen} onClose={() => setIsAuthOpen(false)} />
      <WalletModal isOpen={isWalletOpen} onClose={() => setIsWalletOpen(false)} />
      <LeaderboardModal isOpen={isLeaderboardOpen} onClose={() => setIsLeaderboardOpen(false)} />
      <NotificationDrawer isOpen={isNotificationsOpen} onClose={() => setIsNotificationsOpen(false)} />
      <AdminModal isOpen={isAdminOpen} onClose={() => setIsAdminOpen(false)} />
      <ProfileModal isOpen={isProfileOpen} onClose={() => setIsProfileOpen(false)} />

      {/* First-Time Onboarding Tutorial Overlay */}
      <TutorialOverlay user={user} />
    </div>
  );
}
