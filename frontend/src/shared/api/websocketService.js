class WebSocketGameService {
  constructor() {
    this.socket = null;
    this.listeners = new Set();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 20;
    this.reconnectInterval = 2000;
    this.pendingMessages = [];
    this.openListeners = new Set();
    this.token = null;
  }

  connect(token, onMessageCallback, onStatusChangeCallback) {
    if (onMessageCallback) {
      this.listeners.add(onMessageCallback);
    }

    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      if (this.socket.readyState === WebSocket.OPEN) {
        this.openListeners.forEach((fn) => {
          try { fn(); } catch { /* ignore */ }
        });
      }
      return;
    }

    this.token = token;
    const wsUrl = `${import.meta.env.VITE_SOCKET_URL || 'ws://localhost:8080/ws'}/game?token=${encodeURIComponent(token)}`;
    console.log('Connecting to WebSocket:', wsUrl);

    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        console.log('WebSocket Connection Established');
        this.reconnectAttempts = 0;
        if (onStatusChangeCallback) onStatusChangeCallback(true);
        this.flushPending();
        this.openListeners.forEach((fn) => {
          try { fn(); } catch { /* ignore */ }
        });
      };

      this.socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          if (onMessageCallback) onMessageCallback(message);
          this.listeners.forEach((listener) => listener(message));
        } catch (err) {
          console.error('Failed to parse WebSocket message:', err);
        }
      };

      this.socket.onerror = (error) => {
        console.error('WebSocket Error:', error);
      };

      this.socket.onclose = (event) => {
        console.log('WebSocket Disconnected:', event.code, event.reason);
        if (onStatusChangeCallback) onStatusChangeCallback(false);

        if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts && this.token) {
          this.reconnectAttempts += 1;
          setTimeout(() => {
            console.log(`Reconnecting attempt ${this.reconnectAttempts}...`);
            this.connect(this.token, onMessageCallback, onStatusChangeCallback);
          }, this.reconnectInterval);
        }
      };
    } catch (err) {
      console.error('WebSocket Connection Failed:', err);
    }
  }

  /** Fires immediately if already open; otherwise when the socket opens. */
  onOpen(listener) {
    this.openListeners.add(listener);
    if (this.socket?.readyState === WebSocket.OPEN) {
      try { listener(); } catch { /* ignore */ }
    }
    return () => this.openListeners.delete(listener);
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  flushPending() {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    const queued = [...this.pendingMessages];
    this.pendingMessages = [];
    queued.forEach((msg) => {
      try {
        this.socket.send(JSON.stringify(msg));
      } catch (err) {
        console.warn('Failed to flush pending WS message', err);
      }
    });
  }

  sendMessage(type, tableId, extraData = {}) {
    const msg = {
      type,
      tableId,
      ...extraData,
    };
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(msg));
      return true;
    }
    this.pendingMessages.push(msg);
    console.warn('WebSocket not open yet — queued', type);
    return false;
  }

  disconnect() {
    this.pendingMessages = [];
    this.openListeners.clear();
    if (this.socket) {
      this.socket.close(1000, 'User Disconnected');
      this.socket = null;
    }
  }
}

export const wsGameService = new WebSocketGameService();
export default wsGameService;
