package com.hutech.videostreaming.client;

import com.hutech.videostreaming.common.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/**
 * Live Stream Client
 * Receives and displays live video stream
 */
public class LiveStreamClient {

    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean isReceiving = false;
    private DatabaseManager dbManager;
    private String clientIp;

    // Frame buffer for reconstruction
    private Map<Integer, List<VideoPacket>> frameBuffers;
    private int currentFrameId = 0;
    private int lastSequenceNumber = -1;

    // Statistics
    private int framesReceived = 0;
    private int packetsReceived = 0;
    private int droppedPackets = 0;
    private long bytesReceived = 0;
    private BandwidthMonitor bandwidthMonitor;

    // Callbacks
    private LiveClientCallback callback;

    // Frame reconstruction
    private static final int FRAME_TIMEOUT = 1000; // 1 second
    private ScheduledExecutorService frameReconstructorExecutor;

    // Performance
    private long lastFrameTime = 0;
    private double actualFPS = 0;

    public interface LiveClientCallback {
        void onConnected();
        void onFrameReceived(Image frame, long timestamp);
        void onStreamStarted();
        void onStreamStopped();
        void onDisconnected();
        void onError(String error);
        void onStatisticsUpdate(LiveStreamStatistics stats);
    }

    public LiveStreamClient(LiveClientCallback callback) {
        this.callback = callback;
        this.frameBuffers = new ConcurrentHashMap<>();
        this.dbManager = new DatabaseManager();
        this.bandwidthMonitor = new BandwidthMonitor();
        this.frameReconstructorExecutor = Executors.newScheduledThreadPool(1);
    }

    /**
     * Connect to multicast group
     */
    public void connect() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Constants.MULTICAST_PORT));

            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            // Select network interface
            NetworkInterface networkInterface = findMulticastInterface();
            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);

                try {
                    socket.joinGroup(new InetSocketAddress(group, Constants.MULTICAST_PORT),
                            networkInterface);
                } catch (Throwable t) {
                    socket.joinGroup(group);
                }

                clientIp = getFirstIPv4Address(networkInterface);
                System.out.println("🧩 [LIVE-CLIENT] Using interface: " +
                        networkInterface.getDisplayName());
            } else {
                socket.joinGroup(group);
                clientIp = InetAddress.getLocalHost().getHostAddress();
            }

            // Increase receive buffer
            try {
                socket.setReceiveBufferSize(16 * 1024 * 1024); // 16MB for live stream
                System.out.println("🧪 [LIVE-CLIENT] Receive buffer: " +
                        socket.getReceiveBufferSize());
            } catch (Exception e) {
                System.out.println("⚠️ [LIVE-CLIENT] Could not set receive buffer size");
            }

            socket.setSoTimeout(1000);

            System.out.println("✅ [LIVE-CLIENT] Connected to multicast group");
            System.out.println("📡 Address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 Port: " + Constants.MULTICAST_PORT);
            System.out.println("💻 Local IP: " + clientIp);

            dbManager.recordClientJoin(clientIp);
            dbManager.logEvent("CONNECT", clientIp, "Live client connected");

            if (callback != null) {
                callback.onConnected();
            }

            startReceiving();
            startFrameReconstructor();

        } catch (IOException e) {
            System.err.println("❌ [LIVE-CLIENT] Connection failed: " + e.getMessage());
            if (callback != null) {
                callback.onError("Connection failed: " + e.getMessage());
            }
        }
    }

    /**
     * Start receiving packets
     */
    private void startReceiving() {
        isReceiving = true;

        new Thread(() -> {
            byte[] buffer = new byte[Constants.MAX_PACKET_SIZE + Constants.HEADER_SIZE + 40000];
            System.out.println("🎧 [LIVE-CLIENT] Listening for live stream...");

            while (isReceiving) {
                try {
                    DatagramPacket dgPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dgPacket);

                    // Parse packet
                    VideoPacket packet = VideoPacket.fromByteArray(buffer, dgPacket.getLength());
                    handlePacket(packet);

                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue
                    continue;

                } catch (SocketException e) {
                    if (isReceiving) {
                        System.err.println("❌ [LIVE-CLIENT] Socket error: " + e.getMessage());
                        if (callback != null) {
                            callback.onError("Connection lost");
                        }
                    }
                    break;

                } catch (IOException e) {
                    if (isReceiving) {
                        System.err.println("❌ [LIVE-CLIENT] Error receiving: " + e.getMessage());
                    }
                }
            }

            System.out.println("🔇 [LIVE-CLIENT] Stopped listening");
        }, "LiveClientReceiveThread").start();
    }

    /**
     * Start frame reconstructor
     */
    private void startFrameReconstructor() {
        frameReconstructorExecutor.scheduleAtFixedRate(() -> {
            try {
                reconstructFrames();
            } catch (Exception e) {
                System.err.println("❌ [LIVE-CLIENT] Frame reconstruction error: " +
                        e.getMessage());
            }
        }, 100, 100, TimeUnit.MILLISECONDS); // Check every 100ms
    }

    /**
     * Handle received packet
     */
    private void handlePacket(VideoPacket packet) {
        switch (packet.getCommand()) {
            case Constants.CMD_START:
                handleStreamStart();
                break;

            case Constants.CMD_DATA:
                handleDataPacket(packet);
                break;

            case Constants.CMD_STOP:
                handleStreamStop();
                break;
        }
    }

    private void handleStreamStart() {
        System.out.println("▶️ [LIVE-CLIENT] Live stream started");
        framesReceived = 0;
        packetsReceived = 0;
        droppedPackets = 0;
        bytesReceived = 0;
        frameBuffers.clear();
        bandwidthMonitor.reset();

        dbManager.logEvent("STREAM_START", clientIp, "Live stream started");

        if (callback != null) {
            callback.onStreamStarted();
        }
    }

    private void handleDataPacket(VideoPacket packet) {
        int currentSeq = packet.getSequenceNumber();

        // Check for dropped packets
        if (lastSequenceNumber >= 0 && currentSeq > lastSequenceNumber + 1) {
            int dropped = currentSeq - lastSequenceNumber - 1;
            droppedPackets += dropped;

            if (dropped > 5) {
                System.out.println("⚠️ [LIVE-CLIENT] Detected " + dropped +
                        " dropped packets");
            }
        }

        lastSequenceNumber = currentSeq;
        packetsReceived++;
        bytesReceived += packet.getDataLength();
        bandwidthMonitor.addData(packet.getDataLength());

        // Add to frame buffer
        int frameId = currentSeq / 10; // Assume 10 packets per frame (adjustable)
        frameBuffers.computeIfAbsent(frameId, k -> new CopyOnWriteArrayList<>()).add(packet);

        // Update statistics every 50 packets
        if (packetsReceived % 50 == 0) {
            updateStatistics();
        }
    }

    private void handleStreamStop() {
        System.out.println("⏹️ [LIVE-CLIENT] Live stream stopped");

        System.out.println("📊 [LIVE-CLIENT] Final statistics:");
        System.out.println("   Frames received: " + framesReceived);
        System.out.println("   Packets received: " + packetsReceived);
        System.out.println("   Packets dropped: " + droppedPackets);
        System.out.println("   Avg bandwidth: " + bandwidthMonitor.getFormattedBandwidth());

        dbManager.logEvent("STREAM_STOP", clientIp,
                String.format("Live stream stopped. Frames: %d, Packets: %d",
                        framesReceived, packetsReceived));

        if (callback != null) {
            callback.onStreamStopped();
        }
    }

    /**
     * Reconstruct frames from packets
     */
    private void reconstructFrames() {
        List<Integer> completedFrames = new ArrayList<>();

        for (Map.Entry<Integer, List<VideoPacket>> entry : frameBuffers.entrySet()) {
            int frameId = entry.getKey();
            List<VideoPacket> packets = entry.getValue();

            // Check if frame is complete (timeout-based or packet count)
            if (isFrameComplete(packets)) {
                try {
                    Image frame = reconstructFrame(packets);

                    if (frame != null) {
                        framesReceived++;

                        // Calculate FPS
                        long now = System.currentTimeMillis();
                        if (lastFrameTime > 0) {
                            actualFPS = 1000.0 / (now - lastFrameTime);
                        }
                        lastFrameTime = now;

                        // Send to callback
                        if (callback != null) {
                            callback.onFrameReceived(frame, now);
                        }

                        completedFrames.add(frameId);
                    }

                } catch (Exception e) {
                    System.err.println("❌ [LIVE-CLIENT] Frame reconstruction failed: " +
                            e.getMessage());
                }
            }
        }

        // Clean up completed frames
        for (Integer frameId : completedFrames) {
            frameBuffers.remove(frameId);
        }

        // Clean up old frames (timeout)
        long now = System.currentTimeMillis();
        frameBuffers.entrySet().removeIf(entry -> {
            if (!entry.getValue().isEmpty()) {
                long packetTime = entry.getValue().get(0).getTimestamp();
                return (now - packetTime) > FRAME_TIMEOUT;
            }
            return false;
        });
    }

    /**
     * Check if frame is complete
     */
    private boolean isFrameComplete(List<VideoPacket> packets) {
        if (packets.isEmpty()) return false;

        // Simple heuristic: if no new packets for 100ms, consider complete
        long now = System.currentTimeMillis();
        long lastPacketTime = packets.get(packets.size() - 1).getTimestamp();

        return (now - lastPacketTime) > 100; // 100ms timeout
    }

    /**
     * Reconstruct image from packets
     */
    private Image reconstructFrame(List<VideoPacket> packets) throws IOException {
        // Sort packets by sequence number
        packets.sort(Comparator.comparingInt(VideoPacket::getSequenceNumber));

        // Combine data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (VideoPacket packet : packets) {
            if (packet.getData() != null) {
                baos.write(packet.getData());
            }
        }

        byte[] imageData = baos.toByteArray();

        // Try to decompress if needed (GZIP)
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            GZIPInputStream gzipIn = new GZIPInputStream(bais);
            ByteArrayOutputStream decompressed = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                decompressed.write(buffer, 0, len);
            }

            imageData = decompressed.toByteArray();
        } catch (Exception e) {
            // Not compressed, use as-is
        }

        // Read image
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));

        if (bufferedImage != null) {
            return SwingFXUtils.toFXImage(bufferedImage, null);
        }

        return null;
    }

    /**
     * Update statistics
     */
    private void updateStatistics() {
        if (callback != null) {
            LiveStreamStatistics stats = getStatistics();
            callback.onStatisticsUpdate(stats);
        }
    }

    /**
     * Disconnect from multicast group
     */
    public void disconnect() {
        isReceiving = false;

        if (frameReconstructorExecutor != null) {
            frameReconstructorExecutor.shutdown();
        }

        try {
            if (socket != null && group != null) {
                socket.leaveGroup(group);
                socket.close();
            }

            dbManager.recordClientLeave(clientIp);
            dbManager.logEvent("DISCONNECT", clientIp, "Live client disconnected");
            dbManager.close();

            System.out.println("🔴 [LIVE-CLIENT] Disconnected");

            if (callback != null) {
                callback.onDisconnected();
            }

        } catch (IOException e) {
            System.err.println("❌ [LIVE-CLIENT] Error disconnecting: " + e.getMessage());
        }
    }

    // ==================== UTILITIES ====================

    private static NetworkInterface findMulticastInterface() {
        try {
            List<NetworkInterface> candidates = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                if (!nif.supportsMulticast()) continue;

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

    private static String getFirstIPv4Address(NetworkInterface nif) {
        if (nif == null) return null;
        Enumeration<InetAddress> addrs = nif.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    // ==================== GETTERS ====================

    public int getFramesReceived() { return framesReceived; }
    public int getPacketsReceived() { return packetsReceived; }
    public int getDroppedPackets() { return droppedPackets; }
    public long getBytesReceived() { return bytesReceived; }
    public double getActualFPS() { return actualFPS; }

    /**
     * Get statistics
     */
    public LiveStreamStatistics getStatistics() {
        LiveStreamStatistics stats = new LiveStreamStatistics();
        stats.framesReceived = framesReceived;
        stats.packetsReceived = packetsReceived;
        stats.droppedPackets = droppedPackets;
        stats.bytesReceived = bytesReceived;
        stats.actualFPS = actualFPS;
        stats.bandwidth = bandwidthMonitor.getFormattedBandwidth();
        stats.bufferSize = frameBuffers.size();
        return stats;
    }

    /**
     * Statistics class
     */
    public static class LiveStreamStatistics {
        public int framesReceived;
        public int packetsReceived;
        public int droppedPackets;
        public long bytesReceived;
        public double actualFPS;
        public String bandwidth;
        public int bufferSize;

        @Override
        public String toString() {
            return String.format(
                    "Live Stats: Frames=%d, Packets=%d, Dropped=%d, FPS=%.1f, BW=%s, Buffer=%d",
                    framesReceived, packetsReceived, droppedPackets, actualFPS, bandwidth, bufferSize
            );
        }
    }
}