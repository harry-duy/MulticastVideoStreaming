package com.hutech.videostreaming.client;

import com.hutech.videostreaming.common.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class VideoClient {
    private MulticastSocket socket;
    private InetAddress group;
    private DatabaseManager dbManager;
    private volatile boolean isReceiving = false;
    private volatile boolean isStreamActive = false;
    private Map<Integer, VideoPacket> receivedPackets; // Use Map for faster access
    private ClientCallback callback;
    private String clientIp;

    // Statistics
    private int packetsReceived = 0;
    private int droppedPackets = 0;
    private int lastSequenceNumber = -1;
    private int expectedTotalPackets = 0;

    // Auto-reconnect settings
    private volatile boolean autoReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    // Timeout detection
    private long lastPacketTime = 0;
    private static final long STREAM_TIMEOUT = 5000; // 5 seconds

    // Bandwidth monitor
    private BandwidthMonitor bandwidthMonitor;

    public interface ClientCallback {
        void onConnected();
        void onStreamStarted();
        void onPacketReceived(VideoPacket packet, int received, int dropped);
        void onStreamPaused();
        void onStreamResumed();
        void onStreamStopped();
        void onDisconnected();
        void onError(String error);
    }

    public VideoClient(ClientCallback callback) {
        this.callback = callback;
        this.receivedPackets = new ConcurrentHashMap<>();
        this.dbManager = new DatabaseManager();
        this.bandwidthMonitor = new BandwidthMonitor();
    }

    /** Connect to multicast group */
    public void connect() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Constants.MULTICAST_PORT));

            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            // Select appropriate network interface
            NetworkInterface networkInterface = findMulticastInterface();
            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);

                // Join multicast group on specific interface
                try {
                    socket.joinGroup(new InetSocketAddress(group, Constants.MULTICAST_PORT), networkInterface);
                } catch (Throwable t) {
                    // Fallback for older JDKs
                    socket.joinGroup(group);
                }

                clientIp = getFirstIPv4Address(networkInterface);

                System.out.println("🧩 [CLIENT] Using network interface: " +
                        networkInterface.getName() + " (" + networkInterface.getDisplayName() + ")");
            } else {
                // Fallback to default
                socket.joinGroup(group);
                clientIp = InetAddress.getLocalHost().getHostAddress();
                System.out.println("⚠️ [CLIENT] Using default network interface");
            }

            // Increase receive buffer to reduce packet drops
            try {
                socket.setReceiveBufferSize(8 * 1024 * 1024); // 8MB
                System.out.println("🧪 [CLIENT] Receive buffer: " + socket.getReceiveBufferSize());
            } catch (Exception e) {
                System.out.println("⚠️ [CLIENT] Could not set receive buffer size");
            }

            // Set socket timeout for better error detection
            socket.setSoTimeout(1000); // 1 second timeout

            System.out.println("✅ [CLIENT] Connected to multicast group");
            System.out.println("📡 Address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 Port: " + Constants.MULTICAST_PORT);
            System.out.println("💻 Local IP: " + clientIp);

            dbManager.recordClientJoin(clientIp);
            dbManager.logEvent("CONNECT", clientIp, "Client joined multicast group");

            reconnectAttempts = 0;

            if (callback != null) {
                callback.onConnected();
            }

            startReceiving();
            startTimeoutMonitor();

        } catch (IOException e) {
            System.err.println("❌ [CLIENT] Connection failed: " + e.getMessage());
            if (callback != null) {
                callback.onError("Connection failed: " + e.getMessage());
            }
        }
    }

    /** Find suitable network interface */
    private static NetworkInterface findMulticastInterface() {
        try {
            List<NetworkInterface> candidates = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

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

            return candidates.isEmpty() ? null : candidates.get(0);

        } catch (SocketException e) {
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

    /** Start receiving packets */
    private void startReceiving() {
        isReceiving = true;

        new Thread(() -> {
            byte[] buffer = new byte[Constants.MAX_PACKET_SIZE + Constants.HEADER_SIZE + 40000]; // Extra buffer
            System.out.println("🎧 [CLIENT] Listening for packets...");

            while (isReceiving) {
                try {
                    DatagramPacket dgPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dgPacket);

                    lastPacketTime = System.currentTimeMillis();

                    // Parse packet
                    VideoPacket packet = VideoPacket.fromByteArray(buffer, dgPacket.getLength());
                    handlePacket(packet);

                } catch (SocketTimeoutException e) {
                    // This is normal - just checking for stop condition
                    continue;

                } catch (SocketException e) {
                    if (isReceiving) {
                        System.err.println("❌ [CLIENT] Socket error: " + e.getMessage());
                        if (autoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            System.err.println("🔄 [CLIENT] Attempting reconnect...");
                            attemptReconnect();
                        } else {
                            if (callback != null) {
                                callback.onError("Connection lost");
                            }
                        }
                        break;
                    }

                } catch (IOException e) {
                    if (isReceiving) {
                        System.err.println("❌ [CLIENT] Error receiving packet: " + e.getMessage());
                    }
                }
            }

            System.out.println("🔇 [CLIENT] Stopped listening");
        }, "ClientReceiveThread").start();
    }

    /** Monitor for stream timeout */
    private void startTimeoutMonitor() {
        new Thread(() -> {
            while (isReceiving) {
                try {
                    Thread.sleep(1000);

                    if (isStreamActive && lastPacketTime > 0) {
                        long timeSinceLastPacket = System.currentTimeMillis() - lastPacketTime;
                        if (timeSinceLastPacket > STREAM_TIMEOUT) {
                            System.err.println("⚠️ [CLIENT] Stream timeout detected");
                            isStreamActive = false;

                            if (callback != null) {
                                callback.onError("Stream timeout - no packets received");
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ClientTimeoutMonitor").start();
    }

    /** Attempt to reconnect */
    private void attemptReconnect() {
        if (!autoReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.err.println("🚫 [CLIENT] Max reconnect attempts reached");
            if (callback != null) {
                callback.onError("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
            }
            return;
        }

        reconnectAttempts++;
        System.out.println("🔄 [CLIENT] Reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);

        new Thread(() -> {
            try {
                Thread.sleep(3000); // Wait 3 seconds before reconnecting
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /** Handle received packet */
    private void handlePacket(VideoPacket packet) {
        switch (packet.getCommand()) {
            case Constants.CMD_START:
                handleStreamStart();
                break;

            case Constants.CMD_DATA:
                handleDataPacket(packet);
                break;

            case Constants.CMD_PAUSE:
                handleStreamPause();
                break;

            case Constants.CMD_RESUME:
                handleStreamResume();
                break;

            case Constants.CMD_STOP:
                handleStreamStop();
                break;
        }
    }

    private void handleStreamStart() {
        System.out.println("▶️ [CLIENT] Stream started");
        isStreamActive = true;
        packetsReceived = 0;
        droppedPackets = 0;
        lastSequenceNumber = -1;
        expectedTotalPackets = 0;
        receivedPackets.clear();
        bandwidthMonitor.reset();

        dbManager.logEvent("STREAM_START", clientIp, "Stream started");

        if (callback != null) {
            callback.onStreamStarted();
        }
    }

    private void handleDataPacket(VideoPacket packet) {
        if (!isStreamActive) {
            isStreamActive = true; // Auto-start if we receive data
        }

        int currentSeq = packet.getSequenceNumber();

        // Check for dropped packets
        if (lastSequenceNumber >= 0 && currentSeq > lastSequenceNumber + 1) {
            int dropped = currentSeq - lastSequenceNumber - 1;
            droppedPackets += dropped;

            System.out.println("⚠️ [CLIENT] Detected " + dropped + " dropped packets " +
                    "(expected: " + (lastSequenceNumber + 1) + ", got: " + currentSeq + ")");
        }

        // Store packet
        receivedPackets.put(currentSeq, packet);
        lastSequenceNumber = Math.max(lastSequenceNumber, currentSeq);
        packetsReceived++;

        // Update bandwidth
        if (packet.getData() != null) {
            bandwidthMonitor.addData(packet.getDataLength());
        }

        // Log progress
        if (packetsReceived % 50 == 0) {
            System.out.println("📥 [CLIENT] Received " + packetsReceived +
                    " packets (dropped: " + droppedPackets +
                    ", bandwidth: " + bandwidthMonitor.getFormattedBandwidth() + ")");
            dbManager.updatePacketCount(clientIp, packetsReceived);
        }

        if (callback != null) {
            callback.onPacketReceived(packet, packetsReceived, droppedPackets);
        }
    }

    private void handleStreamPause() {
        System.out.println("⏸️ [CLIENT] Stream paused");
        dbManager.logEvent("STREAM_PAUSE", clientIp, "Stream paused");
        if (callback != null) callback.onStreamPaused();
    }

    private void handleStreamResume() {
        System.out.println("▶️ [CLIENT] Stream resumed");
        isStreamActive = true;
        dbManager.logEvent("STREAM_RESUME", clientIp, "Stream resumed");
        if (callback != null) callback.onStreamResumed();
    }

    private void handleStreamStop() {
        System.out.println("⏹️ [CLIENT] Stream stopped");
        isStreamActive = false;

        System.out.println("📊 [CLIENT] Final statistics:");
        System.out.println("   Total received: " + packetsReceived);
        System.out.println("   Total dropped: " + droppedPackets);

        double successRate = packetsReceived > 0 ?
                (packetsReceived * 100.0 / (packetsReceived + droppedPackets)) : 0;
        System.out.println("   Success rate: " + String.format("%.2f%%", successRate));
        System.out.println("   Avg bandwidth: " + bandwidthMonitor.getFormattedBandwidth());

        dbManager.logEvent("STREAM_STOP", clientIp,
                String.format("Stream stopped. Received: %d, Dropped: %d, Success rate: %.2f%%",
                        packetsReceived, droppedPackets, successRate));

        if (callback != null) callback.onStreamStopped();
    }

    /** Disconnect from multicast group */
    public void disconnect() {
        isReceiving = false;
        isStreamActive = false;

        try {
            if (socket != null && group != null) {
                socket.leaveGroup(group);
                socket.close();
            }

            dbManager.recordClientLeave(clientIp);
            dbManager.logEvent("DISCONNECT", clientIp, "Client left multicast group");
            dbManager.close();

            System.out.println("🔴 [CLIENT] Disconnected");

            if (callback != null) callback.onDisconnected();

        } catch (IOException e) {
            System.err.println("❌ [CLIENT] Error disconnecting: " + e.getMessage());
        }
    }

    /** Save received video to file */
    public void saveReceivedVideo(String outputPath) {
        try {
            System.out.println("💾 [CLIENT] Starting video save process...");
            System.out.println("📦 [CLIENT] Total unique packets: " + receivedPackets.size());

            if (receivedPackets.isEmpty()) {
                System.err.println("❌ [CLIENT] No packets to save!");
                if (callback != null) {
                    callback.onError("No video data to save");
                }
                return;
            }

            // Find sequence range
            int minSeq = Integer.MAX_VALUE;
            int maxSeq = Integer.MIN_VALUE;
            for (int seq : receivedPackets.keySet()) {
                minSeq = Math.min(minSeq, seq);
                maxSeq = Math.max(maxSeq, seq);
            }

            System.out.println("📊 [CLIENT] Sequence range: " + minSeq + " to " + maxSeq);

            // Check for missing packets
            List<Integer> missingSequences = new ArrayList<>();
            for (int seq = minSeq; seq <= maxSeq; seq++) {
                if (!receivedPackets.containsKey(seq)) {
                    missingSequences.add(seq);
                }
            }

            if (!missingSequences.isEmpty()) {
                System.out.println("⚠️ [CLIENT] Missing " + missingSequences.size() + " packets");
                if (missingSequences.size() <= 20) {
                    System.out.println("   Missing sequences: " + missingSequences);
                }
            }

            // Write to file
            System.out.println("💾 [CLIENT] Writing to file: " + outputPath);

            try (FileOutputStream fos = new FileOutputStream(outputPath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 1024 * 1024)) { // 1MB buffer

                long totalBytesWritten = 0;
                int packetsWritten = 0;

                // Write packets in sequence order
                for (int seq = minSeq; seq <= maxSeq; seq++) {
                    VideoPacket packet = receivedPackets.get(seq);

                    if (packet != null &&
                            packet.getCommand() == Constants.CMD_DATA &&
                            packet.getData() != null &&
                            packet.getDataLength() > 0) {

                        bos.write(packet.getData(), 0, packet.getDataLength());
                        totalBytesWritten += packet.getDataLength();
                        packetsWritten++;

                        // Progress every 100 packets
                        if (packetsWritten % 100 == 0) {
                            System.out.println("   Written: " + packetsWritten + " packets, " +
                                    formatFileSize(totalBytesWritten));
                        }
                    }
                }

                bos.flush();

                System.out.println("✅ [CLIENT] Video saved successfully!");
                System.out.println("📊 [CLIENT] Save statistics:");
                System.out.println("   Packets written: " + packetsWritten);
                System.out.println("   Total size: " + formatFileSize(totalBytesWritten));
                System.out.println("   Missing packets: " + missingSequences.size());
                System.out.println("   File: " + outputPath);

                // Verify file
                File savedFile = new File(outputPath);
                if (savedFile.exists() && savedFile.length() > 0) {
                    System.out.println("✅ [CLIENT] File verified: " + formatFileSize(savedFile.length()));

                    // Check if file size is reasonable
                    if (savedFile.length() < 1000) {
                        System.err.println("⚠️ [CLIENT] Warning: File size is very small!");
                    }
                } else {
                    System.err.println("❌ [CLIENT] File verification failed!");
                }

                dbManager.logEvent("SAVE", clientIp,
                        String.format("Video saved: %s (%.2f MB, %d packets)",
                                outputPath, totalBytesWritten / (1024.0 * 1024.0), packetsWritten));

            }

        } catch (IOException e) {
            System.err.println("❌ [CLIENT] Failed to save video: " + e.getMessage());
            e.printStackTrace();
            if (callback != null) {
                callback.onError("Failed to save video: " + e.getMessage());
            }
        }
    }

    /** Format file size */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // Getters
    public List<VideoPacket> getReceivedPackets() {
        return new ArrayList<>(receivedPackets.values());
    }

    public int getPacketsReceived() { return packetsReceived; }
    public int getDroppedPackets() { return droppedPackets; }
    public String getClientIp() { return clientIp; }
    public boolean isPaused() { return false; }

    // Auto-reconnect control
    public void setAutoReconnect(boolean enabled) {
        this.autoReconnect = enabled;
    }

    public boolean isAutoReconnectEnabled() {
        return autoReconnect;
    }
}