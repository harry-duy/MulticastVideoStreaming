package com.hutech.videostreaming.live;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import javafx.scene.media.*;

/**
 * Live streaming from camera or screen capture
 */
public class LiveStreamManager {

    private volatile boolean isStreaming = false;
    private StreamSource currentSource;
    private ExecutorService streamExecutor;
    private ScheduledExecutorService captureScheduler;

    // Stream settings
    private int fps = 25;
    private double quality = 0.8; // 0.0 to 1.0
    private boolean compressionEnabled = true;

    // Callback for sending data
    private StreamDataCallback dataCallback;

    public enum StreamSource {
        WEBCAM("Webcam"),
        SCREEN("Screen Capture"),
        WINDOW("Window Capture"),
        AUDIO("Audio Only");

        private final String name;
        StreamSource(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public interface StreamDataCallback {
        void onFrameReady(byte[] frameData, long timestamp);
        void onError(String error);
    }

    public LiveStreamManager(StreamDataCallback callback) {
        this.dataCallback = callback;
        this.streamExecutor = Executors.newSingleThreadExecutor();
        this.captureScheduler = Executors.newScheduledThreadPool(2);
    }

    // ==================== SCREEN CAPTURE ====================

    /**
     * Start screen capture streaming
     */
    public void startScreenCapture(Rectangle captureArea) {
        if (isStreaming) {
            stopStreaming();
        }

        isStreaming = true;
        currentSource = StreamSource.SCREEN;

        try {
            Robot robot = new Robot();

            // If no area specified, capture full screen
            if (captureArea == null) {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                captureArea = new Rectangle(screenSize);
            }

            final Rectangle area = captureArea;

            System.out.println("🖥️ [LIVE] Starting screen capture...");
            System.out.println("   Area: " + area.width + "x" + area.height);
            System.out.println("   FPS: " + fps);

            // Schedule capture task
            long delay = 1000 / fps;
            captureScheduler.scheduleAtFixedRate(() -> {
                if (!isStreaming) return;

                try {
                    // Capture screen
                    BufferedImage screenshot = robot.createScreenCapture(area);

                    // Resize if too large
                    if (screenshot.getWidth() > 1920 || screenshot.getHeight() > 1080) {
                        screenshot = resizeImage(screenshot, 1920, 1080);
                    }

                    // Convert to bytes
                    byte[] frameData = imageToBytes(screenshot);

                    // Send frame
                    if (dataCallback != null) {
                        dataCallback.onFrameReady(frameData, System.currentTimeMillis());
                    }

                } catch (Exception e) {
                    System.err.println("❌ [LIVE] Capture error: " + e.getMessage());
                }

            }, 0, delay, TimeUnit.MILLISECONDS);

        } catch (AWTException e) {
            System.err.println("❌ [LIVE] Failed to start screen capture: " + e.getMessage());
            if (dataCallback != null) {
                dataCallback.onError("Failed to start screen capture: " + e.getMessage());
            }
        }
    }

    /**
     * Start window capture (specific application)
     */
    public void startWindowCapture(String windowTitle) {
        // This would require platform-specific code (JNA/JNI)
        // Simplified version - captures area where window is located

        System.out.println("🪟 [LIVE] Window capture for: " + windowTitle);

        // For now, fallback to screen capture
        // In production, use JNA to find window bounds
        startScreenCapture(null);
    }

    // ==================== WEBCAM CAPTURE ====================

    /**
     * Start webcam streaming
     */
    public void startWebcamCapture() {
        if (isStreaming) {
            stopStreaming();
        }

        isStreaming = true;
        currentSource = StreamSource.WEBCAM;

        System.out.println("📹 [LIVE] Starting webcam capture...");

        // Using JavaFX Media for webcam (simplified)
        // In production, use libraries like webcam-capture or OpenCV

        Platform.runLater(() -> {
            try {
                // This is a placeholder - real implementation would use webcam library
                simulateWebcamCapture();

            } catch (Exception e) {
                System.err.println("❌ [LIVE] Webcam error: " + e.getMessage());
                if (dataCallback != null) {
                    dataCallback.onError("Webcam error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Simulate webcam capture (placeholder)
     */
    private void simulateWebcamCapture() {
        // Generate test pattern as placeholder
        long delay = 1000 / fps;

        captureScheduler.scheduleAtFixedRate(() -> {
            if (!isStreaming) return;

            try {
                // Create test pattern
                BufferedImage testImage = createTestPattern();
                byte[] frameData = imageToBytes(testImage);

                if (dataCallback != null) {
                    dataCallback.onFrameReady(frameData, System.currentTimeMillis());
                }

            } catch (Exception e) {
                System.err.println("❌ [LIVE] Webcam simulation error: " + e.getMessage());
            }

        }, 0, delay, TimeUnit.MILLISECONDS);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Convert BufferedImage to byte array
     */
    private byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (compressionEnabled) {
            // Use compression
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                ImageIO.write(image, "jpg", gzipOut);
            }
        } else {
            // No compression
            ImageIO.write(image, "jpg", baos);
        }

        return baos.toByteArray();
    }

    /**
     * Resize image maintaining aspect ratio
     */
    private BufferedImage resizeImage(BufferedImage original, int maxWidth, int maxHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        // Calculate new dimensions
        double scale = Math.min(
                (double) maxWidth / originalWidth,
                (double) maxHeight / originalHeight
        );

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // Resize
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * Create test pattern for simulation
     */
    private BufferedImage createTestPattern() {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Draw test pattern
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 640, 480);

        // Draw color bars
        Color[] colors = {Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN,
                Color.MAGENTA, Color.RED, Color.BLUE};
        int barWidth = 640 / colors.length;

        for (int i = 0; i < colors.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(i * barWidth, 0, barWidth, 400);
        }

        // Draw text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        String text = "WEBCAM TEST - " + System.currentTimeMillis() % 1000;
        g.drawString(text, 150, 440);

        g.dispose();
        return image;
    }

    /**
     * Stop streaming
     */
    public void stopStreaming() {
        isStreaming = false;

        if (captureScheduler != null) {
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
    }

    /**
     * Change quality settings
     */
    public void setQuality(double quality, int fps) {
        this.quality = Math.max(0.1, Math.min(1.0, quality));
        this.fps = Math.max(1, Math.min(60, fps));

        System.out.println("⚙️ [LIVE] Quality settings updated:");
        System.out.println("   Quality: " + (this.quality * 100) + "%");
        System.out.println("   FPS: " + this.fps);

        // Restart capture with new settings
        if (isStreaming) {
            StreamSource source = currentSource;
            stopStreaming();

            switch (source) {
                case SCREEN:
                    startScreenCapture(null);
                    break;
                case WEBCAM:
                    startWebcamCapture();
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

    /**
     * Get current statistics
     */
    public StreamStatistics getStatistics() {
        StreamStatistics stats = new StreamStatistics();
        stats.source = currentSource;
        stats.isStreaming = isStreaming;
        stats.fps = fps;
        stats.quality = quality;
        stats.compressionEnabled = compressionEnabled;
        return stats;
    }

    /**
     * Stream statistics
     */
    public static class StreamStatistics {
        public StreamSource source;
        public boolean isStreaming;
        public int fps;
        public double quality;
        public boolean compressionEnabled;
        public long framesCaptures;
        public long bytesTransmitted;

        @Override
        public String toString() {
            return String.format("Stream Stats: Source=%s, FPS=%d, Quality=%.0f%%, Compression=%s",
                    source, fps, quality * 100, compressionEnabled ? "ON" : "OFF");
        }
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        stopStreaming();

        if (streamExecutor != null) {
            streamExecutor.shutdown();
        }

        if (captureScheduler != null) {
            captureScheduler.shutdown();
        }
    }
}