/**
 * Global State Management using React Context API.
 * Redux has been removed and replaced with React Context providers (AuthContext, GameContext).
 */
export { AuthProvider, useAuth } from '@/context/AuthContext';
export { GameProvider, useGame } from '@/context/GameContext';
