import { FrameViewer } from './viewer.js';
import { WebSocketClient, ConnectionStatus } from './websocket.js';

/**
 * EdgeVision Web Viewer Entry Point
 * Displays processed frames from EdgeVision Android app
 */

console.log('EdgeVision Web Viewer initializing...');

// Initialize the frame viewer
const viewer = new FrameViewer('frameCanvas');

// Initialize WebSocket client
const wsClient = new WebSocketClient();

// UI Elements
let connectBtn: HTMLButtonElement;
let disconnectBtn: HTMLButtonElement;
let serverIpInput: HTMLInputElement;
let serverPortInput: HTMLInputElement;
let connectionStatusEl: HTMLElement;

// Load saved connection settings
const loadConnectionSettings = (): void => {
    const savedIp = localStorage.getItem('edgevision_server_ip');
    if (savedIp && serverIpInput) {
        serverIpInput.value = savedIp;
    }
};

// Save connection settings
const saveConnectionSettings = (): void => {
    if (serverIpInput) {
        localStorage.setItem('edgevision_server_ip', serverIpInput.value);
    }
};

// Update connection status UI
const updateConnectionStatus = (status: ConnectionStatus): void => {
    if (!connectionStatusEl) return;

    connectionStatusEl.className = `status-value status-${status}`;
    connectionStatusEl.textContent = status.charAt(0).toUpperCase() + status.slice(1);

    // Update button states
    if (connectBtn && disconnectBtn) {
        connectBtn.disabled = status === 'connected' || status === 'connecting';
        disconnectBtn.disabled = status === 'disconnected';
    }
};

// Validate IP address format
const isValidIpAddress = (ip: string): boolean => {
    const ipPattern = /^(\d{1,3}\.){3}\d{1,3}$/;
    if (!ipPattern.test(ip)) return false;

    const parts = ip.split('.');
    return parts.every(part => {
        const num = parseInt(part);
        return num >= 0 && num <= 255;
    });
};

// Handle connection button click
const handleConnect = (): void => {
    const host = serverIpInput.value.trim();
    const port = parseInt(serverPortInput.value);

    if (!host) {
        alert('Please enter Android device IP address');
        return;
    }

    if (!isValidIpAddress(host)) {
        alert('Please enter a valid IP address (e.g., 192.168.1.100)');
        return;
    }

    if (isNaN(port) || port < 1 || port > 65535) {
        alert('Please enter a valid port number (1-65535)');
        return;
    }

    saveConnectionSettings();
    console.log(`Connecting to ${host}:${port}...`);
    wsClient.connect(host, port);
};

// Handle disconnect button click
const handleDisconnect = (): void => {
    console.log('Disconnecting...');
    wsClient.disconnect();
    viewer.clear();
};

// Setup WebSocket event handlers
const setupWebSocketHandlers = (): void => {
    wsClient.onConnectionStatusChanged = (status: ConnectionStatus) => {
        console.log(`Connection status changed: ${status}`);
        updateConnectionStatus(status);

        // Show user-friendly messages
        if (status === 'connected') {
            console.log('✓ Connected to EdgeVision Android app');
        } else if (status === 'connecting') {
            console.log('Connecting to EdgeVision...');
        } else if (status === 'error') {
            console.error('Connection error - will retry automatically');
        }
    };

    wsClient.onFrameReceived = (frame) => {
        viewer.displayWebSocketFrame(frame);
    };

    wsClient.onError = (error) => {
        console.error('WebSocket error:', error);
        // Don't alert on every error - just log it
        // Alert is too disruptive for auto-reconnect scenarios
    };
};

// Initialize UI event listeners
window.addEventListener('DOMContentLoaded', () => {
    console.log('DOM loaded, initializing UI...');

    // Get UI elements
    connectBtn = document.getElementById('connectBtn') as HTMLButtonElement;
    disconnectBtn = document.getElementById('disconnectBtn') as HTMLButtonElement;
    serverIpInput = document.getElementById('serverIp') as HTMLInputElement;
    serverPortInput = document.getElementById('serverPort') as HTMLInputElement;
    connectionStatusEl = document.getElementById('connectionStatus') as HTMLElement;

    // Setup WebSocket handlers
    setupWebSocketHandlers();

    // Load saved settings
    loadConnectionSettings();

    // Attach event listeners
    if (connectBtn) {
        connectBtn.addEventListener('click', handleConnect);
    }

    if (disconnectBtn) {
        disconnectBtn.addEventListener('click', handleDisconnect);
    }

    // Allow Enter key to connect
    if (serverIpInput) {
        serverIpInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !connectBtn.disabled) {
                handleConnect();
            }
        });
    }

    // Load sample frame initially
    viewer.loadSampleFrame();

    console.log('EdgeVision Web Viewer ready!');
});

// Export for potential future use
export { viewer, wsClient };