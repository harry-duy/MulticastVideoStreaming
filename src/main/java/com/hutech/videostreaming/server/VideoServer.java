package com.hutech.videostreaming.server;

import com.hutech.videostreaming.common.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VideoServer {
    private MulticastSocket socket;
    private InetAddress group;
    private DatabaseManager dbManager;
    private volatile boolean isStreaming = false;
    private volatile boolean isPaused = false;
    private int sequenceNumber = 0;
    private int totalPackets = 0;
    private ServerCallback callback;

    // Quality settings
    private int currentPacketSize = Constants.MAX_PACKET_SIZE;
    private int currentFPS = Constants.FPS;

    // Connected clients management
    private Map<String, ClientConnection> connectedClients = new ConcurrentHashMap<>();

    // Packet retry mechanism
    private static final int PACKET_SEND_RETRIES = 2;
    private static final int PACKET_RETRY_DELAY = 5; // ms

    public static class ClientConnection {
        public String ip;
        public long connectTime;
        public int packetsReceived;
        public boolean isActive;

        public String getDuration() {
            long seconds = (System.currentTimeMillis() - connectTime) / 1000;
            return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }
    }

    public interface ServerCallback {
        void onServerStarted();
        void onStreamStarted(int totalPackets);
        void onPacketSent(int sequenceNumber, int total);
        void onStreamPaused();
        void onStreamResumed();
        void onStreamStopped();
        void onError(String error);
        void onClientConnected(String clientIp);
        void onClientDisconnected(String clientIp);
    }

    public VideoServer(ServerCallback callback) {
        this.callback = callback;
        this.dbManager = new DatabaseManager();
    }

    /** Initialize server with proper network interface */
    public void initialize() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));

            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            // IMPORTANT: Select appropriate network interface
            NetworkInterface networkInterface = findMulticastInterface();
            String serverIp = "Unknown";

            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);
                serverIp = getFirstIPv4Address(networkInterface);

                System.out.println("🎬 [SERVER] Using network interface: " +
                        networkInterface.getName() + " (" + networkInterface.getDisplayName() + ")");
                System.out.println("🌐 [SERVER] Server IP: " + serverIp);
            } else {
                System.out.println("⚠️ [SERVER] Using default network interface");
            }

            // Set TTL for LAN only
            socket.setTimeToLive(1);

            // Increase send buffer
            try {
                socket.setSendBufferSize(8 * 1024 * 1024); // 8MB
                System.out.println("🧪 [SERVER] Send buffer: " + socket.getSendBufferSize());
            } catch (Exception e) {
                System.out.println("⚠️ [SERVER] Could not set send buffer size");
            }

            System.out.println("🎬 [SERVER] Initialized successfully");
            System.out.println("📡 [SERVER] Multicast address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 [SERVER] Port: " + Constants.MULTICAST_PORT);

            dbManager.logEvent("INIT", serverIp, "Server initialized");

            if (callback != null) callback.onServerStarted();

        } catch (IOException e) {
            System.err.println("❌ [SERVER] Failed to initialize: " + e.getMessage());
            if (callback != null) callback.onError("Failed to initialize server: " + e.getMessage());
        }
    }

    /** Find suitable network interface for multicast */
    private static NetworkInterface findMulticastInterface() {
        try {
            List<NetworkInterface> candidates = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

                // Skip if not suitable
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                if (!nif.supportsMulticast()) continue;

                // Check for IPv4 address
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

            // Prefer ethernet/wifi interfaces
            for (NetworkInterface nif : candidates) {
                String name = nif.getName().toLowerCase();
                if (name.contains("eth") || name.contains("en") || name.contains("wlan") || name.contains("wi")) {
                    return nif;
                }
            }

            // Return first available
            return candidates.isEmpty() ? null : candidates.get(0);

        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Get first IPv4 address from interface */
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

    /** Set streaming quality */
    public void setQuality(Constants.StreamQuality quality) {
        this.currentPacketSize = quality.packetSize;
        this.currentFPS = quality.fps;
        System.out.println("⚙️ [SERVER] Quality set to: " + quality);
    }

    /** Register new client */
    public void registerClient(String clientIp) {
        ClientConnection client = new ClientConnection();
        client.ip = clientIp;
        client.connectTime = System.currentTimeMillis();
        client.isActive = true;

        connectedClients.put(clientIp, client);
        System.out.println("🟢 [SERVER] Client connected: " + clientIp);

        if (callback != null) callback.onClientConnected(clientIp);
    }

    /** Remove client */
    public void removeClient(String clientIp) {
        ClientConnection client = connectedClients.get(clientIp);
        if (client != null) {
            client.isActive = false;
            connectedClients.remove(clientIp);
            System.out.println("🔴 [SERVER] Client disconnected: " + clientIp);

            if (callback != null) callback.onClientDisconnected(clientIp);
        }
    }

    /** Get connected clients */
    public List<ClientConnection> getConnectedClients() {
        return new ArrayList<>(connectedClients.values());
    }

    /** Start streaming with quality setting */
    public void startStreaming(String videoPath, Constants.StreamQuality quality) {
        setQuality(quality);
        startStreaming(videoPath);
    }

    /** Start streaming with adaptive rate */
    public void startStreaming(String videoPath) {
        if (isStreaming) {
            System.out.println("⚠️ [SERVER] Already streaming");
            return;
        }

        isStreaming = true;
        isPaused = false;
        sequenceNumber = 0;

        new Thread(() -> {
            try {
                File videoFile = new File(videoPath);
                if (!videoFile.exists()) {
                    throw new FileNotFoundException("Video file not found: " + videoPath);
                }

                byte[] videoData = Files.readAllBytes(videoFile.toPath());
                long fileSize = videoData.length;

                System.out.println("📹 [SERVER] Video loaded: " + videoFile.getName());
                System.out.println("📊 [SERVER] File size: " + formatFileSize(fileSize));
                System.out.println("⚙️ [SERVER] Packet size: " + currentPacketSize + " bytes");
                System.out.println("🎬 [SERVER] FPS: " + currentFPS);

                totalPackets = (int) Math.ceil((double) videoData.length / currentPacketSize);
                System.out.println("📦 [SERVER] Total packets: " + totalPackets);

                // Send START command multiple times for reliability
                for (int i = 0; i < 3; i++) {
                    sendControlPacket(Constants.CMD_START);
                    Thread.sleep(100);
                }

                dbManager.logEvent("START", "SERVER", "Stream started: " + videoFile.getName());

                if (callback != null) callback.onStreamStarted(totalPackets);

                // Wait for clients to be ready
                Thread.sleep(500);

                int offset = 0;
                int packetsSent = 0;
                int frameDelay = 1000 / currentFPS;
                long lastSendTime = System.currentTimeMillis();

                while (isStreaming && offset < videoData.length) {
                    // Handle pause
                    while (isPaused && isStreaming) {
                        Thread.sleep(100);
                    }
                    if (!isStreaming) break;

                    // Prepare chunk
                    int chunkSize = Math.min(currentPacketSize, videoData.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(videoData, offset, chunk, 0, chunkSize);

                    // Create and send packet with retry
                    VideoPacket packet = new VideoPacket(Constants.CMD_DATA, sequenceNumber++, chunk);
                    sendPacketWithRetry(packet);

                    packetsSent++;
                    offset += chunkSize;

                    // Update callback
                    if (callback != null && packetsSent % 10 == 0) {
                        callback.onPacketSent(packetsSent, totalPackets);
                    }

                    // Log progress
                    if (packetsSent % 100 == 0) {
                        System.out.println("📤 [SERVER] Sent " + packetsSent + "/" + totalPackets +
                                " packets (" + String.format("%.1f%%", (packetsSent * 100.0 / totalPackets)) + ")");
                    }

                    // Adaptive delay to maintain FPS
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastSendTime;
                    long sleepTime = frameDelay - elapsed;

                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    } else if (sleepTime < -100) {
                        // If we're falling behind, add extra delay
                        Thread.sleep(frameDelay + 10);
                    }

                    lastSendTime = System.currentTimeMillis();
                }

                if (isStreaming) {
                    // Send STOP command multiple times for reliability
                    for (int i = 0; i < 3; i++) {
                        sendControlPacket(Constants.CMD_STOP);
                        Thread.sleep(100);
                    }

                    dbManager.logEvent("COMPLETE", "SERVER", "Stream completed: " + packetsSent + " packets sent");

                    System.out.println("✅ [SERVER] Streaming completed");
                    System.out.println("📊 [SERVER] Total packets sent: " + packetsSent);

                    if (callback != null) callback.onStreamStopped();
                }

                isStreaming = false;

            } catch (Exception e) {
                System.err.println("❌ [SERVER] Streaming error: " + e.getMessage());
                e.printStackTrace();
                isStreaming = false;

                if (callback != null) callback.onError("Streaming error: " + e.getMessage());
            }
        }).start();
    }

    /** Pause streaming */
    public void pauseStreaming() {
        if (!isStreaming || isPaused) return;

        isPaused = true;
        sendControlPacket(Constants.CMD_PAUSE);
        dbManager.logEvent("PAUSE", "SERVER", "Stream paused at packet #" + sequenceNumber);

        System.out.println("⏸️ [SERVER] Stream paused");
        if (callback != null) callback.onStreamPaused();
    }

    /** Resume streaming */
    public void resumeStreaming() {
        if (!isStreaming || !isPaused) return;

        isPaused = false;
        sendControlPacket(Constants.CMD_RESUME);
        dbManager.logEvent("RESUME", "SERVER", "Stream resumed at packet #" + sequenceNumber);

        System.out.println("▶️ [SERVER] Stream resumed");
        if (callback != null) callback.onStreamResumed();
    }

    /** Stop streaming */
    public void stopStreaming() {
        if (!isStreaming) return;

        isStreaming = false;
        isPaused = false;

        // Send STOP multiple times
        for (int i = 0; i < 3; i++) {
            sendControlPacket(Constants.CMD_STOP);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        dbManager.logEvent("STOP", "SERVER", "Stream stopped by user");

        System.out.println("⏹️ [SERVER] Stream stopped");
        if (callback != null) callback.onStreamStopped();
    }

    /** Send packet with retry mechanism */
    private void sendPacketWithRetry(VideoPacket packet) {
        for (int attempt = 0; attempt <= PACKET_SEND_RETRIES; attempt++) {
            try {
                byte[] data = packet.toByteArray();
                DatagramPacket dgPacket = new DatagramPacket(
                        data, data.length, group, Constants.MULTICAST_PORT
                );
                socket.send(dgPacket);

                if (attempt > 0) {
                    System.out.println("🔄 [SERVER] Packet sent on retry #" + attempt);
                }
                break;

            } catch (IOException e) {
                if (attempt == PACKET_SEND_RETRIES) {
                    System.err.println("❌ [SERVER] Failed to send packet after " +
                            PACKET_SEND_RETRIES + " retries: " + e.getMessage());
                } else {
                    try {
                        Thread.sleep(PACKET_RETRY_DELAY);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /** Send control packet */
    private void sendControlPacket(byte command) {
        VideoPacket packet = new VideoPacket(command, sequenceNumber++, new byte[0]);
        sendPacketWithRetry(packet);
    }

    /** Format file size */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /** Close server */
    public void close() {
        stopStreaming();
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (dbManager != null) {
            dbManager.close();
        }
        System.out.println("🔴 [SERVER] Closed");
    }

    // Getters
    public boolean isStreaming() { return isStreaming; }
    public boolean isPaused() { return isPaused; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getTotalPackets() { return totalPackets; }
}