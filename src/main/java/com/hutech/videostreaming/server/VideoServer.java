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

    // ✅ Quản lý danh sách client kết nối
    private Map<String, ClientConnection> connectedClients = new ConcurrentHashMap<>();

    // Thông tin client
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

    // ✅ Interface callback mở rộng
    public interface ServerCallback {
        void onServerStarted();
        void onStreamStarted(int totalPackets);
        void onPacketSent(int sequenceNumber, int total);
        void onStreamPaused();
        void onStreamResumed();
        void onStreamStopped();
        void onError(String error);

        // ➕ Thêm mới:
        void onClientConnected(String clientIp);
        void onClientDisconnected(String clientIp);
    }

    public VideoServer(ServerCallback callback) {
        this.callback = callback;
        this.dbManager = new DatabaseManager();
    }

    /** Khởi tạo server và multicast socket */
    public void initialize() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            socket.setTimeToLive(1); // TTL=1: chỉ trong mạng LAN
            group = InetAddress.getByName(Constants.MULTICAST_ADDRESS);

            try {
                socket.setSendBufferSize(4 * 1024 * 1024);
            } catch (Exception ignore) {}

            System.out.println("🎬 [SERVER] Initialized successfully");
            System.out.println("📡 [SERVER] Multicast address: " + Constants.MULTICAST_ADDRESS);
            System.out.println("🔌 [SERVER] Port: " + Constants.MULTICAST_PORT);
            try {
                System.out.println("🧪 [SERVER] SendBuf: " + socket.getSendBufferSize());
            } catch (Exception ignore) {}

            dbManager.logEvent("INIT", "SERVER", "Server initialized");

            if (callback != null) callback.onServerStarted();

        } catch (IOException e) {
            System.err.println("❌ [SERVER] Failed to initialize: " + e.getMessage());
            if (callback != null) callback.onError("Failed to initialize server: " + e.getMessage());
        }
    }

    /** ✅ Đăng ký client mới */
    public void registerClient(String clientIp) {
        ClientConnection client = new ClientConnection();
        client.ip = clientIp;
        client.connectTime = System.currentTimeMillis();
        client.isActive = true;

        connectedClients.put(clientIp, client);
        System.out.println("🟢 [SERVER] Client connected: " + clientIp);

        if (callback != null) callback.onClientConnected(clientIp);
    }

    /** ✅ Ngắt kết nối client */
    public void removeClient(String clientIp) {
        ClientConnection client = connectedClients.get(clientIp);
        if (client != null) {
            client.isActive = false;
            connectedClients.remove(clientIp);
            System.out.println("🔴 [SERVER] Client disconnected: " + clientIp);

            if (callback != null) callback.onClientDisconnected(clientIp);
        }
    }

    /** Lấy danh sách client hiện tại */
    public List<ClientConnection> getConnectedClients() {
        return new ArrayList<>(connectedClients.values());
    }

    /** Bắt đầu streaming video từ file */
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

                totalPackets = (int) Math.ceil((double) videoData.length / Constants.MAX_PACKET_SIZE);
                System.out.println("📦 [SERVER] Total packets: " + totalPackets);

                sendControlPacket(Constants.CMD_START);
                dbManager.logEvent("START", "SERVER", "Stream started: " + videoFile.getName());

                if (callback != null) callback.onStreamStarted(totalPackets);

                int offset = 0;
                int packetsSent = 0;

                while (isStreaming && offset < videoData.length) {
                    while (isPaused && isStreaming) Thread.sleep(100);
                    if (!isStreaming) break;

                    int chunkSize = Math.min(Constants.MAX_PACKET_SIZE, videoData.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(videoData, offset, chunk, 0, chunkSize);

                    VideoPacket packet = new VideoPacket(Constants.CMD_DATA, sequenceNumber++, chunk);
                    sendPacket(packet);

                    packetsSent++;
                    offset += chunkSize;

                    if (callback != null && packetsSent % 10 == 0) {
                        callback.onPacketSent(packetsSent, totalPackets);
                    }

                    if (packetsSent % 100 == 0) {
                        System.out.println("📤 [SERVER] Sent " + packetsSent + "/" + totalPackets + " packets");
                    }

                    Thread.sleep(Constants.FRAME_DELAY);
                }

                if (isStreaming) {
                    sendControlPacket(Constants.CMD_STOP);
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

    /** Tạm dừng streaming */
    public void pauseStreaming() {
        if (!isStreaming || isPaused) return;

        isPaused = true;
        sendControlPacket(Constants.CMD_PAUSE);
        dbManager.logEvent("PAUSE", "SERVER", "Stream paused at packet #" + sequenceNumber);

        System.out.println("⏸️ [SERVER] Stream paused");
        if (callback != null) callback.onStreamPaused();
    }

    /** Tiếp tục streaming */
    public void resumeStreaming() {
        if (!isStreaming || !isPaused) return;

        isPaused = false;
        sendControlPacket(Constants.CMD_RESUME);
        dbManager.logEvent("RESUME", "SERVER", "Stream resumed at packet #" + sequenceNumber);

        System.out.println("▶️ [SERVER] Stream resumed");
        if (callback != null) callback.onStreamResumed();
    }

    /** Dừng streaming */
    public void stopStreaming() {
        if (!isStreaming) return;

        isStreaming = false;
        isPaused = false;
        sendControlPacket(Constants.CMD_STOP);
        dbManager.logEvent("STOP", "SERVER", "Stream stopped by user");

        System.out.println("⏹️ [SERVER] Stream stopped");
        if (callback != null) callback.onStreamStopped();
    }

    /** Gửi packet */
    private void sendPacket(VideoPacket packet) {
        try {
            byte[] data = packet.toByteArray();
            DatagramPacket dgPacket = new DatagramPacket(data, data.length, group, Constants.MULTICAST_PORT);
            socket.send(dgPacket);
        } catch (IOException e) {
            System.err.println("❌ [SERVER] Failed to send packet: " + e.getMessage());
        }
    }

    /** Gửi lệnh điều khiển */
    private void sendControlPacket(byte command) {
        VideoPacket packet = new VideoPacket(command, sequenceNumber++, new byte[0]);
        sendPacket(packet);
    }

    /** Format file size */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /** Đóng server */
    public void close() {
        stopStreaming();
        if (socket != null && !socket.isClosed()) socket.close();
        if (dbManager != null) dbManager.close();
        System.out.println("🔴 [SERVER] Closed");
    }

    // Getters
    public boolean isStreaming() { return isStreaming; }
    public boolean isPaused() { return isPaused; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getTotalPackets() { return totalPackets; }
}
