class WebSocketGameService {
  constructor() {
    this.socket = null;
    this.listeners = new Set();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectInterval = 2000;
  }

  connect(token, onMessageCallback, onStatusChangeCallback) {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const wsUrl = `${import.meta.env.VITE_SOCKET_URL || 'ws://localhost:8080/ws'}/game?token=${encodeURIComponent(token)}`;
    console.log('Connecting to WebSocket:', wsUrl);

    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        console.log('WebSocket Connection Established');
        this.reconnectAttempts = 0;
        if (onStatusChangeCallback) onStatusChangeCallback(true);
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

        if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts += 1;
          setTimeout(() => {
            console.log(`Reconnecting attempt ${this.reconnectAttempts}...`);
            this.connect(token, onMessageCallback, onStatusChangeCallback);
          }, this.reconnectInterval);
        }
      };
    } catch (err) {
      console.error('WebSocket Connection Failed:', err);
    }
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  sendMessage(type, tableId, payload = {}) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      const msg = {
        type,
        tableId,
        payload,
      };
      this.socket.send(JSON.stringify(msg));
      return true;
    }
    console.warn('Cannot send WebSocket message: Socket not open');
    return false;
  }

  disconnect() {
    if (this.socket) {
      this.socket.close(1000, 'User Disconnected');
      this.socket = null;
    }
  }
}

export const wsGameService = new WebSocketGameService();
export default wsGameService;
