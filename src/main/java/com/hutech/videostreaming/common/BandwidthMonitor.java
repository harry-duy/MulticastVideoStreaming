package com.hutech.videostreaming.common;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Tính toán băng thông thực tế trong thời gian thực
 */
public class BandwidthMonitor {

    private static class DataPoint {
        long timestamp;
        int bytes;

        DataPoint(long timestamp, int bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }

    private Queue<DataPoint> dataPoints;
    private static final int WINDOW_SECONDS = 5; // Tính trung bình 5 giây
    private long totalBytesTransferred = 0;

    public BandwidthMonitor() {
        dataPoints = new LinkedList<>();
    }

    /**
     * Thêm dữ liệu đã gửi/nhận
     */
    public synchronized void addData(int bytes) {
        long now = System.currentTimeMillis();
        dataPoints.offer(new DataPoint(now, bytes));
        totalBytesTransferred += bytes;

        // Xóa data cũ hơn WINDOW_SECONDS
        while (!dataPoints.isEmpty() &&
                (now - dataPoints.peek().timestamp) > WINDOW_SECONDS * 1000) {
            dataPoints.poll();
        }
    }

    /**
     * Lấy băng thông hiện tại (Mbps)
     */
    public synchronized double getCurrentBandwidthMbps() {
        if (dataPoints.size() < 2) return 0;

        long timeDiff = dataPoints.peek().timestamp -
                ((LinkedList<DataPoint>) dataPoints).getLast().timestamp;
        if (timeDiff == 0) return 0;

        int totalBytes = 0;
        for (DataPoint dp : dataPoints) {
            totalBytes += dp.bytes;
        }

        // Convert to Mbps
        double seconds = Math.abs(timeDiff) / 1000.0;
        return (totalBytes * 8.0) / seconds / 1_000_000;
    }

    /**
     * Lấy băng thông dạng text đẹp
     */
    public String getFormattedBandwidth() {
        double bw = getCurrentBandwidthMbps();
        if (bw < 0.001) {
            return "0 bps";
        } else if (bw < 1) {
            return String.format("%.2f Kbps", bw * 1000);
        } else {
            return String.format("%.2f Mbps", bw);
        }
    }

    /**
     * Tổng dữ liệu đã truyền
     */
    public String getTotalTransferred() {
        double mb = totalBytesTransferred / 1_000_000.0;
        if (mb < 1) {
            return String.format("%.2f KB", totalBytesTransferred / 1000.0);
        } else if (mb < 1000) {
            return String.format("%.2f MB", mb);
        } else {
            return String.format("%.2f GB", mb / 1000.0);
        }
    }

    /**
     * Reset monitor
     */
    public synchronized void reset() {
        dataPoints.clear();
        totalBytesTransferred = 0;
    }

    /**
     * Ước tính thời gian còn lại (giây)
     */
    public int estimateTimeRemaining(int bytesRemaining) {
        double bps = getCurrentBandwidthMbps() * 1_000_000 / 8;
        if (bps == 0) return -1;
        return (int) (bytesRemaining / bps);
    }

    /**
     * Format thời gian còn lại
     */
    public static String formatTime(int seconds) {
        if (seconds < 0) return "Calculating...";
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%dm %ds", minutes, secs);
    }
}
