package com.hutech.videostreaming.common;

import java.sql.*;
import java.time.LocalDateTime;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        connect();
    }

    /**
     * Kết nối đến MySQL database
     */
    private void connect() {
        try {
            // Load MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Tạo connection
            connection = DriverManager.getConnection(
                    Constants.DB_URL,
                    Constants.DB_USER,
                    Constants.DB_PASSWORD
            );

            System.out.println("✅ [DB] Connected to MySQL successfully");

        } catch (ClassNotFoundException e) {
            System.err.println("❌ [DB] MySQL Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("❌ [DB] Connection failed: " + e.getMessage());
            System.err.println("    Make sure MySQL is running and credentials are correct");
        }
    }

    /**
     * Ghi log sự kiện vào database
     */
    public void logEvent(String eventType, String clientIp, String message) {
        if (connection == null) return;

        String sql = "INSERT INTO stream_logs (event_type, client_ip, message) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, eventType);
            stmt.setString(2, clientIp);
            stmt.setString(3, message);
            stmt.executeUpdate();

            System.out.println("📝 [DB] Logged: " + eventType + " - " + message);

        } catch (SQLException e) {
            System.err.println("❌ [DB] Failed to log event: " + e.getMessage());
        }
    }

    /**
     * Ghi nhận client join vào session
     */
    public void recordClientJoin(String clientIp) {
        if (connection == null) return;

        String sql = "INSERT INTO client_sessions (client_ip, join_time) VALUES (?, NOW())";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, clientIp);
            stmt.executeUpdate();

            System.out.println("📝 [DB] Client joined: " + clientIp);

        } catch (SQLException e) {
            System.err.println("❌ [DB] Failed to record join: " + e.getMessage());
        }
    }

    /**
     * Ghi nhận client leave khỏi session
     */
    public void recordClientLeave(String clientIp) {
        if (connection == null) return;

        String sql = "UPDATE client_sessions SET leave_time = NOW() " +
                "WHERE client_ip = ? AND leave_time IS NULL";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, clientIp);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                System.out.println("📝 [DB] Client left: " + clientIp);
            }

        } catch (SQLException e) {
            System.err.println("❌ [DB] Failed to record leave: " + e.getMessage());
        }
    }

    /**
     * Cập nhật số packet đã nhận cho client
     */
    public void updatePacketCount(String clientIp, int packetCount) {
        if (connection == null) return;

        String sql = "UPDATE client_sessions SET packets_received = ? " +
                "WHERE client_ip = ? AND leave_time IS NULL";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, packetCount);
            stmt.setString(2, clientIp);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ [DB] Failed to update packet count: " + e.getMessage());
        }
    }

    /**
     * Đóng kết nối database
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ [DB] Connection closed");
            }
        } catch (SQLException e) {
            System.err.println("❌ [DB] Error closing connection: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra connection có còn hoạt động không
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}