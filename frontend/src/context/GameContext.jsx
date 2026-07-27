import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

const GameContext = createContext(null);

export const GameProvider = ({ children }) => {
  const [activeTable, setActiveTable] = useState(null);
  const [gameState, setGameState] = useState(null);
  const [isWsConnected, setIsWsConnected] = useState(false);
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);
  const [notifications, setNotifications] = useState([]);

  const updateTableState = useCallback((tableData) => {
    setActiveTable(tableData);
  }, []);

  const updateGameState = useCallback((stateData) => {
    if (typeof stateData === 'function') {
      setGameState((prev) => stateData(prev));
    } else {
      setGameState(stateData);
    }
  }, []);

  const addNotification = useCallback((notification) => {
    setNotifications((prev) => [notification, ...prev]);
    setUnreadNotificationsCount((prev) => prev + 1);
  }, []);

  const clearNotifications = useCallback(() => {
    setNotifications([]);
    setUnreadNotificationsCount(0);
  }, []);

  const value = useMemo(
    () => ({
      activeTable,
      gameState,
      isWsConnected,
      setIsWsConnected,
      notifications,
      unreadNotificationsCount,
      updateTableState,
      updateGameState,
      addNotification,
      clearNotifications,
      setUnreadNotificationsCount,
    }),
    [
      activeTable,
      gameState,
      isWsConnected,
      notifications,
      unreadNotificationsCount,
      updateTableState,
      updateGameState,
      addNotification,
      clearNotifications,
    ]
  );

  return (
    <GameContext.Provider value={value}>
      {children}
    </GameContext.Provider>
  );
};

export const useGame = () => {
  const context = useContext(GameContext);
  if (!context) {
    throw new Error('useGame must be used within a GameProvider');
  }
  return context;
};
