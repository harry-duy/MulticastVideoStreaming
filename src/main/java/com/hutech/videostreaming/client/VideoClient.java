package com.hutech.videostreaming.client;

import com.hutech.videostreaming.common.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoClient {
    private MulticastSocket socket;
    private InetAddress group;
    private DatabaseManager dbManager;
    private volatile boolean isReceiving = false;
    private List<VideoPacket> receivedPackets;
    private ClientCallback callback;
    private String clientIp;
    private int packetsReceived = 0;
    private int droppedPackets = 0;
    private int lastSequenceNumber = -1;

    // ===== Auto-Reconnect variables =====
    private volatile boolean autoReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    // ===== Interface for GUI callbacks =====
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
        this.receivedPackets = new CopyOnWriteArrayList<>();
        this.dbManager = new DatabaseManager();
    }

    /**
     * Kết nối vào nhóm multicast
     */
    public void connect() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Constants.MULTICAST_PORT));

            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            // Select appropriate NIC for multicast
            NetworkInterface networkInterface = findMulticastInterface();
            if (networkInterface != null) {
                socket.setNetworkInterface(networkInterface);
                try {
                    socket.joinGroup(new InetSocketAddress(group, Constants.MULTICAST_PORT), networkInterface);
                } catch (Throwable t) {
                    // Fallback for older JDKs
                    socket.joinGroup(group);
                }
                clientIp = getFirstIPv4Address(networkInterface);
            } else {
                // Fallback to default
                socket.joinGroup(group);
                clientIp = InetAddress.getLocalHost().getHostAddress();
            }

            // Increase receive buffer to reduce drops under bursty load
            try {
                socket.setReceiveBufferSize(4 * 1024 * 1024);
            } catch (Exception ignore) {}

            System.out.println("✅ [CLIENT] Connected to multicast group");
            System.out.println("📡 Address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 Port: " + Constants.MULTICAST_PORT);
            System.out.println("💻 Local IP: " + clientIp);
            try {
                System.out.println("🧪 [CLIENT] RecvBuf: " + socket.getReceiveBufferSize());
                if (networkInterface != null) {
                    System.out.println("🧩 [CLIENT] NIC: " + networkInterface.getName() +
                            " (" + networkInterface.getDisplayName() + ")");
                }
            } catch (Exception ignore) {}

            dbManager.recordClientJoin(clientIp);
            dbManager.logEvent("CONNECT", clientIp, "Client joined multicast group");

            reconnectAttempts = 0; // reset attempts on success

            if (callback != null) {
                callback.onConnected();
            }

            startReceiving();

        } catch (IOException e) {
            System.err.println("❌ [CLIENT] Connection failed: " + e.getMessage());
            if (callback != null) {
                callback.onError("Connection failed: " + e.getMessage());
            }
        }
    }

    /**
     * Bắt đầu nhận packets từ multicast
     */
    private void startReceiving() {
        isReceiving = true;

        new Thread(() -> {
            byte[] buffer = new byte[Constants.MAX_PACKET_SIZE + Constants.HEADER_SIZE];
            System.out.println("🎧 [CLIENT] Listening for packets...");

            while (isReceiving) {
                try {
                    DatagramPacket dgPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dgPacket);

                    VideoPacket packet = VideoPacket.fromByteArray(buffer, dgPacket.getLength());
                    handlePacket(packet);

                } catch (SocketException e) {
                    if (isReceiving) {
                        System.err.println("❌ [CLIENT] Socket error: " + e.getMessage());
                        if (autoReconnect) {
                            System.err.println("🔄 [CLIENT] Connection lost. Attempting reconnect...");
                            if (callback != null) {
                                callback.onError("Connection lost. Attempting to reconnect...");
                            }
                            attemptReconnect();
                        } else {
                            if (callback != null) {
                                callback.onError("Connection lost. Auto-reconnect disabled.");
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
        }).start();
    }

    private static NetworkInterface findMulticastInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                if (!nif.supportsMulticast()) continue;
                // Prefer interface with an IPv4 address
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return nif;
                    }
                }
            }
        } catch (SocketException ignored) {}
        return null;
    }

    private static String getFirstIPv4Address(NetworkInterface nif) {
        if (nif == null) return null;
        Enumeration<InetAddress> addrs = nif.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    /**
     * Tự động reconnect sau khi mất kết nối
     */
    private void attemptReconnect() {
        if (!autoReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.err.println("🚫 [CLIENT] Reconnect attempts exceeded or disabled.");
            if (callback != null) callback.onError("Max reconnect attempts reached.");
            return;
        }

        reconnectAttempts++;
        System.out.println("🔄 [CLIENT] Attempting to reconnect... (" +
                reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");

        new Thread(() -> {
            try {
                Thread.sleep(3000); // wait 3 seconds
                connect(); // reconnect
                reconnectAttempts = 0;
            } catch (InterruptedException e) {
                System.err.println("⚠️ [CLIENT] Reconnect thread interrupted.");
            }
        }).start();
    }

    /**
     * Xử lý packet nhận được
     */
    private void handlePacket(VideoPacket packet) {
        switch (packet.getCommand()) {
            case Constants.CMD_START:
                System.out.println("▶️ [CLIENT] Stream started");
                packetsReceived = 0;
                droppedPackets = 0;
                lastSequenceNumber = -1;
                receivedPackets.clear();

                dbManager.logEvent("STREAM_START", clientIp, "Stream started");

                if (callback != null) {
                    callback.onStreamStarted();
                }
                break;

            case Constants.CMD_DATA:
                if (lastSequenceNumber != -1) {
                    int expected = lastSequenceNumber + 1;
                    if (packet.getSequenceNumber() > expected) {
                        droppedPackets += (packet.getSequenceNumber() - expected);
                    }
                }

                lastSequenceNumber = packet.getSequenceNumber();
                packetsReceived++;
                receivedPackets.add(packet);

                if (packetsReceived % 50 == 0) {
                    System.out.println("📥 [CLIENT] Received " + packetsReceived +
                            " packets (dropped: " + droppedPackets + ")");
                    dbManager.updatePacketCount(clientIp, packetsReceived);
                }

                if (callback != null) {
                    callback.onPacketReceived(packet, packetsReceived, droppedPackets);
                }
                break;

            case Constants.CMD_PAUSE:
                System.out.println("⏸️ [CLIENT] Stream paused");
                dbManager.logEvent("STREAM_PAUSE", clientIp, "Stream paused");
                if (callback != null) callback.onStreamPaused();
                break;

            case Constants.CMD_RESUME:
                System.out.println("▶️ [CLIENT] Stream resumed");
                dbManager.logEvent("STREAM_RESUME", clientIp, "Stream resumed");
                if (callback != null) callback.onStreamResumed();
                break;

            case Constants.CMD_STOP:
                System.out.println("⏹️ [CLIENT] Stream stopped");
                System.out.println("📊 Total received: " + packetsReceived);
                System.out.println("📊 Total dropped: " + droppedPackets);

                dbManager.logEvent("STREAM_STOP", clientIp,
                        "Stream stopped. Received: " + packetsReceived +
                                ", Dropped: " + droppedPackets);

                if (callback != null) callback.onStreamStopped();
                break;
        }
    }

    /**
     * Ngắt kết nối khỏi nhóm multicast
     */
    public void disconnect() {
        isReceiving = false;

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

    /**
     * Lưu video đã nhận vào file
     */
    public void saveReceivedVideo(String outputPath) {
        try {
            System.out.println("💾 [CLIENT] Starting video save process...");
            System.out.println("📦 [CLIENT] Total packets collected: " + receivedPackets.size());

            if (receivedPackets.isEmpty()) {
                System.err.println("❌ [CLIENT] No packets to save!");
                return;
            }

            // Bước 1: Sắp xếp packets
            System.out.println("🔄 [CLIENT] Sorting packets...");
            receivedPackets.sort(Comparator.comparingInt(VideoPacket::getSequenceNumber));

            // Bước 2: Kiểm tra packets thiếu
            checkMissingPackets();

            // Bước 3: Ghi vào file
            System.out.println("💾 [CLIENT] Writing to file: " + outputPath);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                long totalBytesWritten = 0;
                int packetsWritten = 0;

                for (VideoPacket packet : receivedPackets) {
                    // Chỉ ghi DATA packets
                    if (packet.getCommand() == Constants.CMD_DATA &&
                            packet.getData() != null &&
                            packet.getDataLength() > 0) {

                        fos.write(packet.getData(), 0, packet.getDataLength());
                        totalBytesWritten += packet.getDataLength();
                        packetsWritten++;

                        // Progress mỗi 100 packets
                        if (packetsWritten % 100 == 0) {
                            System.out.println("  Written: " + packetsWritten + " packets, " +
                                    (totalBytesWritten / 1024) + " KB");
                        }
                    }
                }

                fos.flush();

                System.out.println("✅ [CLIENT] Video saved successfully!");
                System.out.println("📊 [CLIENT] Statistics:");
                System.out.println("   - Packets written: " + packetsWritten);
                System.out.println("   - Total bytes: " + totalBytesWritten +
                        " (" + (totalBytesWritten / 1024 / 1024.0) + " MB)");
                System.out.println("   - File: " + outputPath);

                // Verify file
                File savedFile = new File(outputPath);
                if (savedFile.exists()) {
                    System.out.println("✅ [CLIENT] File exists, size: " +
                            savedFile.length() + " bytes");
                } else {
                    System.err.println("❌ [CLIENT] File was not created!");
                }

                dbManager.logEvent("SAVE", clientIp,
                        "Video saved: " + outputPath + " (" + totalBytesWritten + " bytes)");

            }

        } catch (IOException e) {
            System.err.println("❌ [CLIENT] Failed to save video: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void checkMissingPackets() {
        if (receivedPackets.isEmpty()) return;

        List<Integer> missingSeq = new ArrayList<>();
        int firstSeq = receivedPackets.get(0).getSequenceNumber();
        int lastSeq = receivedPackets.get(receivedPackets.size() - 1).getSequenceNumber();

        int receivedIndex = 0;
        for (int expectedSeq = firstSeq; expectedSeq <= lastSeq; expectedSeq++) {
            if (receivedIndex < receivedPackets.size() &&
                    receivedPackets.get(receivedIndex).getSequenceNumber() == expectedSeq) {
                receivedIndex++;
            } else {
                missingSeq.add(expectedSeq);
            }
        }

        if (!missingSeq.isEmpty()) {
            System.out.println("⚠️ [CLIENT] Missing packets: " + missingSeq.size() +
                    " out of " + (lastSeq - firstSeq + 1));
            System.out.println("    Sequence range: " + firstSeq + " to " + lastSeq);
            if (missingSeq.size() <= 10) {
                System.out.println("    Missing: " + missingSeq);
            }
        } else {
            System.out.println("✅ [CLIENT] All packets received in sequence!");
        }
    }

    // ===== Getters =====
    public List<VideoPacket> getReceivedPackets() {
        return new ArrayList<>(receivedPackets);
    }

    public int getPacketsReceived() { return packetsReceived; }
    public int getDroppedPackets() { return droppedPackets; }
    public String getClientIp() { return clientIp; }

    // ===== Auto-reconnect control =====
    public void setAutoReconnect(boolean enabled) {
        this.autoReconnect = enabled;
    }

    public boolean isAutoReconnectEnabled() {
        return autoReconnect;
    }
}
