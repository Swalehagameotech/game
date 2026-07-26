import React, { createContext, useContext, useState } from 'react';

const GameContext = createContext(null);

export const GameProvider = ({ children }) => {
  const [activeTable, setActiveTable] = useState(null);
  const [gameState, setGameState] = useState(null);
  const [isWsConnected, setIsWsConnected] = useState(false);
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);
  const [notifications, setNotifications] = useState([]);

  const updateTableState = (tableData) => {
    setActiveTable(tableData);
  };

  const updateGameState = (stateData) => {
    setGameState(stateData);
  };

  const addNotification = (notification) => {
    setNotifications((prev) => [notification, ...prev]);
    setUnreadNotificationsCount((prev) => prev + 1);
  };

  const clearNotifications = () => {
    setNotifications([]);
    setUnreadNotificationsCount(0);
  };

  return (
    <GameContext.Provider
      value={{
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
      }}
    >
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
