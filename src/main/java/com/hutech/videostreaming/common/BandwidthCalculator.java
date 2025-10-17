package com.hutech.videostreaming.common;

import java.util.*;

public class BandwidthCalculator {
    private LinkedList<Long> timestamps;
    private LinkedList<Integer> bytesReceived;
    private static final int WINDOW_SIZE = 10; // 10 seconds window

    public BandwidthCalculator() {
        timestamps = new LinkedList<>();
        bytesReceived = new LinkedList<>();
    }

    public void addDataPoint(int bytes) {
        long now = System.currentTimeMillis();

        timestamps.add(now);
        bytesReceived.add(bytes);

        // Remove old data
        while (!timestamps.isEmpty() &&
                (now - timestamps.getFirst()) > WINDOW_SIZE * 1000) {
            timestamps.removeFirst();
            bytesReceived.removeFirst();
        }
    }

    public double getCurrentBandwidth() {
        if (timestamps.size() < 2) return 0;

        long timeDiff = timestamps.getLast() - timestamps.getFirst();
        if (timeDiff == 0) return 0;

        int totalBytes = bytesReceived.stream().mapToInt(Integer::intValue).sum();

        // Return in Mbps
        return (totalBytes * 8.0) / (timeDiff / 1000.0) / 1_000_000;
    }

    public String getFormattedBandwidth() {
        double bw = getCurrentBandwidth();
        if (bw < 1) {
            return String.format("%.2f Kbps", bw * 1000);
        }
        return String.format("%.2f Mbps", bw);
    }
}