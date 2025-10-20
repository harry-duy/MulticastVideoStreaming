package com.hutech.videostreaming.server;

import com.hutech.videostreaming.common.*;
import com.hutech.videostreaming.live.LiveStreamManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Live Video Server
 * Streams live content from webcam or screen capture
 */
public class LiveVideoServer implements LiveStreamManager.StreamDataCallback {

    private MulticastSocket socket;
    private InetAddress group;
    private DatabaseManager dbManager;
    private volatile boolean isStreaming = false;
    private int sequenceNumber = 0;

    // Queue for frame buffering
    private BlockingQueue<byte[]> frameQueue;
    private ExecutorService sendExecutor;

    // Statistics
    private long framesSent = 0;
    private long bytesSent = 0;
    private long droppedFrames = 0;
    private BandwidthMonitor bandwidthMonitor;

    // Settings
    private static final int MAX_PACKET_SIZE = 60000;
    private static final int QUEUE_SIZE = 100; // Buffer up to 100 frames

    public LiveVideoServer() {
        this.dbManager = new DatabaseManager();
        this.frameQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        this.sendExecutor = Executors.newSingleThreadExecutor();
        this.bandwidthMonitor = new BandwidthMonitor();
    }

    /**
     * Initialize multicast server
     */
    public void initialize() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));

            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            // Select network interface
            NetworkInterface networkInterface = findMulticastInterface();
            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);
                System.out.println("🎬 [LIVE-SERVER] Using interface: " +
                        networkInterface.getDisplayName());
            }

            socket.setTimeToLive(1);

            // Increase send buffer
            try {
                socket.setSendBufferSize(8 * 1024 * 1024);
                System.out.println("🧪 [LIVE-SERVER] Send buffer: " +
                        socket.getSendBufferSize());
            } catch (Exception e) {
                System.out.println("⚠️ [LIVE-SERVER] Could not set send buffer");
            }

            System.out.println("✅ [LIVE-SERVER] Initialized");
            System.out.println("📡 Address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 Port: " + Constants.MULTICAST_PORT);

            dbManager.logEvent("INIT", "LIVE-SERVER", "Live server initialized");

        } catch (IOException e) {
            System.err.println("❌ [LIVE-SERVER] Init failed: " + e.getMessage());
        }
    }

    /**
     * Start streaming from LiveStreamManager
     */
    public void startStreaming(LiveStreamManager liveManager) {
        if (isStreaming) {
            System.out.println("⚠️ [LIVE-SERVER] Already streaming");
            return;
        }

        initialize();

        isStreaming = true;
        sequenceNumber = 0;
        framesSent = 0;
        bytesSent = 0;
        droppedFrames = 0;
        bandwidthMonitor.reset();

        System.out.println("▶️ [LIVE-SERVER] Starting live stream...");

        // Send START command
        sendControlCommand(Constants.CMD_START);

        // Start frame sender thread
        sendExecutor.submit(() -> {
            System.out.println("📤 [LIVE-SERVER] Frame sender started");

            while (isStreaming) {
                try {
                    // Get frame from queue (blocking)
                    byte[] frameData = frameQueue.poll(1, TimeUnit.SECONDS);

                    if (frameData != null) {
                        sendFrame(frameData);
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("❌ [LIVE-SERVER] Send error: " + e.getMessage());
                }
            }

            System.out.println("🛑 [LIVE-SERVER] Frame sender stopped");
        });

        dbManager.logEvent("START", "LIVE-SERVER", "Live streaming started");
    }

    /**
     * Send single frame
     */
    private void sendFrame(byte[] frameData) throws IOException {
        int offset = 0;
        int framePackets = 0;

        // Split frame into packets if needed
        while (offset < frameData.length) {
            int chunkSize = Math.min(MAX_PACKET_SIZE, frameData.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(frameData, offset, chunk, 0, chunkSize);

            // Create and send packet
            VideoPacket packet = new VideoPacket(
                    Constants.CMD_DATA,
                    sequenceNumber++,
                    chunk
            );

            byte[] packetData = packet.toByteArray();
            DatagramPacket dgPacket = new DatagramPacket(
                    packetData,
                    packetData.length,
                    group,
                    Constants.MULTICAST_PORT
            );

            socket.send(dgPacket);

            offset += chunkSize;
            framePackets++;
            bytesSent += packetData.length;
            bandwidthMonitor.addData(packetData.length);
        }

        framesSent++;

        // Log progress
        if (framesSent % 100 == 0) {
            System.out.println(String.format(
                    "📊 [LIVE-SERVER] Frames: %d | Packets: %d | Bandwidth: %s | Dropped: %d",
                    framesSent, sequenceNumber,
                    bandwidthMonitor.getFormattedBandwidth(), droppedFrames
            ));
        }
    }

    /**
     * Send control command
     */
    private void sendControlCommand(byte command) {
        try {
            VideoPacket packet = new VideoPacket(command, sequenceNumber++, new byte[0]);
            byte[] data = packet.toByteArray();

            DatagramPacket dgPacket = new DatagramPacket(
                    data, data.length, group, Constants.MULTICAST_PORT
            );

            socket.send(dgPacket);

            System.out.println("📨 [LIVE-SERVER] Sent control command: " + command);

        } catch (IOException e) {
            System.err.println("❌ [LIVE-SERVER] Failed to send command: " + e.getMessage());
        }
    }

    /**
     * Stop streaming
     */
    public void stopStreaming() {
        if (!isStreaming) return;

        isStreaming = false;

        // Send STOP command
        sendControlCommand(Constants.CMD_STOP);

        // Clear queue
        frameQueue.clear();

        System.out.println("⏹️ [LIVE-SERVER] Stopped");
        System.out.println("📊 Final statistics:");
        System.out.println("   Frames sent: " + framesSent);
        System.out.println("   Packets sent: " + sequenceNumber);
        System.out.println("   Data sent: " + formatBytes(bytesSent));
        System.out.println("   Frames dropped: " + droppedFrames);
        System.out.println("   Avg bandwidth: " + bandwidthMonitor.getFormattedBandwidth());

        dbManager.logEvent("STOP", "LIVE-SERVER",
                String.format("Live stream stopped. Frames: %d, Dropped: %d",
                        framesSent, droppedFrames));
    }

    /**
     * Close server
     */
    public void close() {
        stopStreaming();

        if (sendExecutor != null) {
            sendExecutor.shutdown();
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (dbManager != null) {
            dbManager.close();
        }

        System.out.println("🔴 [LIVE-SERVER] Closed");
    }

    // ==================== CALLBACKS ====================

    @Override
    public void onFrameReady(byte[] frameData, long timestamp) {
        if (!isStreaming) return;

        // Try to add to queue
        boolean added = frameQueue.offer(frameData);

        if (!added) {
            // Queue full - drop frame
            droppedFrames++;

            if (droppedFrames % 10 == 0) {
                System.err.println("⚠️ [LIVE-SERVER] Queue full! Dropped " +
                        droppedFrames + " frames");
            }
        }
    }

    @Override
    public void onError(String error) {
        System.err.println("❌ [LIVE-SERVER] Stream error: " + error);
    }

    @Override
    public void onStatisticsUpdate(LiveStreamManager.StreamStatistics stats) {
        // Can log or forward stats if needed
    }

    // ==================== UTILITIES ====================

    /**
     * Find suitable network interface
     */
    private static NetworkInterface findMulticastInterface() {
        try {
            List<NetworkInterface> candidates = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                if (!nif.supportsMulticast()) continue;

                // Check for IPv4
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                boolean hasIPv4 = false;
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        hasIPv4 = true;
                        break;
                    }
                }

                if (hasIPv4) {
                    candidates.add(nif);
                }
            }

            // Prefer ethernet/wifi
            for (NetworkInterface nif : candidates) {
                String name = nif.getName().toLowerCase();
                if (name.contains("eth") || name.contains("en") ||
                        name.contains("wlan") || name.contains("wi")) {
                    return nif;
                }
            }

            return candidates.isEmpty() ? null : candidates.get(0);

        } catch (SocketException e) {
            return null;
        }
    }

    /**
     * Format bytes
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB",
                bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // ==================== GETTERS ====================

    public boolean isStreaming() { return isStreaming; }
    public long getFramesSent() { return framesSent; }
    public long getBytesSent() { return bytesSent; }
    public long getDroppedFrames() { return droppedFrames; }
    public int getQueueSize() { return frameQueue.size(); }

    /**
     * Get statistics
     */
    public LiveStreamStatistics getStatistics() {
        LiveStreamStatistics stats = new LiveStreamStatistics();
        stats.isStreaming = isStreaming;
        stats.framesSent = framesSent;
        stats.packetsSent = sequenceNumber;
        stats.bytesSent = bytesSent;
        stats.droppedFrames = droppedFrames;
        stats.queueSize = frameQueue.size();
        stats.bandwidth = bandwidthMonitor.getFormattedBandwidth();
        return stats;
    }

    /**
     * Statistics class
     */
    public static class LiveStreamStatistics {
        public boolean isStreaming;
        public long framesSent;
        public int packetsSent;
        public long bytesSent;
        public long droppedFrames;
        public int queueSize;
        public String bandwidth;

        @Override
        public String toString() {
            return String.format(
                    "Live Stats: Frames=%d, Packets=%d, Bytes=%d, Dropped=%d, Queue=%d, BW=%s",
                    framesSent, packetsSent, bytesSent, droppedFrames, queueSize, bandwidth
            );
        }
    }
}