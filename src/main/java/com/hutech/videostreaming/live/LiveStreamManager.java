package com.hutech.videostreaming.live;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * Enhanced Live Stream Manager with Real Webcam & Screen Capture
 */
public class LiveStreamManager {

    private volatile boolean isStreaming = false;
    private StreamSource currentSource;
    private ExecutorService streamExecutor;
    private ScheduledExecutorService captureScheduler;

    // Stream settings
    private int fps = 25;
    private double quality = 0.8;
    private boolean compressionEnabled = true;
    private Rectangle captureRegion = null;

    // Webcam
    private Webcam webcam;
    private Dimension webcamResolution = WebcamResolution.VGA.getSize();

    // Screen capture
    private Robot robot;

    // Statistics
    private long framesCaptures = 0;
    private long bytesTransmitted = 0;
    private long lastFrameTime = 0;
    private double actualFPS = 0;

    // Callback
    private StreamDataCallback dataCallback;

    public enum StreamSource {
        WEBCAM("Webcam"),
        SCREEN("Screen Capture"),
        SCREEN_REGION("Screen Region"),
        WINDOW("Window Capture");

        private final String name;
        StreamSource(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public interface StreamDataCallback {
        void onFrameReady(byte[] frameData, long timestamp);
        void onError(String error);
        void onStatisticsUpdate(StreamStatistics stats);
    }

    public LiveStreamManager(StreamDataCallback callback) {
        this.dataCallback = callback;
        this.streamExecutor = Executors.newSingleThreadExecutor();
        this.captureScheduler = Executors.newScheduledThreadPool(2);

        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            System.err.println("❌ [LIVE] Failed to initialize Robot: " + e.getMessage());
        }
    }

    // ==================== WEBCAM STREAMING ====================

    /**
     * Start webcam streaming
     */
    public void startWebcamStream() {
        if (isStreaming) {
            stopStreaming();
        }

        try {
            // Get default webcam
            webcam = Webcam.getDefault();

            if (webcam == null) {
                throw new RuntimeException("No webcam found!");
            }

            webcam.setViewSize(webcamResolution);
            webcam.open();

            isStreaming = true;
            currentSource = StreamSource.WEBCAM;

            System.out.println("📹 [LIVE] Webcam streaming started");
            System.out.println("   Resolution: " + webcamResolution.width + "x" + webcamResolution.height);
            System.out.println("   FPS: " + fps);

            // Start capture loop
            long delay = 1000 / fps;
            captureScheduler.scheduleAtFixedRate(() -> {
                if (!isStreaming || webcam == null) return;

                try {
                    BufferedImage image = webcam.getImage();

                    if (image != null) {
                        processAndSendFrame(image);
                    }

                } catch (Exception e) {
                    System.err.println("❌ [LIVE] Webcam capture error: " + e.getMessage());
                }

            }, 0, delay, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("❌ [LIVE] Failed to start webcam: " + e.getMessage());
            if (dataCallback != null) {
                dataCallback.onError("Webcam error: " + e.getMessage());
            }
        }
    }

    /**
     * Change webcam resolution
     */
    public void setWebcamResolution(Dimension resolution) {
        this.webcamResolution = resolution;

        if (webcam != null && webcam.isOpen()) {
            boolean wasStreaming = isStreaming;
            stopStreaming();

            if (wasStreaming) {
                startWebcamStream();
            }
        }
    }

    /**
     * List available webcams
     */
    public static java.util.List<Webcam> getAvailableWebcams() {
        return Webcam.getWebcams();
    }

    // ==================== SCREEN CAPTURE ====================

    /**
     * Start full screen capture
     */
    public void startScreenCapture() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle fullScreen = new Rectangle(screenSize);
        startScreenCapture(fullScreen);
    }

    /**
     * Start screen capture with custom region
     */
    public void startScreenCapture(Rectangle region) {
        if (isStreaming) {
            stopStreaming();
        }

        if (robot == null) {
            if (dataCallback != null) {
                dataCallback.onError("Screen capture not available");
            }
            return;
        }

        isStreaming = true;
        currentSource = region.width == Toolkit.getDefaultToolkit().getScreenSize().width ?
                StreamSource.SCREEN : StreamSource.SCREEN_REGION;
        this.captureRegion = region;

        System.out.println("🖥️ [LIVE] Screen capture started");
        System.out.println("   Region: " + region.width + "x" + region.height +
                " at (" + region.x + "," + region.y + ")");
        System.out.println("   FPS: " + fps);

        // Start capture loop
        long delay = 1000 / fps;
        captureScheduler.scheduleAtFixedRate(() -> {
            if (!isStreaming) return;

            try {
                BufferedImage screenshot = robot.createScreenCapture(captureRegion);
                processAndSendFrame(screenshot);

            } catch (Exception e) {
                System.err.println("❌ [LIVE] Screen capture error: " + e.getMessage());
            }

        }, 0, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Update capture region on-the-fly
     */
    public void updateCaptureRegion(Rectangle newRegion) {
        this.captureRegion = newRegion;
        System.out.println("🔄 [LIVE] Capture region updated: " +
                newRegion.width + "x" + newRegion.height);
    }

    // ==================== FRAME PROCESSING ====================

    /**
     * Process and send frame
     */
    private void processAndSendFrame(BufferedImage image) {
        try {
            // Resize if needed
            if (image.getWidth() > 1920 || image.getHeight() > 1080) {
                image = resizeImage(image, 1920, 1080);
            }

            // Convert to bytes
            byte[] frameData = imageToBytes(image);

            // Update statistics
            framesCaptures++;
            bytesTransmitted += frameData.length;

            long now = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                actualFPS = 1000.0 / (now - lastFrameTime);
            }
            lastFrameTime = now;

            // Send frame
            if (dataCallback != null) {
                dataCallback.onFrameReady(frameData, now);
            }

            // Send statistics update every 30 frames
            if (framesCaptures % 30 == 0 && dataCallback != null) {
                dataCallback.onStatisticsUpdate(getStatistics());
            }

        } catch (Exception e) {
            System.err.println("❌ [LIVE] Frame processing error: " + e.getMessage());
        }
    }

    /**
     * Convert image to bytes with compression
     */
    private byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (compressionEnabled) {
            // JPEG compression
            ImageIO.write(image, "jpg", baos);
        } else {
            // PNG (lossless but larger)
            ImageIO.write(image, "png", baos);
        }

        byte[] imageBytes = baos.toByteArray();

        // Optional: Additional compression with GZIP
        if (compressionEnabled && imageBytes.length > 50000) {
            ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(gzipBaos)) {
                gzipOut.write(imageBytes);
            }
            return gzipBaos.toByteArray();
        }

        return imageBytes;
    }

    /**
     * Resize image maintaining aspect ratio
     */
    private BufferedImage resizeImage(BufferedImage original, int maxWidth, int maxHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        double scale = Math.min(
                (double) maxWidth / originalWidth,
                (double) maxHeight / originalHeight
        );

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        // High quality rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    // ==================== CONTROL METHODS ====================

    /**
     * Stop streaming
     */
    public void stopStreaming() {
        isStreaming = false;

        // Stop webcam
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            webcam = null;
        }

        // Stop scheduler
        if (captureScheduler != null && !captureScheduler.isShutdown()) {
            captureScheduler.shutdown();
            try {
                if (!captureScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    captureScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                captureScheduler.shutdownNow();
            }
            captureScheduler = Executors.newScheduledThreadPool(2);
        }

        System.out.println("⏹️ [LIVE] Streaming stopped");
        System.out.println("📊 Statistics:");
        System.out.println("   Frames captured: " + framesCaptures);
        System.out.println("   Data transmitted: " + formatBytes(bytesTransmitted));
        System.out.println("   Avg FPS: " + String.format("%.1f", actualFPS));
    }

    /**
     * Pause streaming
     */
    public void pauseStreaming() {
        isStreaming = false;
        System.out.println("⏸️ [LIVE] Streaming paused");
    }

    /**
     * Resume streaming
     */
    public void resumeStreaming() {
        isStreaming = true;
        System.out.println("▶️ [LIVE] Streaming resumed");
    }

    /**
     * Set quality and FPS
     */
    public void setQuality(double quality, int fps) {
        this.quality = Math.max(0.1, Math.min(1.0, quality));
        this.fps = Math.max(1, Math.min(60, fps));

        System.out.println("⚙️ [LIVE] Quality updated:");
        System.out.println("   Quality: " + (this.quality * 100) + "%");
        System.out.println("   FPS: " + this.fps);

        // Restart if streaming
        if (isStreaming) {
            StreamSource source = currentSource;
            Rectangle region = captureRegion;
            stopStreaming();

            switch (source) {
                case WEBCAM:
                    startWebcamStream();
                    break;
                case SCREEN:
                case SCREEN_REGION:
                    startScreenCapture(region != null ? region :
                            new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                    break;
            }
        }
    }

    /**
     * Enable/disable compression
     */
    public void setCompression(boolean enabled) {
        this.compressionEnabled = enabled;
        System.out.println("🗜️ [LIVE] Compression: " + (enabled ? "Enabled" : "Disabled"));
    }

    // ==================== GETTERS ====================

    public StreamStatistics getStatistics() {
        StreamStatistics stats = new StreamStatistics();
        stats.source = currentSource;
        stats.isStreaming = isStreaming;
        stats.fps = fps;
        stats.actualFPS = actualFPS;
        stats.quality = quality;
        stats.compressionEnabled = compressionEnabled;
        stats.framesCaptures = framesCaptures;
        stats.bytesTransmitted = bytesTransmitted;
        stats.resolution = captureRegion != null ?
                captureRegion.width + "x" + captureRegion.height : "N/A";
        return stats;
    }

    public boolean isStreaming() { return isStreaming; }
    public StreamSource getCurrentSource() { return currentSource; }

    // ==================== UTILITIES ====================

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        stopStreaming();

        if (streamExecutor != null) {
            streamExecutor.shutdown();
        }
    }

    // ==================== STATISTICS CLASS ====================

    public static class StreamStatistics {
        public StreamSource source;
        public boolean isStreaming;
        public int fps;
        public double actualFPS;
        public double quality;
        public boolean compressionEnabled;
        public long framesCaptures;
        public long bytesTransmitted;
        public String resolution;

        @Override
        public String toString() {
            return String.format(
                    "Stream Stats: Source=%s, FPS=%.1f/%d, Quality=%.0f%%, " +
                            "Frames=%d, Data=%s, Compression=%s",
                    source, actualFPS, fps, quality * 100, framesCaptures,
                    formatBytes(bytesTransmitted), compressionEnabled ? "ON" : "OFF"
            );
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}