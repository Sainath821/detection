/**
 * WebSocket client for receiving real-time frames from EdgeVision Android app
 */

export interface FrameMessage {
    timestamp: string;
    width: number;
    height: number;
    format: string;
    processingMode: string;
    fps: number;
    frameData: string;  // Base64 encoded
    frameSize: number;
}

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export class WebSocketClient {
    private ws: WebSocket | null = null;
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 1000; // Start with 1 second
    private reconnectTimer: number | null = null;
    private isManuallyDisconnected = false;

    // Event callbacks
    public onConnectionStatusChanged: ((status: ConnectionStatus) => void) | null = null;
    public onFrameReceived: ((frame: FrameMessage) => void) | null = null;
    public onError: ((error: string) => void) | null = null;

    private currentStatus: ConnectionStatus = 'disconnected';

    constructor() {
        console.log('WebSocketClient initialized');
    }

    /**
     * Connect to WebSocket server
     */
    public connect(host: string, port: number = 8888): void {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            console.warn('Already connected to WebSocket');
            return;
        }

        this.isManuallyDisconnected = false;
        const url = `ws://${host}:${port}`;
        console.log(`Connecting to WebSocket: ${url}`);

        try {
            this.updateStatus('connecting');
            this.ws = new WebSocket(url);

            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.reconnectAttempts = 0;
                this.reconnectDelay = 1000;
                this.updateStatus('connected');
            };

            this.ws.onmessage = (event) => {
                this.handleMessage(event.data);
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.updateStatus('error');
                this.onError?.('WebSocket connection error');
            };

            this.ws.onclose = (event) => {
                console.log(`WebSocket closed: ${event.code} - ${event.reason}`);
                this.updateStatus('disconnected');

                // Auto-reconnect if not manually disconnected
                if (!this.isManuallyDisconnected && this.reconnectAttempts < this.maxReconnectAttempts) {
                    this.scheduleReconnect(host, port);
                }
            };
        } catch (error) {
            console.error('Error creating WebSocket:', error);
            this.updateStatus('error');
            this.onError?.(`Failed to connect: ${error}`);
        }
    }

    /**
     * Disconnect from WebSocket server
     */
    public disconnect(): void {
        console.log('Disconnecting from WebSocket');
        this.isManuallyDisconnected = true;
        this.clearReconnectTimer();

        if (this.ws) {
            this.ws.close(1000, 'Client disconnected');
            this.ws = null;
        }

        this.updateStatus('disconnected');
    }

    /**
     * Check if connected
     */
    public isConnected(): boolean {
        return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
    }

    /**
     * Get current connection status
     */
    public getStatus(): ConnectionStatus {
        return this.currentStatus;
    }

    /**
     * Handle incoming WebSocket message
     */
    private handleMessage(data: string): void {
        try {
            // Check if it's an echo message (for testing)
            if (data.startsWith('Echo:')) {
                console.log('Received echo:', data);
                return;
            }

            // Parse frame message
            const frame: FrameMessage = JSON.parse(data);

            // Validate frame structure
            if (!this.isValidFrame(frame)) {
                console.error('Invalid frame structure:', frame);
                return;
            }

            // Emit frame to listeners
            this.onFrameReceived?.(frame);
        } catch (error) {
            console.error('Error parsing WebSocket message:', error);
            this.onError?.(`Failed to parse frame: ${error}`);
        }
    }

    /**
     * Validate frame message structure
     */
    private isValidFrame(frame: any): frame is FrameMessage {
        return (
            typeof frame === 'object' &&
            typeof frame.timestamp === 'string' &&
            typeof frame.width === 'number' &&
            typeof frame.height === 'number' &&
            typeof frame.format === 'string' &&
            typeof frame.processingMode === 'string' &&
            typeof frame.fps === 'number' &&
            typeof frame.frameData === 'string' &&
            typeof frame.frameSize === 'number'
        );
    }

    /**
     * Schedule reconnection attempt
     */
    private scheduleReconnect(host: string, port: number): void {
        this.clearReconnectTimer();

        this.reconnectAttempts++;
        const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1); // Exponential backoff

        console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

        this.reconnectTimer = window.setTimeout(() => {
            console.log('Attempting to reconnect...');
            this.connect(host, port);
        }, delay);
    }

    /**
     * Clear reconnect timer
     */
    private clearReconnectTimer(): void {
        if (this.reconnectTimer !== null) {
            window.clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }

    /**
     * Update connection status
     */
    private updateStatus(status: ConnectionStatus): void {
        if (this.currentStatus !== status) {
            this.currentStatus = status;
            this.onConnectionStatusChanged?.(status);
        }
    }
}
