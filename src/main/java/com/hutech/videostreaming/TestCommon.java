package com.hutech.videostreaming;

import com.hutech.videostreaming.common.*;

public class TestCommon {
    public static void main(String[] args) {
        System.out.println("=== TEST COMMON CLASSES ===\n");

        // Test 1: Constants
        System.out.println("1. Testing Constants:");
        System.out.println("   Multicast Address: " + Constants.MULTICAST_ADDRESS);
        System.out.println("   Multicast Port: " + Constants.MULTICAST_PORT);
        System.out.println("   ✅ Constants loaded\n");

        // Test 2: VideoPacket
        System.out.println("2. Testing VideoPacket:");
        try {
            byte[] testData = "Hello Video!".getBytes();
            VideoPacket packet = new VideoPacket(Constants.CMD_DATA, 1, testData);

            byte[] serialized = packet.toByteArray();
            System.out.println("   Serialized size: " + serialized.length + " bytes");

            VideoPacket deserialized = VideoPacket.fromByteArray(serialized, serialized.length);
            System.out.println("   Deserialized: " + new String(deserialized.getData()));
            System.out.println("   ✅ VideoPacket works\n");

        } catch (Exception e) {
            System.err.println("   ❌ VideoPacket failed: " + e.getMessage());
        }

        // Test 3: DatabaseManager
        System.out.println("3. Testing DatabaseManager:");
        DatabaseManager db = new DatabaseManager();

        if (db.isConnected()) {
            db.logEvent("TEST", "127.0.0.1", "Testing database connection");
            System.out.println("   ✅ Database connected and logged\n");
        } else {
            System.err.println("   ❌ Database connection failed\n");
        }

        db.close();

        System.out.println("=== TEST COMPLETED ===");
    }
}