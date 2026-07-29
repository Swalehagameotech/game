import { Client } from '@stomp/stompjs';
import { StompDestinations } from './realtimeEvents';

/**
 * STOMP client for lobby, wallet, and platform events (/ws/stomp).
 * Gameplay actions use raw WebSocket via websocketService.js (/ws/game).
 */
class StompRealtimeService {
  constructor() {
    this.client = null;
    this.listeners = new Set();
    this.subscriptions = [];
    this.tableIds = new Set();
    this.connected = false;
    this.userId = null;
    this.token = null;
  }

  isConnected() {
    return this.connected && !!this.client?.connected;
  }

  connect(token, userId) {
    if (!token) return;

    if (this.client?.active && this.userId === userId && this.token === token) {
      return;
    }

    if (this.client?.active) {
      this.disconnect();
    }

    this.userId = userId;
    this.token = token;
    const base = import.meta.env.VITE_STOMP_URL
      || import.meta.env.VITE_SOCKET_URL
      || 'ws://localhost:8080/ws';
    const normalizedBase = base.replace(/\/$/, '');
    const brokerURL = `${normalizedBase}/stomp?token=${encodeURIComponent(token)}`;

    this.client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected = true;
        this.subscribeDefaults(userId);
        this.tableIds.forEach((tableId) => {
          this.subscribeDestination(StompDestinations.topicTable(tableId));
        });
        this.listeners.forEach((fn) => fn({ eventType: 'STOMP_CONNECTED', payload: null }));
      },
      onDisconnect: () => {
        this.connected = false;
        this.clearSubscriptions();
      },
      onStompError: (frame) => {
        console.warn('STOMP error:', frame.headers?.message || frame.body || 'unknown');
      },
      onWebSocketError: () => {
        // Backend may be restarting; reconnectDelay handles retry without console spam.
        this.connected = false;
      },
    });

    this.client.activate();
  }

  subscribeDefaults(userId) {
    this.clearSubscriptions();
    this.subscribeDestination(StompDestinations.TOPIC_TABLES);
    this.subscribeDestination(StompDestinations.TOPIC_ANNOUNCEMENTS);
    this.subscribeDestination(StompDestinations.TOPIC_USERS);
    if (userId) {
      this.subscribeDestination(StompDestinations.queueWallet(userId));
      this.subscribeDestination(StompDestinations.queueNotifications(userId));
      this.subscribeDestination(StompDestinations.queueGame(userId));
    }
  }

  subscribeDestination(destination) {
    if (!this.client?.connected || !destination) return null;

    const sub = this.client.subscribe(destination, (message) => {
      try {
        const event = JSON.parse(message.body);
        const normalized = {
          eventType: event.eventType || event.type,
          payload: event.payload !== undefined ? event.payload : event,
          timestamp: event.timestamp,
          destination,
        };
        this.listeners.forEach((listener) => listener(normalized));
      } catch (err) {
        console.error('Failed to parse STOMP message:', err);
      }
    });

    this.subscriptions.push(sub);
    return sub;
  }

  subscribeTable(tableId) {
    if (!tableId) return null;
    this.tableIds.add(tableId);
    const sub = this.subscribeDestination(StompDestinations.topicTable(tableId));
    return {
      unsubscribe: () => {
        this.tableIds.delete(tableId);
        try { sub?.unsubscribe?.(); } catch { /* ignore */ }
      },
    };
  }

  /** Register a local listener for all STOMP events. Returns unsubscribe fn. */
  onEvent(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Alias used by RealtimeProvider */
  subscribe(listener) {
    return this.onEvent(listener);
  }

  clearSubscriptions() {
    this.subscriptions.forEach((sub) => {
      try {
        sub.unsubscribe();
      } catch {
        // ignore
      }
    });
    this.subscriptions = [];
  }

  disconnect() {
    this.clearSubscriptions();
    this.tableIds.clear();
    this.connected = false;
    this.userId = null;
    this.token = null;
    if (this.client) {
      try {
        this.client.deactivate();
      } catch {
        // ignore
      }
      this.client = null;
    }
  }
}

export const stompService = new StompRealtimeService();
export default stompService;
