package com.hutech.videostreaming.common;

import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Thông tin về client đang kết nối
 */
public class ClientInfo {
    private String ipAddress;
    private LocalDateTime joinTime;
    private int packetsReceived;
    private int packetsDropped;
    private boolean isActive;

    public ClientInfo(String ipAddress) {
        this.ipAddress = ipAddress;
        this.joinTime = LocalDateTime.now();
        this.packetsReceived = 0;
        this.packetsDropped = 0;
        this.isActive = true;
    }

    // Getters
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getJoinTime() { return joinTime; }
    public int getPacketsReceived() { return packetsReceived; }
    public int getPacketsDropped() { return packetsDropped; }
    public boolean isActive() { return isActive; }

    // Setters
    public void setPacketsReceived(int count) { this.packetsReceived = count; }
    public void setPacketsDropped(int count) { this.packetsDropped = count; }
    public void setActive(boolean active) { this.isActive = active; }

    /**
     * Tính thời gian kết nối
     */
    public String getConnectionDuration() {
        Duration duration = Duration.between(joinTime, LocalDateTime.now());
        long seconds = duration.getSeconds();
        return String.format("%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60
        );
    }

    /**
     * Tính success rate
     */
    public double getSuccessRate() {
        int total = packetsReceived + packetsDropped;
        if (total == 0) return 100.0;
        return (double) packetsReceived / total * 100;
    }

    public String getFormattedSuccessRate() {
        return String.format("%.1f%%", getSuccessRate());
    }
}