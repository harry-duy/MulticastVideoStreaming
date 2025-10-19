package com.hutech.videostreaming.analytics;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.animation.AnimationTimer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Real-time Analytics Dashboard for monitoring streaming performance
 */
public class AnalyticsDashboard {

    private Stage dashboardStage;
    private Map<String, MetricCard> metricCards;
    private Map<String, Chart> charts;
    private AnimationTimer updateTimer;

    // Data sources
    private StreamMetricsCollector metricsCollector;
    private Queue<NetworkSample> networkSamples;
    private Queue<PerformanceSample> performanceSamples;

    // Update frequency
    private static final long UPDATE_INTERVAL_MS = 1000; // 1 second
    private long lastUpdateTime = 0;

    /**
     * Network sample data point
     */
    public static class NetworkSample {
        public long timestamp;
        public double bandwidthMbps;
        public int packetsPerSecond;
        public double packetLossRate;
        public double latency;

        public NetworkSample(double bandwidth, int pps, double loss, double latency) {
            this.timestamp = System.currentTimeMillis();
            this.bandwidthMbps = bandwidth;
            this.packetsPerSecond = pps;
            this.packetLossRate = loss;
            this.latency = latency;
        }
    }

    /**
     * Performance sample data point
     */
    public static class PerformanceSample {
        public long timestamp;
        public double cpuUsage;
        public double memoryUsage;
        public double diskIORate;
        public int activeThreads;

        public PerformanceSample(double cpu, double memory, double disk, int threads) {
            this.timestamp = System.currentTimeMillis();
            this.cpuUsage = cpu;
            this.memoryUsage = memory;
            this.diskIORate = disk;
            this.activeThreads = threads;
        }
    }

    /**
     * Initialize dashboard
     */
    public void initialize() {
        metricCards = new HashMap<>();
        charts = new HashMap<>();
        networkSamples = new ConcurrentLinkedQueue<>();
        performanceSamples = new ConcurrentLinkedQueue<>();
        metricsCollector = new StreamMetricsCollector();

        createDashboardUI();
        startDataCollection();
        startUpdateTimer();
    }

    /**
     * Create dashboard UI
     */
    private void createDashboardUI() {
        dashboardStage = new Stage();
        dashboardStage.setTitle("📊 Streaming Analytics Dashboard");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Header
        VBox header = createHeader();
        root.setTop(header);

        // Main content with scroll
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");

        VBox content = createMainContent();
        scrollPane.setContent(content);
        root.setCenter(scrollPane);

        // Footer status bar
        HBox footer = createFooter();
        root.setBottom(footer);

        // Scene
        Scene scene = new Scene(root, 1200, 800);
        dashboardStage.setScene(scene);
    }

    /**
     * Create header
     */
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label title = new Label("📊 Real-time Streaming Analytics");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Monitor network, performance, and quality metrics");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    /**
     * Create main content area
     */
    private VBox createMainContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        // Metric cards grid
        GridPane metricsGrid = createMetricsGrid();

        // Charts grid
        GridPane chartsGrid = createChartsGrid();

        // Client table
        VBox clientSection = createClientSection();

        // Alerts section
        VBox alertsSection = createAlertsSection();

        content.getChildren().addAll(
                createSectionTitle("📈 Key Metrics"),
                metricsGrid,
                createSectionTitle("📊 Performance Charts"),
                chartsGrid,
                createSectionTitle("👥 Connected Clients"),
                clientSection,
                createSectionTitle("⚠️ Alerts & Warnings"),
                alertsSection
        );

        return content;
    }

    /**
     * Create metrics grid
     */
    private GridPane createMetricsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        // Create metric cards
        MetricCard bandwidthCard = new MetricCard("🌐 Bandwidth", "0 Mbps", Color.web("#3b82f6"));
        MetricCard packetsCard = new MetricCard("📦 Packets/s", "0", Color.web("#10b981"));
        MetricCard lossCard = new MetricCard("📉 Packet Loss", "0%", Color.web("#f59e0b"));
        MetricCard latencyCard = new MetricCard("⏱️ Latency", "0 ms", Color.web("#8b5cf6"));
        MetricCard cpuCard = new MetricCard("💻 CPU Usage", "0%", Color.web("#ef4444"));
        MetricCard memoryCard = new MetricCard("🧠 Memory", "0 MB", Color.web("#06b6d4"));
        MetricCard clientsCard = new MetricCard("👥 Clients", "0", Color.web("#ec4899"));
        MetricCard uptimeCard = new MetricCard("⏰ Uptime", "00:00:00", Color.web("#84cc16"));

        // Store cards for updates
        metricCards.put("bandwidth", bandwidthCard);
        metricCards.put("packets", packetsCard);
        metricCards.put("loss", lossCard);
        metricCards.put("latency", latencyCard);
        metricCards.put("cpu", cpuCard);
        metricCards.put("memory", memoryCard);
        metricCards.put("clients", clientsCard);
        metricCards.put("uptime", uptimeCard);

        // Add to grid
        grid.add(bandwidthCard, 0, 0);
        grid.add(packetsCard, 1, 0);
        grid.add(lossCard, 2, 0);
        grid.add(latencyCard, 3, 0);
        grid.add(cpuCard, 0, 1);
        grid.add(memoryCard, 1, 1);
        grid.add(clientsCard, 2, 1);
        grid.add(uptimeCard, 3, 1);

        return grid;
    }

    /**
     * Create charts grid
     */
    private GridPane createChartsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        // Bandwidth chart
        LineChart<Number, Number> bandwidthChart = createLineChart(
                "Bandwidth Usage", "Time (s)", "Mbps"
        );
        charts.put("bandwidth", bandwidthChart);

        // Packet loss chart
        LineChart<Number, Number> lossChart = createLineChart(
                "Packet Loss Rate", "Time (s)", "Loss %"
        );
        charts.put("packetLoss", lossChart);

        // CPU/Memory chart
        LineChart<Number, Number> performanceChart = createLineChart(
                "System Performance", "Time (s)", "Usage %"
        );
        XYChart.Series<Number, Number> cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("CPU");
        XYChart.Series<Number, Number> memorySeries = new XYChart.Series<>();
        memorySeries.setName("Memory");
        performanceChart.getData().addAll(cpuSeries, memorySeries);
        charts.put("performance", performanceChart);

        // Client distribution pie chart
        PieChart clientPieChart = new PieChart();
        clientPieChart.setTitle("Client Distribution");
        clientPieChart.setPrefSize(300, 300);
        charts.put("clientPie", clientPieChart);

        // Add to grid
        grid.add(bandwidthChart, 0, 0, 2, 1);
        grid.add(lossChart, 2, 0, 2, 1);
        grid.add(performanceChart, 0, 1, 2, 1);
        grid.add(clientPieChart, 2, 1, 2, 1);

        return grid;
    }

    /**
     * Create line chart
     */
    private LineChart<Number, Number> createLineChart(String title, String xLabel, String yLabel) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setAutoRanging(true);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setPrefSize(400, 300);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setStyle("-fx-background-color: #2a2a3a;");

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(title);
        chart.getData().add(series);

        return chart;
    }

    /**
     * Create client section
     */
    private VBox createClientSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #262637; -fx-background-radius: 10;");

        TableView<ClientInfo> clientTable = new TableView<>();
        clientTable.setPrefHeight(200);

        TableColumn<ClientInfo, String> ipCol = new TableColumn<>("IP Address");
        TableColumn<ClientInfo, String> statusCol = new TableColumn<>("Status");
        TableColumn<ClientInfo, String> bandwidthCol = new TableColumn<>("Bandwidth");
        TableColumn<ClientInfo, String> packetsCol = new TableColumn<>("Packets");
        TableColumn<ClientInfo, String> lossCol = new TableColumn<>("Loss %");

        clientTable.getColumns().addAll(ipCol, statusCol, bandwidthCol, packetsCol, lossCol);

        section.getChildren().add(clientTable);
        return section;
    }

    /**
     * Create alerts section
     */
    private VBox createAlertsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #262637; -fx-background-radius: 10;");
        section.setPrefHeight(150);

        ListView<String> alertsList = new ListView<>();
        alertsList.setStyle("-fx-background-color: #2a2a3a;");

        section.getChildren().add(alertsList);
        return section;
    }

    /**
     * Create footer status bar
     */
    private HBox createFooter() {
        HBox footer = new HBox(20);
        footer.setPadding(new Insets(10, 20, 10, 20));
        footer.setStyle("-fx-background-color: #16213e;");
        footer.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("● Connected");
        statusLabel.setTextFill(Color.web("#10b981"));

        Label timeLabel = new Label();
        timeLabel.setTextFill(Color.web("#e0e0e0"));

        // Update time every second
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                timeLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
        };
        timer.start();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("📊 Export Data");
        exportBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;");

        footer.getChildren().addAll(statusLabel, timeLabel, spacer, exportBtn);
        return footer;
    }

    /**
     * Create section title
     */
    private Label createSectionTitle(String text) {
        Label title = new Label(text);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));
        return title;
    }

    /**
     * Metric card component
     */
    private static class MetricCard extends VBox {
        private Label valueLabel;
        private Label trendLabel;

        public MetricCard(String title, String value, Color color) {
            super(8);
            setAlignment(Pos.CENTER);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #262637; -fx-background-radius: 10;");
            setPrefSize(150, 100);

            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
            titleLabel.setTextFill(Color.web("#9ca3af"));

            valueLabel = new Label(value);
            valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
            valueLabel.setTextFill(color);

            trendLabel = new Label("");
            trendLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
            trendLabel.setTextFill(Color.web("#9ca3af"));

            getChildren().addAll(titleLabel, valueLabel, trendLabel);
        }

        public void updateValue(String value, String trend) {
            Platform.runLater(() -> {
                valueLabel.setText(value);
                trendLabel.setText(trend);
            });
        }
    }

    /**
     * Start data collection
     */
    private void startDataCollection() {
        metricsCollector.start();
    }

    /**
     * Start update timer
     */
    private void startUpdateTimer() {
        updateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastUpdateTime > UPDATE_INTERVAL_MS * 1_000_000) {
                    updateDashboard();
                    lastUpdateTime = now;
                }
            }
        };
        updateTimer.start();
    }

    /**
     * Update dashboard with latest data
     */
    private void updateDashboard() {
        // Update metric cards
        StreamMetrics metrics = metricsCollector.getCurrentMetrics();

        metricCards.get("bandwidth").updateValue(
                String.format("%.2f Mbps", metrics.bandwidthMbps),
                metrics.bandwidthTrend > 0 ? "↑" : "↓"
        );

        metricCards.get("packets").updateValue(
                String.valueOf(metrics.packetsPerSecond),
                "avg: " + metrics.avgPacketsPerSecond
        );

        metricCards.get("loss").updateValue(
                String.format("%.2f%%", metrics.packetLossRate),
                metrics.packetLossRate > 5 ? "⚠️ High" : "✅ Normal"
        );

        metricCards.get("latency").updateValue(
                String.format("%.1f ms", metrics.latency),
                "jitter: " + String.format("%.1f", metrics.jitter)
        );

        // Update charts
        updateCharts(metrics);
    }

    /**
     * Update charts with new data
     */
    private void updateCharts(StreamMetrics metrics) {
        // Add data point to bandwidth chart
        LineChart<Number, Number> bandwidthChart = (LineChart<Number, Number>) charts.get("bandwidth");
        if (bandwidthChart != null && !bandwidthChart.getData().isEmpty()) {
            XYChart.Series<Number, Number> series = bandwidthChart.getData().get(0);
            series.getData().add(new XYChart.Data<>(
                    System.currentTimeMillis() / 1000,
                    metrics.bandwidthMbps
            ));

            // Keep only last 60 points
            if (series.getData().size() > 60) {
                series.getData().remove(0);
            }
        }
    }

    /**
     * Show dashboard
     */
    public void show() {
        dashboardStage.show();
    }

    /**
     * Hide dashboard
     */
    public void hide() {
        dashboardStage.hide();
    }

    /**
     * Shutdown dashboard
     */
    public void shutdown() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
        if (metricsCollector != null) {
            metricsCollector.stop();
        }
        dashboardStage.close();
    }

    /**
     * Stream metrics data
     */
    public static class StreamMetrics {
        public double bandwidthMbps;
        public int packetsPerSecond;
        public double packetLossRate;
        public double latency;
        public double jitter;
        public int bandwidthTrend;
        public int avgPacketsPerSecond;
        public double cpuUsage;
        public double memoryUsage;
        public int connectedClients;
        public long uptime;
    }

    /**
     * Client info for table
     */
    public static class ClientInfo {
        public String ipAddress;
        public String status;
        public double bandwidth;
        public int packets;
        public double lossRate;
    }

    /**
     * Metrics collector
     */
    private static class StreamMetricsCollector {
        private ScheduledExecutorService scheduler;
        private StreamMetrics currentMetrics;

        public void start() {
            currentMetrics = new StreamMetrics();
            scheduler = Executors.newScheduledThreadPool(1);

            // Simulate metrics collection
            scheduler.scheduleAtFixedRate(() -> {
                // Simulate random metrics
                currentMetrics.bandwidthMbps = 10 + Math.random() * 20;
                currentMetrics.packetsPerSecond = 100 + (int)(Math.random() * 50);
                currentMetrics.packetLossRate = Math.random() * 5;
                currentMetrics.latency = 10 + Math.random() * 50;
                currentMetrics.jitter = Math.random() * 10;
                currentMetrics.cpuUsage = 20 + Math.random() * 60;
                currentMetrics.memoryUsage = 30 + Math.random() * 50;
            }, 0, 1, TimeUnit.SECONDS);
        }

        public StreamMetrics getCurrentMetrics() {
            return currentMetrics;
        }

        public void stop() {
            if (scheduler != null) {
                scheduler.shutdown();
            }
        }
    }
}