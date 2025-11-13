import { FrameMessage } from './websocket.js';

/**
 * FrameViewer class for displaying processed EdgeVision frames
 */
export class FrameViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private noFrameMessage: HTMLElement | null;
    private width: number = 1280;
    private height: number = 720;
    private currentFps: number = 0;
    private lastFrameTime: number = 0;
    private frameCount: number = 0;

    constructor(canvasId: string) {
        const canvas = document.getElementById(canvasId) as HTMLCanvasElement;
        if (!canvas) {
            throw new Error(`Canvas element with id '${canvasId}' not found`);
        }

        this.canvas = canvas;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            throw new Error('Failed to get 2D rendering context');
        }

        this.ctx = ctx;
        this.noFrameMessage = document.getElementById('noFrameMessage');

        console.log(`FrameViewer initialized for canvas: ${canvasId}`);
    }

    /**
     * Load and display a frame from base64 encoded data
     */
    public async loadFrameFromBase64(base64Data: string): Promise<void> {
        try {
            const img = new Image();

            return new Promise((resolve, reject) => {
                img.onload = () => {
                    this.ctx.clearRect(0, 0, this.width, this.height);
                    this.ctx.drawImage(img, 0, 0, this.width, this.height);
                    this.hideNoFrameMessage();
                    this.updateStats();
                    console.log('Frame loaded successfully from base64');
                    resolve();
                };

                img.onerror = () => {
                    console.error('Failed to load image from base64');
                    reject(new Error('Failed to load image'));
                };

                img.src = `data:image/png;base64,${base64Data}`;
            });
        } catch (error) {
            console.error('Error loading frame:', error);
            throw error;
        }
    }

    /**
     * Load sample processed frame (simulated Canny edge detection)
     */
    public loadSampleFrame(): void {
        console.log('Generating sample Canny edge detection frame...');

        // Clear canvas
        this.ctx.fillStyle = '#000';
        this.ctx.fillRect(0, 0, this.width, this.height);

        // Draw sample edge detection pattern
        this.ctx.strokeStyle = '#fff';
        this.ctx.lineWidth = 2;

        // Draw grid pattern (simulating edges)
        for (let x = 0; x < this.width; x += 50) {
            this.ctx.beginPath();
            this.ctx.moveTo(x, 0);
            this.ctx.lineTo(x, this.height);
            this.ctx.stroke();
        }

        for (let y = 0; y < this.height; y += 50) {
            this.ctx.beginPath();
            this.ctx.moveTo(0, y);
            this.ctx.lineTo(this.width, y);
            this.ctx.stroke();
        }

        // Draw sample geometric shapes (simulating detected edges)
        this.ctx.beginPath();
        this.ctx.arc(640, 360, 200, 0, Math.PI * 2);
        this.ctx.stroke();

        this.ctx.beginPath();
        this.ctx.rect(300, 200, 300, 320);
        this.ctx.stroke();

        this.ctx.beginPath();
        this.ctx.moveTo(900, 200);
        this.ctx.lineTo(1100, 200);
        this.ctx.lineTo(1000, 450);
        this.ctx.closePath();
        this.ctx.stroke();

        // Add text overlay
        this.ctx.fillStyle = '#fff';
        this.ctx.font = '24px monospace';
        this.ctx.fillText('Sample Canny Edge Detection Output', 50, 50);
        this.ctx.font = '18px monospace';
        this.ctx.fillText('OpenCV C++ Processing - EdgeVision', 50, 680);

        this.hideNoFrameMessage();
        this.updateStats();
        console.log('Sample frame rendered');
    }

    /**
     * Load frame from raw grayscale byte array
     */
    public loadFrameFromBytes(data: Uint8Array, width: number, height: number): void {
        const imageData = this.ctx.createImageData(width, height);

        // Convert grayscale to RGBA
        for (let i = 0; i < data.length; i++) {
            const gray = data[i];
            const idx = i * 4;
            imageData.data[idx] = gray;     // R
            imageData.data[idx + 1] = gray; // G
            imageData.data[idx + 2] = gray; // B
            imageData.data[idx + 3] = 255;  // A
        }

        this.ctx.putImageData(imageData, 0, 0);
        this.hideNoFrameMessage();
        this.updateStats(width, height, data.length);
        console.log(`Frame loaded from bytes: ${width}x${height}`);
    }

    /**
     * Display frame from WebSocket message
     */
    public displayWebSocketFrame(frame: FrameMessage): void {
        try {
            // Update canvas size if needed (rotate dimensions for portrait)
            if (this.width !== frame.height || this.height !== frame.width) {
                this.width = frame.height;
                this.height = frame.width;
                this.canvas.width = frame.height;
                this.canvas.height = frame.width;
            }

            // Decode base64 frame data
            const binaryString = atob(frame.frameData);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }

            // Create image data from original frame
            const tempImageData = this.ctx.createImageData(frame.width, frame.height);
            for (let i = 0; i < bytes.length; i++) {
                const gray = bytes[i];
                const idx = i * 4;
                tempImageData.data[idx] = gray;
                tempImageData.data[idx + 1] = gray;
                tempImageData.data[idx + 2] = gray;
                tempImageData.data[idx + 3] = 255;
            }

            // Clear canvas and rotate 90 degrees clockwise
            this.ctx.save();
            this.ctx.clearRect(0, 0, this.width, this.height);

            // Translate to center, rotate 90 degrees clockwise, then translate back
            this.ctx.translate(this.width, 0);
            this.ctx.rotate(Math.PI / 2);

            // Draw the rotated image
            const tempCanvas = document.createElement('canvas');
            tempCanvas.width = frame.width;
            tempCanvas.height = frame.height;
            const tempCtx = tempCanvas.getContext('2d')!;
            tempCtx.putImageData(tempImageData, 0, 0);
            this.ctx.drawImage(tempCanvas, 0, 0);

            this.ctx.restore();
            this.hideNoFrameMessage();

            // Calculate FPS
            this.calculateFps();

            // Update stats with frame metadata
            this.updateStatsFromFrame(frame);
        } catch (error) {
            console.error('Error displaying WebSocket frame:', error);
        }
    }

    /**
     * Calculate FPS from incoming frames
     */
    private calculateFps(): void {
        const now = performance.now();
        if (this.lastFrameTime > 0) {
            const delta = now - this.lastFrameTime;
            const instantFps = 1000 / delta;
            // Smooth FPS with exponential moving average
            this.currentFps = this.currentFps * 0.9 + instantFps * 0.1;
        }
        this.lastFrameTime = now;
        this.frameCount++;
    }

    /**
     * Update stats from WebSocket frame message
     */
    private updateStatsFromFrame(frame: FrameMessage): void {
        const resolutionEl = document.getElementById('resolution');
        const frameSizeEl = document.getElementById('frameSize');
        const lastUpdatedEl = document.getElementById('lastUpdated');
        const formatEl = document.getElementById('format');
        const fpsEl = document.getElementById('fps');

        if (resolutionEl) resolutionEl.textContent = `${frame.width} x ${frame.height}`;
        if (frameSizeEl) frameSizeEl.textContent = `${frame.frameSize.toLocaleString()} bytes`;
        if (lastUpdatedEl) lastUpdatedEl.textContent = new Date(frame.timestamp).toLocaleTimeString();
        if (formatEl) formatEl.textContent = frame.processingMode;
        if (fpsEl) fpsEl.textContent = `${this.currentFps.toFixed(1)} FPS (client)`;
    }

    /**
     * Update statistics display
     */
    private updateStats(width: number = 1280, height: number = 720, size: number = 921600): void {
        const resolutionEl = document.getElementById('resolution');
        const frameSizeEl = document.getElementById('frameSize');
        const lastUpdatedEl = document.getElementById('lastUpdated');

        if (resolutionEl) resolutionEl.textContent = `${width} x ${height}`;
        if (frameSizeEl) frameSizeEl.textContent = `${size.toLocaleString()} bytes`;
        if (lastUpdatedEl) lastUpdatedEl.textContent = new Date().toLocaleTimeString();
    }

    /**
     * Hide the "no frame" message
     */
    private hideNoFrameMessage(): void {
        if (this.noFrameMessage) {
            this.noFrameMessage.classList.add('hidden');
        }
    }

    /**
     * Show the "no frame" message
     */
    private showNoFrameMessage(): void {
        if (this.noFrameMessage) {
            this.noFrameMessage.classList.remove('hidden');
        }
    }

    /**
     * Clear the canvas
     */
    public clear(): void {
        this.ctx.clearRect(0, 0, this.width, this.height);
        this.showNoFrameMessage();
    }
}
