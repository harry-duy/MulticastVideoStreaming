package com.hutech.videostreaming.test;

import com.hutech.videostreaming.common.*;
import com.hutech.videostreaming.live.JavaCVWebcamCapture;
import com.hutech.videostreaming.live.LiveStreamManager;
import com.hutech.videostreaming.server.LiveVideoServer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Scanner;

/**
 * Live Streaming Test Tool
 * Test các components riêng lẻ với JavaCV
 */
public class LiveStreamTest {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   LIVE STREAMING TEST TOOL            ║");
        System.out.println("║   Using JavaCV Webcam Capture         ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Select test:");
        System.out.println("1. Test Webcam Detection (JavaCV)");
        System.out.println("2. Test Webcam Capture (JavaCV)");
        System.out.println("3. Test Screen Capture");
        System.out.println("4. Test LiveVideoServer");
        System.out.println("5. Test Full Live Stream (10 seconds)");
        System.out.println("6. Test Full Live Stream (GUI preview)");
        System.out.println("7. Test All Components");
        System.out.print("\nEnter choice (1-7): ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                testWebcamDetection();
                break;
            case 2:
                testWebcamCapture();
                break;
            case 3:
                testScreenCapture();
                break;
            case 4:
                testLiveVideoServer();
                break;
            case 5:
                testFullLiveStream(10);
                break;
            case 6:
                testWithGUIPreview();
                break;
            case 7:
                testAllComponents();
                break;
            default:
                System.out.println("Invalid choice!");
        }

        scanner.close();
    }

    /**
     * TEST 1: Webcam Detection với JavaCV
     */
    private static void testWebcamDetection() {
        System.out.println("\n🧪 TEST 1: Webcam Detection (JavaCV)\n");

        System.out.println("Scanning for webcams using JavaCV...");

        try {
            java.util.List<JavaCVWebcamCapture.WebcamInfo> webcams =
                    JavaCVWebcamCapture.listWebcams();

            if (webcams.isEmpty()) {
                System.out.println("❌ No webcams found!");
                System.out.println("\nTroubleshooting:");
                System.out.println("- Check if webcam is connected");
                System.out.println("- Check if webcam is being used by another app");
                System.out.println("- Try restarting the computer");
                System.out.println("- Check camera permissions in system settings");
                return;
            }

            System.out.println("✅ Found " + webcams.size() + " webcam(s):\n");

            for (JavaCVWebcamCapture.WebcamInfo webcam : webcams) {
                System.out.println("📹 " + webcam.toString());
                System.out.println("   ID: " + webcam.id);
                System.out.println("   Resolution: " + webcam.width + "x" + webcam.height);
                System.out.println();
            }

            System.out.println("✅ Webcam detection test PASSED");

        } catch (Exception e) {
            System.err.println("❌ Test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * TEST 2: Webcam Capture với JavaCV
     */
    private static void testWebcamCapture() throws Exception {
        System.out.println("\n🧪 TEST 2: Webcam Capture with JavaCV (5 seconds)\n");

        JavaCVWebcamCapture capture = null;

        try {
            capture = new JavaCVWebcamCapture();

            System.out.println("📹 Using default webcam");
            System.out.println("📐 Setting resolution to 640x480");

            capture.setResolution(640, 480);
            capture.setFrameRate(25);
            capture.start();

            System.out.println("✅ Webcam started successfully");
            System.out.println("📸 Capturing frames for 5 seconds...\n");

            int framesCaptured = 0;
            long startTime = System.currentTimeMillis();
            long totalBytes = 0;

            while ((System.currentTimeMillis() - startTime) < 5000) {
                byte[] frameData = capture.captureFrameBytes();

                if (frameData != null) {
                    framesCaptured++;
                    totalBytes += frameData.length;

                    if (framesCaptured % 25 == 0) {
                        System.out.println("📊 Captured " + framesCaptured + " frames, " +
                                formatBytes(totalBytes) + " total");
                    }
                }

                Thread.sleep(40); // ~25 FPS
            }

            System.out.println("\n✅ Webcam capture test PASSED");
            System.out.println("📊 Statistics:");
            System.out.println("   Total frames: " + framesCaptured);
            System.out.println("   Total data: " + formatBytes(totalBytes));
            System.out.println("   Avg FPS: " + (framesCaptured / 5.0));
            System.out.println("   Avg frame size: " + formatBytes(totalBytes / Math.max(1, framesCaptured)));

        } catch (Exception e) {
            System.err.println("❌ Test FAILED: " + e.getMessage());
            e.printStackTrace();
            throw e;

        } finally {
            if (capture != null) {
                capture.release();
            }
        }
    }

    /**
     * TEST 3: Screen Capture
     */
    private static void testScreenCapture() throws Exception {
        System.out.println("\n🧪 TEST 3: Screen Capture (5 seconds)\n");

        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle captureRect = new Rectangle(screenSize);

        System.out.println("🖥️ Screen size: " + screenSize.width + "x" + screenSize.height);
        System.out.println("📸 Capturing frames for 5 seconds...\n");

        int framesCaptured = 0;
        long startTime = System.currentTimeMillis();
        long totalBytes = 0;

        while ((System.currentTimeMillis() - startTime) < 5000) {
            BufferedImage screenshot = robot.createScreenCapture(captureRect);

            // Convert to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            framesCaptured++;
            totalBytes += imageBytes.length;

            if (framesCaptured % 25 == 0) {
                System.out.println("📊 Captured " + framesCaptured + " frames, " +
                        formatBytes(totalBytes));
            }

            Thread.sleep(40); // ~25 FPS
        }

        System.out.println("\n✅ Screen capture test PASSED");
        System.out.println("📊 Statistics:");
        System.out.println("   Total frames: " + framesCaptured);
        System.out.println("   Total data: " + formatBytes(totalBytes));
        System.out.println("   Avg FPS: " + (framesCaptured / 5.0));
        System.out.println("   Avg frame size: " + formatBytes(totalBytes / framesCaptured));
    }

    /**
     * TEST 4: LiveVideoServer
     */
    private static void testLiveVideoServer() throws Exception {
        System.out.println("\n🧪 TEST 4: LiveVideoServer (10 seconds)\n");

        LiveVideoServer server = new LiveVideoServer();
        server.initialize();

        System.out.println("✅ Server initialized");
        System.out.println("▶️ Starting broadcast...");

        server.startBroadcasting();

        System.out.println("📤 Sending test frames for 10 seconds...\n");

        // Send test frames
        int framesSent = 0;
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < 10000) {
            // Create test frame data (simulate image)
            byte[] testFrame = createTestFrame(framesSent);
            server.sendFrame(testFrame, System.currentTimeMillis());

            framesSent++;

            if (framesSent % 50 == 0) {
                System.out.println("📊 " + server.getStatistics());
            }

            Thread.sleep(40); // ~25 FPS
        }

        System.out.println("\n⏹️ Stopping server...");
        server.stopStreaming();
        server.close();

        System.out.println("\n✅ LiveVideoServer test PASSED");
        System.out.println("📊 Final statistics:");
        System.out.println("   " + server.getStatistics());
    }

    /**
     * TEST 5: Full Live Stream
     */
    private static void testFullLiveStream(int durationSeconds) throws Exception {
        System.out.println("\n🧪 TEST 5: Full Live Stream (" + durationSeconds + " seconds)\n");

        // Initialize components
        LiveVideoServer server = new LiveVideoServer();
        server.initialize();

        TestCallback callback = new TestCallback(server);
        LiveStreamManager manager = new LiveStreamManager(callback);

        // Check webcam
        System.out.println("🔍 Checking for webcams...");
        java.util.List<JavaCVWebcamCapture.WebcamInfo> webcams =
                JavaCVWebcamCapture.listWebcams();

        if (webcams.isEmpty()) {
            System.out.println("❌ No webcam found! Using screen capture instead...");

            System.out.println("▶️ Starting screen capture...");
            manager.setQuality(0.8, 25);
            manager.startScreenCapture();
        } else {
            System.out.println("✅ Found webcam: " + webcams.get(0).name);
            System.out.println("▶️ Starting webcam stream...");
            manager.setQuality(0.8, 25);
            manager.setWebcamResolution(640, 480);
            manager.startWebcamStream();
        }

        // Start server
        server.startBroadcasting();

        System.out.println("🔴 Live streaming for " + durationSeconds + " seconds...\n");

        // Monitor progress
        for (int i = 0; i < durationSeconds; i++) {
            Thread.sleep(1000);
            System.out.print(".");
            if ((i + 1) % 10 == 0) {
                System.out.println(" [" + (i + 1) + "s]");
            }
        }
        System.out.println();

        // Stop
        System.out.println("\n⏹️ Stopping stream...");
        manager.stopStreaming();
        server.stopStreaming();
        server.close();

        System.out.println("\n✅ Full live stream test PASSED");
        System.out.println("📊 Manager statistics:");
        System.out.println("   " + manager.getStatistics());
        System.out.println("📊 Server statistics:");
        System.out.println("   " + server.getStatistics());
    }

    /**
     * TEST 6: With GUI Preview (JavaFX)
     */
    private static void testWithGUIPreview() {
        System.out.println("\n🧪 TEST 6: Live Stream with GUI Preview\n");
        System.out.println("Opening JavaFX window...");

        // Launch JavaFX app
        javafx.application.Application.launch(LiveStreamTestGUI.class);
    }

    /**
     * TEST 7: Test All Components
     */
    private static void testAllComponents() throws Exception {
        System.out.println("\n🧪 TEST 7: Testing All Components\n");

        int passed = 0;
        int failed = 0;

        // Test 1: Webcam Detection
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            testWebcamDetection();
            passed++;
        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getMessage());
            failed++;
        }

        Thread.sleep(2000);

        // Test 2: Webcam Capture
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            testWebcamCapture();
            passed++;
        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getMessage());
            failed++;
        }

        Thread.sleep(2000);

        // Test 3: Screen Capture
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            testScreenCapture();
            passed++;
        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getMessage());
            failed++;
        }

        Thread.sleep(2000);

        // Test 4: Server
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            testLiveVideoServer();
            passed++;
        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getMessage());
            failed++;
        }

        // Summary
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║          TEST SUMMARY                 ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println("✅ Passed: " + passed);
        System.out.println("❌ Failed: " + failed);
        System.out.println("📊 Total: " + (passed + failed));
        System.out.println();

        if (failed == 0) {
            System.out.println("🎉 ALL TESTS PASSED!");
        } else {
            System.out.println("⚠️ Some tests failed. Check output above.");
        }
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Test callback that forwards frames to server
     */
    private static class TestCallback implements LiveStreamManager.StreamDataCallback {
        private LiveVideoServer server;
        private int framesReceived = 0;

        public TestCallback(LiveVideoServer server) {
            this.server = server;
        }

        @Override
        public void onFrameReady(byte[] frameData, long timestamp) {
            server.sendFrame(frameData, timestamp);
            framesReceived++;

            if (framesReceived % 50 == 0) {
                System.out.println("📊 Callback: Received " + framesReceived + " frames");
            }
        }

        @Override
        public void onError(String error) {
            System.err.println("❌ Error: " + error);
        }

        @Override
        public void onStatisticsUpdate(LiveStreamManager.StreamStatistics stats) {
            // Optional: log stats
        }
    }

    /**
     * Create test frame data
     */
    private static byte[] createTestFrame(int frameNumber) {
        String data = "TEST_FRAME_" + frameNumber + "_" + System.currentTimeMillis();
        byte[] bytes = data.getBytes();

        // Pad to simulate realistic frame size (~20KB)
        byte[] paddedBytes = new byte[20000];
        System.arraycopy(bytes, 0, paddedBytes, 0, Math.min(bytes.length, paddedBytes.length));

        return paddedBytes;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * JavaFX GUI for testing with preview
     */
    public static class LiveStreamTestGUI extends javafx.application.Application {
        private LiveStreamManager manager;
        private LiveVideoServer server;
        private javafx.scene.image.ImageView preview;
        private javafx.scene.control.Label statsLabel;
        private javafx.scene.control.Label fpsLabel;

        @Override
        public void start(javafx.stage.Stage stage) {
            // Initialize
            server = new LiveVideoServer();
            server.initialize();

            TestGUICallback callback = new TestGUICallback();
            manager = new LiveStreamManager(callback);

            // UI
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(20);
            root.setAlignment(javafx.geometry.Pos.CENTER);
            root.setPadding(new javafx.geometry.Insets(20));
            root.setStyle("-fx-background-color: #1e1e2e;");

            javafx.scene.control.Label title = new javafx.scene.control.Label("🧪 Live Stream Test with Preview");
            title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 24));
            title.setStyle("-fx-text-fill: white;");

            preview = new javafx.scene.image.ImageView();
            preview.setFitWidth(640);
            preview.setFitHeight(480);
            preview.setPreserveRatio(true);
            preview.setStyle("-fx-background-color: #000000;");

            fpsLabel = new javafx.scene.control.Label("FPS: 0.0");
            fpsLabel.setFont(javafx.scene.text.Font.font(16));
            fpsLabel.setStyle("-fx-text-fill: #60a5fa;");

            statsLabel = new javafx.scene.control.Label("Ready to start");
            statsLabel.setFont(javafx.scene.text.Font.font(14));
            statsLabel.setStyle("-fx-text-fill: #9ca3af;");
            statsLabel.setWrapText(true);
            statsLabel.setMaxWidth(640);

            javafx.scene.control.Button startWebcamBtn = new javafx.scene.control.Button("📹 Start Webcam");
            styleButton(startWebcamBtn, "#10b981");
            startWebcamBtn.setOnAction(e -> {
                try {
                    manager.setQuality(0.8, 25);
                    manager.setWebcamResolution(640, 480);
                    manager.startWebcamStream();
                    server.startBroadcasting();
                    System.out.println("✅ Webcam started!");
                } catch (Exception ex) {
                    System.err.println("❌ Failed: " + ex.getMessage());
                }
            });

            javafx.scene.control.Button startScreenBtn = new javafx.scene.control.Button("🖥️ Start Screen");
            styleButton(startScreenBtn, "#3b82f6");
            startScreenBtn.setOnAction(e -> {
                manager.setQuality(0.8, 25);
                manager.startScreenCapture();
                server.startBroadcasting();
                System.out.println("✅ Screen capture started!");
            });

            javafx.scene.control.Button stopBtn = new javafx.scene.control.Button("⏹️ Stop");
            styleButton(stopBtn, "#ef4444");
            stopBtn.setOnAction(e -> {
                manager.stopStreaming();
                server.stopStreaming();
                System.out.println("⏹️ Stopped!");
            });

            javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(10, startWebcamBtn, startScreenBtn, stopBtn);
            buttons.setAlignment(javafx.geometry.Pos.CENTER);

            root.getChildren().addAll(title, preview, fpsLabel, statsLabel, buttons);

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 800, 800);
            stage.setScene(scene);
            stage.setTitle("Live Stream Test - Preview");
            stage.setOnCloseRequest(e -> {
                manager.shutdown();
                server.close();
            });
            stage.show();

            // Update stats periodically
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.5), e -> {
                        LiveStreamManager.StreamStatistics managerStats = manager.getStatistics();
                        LiveVideoServer.LiveStreamStatistics serverStats = server.getStatistics();

                        fpsLabel.setText(String.format("FPS: %.1f", managerStats.actualFPS));

                        statsLabel.setText(
                                String.format("Frames: %d | Server Frames: %d | BW: %s | Queue: %d",
                                        managerStats.framesCaptures,
                                        serverStats.framesSent,
                                        serverStats.bandwidth,
                                        serverStats.queueSize)
                        );
                    })
            );
            timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            timeline.play();
        }

        private void styleButton(javafx.scene.control.Button button, String color) {
            button.setPrefWidth(150);
            button.setPrefHeight(40);
            button.setStyle(
                    "-fx-background-color: " + color + ";" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 8;"
            );
        }

        class TestGUICallback implements LiveStreamManager.StreamDataCallback {
            private int frameCount = 0;

            @Override
            public void onFrameReady(byte[] frameData, long timestamp) {
                server.sendFrame(frameData, timestamp);
                frameCount++;

                // Update preview every 10 frames
                if (frameCount % 10 == 0) {
                    try {
                        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(frameData);
                        java.awt.image.BufferedImage bufferedImage = ImageIO.read(bais);
                        if (bufferedImage != null) {
                            javafx.scene.image.Image fxImage =
                                    javafx.embed.swing.SwingFXUtils.toFXImage(bufferedImage, null);
                            javafx.application.Platform.runLater(() -> preview.setImage(fxImage));
                        }
                    } catch (Exception e) {
                        // Ignore preview errors
                    }
                }
            }

            @Override
            public void onError(String error) {
                System.err.println("❌ " + error);
            }

            @Override
            public void onStatisticsUpdate(LiveStreamManager.StreamStatistics stats) {
            }
        }
    }
}