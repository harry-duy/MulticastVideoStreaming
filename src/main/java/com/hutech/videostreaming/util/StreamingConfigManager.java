package com.hutech.videostreaming.util;

import java.io.*;
import java.util.*;

/**
 * Streaming Configuration Manager
 * Manages presets and custom configurations
 */
public class StreamingConfigManager {

    private Map<String, StreamingConfig> presets;
    private StreamingConfig currentConfig;
    private static final String CONFIG_FILE = "streaming_config.properties";

    public StreamingConfigManager() {
        this.presets = new LinkedHashMap<>();
        initializePresets();
        loadConfig();
    }

    /**
     * Initialize default presets
     */
    private void initializePresets() {
        // Ultra Low - For very slow connections
        presets.put("ultra_low", new StreamingConfig(
                "Ultra Low",
                "For 2G/slow connections",
                640, 480,
                5, 30,
                true,
                0.3
        ));

        // Low - For mobile/slow WiFi
        presets.put("low", new StreamingConfig(
                "Low Quality",
                "For 3G/slow WiFi (< 1 Mbps)",
                640, 480,
                10, 40,
                true,
                0.5
        ));

        // Medium - Balanced
        presets.put("medium", new StreamingConfig(
                "Medium Quality",
                "Balanced for most scenarios (1-3 Mbps)",
                800, 600,
                15, 60,
                true,
                1.5
        ));

        // High - Good quality
        presets.put("high", new StreamingConfig(
                "High Quality",
                "For good connections (3-5 Mbps)",
                1280, 720,
                20, 70,
                true,
                3.5
        ));

        // Very High - Professional
        presets.put("very_high", new StreamingConfig(
                "Very High Quality",
                "For LAN/fast connections (5-10 Mbps)",
                1280, 720,
                25, 85,
                true,
                7.0
        ));

        // Ultra - Best quality
        presets.put("ultra", new StreamingConfig(
                "Ultra Quality",
                "Best quality, high bandwidth (10-20 Mbps)",
                1920, 1080,
                30, 95,
                false,
                15.0
        ));

        // Gaming - High FPS
        presets.put("gaming", new StreamingConfig(
                "Gaming",
                "Optimized for games (60 FPS)",
                1280, 720,
                60, 80,
                true,
                12.0
        ));

        // Tutorial - Screen capture optimized
        presets.put("tutorial", new StreamingConfig(
                "Tutorial/Demo",
                "Optimized for screen sharing",
                1920, 1080,
                15, 75,
                true,
                4.0
        ));

        // Meeting - Webcam optimized
        presets.put("meeting", new StreamingConfig(
                "Video Meeting",
                "Optimized for webcam meetings",
                1280, 720,
                20, 70,
                true,
                3.0
        ));

        // Classroom - Many clients
        presets.put("classroom", new StreamingConfig(
                "Classroom",
                "Optimized for many viewers",
                800, 600,
                15, 65,
                true,
                2.0
        ));
    }

    /**
     * Get all available presets
     */
    public Map<String, StreamingConfig> getAllPresets() {
        return new LinkedHashMap<>(presets);
    }

    /**
     * Get preset by key
     */
    public StreamingConfig getPreset(String key) {
        return presets.get(key);
    }

    /**
     * Get preset names for UI
     */
    public List<String> getPresetNames() {
        List<String> names = new ArrayList<>();
        for (StreamingConfig config : presets.values()) {
            names.add(config.name);
        }
        return names;
    }

    /**
     * Get preset keys
     */
    public List<String> getPresetKeys() {
        return new ArrayList<>(presets.keySet());
    }

    /**
     * Load configuration from file
     */
    public void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            // Use medium preset as default
            currentConfig = presets.get("medium");
            System.out.println("📋 [CONFIG] Using default: Medium Quality");
            return;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);

            currentConfig = new StreamingConfig();
            currentConfig.name = props.getProperty("name", "Custom");
            currentConfig.description = props.getProperty("description", "");
            currentConfig.width = Integer.parseInt(props.getProperty("width", "800"));
            currentConfig.height = Integer.parseInt(props.getProperty("height", "600"));
            currentConfig.fps = Integer.parseInt(props.getProperty("fps", "15"));
            currentConfig.quality = Integer.parseInt(props.getProperty("quality", "60"));
            currentConfig.compression = Boolean.parseBoolean(props.getProperty("compression", "true"));
            currentConfig.estimatedBandwidth = Double.parseDouble(
                    props.getProperty("bandwidth", "2.0"));

            System.out.println("✅ [CONFIG] Loaded configuration: " + currentConfig.name);

        } catch (IOException | NumberFormatException e) {
            System.err.println("❌ [CONFIG] Failed to load config: " + e.getMessage());
            currentConfig = presets.get("medium");
        }
    }

    /**
     * Save current configuration
     */
    public void saveConfig(StreamingConfig config) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.setProperty("name", config.name);
            props.setProperty("description", config.description);
            props.setProperty("width", String.valueOf(config.width));
            props.setProperty("height", String.valueOf(config.height));
            props.setProperty("fps", String.valueOf(config.fps));
            props.setProperty("quality", String.valueOf(config.quality));
            props.setProperty("compression", String.valueOf(config.compression));
            props.setProperty("bandwidth", String.valueOf(config.estimatedBandwidth));

            props.store(fos, "Streaming Configuration");

            currentConfig = config;
            System.out.println("💾 [CONFIG] Saved configuration: " + config.name);

        } catch (IOException e) {
            System.err.println("❌ [CONFIG] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Get current configuration
     */
    public StreamingConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * Set configuration from preset
     */
    public void setPreset(String presetKey) {
        StreamingConfig preset = presets.get(presetKey);
        if (preset != null) {
            currentConfig = preset;
            System.out.println("📋 [CONFIG] Applied preset: " + preset.name);
        }
    }

    /**
     * Auto-select best preset based on bandwidth
     */
    public StreamingConfig autoSelectPreset(double availableBandwidthMbps) {
        System.out.println("🔍 [CONFIG] Auto-selecting preset for " +
                availableBandwidthMbps + " Mbps...");

        // Find best matching preset
        StreamingConfig best = presets.get("ultra_low");

        for (StreamingConfig config : presets.values()) {
            if (config.estimatedBandwidth <= availableBandwidthMbps) {
                if (config.estimatedBandwidth > best.estimatedBandwidth) {
                    best = config;
                }
            }
        }

        System.out.println("✅ [CONFIG] Auto-selected: " + best.name);
        return best;
    }

    /**
     * Get recommended preset based on network test
     */
    public StreamingConfig getRecommendedPreset(NetworkTestResult testResult) {
        double bandwidth = testResult.bandwidthMbps;
        double latency = testResult.latencyMs;
        double packetLoss = testResult.packetLossRate;

        System.out.println("🔍 [CONFIG] Analyzing network test results...");
        System.out.println("   Bandwidth: " + bandwidth + " Mbps");
        System.out.println("   Latency: " + latency + " ms");
        System.out.println("   Packet Loss: " + packetLoss + "%");

        // Adjust for latency and packet loss
        double adjustedBandwidth = bandwidth;

        // High latency penalty
        if (latency > 100) {
            adjustedBandwidth *= 0.8;
            System.out.println("   ⚠️ High latency detected, reducing bandwidth estimate");
        }

        // Packet loss penalty
        if (packetLoss > 2) {
            adjustedBandwidth *= 0.7;
            System.out.println("   ⚠️ Packet loss detected, reducing bandwidth estimate");
        }

        StreamingConfig recommended = autoSelectPreset(adjustedBandwidth);

        // Additional adjustments
        if (packetLoss > 5) {
            // High packet loss - use more compression
            recommended = new StreamingConfig(recommended);
            recommended.compression = true;
            recommended.fps = Math.max(10, recommended.fps - 5);
            System.out.println("   ⚠️ Enabling compression and reducing FPS due to packet loss");
        }

        return recommended;
    }

    /**
     * Compare two configurations
     */
    public String compareConfigs(StreamingConfig config1, StreamingConfig config2) {
        StringBuilder comparison = new StringBuilder();

        comparison.append("╔════════════════════════════════════════╗\n");
        comparison.append("║      CONFIGURATION COMPARISON         ║\n");
        comparison.append("╚════════════════════════════════════════╝\n\n");

        comparison.append(String.format("%-20s | %-15s | %-15s\n",
                "Property", config1.name, config2.name));
        comparison.append("─".repeat(55)).append("\n");

        comparison.append(String.format("%-20s | %-15s | %-15s\n",
                "Resolution", config1.getResolution(), config2.getResolution()));

        comparison.append(String.format("%-20s | %-15d | %-15d\n",
                "FPS", config1.fps, config2.fps));

        comparison.append(String.format("%-20s | %-15d | %-15d\n",
                "Quality", config1.quality, config2.quality));

        comparison.append(String.format("%-20s | %-15s | %-15s\n",
                "Compression", config1.compression ? "ON" : "OFF",
                config2.compression ? "ON" : "OFF"));

        comparison.append(String.format("%-20s | %-15.1f | %-15.1f\n",
                "Est. Bandwidth", config1.estimatedBandwidth, config2.estimatedBandwidth));

        return comparison.toString();
    }

    /**
     * Print all presets
     */
    public void printAllPresets() {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║          AVAILABLE STREAMING PRESETS              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        for (Map.Entry<String, StreamingConfig> entry : presets.entrySet()) {
            StreamingConfig config = entry.getValue();
            System.out.println("🎬 " + config.name);
            System.out.println("   " + config.description);
            System.out.println("   Resolution: " + config.getResolution());
            System.out.println("   FPS: " + config.fps + " | Quality: " + config.quality + "%");
            System.out.println("   Compression: " + (config.compression ? "ON" : "OFF"));
            System.out.println("   Est. Bandwidth: " + config.estimatedBandwidth + " Mbps");
            System.out.println();
        }
    }

    // ==================== DATA CLASSES ====================

    public static class StreamingConfig {
        public String name;
        public String description;
        public int width;
        public int height;
        public int fps;
        public int quality;
        public boolean compression;
        public double estimatedBandwidth;

        public StreamingConfig() {
            this.name = "Custom";
            this.description = "";
            this.width = 800;
            this.height = 600;
            this.fps = 15;
            this.quality = 60;
            this.compression = true;
            this.estimatedBandwidth = 2.0;
        }

        public StreamingConfig(String name, String description,
                               int width, int height, int fps, int quality,
                               boolean compression, double bandwidth) {
            this.name = name;
            this.description = description;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.quality = quality;
            this.compression = compression;
            this.estimatedBandwidth = bandwidth;
        }

        // Copy constructor
        public StreamingConfig(StreamingConfig other) {
            this.name = other.name;
            this.description = other.description;
            this.width = other.width;
            this.height = other.height;
            this.fps = other.fps;
            this.quality = other.quality;
            this.compression = other.compression;
            this.estimatedBandwidth = other.estimatedBandwidth;
        }

        public String getResolution() {
            return width + "x" + height;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s: %dx%d @ %d FPS, %d%% quality, %s compression (%.1f Mbps)",
                    name, width, height, fps, quality,
                    compression ? "with" : "without", estimatedBandwidth
            );
        }
    }

    public static class NetworkTestResult {
        public double bandwidthMbps;
        public double latencyMs;
        public double packetLossRate;
        public String networkType; // "LAN", "WiFi", "Mobile"

        public NetworkTestResult(double bandwidth, double latency, double loss, String type) {
            this.bandwidthMbps = bandwidth;
            this.latencyMs = latency;
            this.packetLossRate = loss;
            this.networkType = type;
        }

        @Override
        public String toString() {
            return String.format(
                    "Network: %s, Bandwidth: %.1f Mbps, Latency: %.1f ms, Loss: %.2f%%",
                    networkType, bandwidthMbps, latencyMs, packetLossRate
            );
        }
    }

    // ==================== MAIN FOR TESTING ====================

    public static void main(String[] args) {
        StreamingConfigManager manager = new StreamingConfigManager();

        // Print all presets
        manager.printAllPresets();

        // Test auto-selection
        System.out.println("Testing auto-selection:");
        System.out.println("─".repeat(50));

        double[] bandwidths = {0.5, 2.0, 5.0, 10.0, 20.0};
        for (double bw : bandwidths) {
            StreamingConfig config = manager.autoSelectPreset(bw);
            System.out.println(bw + " Mbps → " + config.name);
        }

        System.out.println("\nTesting network-based recommendation:");
        System.out.println("─".repeat(50));

        // Simulate network test results
        NetworkTestResult goodNetwork = new NetworkTestResult(5.0, 30, 0.5, "LAN");
        NetworkTestResult mediumNetwork = new NetworkTestResult(3.0, 80, 2.0, "WiFi");
        NetworkTestResult poorNetwork = new NetworkTestResult(1.5, 150, 8.0, "Mobile");

        System.out.println("\nGood Network:");
        System.out.println(goodNetwork);
        System.out.println("Recommended: " + manager.getRecommendedPreset(goodNetwork).name);

        System.out.println("\nMedium Network:");
        System.out.println(mediumNetwork);
        System.out.println("Recommended: " + manager.getRecommendedPreset(mediumNetwork).name);

        System.out.println("\nPoor Network:");
        System.out.println(poorNetwork);
        System.out.println("Recommended: " + manager.getRecommendedPreset(poorNetwork).name);

        // Test comparison
        System.out.println("\n" + manager.compareConfigs(
                manager.getPreset("low"),
                manager.getPreset("high")
        ));
    }
}