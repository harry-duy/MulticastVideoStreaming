package com.hutech.videostreaming.gui;

import com.hutech.videostreaming.client.VideoClient;
import com.hutech.videostreaming.common.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;

public class ClientGUI extends Application implements VideoClient.ClientCallback {

    private VideoClient client;
    private Stage primaryStage;

    // Status
    private Label statusLabel;
    private Label packetLabel;
    private Label droppedLabel;
    private Label successRateLabel;
    private Label bandwidthLabel;
    private ProgressBar progressBar;
    private Label progressPercentLabel;
    private Label connectionLabel;

    // Controls
    private Button connectBtn;
    private Button disconnectBtn;
    private Button saveBtn;
    private TextArea logArea;

    // Chart
    private LineChart<Number, Number> packetChart;
    private XYChart.Series<Number, Number> receivedSeries;
    private XYChart.Series<Number, Number> droppedSeries;
    private int chartTimeCounter = 0;

    // Bandwidth Monitor
    private BandwidthMonitor bandwidthMonitor;

    private int totalPackets = 0;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize
        client = new VideoClient(this);
        bandwidthMonitor = new BandwidthMonitor();

        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // Header
        VBox header = createHeader();
        root.setTop(header);

        // Center with scroll
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");

        VBox center = createMainControls();
        scrollPane.setContent(center);
        root.setCenter(scrollPane);

        // Bottom logs
        VBox bottom = createLogSection();
        root.setBottom(bottom);

        // Scene
        Scene scene = new Scene(root, 1000, 850);
        scene.setFill(Color.web("#1e1e2e"));

        primaryStage.setTitle("📺 Multicast Video Client - HUTECH");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (client != null) {
                client.disconnect();
            }
            Platform.exit();
        });
        primaryStage.show();

        log("✅ Client GUI initialized");
        NotificationManager.success("Client initialized successfully!", primaryStage);
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #f093fb, #f5576c);");

        Label title = new Label("📺 MULTICAST VIDEO CLIENT");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Receive and watch multicast video streams");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private VBox createMainControls() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.setAlignment(Pos.TOP_CENTER);

        // Cards
        VBox connectionCard = createCard("🔌 Connection", createConnectionPane());
        VBox statusCard = createCard("📊 Stream Status", createStatusPane());
        VBox statsCard = createCard("📈 Statistics", createStatisticsPane());
        VBox chartCard = createCard("📊 Real-time Chart", createChartPane());
        VBox controlCard = createCard("💾 Actions", createControlButtonsPane());

        container.getChildren().addAll(connectionCard, statusCard, statsCard, chartCard, controlCard);
        return container;
    }

    private Pane createConnectionPane() {
        VBox connectionPane = new VBox(15);
        connectionPane.setAlignment(Pos.CENTER);
        connectionPane.setPadding(new Insets(15));

        connectionLabel = new Label("🔴 Not Connected");
        connectionLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        connectionLabel.setTextFill(Color.web("#ef4444"));
        connectionLabel.setStyle("-fx-padding: 12; -fx-background-color: #2a2a3a; -fx-background-radius: 8;");
        connectionLabel.setMaxWidth(Double.MAX_VALUE);
        connectionLabel.setAlignment(Pos.CENTER);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        connectBtn = createStyledButton("🔗 Connect", "#10b981");
        connectBtn.setPrefWidth(180);
        connectBtn.setOnAction(e -> connectToMulticast());

        disconnectBtn = createStyledButton("🔌 Disconnect", "#ef4444");
        disconnectBtn.setPrefWidth(180);
        disconnectBtn.setDisable(true);
        disconnectBtn.setOnAction(e -> disconnectFromMulticast());

        buttonBox.getChildren().addAll(connectBtn, disconnectBtn);

        connectionPane.getChildren().addAll(connectionLabel, buttonBox);
        return connectionPane;
    }

    private Pane createStatusPane() {
        HBox statusPane = new HBox(30);
        statusPane.setAlignment(Pos.CENTER);
        statusPane.setPadding(new Insets(15));

        VBox statusBox = new VBox(8);
        statusBox.setAlignment(Pos.CENTER);

        Label statusTitle = new Label("Status");
        statusTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        statusTitle.setTextFill(Color.web("#9ca3af"));

        statusLabel = new Label("⚪ Idle");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        statusLabel.setTextFill(Color.web("#9ca3af"));

        statusBox.getChildren().addAll(statusTitle, statusLabel);
        statusPane.getChildren().add(statusBox);
        return statusPane;
    }

    private Pane createStatisticsPane() {
        VBox statsContainer = new VBox(20);
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setPadding(new Insets(15));

        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(15);
        statsPane.setAlignment(Pos.CENTER);

        // Packets Received
        VBox receivedBox = createStatBox("📥 Received", "0");
        packetLabel = (Label) receivedBox.getChildren().get(1);

        // Packets Dropped
        VBox droppedBox = createStatBox("📉 Dropped", "0");
        droppedLabel = (Label) droppedBox.getChildren().get(1);

        // Success Rate
        VBox rateBox = createStatBox("✅ Success", "100%");
        successRateLabel = (Label) rateBox.getChildren().get(1);

        // Bandwidth
        VBox bwBox = createStatBox("🌐 Bandwidth", "0 Mbps");
        bandwidthLabel = (Label) bwBox.getChildren().get(1);

        statsPane.add(receivedBox, 0, 0);
        statsPane.add(droppedBox, 1, 0);
        statsPane.add(rateBox, 2, 0);
        statsPane.add(bwBox, 3, 0);

        // Progress section
        VBox progressBox = new VBox(10);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(15, 0, 0, 0));

        Label progressTitle = new Label("Download Progress");
        progressTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        progressTitle.setTextFill(Color.web("#9ca3af"));

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setPrefHeight(20);
        progressBar.setStyle(
                "-fx-accent: linear-gradient(to right, #f093fb, #f5576c);" +
                        "-fx-background-color: #2a2a3a;" +
                        "-fx-background-radius: 10;"
        );

        progressPercentLabel = new Label("0%");
        progressPercentLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        progressPercentLabel.setTextFill(Color.web("#60a5fa"));

        progressBox.getChildren().addAll(progressTitle, progressBar, progressPercentLabel);

        statsContainer.getChildren().addAll(statsPane, progressBox);
        return statsContainer;
    }

    private VBox createStatBox(String title, String value) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 15; -fx-background-color: #2a2a3a; -fx-background-radius: 10;");
        box.setPrefWidth(130);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
        titleLabel.setTextFill(Color.web("#9ca3af"));
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(TextAlignment.CENTER);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        valueLabel.setTextFill(Color.web("#60a5fa"));

        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private Pane createChartPane() {
        VBox chartPane = new VBox(10);
        chartPane.setPadding(new Insets(15));

        // Axes
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        xAxis.setAutoRanging(true);
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Packets");
        yAxis.setAutoRanging(true);

        // Chart
        packetChart = new LineChart<>(xAxis, yAxis);
        packetChart.setTitle("Real-time Packet Reception");
        packetChart.setPrefHeight(280);
        packetChart.setCreateSymbols(false);
        packetChart.setLegendVisible(true);
        packetChart.setAnimated(false);
        packetChart.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-border-color: #3a3a4a;" +
                        "-fx-border-radius: 8;"
        );

        // Series
        receivedSeries = new XYChart.Series<>();
        receivedSeries.setName("Received");

        droppedSeries = new XYChart.Series<>();
        droppedSeries.setName("Dropped");

        packetChart.getData().addAll(receivedSeries, droppedSeries);

        chartPane.getChildren().add(packetChart);
        return chartPane;
    }

    private Pane createControlButtonsPane() {
        VBox controlPane = new VBox(15);
        controlPane.setAlignment(Pos.CENTER);
        controlPane.setPadding(new Insets(15));

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        saveBtn = createStyledButton("💾 Save Received Video", "#8b5cf6");
        saveBtn.setPrefWidth(250);
        saveBtn.setDisable(true);
        saveBtn.setOnAction(e -> saveVideo());

        Button clearBtn = createStyledButton("🗑️ Clear Logs", "#6b7280");
        clearBtn.setPrefWidth(200);
        clearBtn.setOnAction(e -> logArea.clear());

        buttonBox.getChildren().addAll(saveBtn, clearBtn);

        Label instructionLabel = new Label("💡 Save button enables after stream completes");
        instructionLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        instructionLabel.setTextFill(Color.web("#9ca3af"));
        instructionLabel.setWrapText(true);
        instructionLabel.setTextAlignment(TextAlignment.CENTER);

        controlPane.getChildren().addAll(buttonBox, instructionLabel);
        return controlPane;
    }

    private VBox createLogSection() {
        VBox logSection = new VBox(10);
        logSection.setPadding(new Insets(20, 30, 20, 30));
        logSection.setStyle("-fx-background-color: #181824;");

        HBox logHeader = new HBox(15);
        logHeader.setAlignment(Pos.CENTER_LEFT);

        Label logTitle = new Label("📋 Activity Log");
        logTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        logTitle.setTextFill(Color.web("#e0e0e0"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearLogBtn = new Button("🗑️ Clear");
        clearLogBtn.setStyle(
                "-fx-background-color: #ef4444;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 5 15;"
        );
        clearLogBtn.setOnAction(e -> logArea.clear());

        logHeader.getChildren().addAll(logTitle, spacer, clearLogBtn);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle(
                "-fx-control-inner-background: #2a2a3a;" +
                        "-fx-text-fill: #e0e0e0;" +
                        "-fx-font-family: 'Consolas', monospace;" +
                        "-fx-font-size: 11px;"
        );

        logSection.getChildren().addAll(logHeader, logArea);
        return logSection;
    }

    private VBox createCard(String title, Pane content) {
        VBox card = new VBox(15);
        card.setStyle(
                "-fx-background-color: #262637;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);"
        );
        card.setPadding(new Insets(20));
        card.setMaxWidth(800);

        Label cardTitle = new Label(title);
        cardTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        cardTitle.setTextFill(Color.web("#e0e0e0"));

        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #3a3a4a;");

        card.getChildren().addAll(cardTitle, separator, content);
        return card;
    }

    private Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setTextFill(Color.WHITE);
        button.setPrefHeight(45);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: derive(" + color + ", -10%);" +
                                "-fx-background-radius: 8;" +
                                "-fx-cursor: hand;"
                );
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: " + color + ";" +
                                "-fx-background-radius: 8;" +
                                "-fx-cursor: hand;"
                );
            }
        });

        return button;
    }

    // === Actions ===

    private void connectToMulticast() {
        client.connect();
        connectBtn.setDisable(true);
        disconnectBtn.setDisable(false);
        NotificationManager.info("Connecting to multicast...", primaryStage);
    }

    private void disconnectFromMulticast() {
        client.disconnect();
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        saveBtn.setDisable(true);
        NotificationManager.warning("Disconnected", primaryStage);
    }

    private void saveVideo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Received Video");
        fileChooser.setInitialFileName("received_video.mp4");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MP4 Files", "*.mp4")
        );

        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            client.saveReceivedVideo(file.getAbsolutePath());
            log("💾 Saving video to: " + file.getAbsolutePath());
            showAlert("Success",
                    "Video saved successfully!\n\n" +
                            "Location: " + file.getAbsolutePath() + "\n" +
                            "Packets Received: " + client.getPacketsReceived() + "\n" +
                            "Packets Dropped: " + client.getDroppedPackets(),
                    Alert.AlertType.INFORMATION);
            NotificationManager.success("Video saved successfully!", primaryStage);
        }
    }

    // === Callbacks ===

    @Override
    public void onConnected() {
        Platform.runLater(() -> {
            connectionLabel.setText("🟢 Connected - " + client.getClientIp());
            connectionLabel.setTextFill(Color.web("#10b981"));
            statusLabel.setText("🟢 Connected");
            statusLabel.setTextFill(Color.web("#10b981"));
            log("✅ Connected to multicast group");
            log("💻 Local IP: " + client.getClientIp());
            NotificationManager.success("Connected successfully!", primaryStage);
        });
    }

    @Override
    public void onStreamStarted() {
        Platform.runLater(() -> {
            statusLabel.setText("▶️ Receiving");
            statusLabel.setTextFill(Color.web("#3b82f6"));
            packetLabel.setText("0");
            droppedLabel.setText("0");
            successRateLabel.setText("100%");
            progressBar.setProgress(0);
            progressPercentLabel.setText("0%");
            totalPackets = 0;
            chartTimeCounter = 0;
            receivedSeries.getData().clear();
            droppedSeries.getData().clear();
            bandwidthMonitor.reset();
            saveBtn.setDisable(true);
            log("▶️ Stream started - receiving packets...");
            NotificationManager.info("Stream started!", primaryStage);
        });
    }

    @Override
    public void onPacketReceived(VideoPacket packet, int received, int dropped) {
        Platform.runLater(() -> {
            packetLabel.setText(String.valueOf(received));
            droppedLabel.setText(String.valueOf(dropped));

            // Calculate success rate
            int total = received + dropped;
            if (total > 0) {
                double rate = (double) received / total * 100;
                successRateLabel.setText(String.format("%.1f%%", rate));
            }

            // Update bandwidth
            bandwidthMonitor.addData(packet.getDataLength());
            bandwidthLabel.setText(bandwidthMonitor.getFormattedBandwidth());

            // Estimate progress
            if (totalPackets == 0) {
                totalPackets = received + 200;
            }

            double progress = Math.min(1.0, (double) received / totalPackets);
            progressBar.setProgress(progress);
            progressPercentLabel.setText(String.format("%.1f%%", progress * 100));

            // Update chart every 5 packets
            if (received % 5 == 0) {
                receivedSeries.getData().add(new XYChart.Data<>(chartTimeCounter, received));
                droppedSeries.getData().add(new XYChart.Data<>(chartTimeCounter, dropped));
                chartTimeCounter++;

                // Keep chart manageable
                if (receivedSeries.getData().size() > 100) {
                    receivedSeries.getData().remove(0);
                    droppedSeries.getData().remove(0);
                }
            }
        });
    }

    @Override
    public void onStreamPaused() {
        Platform.runLater(() -> {
            statusLabel.setText("⏸️ Paused");
            statusLabel.setTextFill(Color.web("#f59e0b"));
            log("⏸️ Stream paused");
            NotificationManager.warning("Stream paused", primaryStage);
        });
    }

    @Override
    public void onStreamResumed() {
        Platform.runLater(() -> {
            statusLabel.setText("▶️ Receiving");
            statusLabel.setTextFill(Color.web("#3b82f6"));
            log("▶️ Stream resumed");
            NotificationManager.info("Stream resumed", primaryStage);
        });
    }

    @Override
    public void onStreamStopped() {
        Platform.runLater(() -> {
            statusLabel.setText("⏹️ Stopped");
            statusLabel.setTextFill(Color.web("#ef4444"));
            progressBar.setProgress(1.0);
            progressPercentLabel.setText("100%");
            saveBtn.setDisable(false);
            log("⏹️ Stream stopped");
            log("📊 Total received: " + client.getPacketsReceived() + " packets");
            log("📊 Total dropped: " + client.getDroppedPackets() + " packets");

            // Calculate final success rate
            int total = client.getPacketsReceived() + client.getDroppedPackets();
            if (total > 0) {
                double rate = (double) client.getPacketsReceived() / total * 100;
                log(String.format("✅ Success rate: %.2f%%", rate));
            }

            NotificationManager.success("Stream completed! You can now save the video.", primaryStage);
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            connectionLabel.setText("🔴 Disconnected");
            connectionLabel.setTextFill(Color.web("#ef4444"));
            statusLabel.setText("⚪ Idle");
            statusLabel.setTextFill(Color.web("#9ca3af"));
            log("🔴 Disconnected from multicast group");
        });
    }

    @Override
    public void onError(String error) {
        Platform.runLater(() -> {
            statusLabel.setText("❌ Error");
            statusLabel.setTextFill(Color.web("#ef4444"));
            log("❌ ERROR: " + error);
            showAlert("Error", error, Alert.AlertType.ERROR);
            NotificationManager.error(error, primaryStage);
        });
    }

    // === Utilities ===

    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #262637;");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: #e0e0e0;");

        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}