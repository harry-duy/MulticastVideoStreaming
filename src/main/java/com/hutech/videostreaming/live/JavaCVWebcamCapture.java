package com.hutech.videostreaming.live;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaCV Webcam Capture - FIXED VERSION
 * Sử dụng OpenCVFrameGrabber trực tiếp để tránh lỗi native libraries
 */
public class JavaCVWebcamCapture {

    private OpenCVFrameGrabber grabber;  // ✅ SỬ DỤNG TRỰC TIẾP OpenCVFrameGrabber
    private OpenCVFrameConverter.ToMat converter;
    private Java2DFrameConverter java2DConverter;
    private volatile boolean isCapturing = false;
    private int deviceId;

    /**
     * Khởi tạo webcam capture
     */
    public JavaCVWebcamCapture(int deviceId) throws Exception {
        this.deviceId = deviceId;

        // ✅ Tạo OpenCVFrameGrabber trực tiếp
        this.grabber = new OpenCVFrameGrabber(deviceId);
        this.converter = new OpenCVFrameConverter.ToMat();
        this.java2DConverter = new Java2DFrameConverter();

        System.out.println("📹 [JAVACV] Webcam capture initialized (OpenCV)");
    }

    /**
     * Khởi tạo với default webcam
     */
    public JavaCVWebcamCapture() throws Exception {
        this(0); // Device 0 = default webcam
    }

    /**
     * Thiết lập resolution
     */
    public void setResolution(int width, int height) {
        grabber.setImageWidth(width);
        grabber.setImageHeight(height);
        System.out.println("📐 [JAVACV] Resolution set to: " + width + "x" + height);
    }

    /**
     * Thiết lập FPS
     */
    public void setFrameRate(double fps) {
        grabber.setFrameRate(fps);
        System.out.println("🎬 [JAVACV] Frame rate set to: " + fps);
    }

    /**
     * Bắt đầu capture
     */
    public void start() throws Exception {
        if (isCapturing) {
            System.out.println("⚠️ [JAVACV] Already capturing");
            return;
        }

        try {
            grabber.start();
            isCapturing = true;
            System.out.println("✅ [JAVACV] Webcam started (device " + deviceId + ")");
        } catch (Exception e) {
            System.err.println("❌ [JAVACV] Failed to start webcam: " + e.getMessage());
            throw new Exception("Cannot start webcam. Please check:\n" +
                    "1. Webcam is connected\n" +
                    "2. No other app is using the webcam\n" +
                    "3. Camera permissions are granted", e);
        }
    }

    /**
     * Dừng capture
     */
    public void stop() throws Exception {
        if (!isCapturing) return;

        isCapturing = false;
        grabber.stop();
        System.out.println("⏹️ [JAVACV] Webcam stopped");
    }

    /**
     * Lấy frame dạng BufferedImage
     */
    public BufferedImage captureFrame() throws Exception {
        if (!isCapturing) {
            throw new IllegalStateException("Webcam not started");
        }

        try {
            Frame frame = grabber.grab();
            if (frame == null) {
                return null;
            }

            return java2DConverter.convert(frame);
        } catch (Exception e) {
            System.err.println("⚠️ [JAVACV] Frame capture error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy frame dạng byte array (JPEG)
     */
    public byte[] captureFrameBytes() throws Exception {
        BufferedImage image = captureFrame();
        if (image == null) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * Kiểm tra webcam có đang capture không
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * Đóng tài nguyên
     */
    public void release() throws Exception {
        stop();
        if (grabber != null) {
            grabber.release();
        }
        System.out.println("🔴 [JAVACV] Resources released");
    }

    /**
     * ✅ FIXED: Liệt kê các webcam có sẵn
     * Sử dụng OpenCVFrameGrabber và bắt exceptions
     */
    public static List<WebcamInfo> listWebcams() {
        List<WebcamInfo> webcams = new ArrayList<>();

        System.out.println("🔍 [JAVACV] Scanning for webcams using OpenCV...");

        // Try to detect webcams (0-5)
        for (int i = 0; i < 5; i++) {
            OpenCVFrameGrabber testGrabber = null;

            try {
                // ✅ Sử dụng OpenCVFrameGrabber trực tiếp
                testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.start();

                // Get info
                int width = testGrabber.getImageWidth();
                int height = testGrabber.getImageHeight();

                WebcamInfo info = new WebcamInfo();
                info.id = i;
                info.name = "Webcam " + i;
                info.width = width > 0 ? width : 640;
                info.height = height > 0 ? height : 480;

                webcams.add(info);

                System.out.println("✅ [JAVACV] Found webcam " + i + ": " +
                        info.width + "x" + info.height);

                testGrabber.stop();
                testGrabber.release();

            } catch (Exception e) {
                // ✅ Webcam không tồn tại hoặc không truy cập được
                // Không in error, chỉ break
                if (testGrabber != null) {
                    try {
                        testGrabber.release();
                    } catch (Exception ignored) {}
                }
                break; // Không còn webcam nào nữa
            }
        }

        if (webcams.isEmpty()) {
            System.out.println("⚠️ [JAVACV] No webcams found");
        } else {
            System.out.println("✅ [JAVACV] Total webcams found: " + webcams.size());
        }

        return webcams;
    }

    /**
     * Webcam info class
     */
    public static class WebcamInfo {
        public int id;
        public String name;
        public int width;
        public int height;

        @Override
        public String toString() {
            return name + " (" + width + "x" + height + ")";
        }
    }

    /**
     * Test method
     */
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║  JavaCV Webcam Capture Test (OpenCV) ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        try {
            // List webcams
            System.out.println("📹 Testing webcam detection:\n");
            List<WebcamInfo> webcams = listWebcams();

            if (webcams.isEmpty()) {
                System.out.println("\n❌ No webcams found!");
                System.out.println("\nTroubleshooting:");
                System.out.println("1. Check if webcam is connected");
                System.out.println("2. Close other apps using the webcam");
                System.out.println("3. Check camera permissions in system settings");
                System.out.println("4. Try running as administrator");
                return;
            }

            System.out.println("\n✅ Found " + webcams.size() + " webcam(s):");
            for (WebcamInfo info : webcams) {
                System.out.println("   - " + info);
            }

            // Test capture
            System.out.println("\n📸 Testing capture (5 seconds)...\n");
            JavaCVWebcamCapture capture = new JavaCVWebcamCapture(0);
            capture.setResolution(640, 480);
            capture.setFrameRate(25);
            capture.start();

            int frames = 0;
            long startTime = System.currentTimeMillis();
            long totalBytes = 0;

            while ((System.currentTimeMillis() - startTime) < 5000) {
                byte[] frameData = capture.captureFrameBytes();
                if (frameData != null) {
                    frames++;
                    totalBytes += frameData.length;

                    if (frames % 25 == 0) {
                        System.out.println("   Captured " + frames + " frames, " +
                                formatBytes(totalBytes) + " total");
                    }
                }
                Thread.sleep(40);
            }

            capture.release();

            System.out.println("\n✅ Test completed successfully!");
            System.out.println("📊 Statistics:");
            System.out.println("   Total frames: " + frames);
            System.out.println("   Total data: " + formatBytes(totalBytes));
            System.out.println("   Avg FPS: " + String.format("%.1f", frames / 5.0));
            System.out.println("   Avg frame size: " + formatBytes(totalBytes / Math.max(1, frames)));

        } catch (Exception e) {
            System.err.println("\n❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}