package com.hutech.videostreaming.util;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Streaming Performance Profiler
 * Monitors CPU, Memory, Network, and provides performance recommendations
 */
public class StreamingProfiler {

    private OperatingSystemMXBean osBean;
    private MemoryMXBean memoryBean;
    private ThreadMXBean threadBean;
    private RuntimeMXBean runtimeBean;

    // Metrics
    private Map<String, MetricHistory> metrics;
    private ScheduledExecutorService scheduler;

    // Thresholds
    private static final double CPU_WARNING_THRESHOLD = 70.0;
    private static final double CPU_CRITICAL_THRESHOLD = 90.0;
    private static final double MEMORY_WARNING_THRESHOLD = 75.0;
    private static final double MEMORY_CRITICAL_THRESHOLD = 90.0;

    public StreamingProfiler() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.metrics = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Start profiling
     */
    public void startProfiling() {
        System.out.println("🔍 [PROFILER] Starting performance profiling...");

        scheduler.scheduleAtFixedRate(() -> {
            collectMetrics();
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Stop profiling
     */
    public void stopProfiling() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        System.out.println("🔍 [PROFILER] Profiling stopped");
    }

    /**
     * Collect system metrics
     */
    private void collectMetrics() {
        // CPU Usage
        double cpuLoad = getCPULoad();
        addMetric("cpu", cpuLoad);

        // Memory Usage
        double memoryUsage = getMemoryUsage();
        addMetric("memory", memoryUsage);

        // Thread Count
        int threadCount = threadBean.getThreadCount();
        addMetric("threads", threadCount);

        // Check thresholds
        checkThresholds(cpuLoad, memoryUsage);
    }

    /**
     * Get CPU load percentage
     */
    private double getCPULoad() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
            return sunOsBean.getProcessCpuLoad() * 100.0;
        }
        return osBean.getSystemLoadAverage();
    }

    /**
     * Get memory usage percentage
     */
    private double getMemoryUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        return (used * 100.0) / max;
    }

    /**
     * Add metric to history
     */
    private void addMetric(String name, double value) {
        metrics.computeIfAbsent(name, k -> new MetricHistory()).add(value);
    }

    /**
     * Check performance thresholds
     */
    private void checkThresholds(double cpuLoad, double memoryUsage) {
        // CPU warnings
        if (cpuLoad > CPU_CRITICAL_THRESHOLD) {
            System.err.println("🔴 [PROFILER] CRITICAL: CPU usage at " +
                    String.format("%.1f%%", cpuLoad));
            System.err.println("   Recommendation: Reduce FPS or quality");
        } else if (cpuLoad > CPU_WARNING_THRESHOLD) {
            System.out.println("⚠️ [PROFILER] WARNING: High CPU usage at " +
                    String.format("%.1f%%", cpuLoad));
        }

        // Memory warnings
        if (memoryUsage > MEMORY_CRITICAL_THRESHOLD) {
            System.err.println("🔴 [PROFILER] CRITICAL: Memory usage at " +
                    String.format("%.1f%%", memoryUsage));
            System.err.println("   Recommendation: Enable compression or restart");
        } else if (memoryUsage > MEMORY_WARNING_THRESHOLD) {
            System.out.println("⚠️ [PROFILER] WARNING: High memory usage at " +
                    String.format("%.1f%%", memoryUsage));
        }
    }

    /**
     * Get current metrics
     */
    public ProfileMetrics getCurrentMetrics() {
        ProfileMetrics metrics = new ProfileMetrics();

        metrics.cpuLoad = getCPULoad();
        metrics.memoryUsage = getMemoryUsage();
        metrics.threadCount = threadBean.getThreadCount();
        metrics.uptime = runtimeBean.getUptime();

        // Memory details
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        metrics.heapUsed = heapUsage.getUsed();
        metrics.heapMax = heapUsage.getMax();

        // System info
        metrics.availableProcessors = osBean.getAvailableProcessors();

        return metrics;
    }

    /**
     * Get performance report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        ProfileMetrics current = getCurrentMetrics();

        report.append("╔══════════════════════════════════════════╗\n");
        report.append("║     STREAMING PERFORMANCE REPORT        ║\n");
        report.append("╚══════════════════════════════════════════╝\n\n");

        // System Info
        report.append("📊 SYSTEM INFORMATION:\n");
        report.append("   CPU Cores: ").append(current.availableProcessors).append("\n");
        report.append("   Uptime: ").append(formatUptime(current.uptime)).append("\n\n");

        // Current Metrics
        report.append("📈 CURRENT METRICS:\n");
        report.append("   CPU Load: ").append(String.format("%.1f%%", current.cpuLoad));
        report.append(getHealthIndicator(current.cpuLoad, CPU_WARNING_THRESHOLD, CPU_CRITICAL_THRESHOLD)).append("\n");

        report.append("   Memory: ").append(String.format("%.1f%%", current.memoryUsage));
        report.append(getHealthIndicator(current.memoryUsage, MEMORY_WARNING_THRESHOLD, MEMORY_CRITICAL_THRESHOLD)).append("\n");

        report.append("   Threads: ").append(current.threadCount).append("\n");
        report.append("   Heap: ").append(formatBytes(current.heapUsed))
                .append(" / ").append(formatBytes(current.heapMax)).append("\n\n");

        // Historical Averages
        report.append("📊 AVERAGES (Last 60s):\n");
        for (Map.Entry<String, MetricHistory> entry : metrics.entrySet()) {
            String name = entry.getKey();
            MetricHistory history = entry.getValue();
            report.append("   ").append(capitalize(name)).append(": ")
                    .append(String.format("%.1f", history.getAverage()));

            if (name.equals("cpu") || name.equals("memory")) {
                report.append("%");
            }
            report.append("\n");
        }
        report.append("\n");

        // Recommendations
        report.append(generateRecommendations(current));

        return report.toString();
    }

    /**
     * Generate recommendations based on metrics
     */
    private String generateRecommendations(ProfileMetrics metrics) {
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("💡 RECOMMENDATIONS:\n");

        List<String> tips = new ArrayList<>();

        // CPU recommendations
        if (metrics.cpuLoad > CPU_CRITICAL_THRESHOLD) {
            tips.add("🔴 URGENT: Reduce FPS to 10-15");
            tips.add("🔴 URGENT: Lower quality to 40-50%");
            tips.add("🔴 URGENT: Disable compression");
        } else if (metrics.cpuLoad > CPU_WARNING_THRESHOLD) {
            tips.add("⚠️ Reduce FPS by 20-30%");
            tips.add("⚠️ Consider lowering quality");
        } else if (metrics.cpuLoad < 30) {
            tips.add("✅ CPU healthy - can increase quality/FPS");
        }

        // Memory recommendations
        if (metrics.memoryUsage > MEMORY_CRITICAL_THRESHOLD) {
            tips.add("🔴 URGENT: Restart application");
            tips.add("🔴 URGENT: Reduce buffer size");
        } else if (metrics.memoryUsage > MEMORY_WARNING_THRESHOLD) {
            tips.add("⚠️ Enable compression to reduce memory");
            tips.add("⚠️ Clear frame buffers periodically");
        }

        // Thread recommendations
        if (metrics.threadCount > 50) {
            tips.add("⚠️ High thread count - check for leaks");
        }

        // Quality recommendations
        double score = calculatePerformanceScore(metrics);
        if (score > 80) {
            tips.add("✅ Excellent performance - optimal settings");
        } else if (score > 60) {
            tips.add("🟡 Good performance - minor optimizations possible");
        } else if (score > 40) {
            tips.add("🟠 Fair performance - consider optimizing");
        } else {
            tips.add("🔴 Poor performance - immediate action needed");
        }

        if (tips.isEmpty()) {
            recommendations.append("   ✅ All metrics within normal range\n");
        } else {
            for (String tip : tips) {
                recommendations.append("   ").append(tip).append("\n");
            }
        }

        recommendations.append("\n");
        recommendations.append("📊 Performance Score: ").append(String.format("%.1f", score)).append("/100\n");

        return recommendations.toString();
    }

    /**
     * Calculate overall performance score
     */
    private double calculatePerformanceScore(ProfileMetrics metrics) {
        double cpuScore = Math.max(0, 100 - metrics.cpuLoad);
        double memoryScore = Math.max(0, 100 - metrics.memoryUsage);

        // Weighted average
        return (cpuScore * 0.6) + (memoryScore * 0.4);
    }

    /**
     * Get optimal settings based on current performance
     */
    public StreamingSettings getOptimalSettings() {
        ProfileMetrics metrics = getCurrentMetrics();
        StreamingSettings settings = new StreamingSettings();

        // Base on CPU load
        if (metrics.cpuLoad > 80) {
            // Critical - minimum settings
            settings.fps = 10;
            settings.quality = 40;
            settings.resolution = "640x480";
            settings.compression = false; // Disable to save CPU
        } else if (metrics.cpuLoad > 60) {
            // High - conservative settings
            settings.fps = 15;
            settings.quality = 60;
            settings.resolution = "800x600";
            settings.compression = true;
        } else if (metrics.cpuLoad > 40) {
            // Medium - balanced settings
            settings.fps = 20;
            settings.quality = 70;
            settings.resolution = "1280x720";
            settings.compression = true;
        } else {
            // Low - high quality settings
            settings.fps = 30;
            settings.quality = 85;
            settings.resolution = "1920x1080";
            settings.compression = true;
        }

        // Adjust for memory
        if (metrics.memoryUsage > 80) {
            settings.compression = true; // Force compression
            settings.fps = Math.min(settings.fps, 15); // Cap FPS
        }

        return settings;
    }

    /**
     * Test streaming performance
     */
    public void runPerformanceTest(int durationSeconds) {
        System.out.println("🧪 [PROFILER] Starting " + durationSeconds + "s performance test...\n");

        startProfiling();

        try {
            Thread.sleep(durationSeconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopProfiling();

        System.out.println("\n" + generateReport());
    }

    // ==================== UTILITY METHODS ====================

    private String getHealthIndicator(double value, double warning, double critical) {
        if (value > critical) {
            return " 🔴";
        } else if (value > warning) {
            return " 🟡";
        } else {
            return " 🟢";
        }
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // ==================== DATA CLASSES ====================

    public static class ProfileMetrics {
        public double cpuLoad;
        public double memoryUsage;
        public int threadCount;
        public long uptime;
        public long heapUsed;
        public long heapMax;
        public int availableProcessors;

        @Override
        public String toString() {
            return String.format(
                    "CPU: %.1f%%, Memory: %.1f%%, Threads: %d, Uptime: %dms",
                    cpuLoad, memoryUsage, threadCount, uptime
            );
        }
    }

    public static class StreamingSettings {
        public int fps;
        public int quality;
        public String resolution;
        public boolean compression;

        @Override
        public String toString() {
            return String.format(
                    "Optimal Settings: %d FPS, %d%% quality, %s, Compression: %s",
                    fps, quality, resolution, compression ? "ON" : "OFF"
            );
        }
    }

    private static class MetricHistory {
        private Queue<Double> values = new LinkedList<>();
        private static final int MAX_SIZE = 60; // Keep 60 seconds

        public void add(double value) {
            values.offer(value);
            if (values.size() > MAX_SIZE) {
                values.poll();
            }
        }

        public double getAverage() {
            if (values.isEmpty()) return 0;
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        public double getMax() {
            if (values.isEmpty()) return 0;
            return values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }

        public double getMin() {
            if (values.isEmpty()) return 0;
            return values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        }
    }

    // ==================== MAIN FOR TESTING ====================

    public static void main(String[] args) {
        StreamingProfiler profiler = new StreamingProfiler();

        // Run 10 second test
        profiler.runPerformanceTest(10);

        // Get optimal settings
        StreamingSettings optimal = profiler.getOptimalSettings();
        System.out.println("\n" + optimal);
    }
}