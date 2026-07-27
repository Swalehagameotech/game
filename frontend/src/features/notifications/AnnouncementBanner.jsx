import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Megaphone, X } from 'lucide-react';

/**
 * Dismissible banner for admin/system announcements received over STOMP.
 */
export default function AnnouncementBanner() {
  const [announcement, setAnnouncement] = useState(null);

  useEffect(() => {
    const handler = (event) => {
      const payload = event.detail;
      if (!payload) return;
      setAnnouncement({
        title: payload.title || 'System Announcement',
        message: payload.message || payload.body || (typeof payload === 'string' ? payload : ''),
      });
    };
    window.addEventListener('announcement', handler);
    return () => window.removeEventListener('announcement', handler);
  }, []);

  if (!announcement) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        className="fixed top-20 left-1/2 -translate-x-1/2 z-[60] w-[min(92vw,520px)]"
      >
        <div className="bg-amber-500/10 border border-amber-500/40 backdrop-blur-md rounded-2xl p-4 shadow-2xl flex items-start gap-3">
          <Megaphone className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-amber-300">{announcement.title}</p>
            {announcement.message ? (
              <p className="text-xs text-slate-200 mt-1 leading-relaxed">{announcement.message}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={() => setAnnouncement(null)}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-200"
            aria-label="Dismiss announcement"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
