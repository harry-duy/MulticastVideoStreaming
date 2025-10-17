package com.hutech.videostreaming.common;

public class Constants {
    // ===== Multicast Configuration =====
    public static final String MULTICAST_ADDRESS = "230.0.0.1";
    public static final int MULTICAST_PORT = 4446;

    // ===== Default Packet Settings =====
    public static final int MAX_PACKET_SIZE = 60000;
    public static final int HEADER_SIZE = 17;

    // ===== Default Video Settings =====
    public static final int FPS = 15;
    public static final int FRAME_DELAY = 1000 / FPS;

    // ===== Database Configuration =====
    public static final String DB_URL = "jdbc:mysql://localhost:3306/video_streaming";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "159357bapD";

    // ===== Control Commands =====
    public static final byte CMD_START  = 0x01;
    public static final byte CMD_PAUSE  = 0x02;
    public static final byte CMD_STOP   = 0x03;
    public static final byte CMD_DATA   = 0x04;
    public static final byte CMD_RESUME = 0x05;

    // ===== Stream Quality Presets =====
    public enum StreamQuality {
        LOW(30000, 15),      // 30KB packets, 15 FPS
        MEDIUM(60000, 25),   // 60KB packets, 25 FPS
        HIGH(100000, 30);    // 100KB packets, 30 FPS

        public final int packetSize;
        public final int fps;

        StreamQuality(int packetSize, int fps) {
            this.packetSize = packetSize;
            this.fps = fps;
        }

        public int getFrameDelay() {
            return 1000 / fps;
        }

        @Override
        public String toString() {
            return name() + " (" + packetSize / 1000 + "KB, " + fps + " FPS)";
        }
    }
}
