package com.hutech.videostreaming.gui;

import com.hutech.videostreaming.server.VideoServer;
import com.hutech.videostreaming.common.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServerGUI extends Application implements VideoServer.ServerCallback {

    private VideoServer server;
    private Stage primaryStage;

    // Status
    private Label statusLabel;
    private Label packetLabel;
    private Label bandwidthLabel;
    private Label totalDataLabel;
    private ProgressBar progressBar;
    private Label progressLabel;

    // Controls
    private Button selectFileBtn;
    private Button startBtn;
    private Button pauseBtn;
    private Button stopBtn;
    private TextArea logArea;
    private Label fileNameLabel;
    private File selectedVideoFile;

    // Queue
    private ListView<String> queueListView;
    private Queue<File> videoQueue = new LinkedList<>();
    private Label queueCountLabel;

    // Quality
    private ComboBox<String> qualitySelector;
    private Label qualityInfoLabel;

    // Client List
    private TableView<ClientInfo> clientTable;
    private ObservableList<ClientInfo> clientList;
    private Label clientCountLabel;

    // Bandwidth Monitor
    private BandwidthMonitor bandwidthMonitor;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize
        server = new VideoServer(this);
        server.initialize();
        bandwidthMonitor = new BandwidthMonitor();
        clientList = FXCollections.observableArrayList();

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

        primaryStage.setTitle("🎬 Multicast Video Server - HUTECH");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            server.close();
            Platform.exit();
        });
        primaryStage.show();

        log("✅ Server GUI initialized");
        NotificationManager.success("Server initialized successfully!", primaryStage);
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label title = new Label("🎬 MULTICAST VIDEO SERVER");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Stream video to multiple clients simultaneously");
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
        VBox statusCard = createCard("📊 Server Status", createStatusPane());
        VBox statsCard = createCard("📈 Statistics", createStatisticsPane());
        VBox qualityCard = createCard("⚙️ Stream Quality", createQualityPane());
        VBox fileCard = createCard("📁 Video File", createFileSelectionPane());
        VBox queueCard = createCard("📋 Video Queue", createQueuePane());
        VBox controlCard = createCard("🎮 Stream Controls", createControlButtonsPane());
        VBox progressCard = createCard("📊 Streaming Progress", createProgressPane());
        VBox clientCard = createCard("👥 Connected Clients", createClientListPane());

        container.getChildren().addAll(
                statusCard, statsCard, qualityCard, fileCard,
                queueCard, controlCard, progressCard, clientCard
        );
        return container;
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

        statusLabel = new Label("🟢 Ready");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        statusLabel.setTextFill(Color.web("#10b981"));

        statusBox.getChildren().addAll(statusTitle, statusLabel);

        VBox packetBox = new VBox(8);
        packetBox.setAlignment(Pos.CENTER);

        Label packetTitle = new Label("Packets Sent");
        packetTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        packetTitle.setTextFill(Color.web("#9ca3af"));

        packetLabel = new Label("0 / 0");
        packetLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        packetLabel.setTextFill(Color.web("#60a5fa"));

        packetBox.getChildren().addAll(packetTitle, packetLabel);

        statusPane.getChildren().addAll(statusBox, createSeparator(), packetBox);
        return statusPane;
    }

    private Pane createStatisticsPane() {
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(15);
        statsPane.setAlignment(Pos.CENTER);
        statsPane.setPadding(new Insets(15));

        // Bandwidth
        VBox bwBox = createStatBox("🌐 Bandwidth", "0 Mbps");
        bandwidthLabel = (Label) bwBox.getChildren().get(1);

        // Total Data
        VBox dataBox = createStatBox("💾 Total Data", "0 MB");
        totalDataLabel = (Label) dataBox.getChildren().get(1);

        // Clients
        VBox clientBox = createStatBox("👥 Clients", "0");
        clientCountLabel = (Label) clientBox.getChildren().get(1);

        statsPane.add(bwBox, 0, 0);
        statsPane.add(dataBox, 1, 0);
        statsPane.add(clientBox, 2, 0);

        return statsPane;
    }

    private VBox createStatBox(String title, String value) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-padding: 15; -fx-background-color: #2a2a3a; -fx-background-radius: 10;");
        box.setPrefWidth(150);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        titleLabel.setTextFill(Color.web("#9ca3af"));
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(TextAlignment.CENTER);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        valueLabel.setTextFill(Color.web("#60a5fa"));

        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private Pane createQualityPane() {
        VBox qualityPane = new VBox(15);
        qualityPane.setAlignment(Pos.CENTER);
        qualityPane.setPadding(new Insets(15));

        HBox selectorBox = new HBox(15);
        selectorBox.setAlignment(Pos.CENTER);

        Label label = new Label("Select Quality:");
        label.setTextFill(Color.web("#e0e0e0"));
        label.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        qualitySelector = new ComboBox<>();
        qualitySelector.getItems().addAll(
                "🔴 High Quality (100KB, 30 FPS)",
                "🟡 Medium Quality (60KB, 25 FPS)",
                "🟢 Low Quality (30KB, 15 FPS)"
        );
        qualitySelector.setValue("🟡 Medium Quality (60KB, 25 FPS)");
        qualitySelector.setPrefWidth(280);
        qualitySelector.setStyle(
                "-fx-background-color: #3b82f6;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;"
        );

        qualitySelector.setOnAction(e -> updateQualityInfo());

        selectorBox.getChildren().addAll(label, qualitySelector);

        qualityInfoLabel = new Label("📊 Packet: 60KB | FPS: 25 | Est. Bandwidth: ~12 Mbps");
        qualityInfoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        qualityInfoLabel.setTextFill(Color.web("#9ca3af"));
        qualityInfoLabel.setWrapText(true);
        qualityInfoLabel.setTextAlignment(TextAlignment.CENTER);

        qualityPane.getChildren().addAll(selectorBox, qualityInfoLabel);
        return qualityPane;
    }

    private void updateQualityInfo() {
        String quality = qualitySelector.getValue();
        if (quality.contains("High")) {
            qualityInfoLabel.setText("📊 Packet: 100KB | FPS: 30 | Est. Bandwidth: ~24 Mbps | Best Quality");
        } else if (quality.contains("Medium")) {
            qualityInfoLabel.setText("📊 Packet: 60KB | FPS: 25 | Est. Bandwidth: ~12 Mbps | Balanced");
        } else {
            qualityInfoLabel.setText("📊 Packet: 30KB | FPS: 15 | Est. Bandwidth: ~3.6 Mbps | Data Saver");
        }
        log("⚙️ Quality changed to: " + quality);
    }

    private Pane createFileSelectionPane() {
        VBox filePane = new VBox(12);
        filePane.setAlignment(Pos.CENTER);
        filePane.setPadding(new Insets(15));

        fileNameLabel = new Label("No file selected");
        fileNameLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        fileNameLabel.setTextFill(Color.web("#9ca3af"));
        fileNameLabel.setStyle("-fx-padding: 10; -fx-background-color: #2a2a3a; -fx-background-radius: 8;");
        fileNameLabel.setMaxWidth(Double.MAX_VALUE);
        fileNameLabel.setAlignment(Pos.CENTER);

        selectFileBtn = createStyledButton("📂 Select Video File", "#3b82f6");
        selectFileBtn.setMaxWidth(250);
        selectFileBtn.setOnAction(e -> selectVideoFile());

        filePane.getChildren().addAll(fileNameLabel, selectFileBtn);
        return filePane;
    }

    private Pane createQueuePane() {
        VBox queuePane = new VBox(12);
        queuePane.setPadding(new Insets(15));
        queuePane.setAlignment(Pos.CENTER);

        queueCountLabel = new Label("Queue: 0 videos");
        queueCountLabel.setTextFill(Color.web("#60a5fa"));
        queueCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        queueListView = new ListView<>();
        queueListView.setPrefHeight(120);
        queueListView.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-control-inner-background: #2a2a3a;"
        );
        queueListView.setPlaceholder(new Label("No videos in queue"));

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button addBtn = createStyledButton("➕ Add to Queue", "#10b981");
        addBtn.setPrefWidth(150);
        addBtn.setOnAction(e -> addToQueue());

        Button clearBtn = createStyledButton("🗑️ Clear Queue", "#ef4444");
        clearBtn.setPrefWidth(150);
        clearBtn.setOnAction(e -> {
            videoQueue.clear();
            updateQueueDisplay();
            log("🗑️ Queue cleared");
        });

        buttonBox.getChildren().addAll(addBtn, clearBtn);
        queuePane.getChildren().addAll(queueCountLabel, queueListView, buttonBox);

        return queuePane;
    }

    private Pane createControlButtonsPane() {
        HBox buttonPane = new HBox(15);
        buttonPane.setAlignment(Pos.CENTER);
        buttonPane.setPadding(new Insets(15));

        startBtn = createStyledButton("▶️ Start Stream", "#10b981");
        startBtn.setPrefWidth(150);
        startBtn.setOnAction(e -> startStreaming());

        pauseBtn = createStyledButton("⏸️ Pause", "#f59e0b");
        pauseBtn.setPrefWidth(150);
        pauseBtn.setDisable(true);
        pauseBtn.setOnAction(e -> togglePause());

        stopBtn = createStyledButton("⏹️ Stop", "#ef4444");
        stopBtn.setPrefWidth(150);
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> stopStreaming());

        buttonPane.getChildren().addAll(startBtn, pauseBtn, stopBtn);
        return buttonPane;
    }

    private Pane createProgressPane() {
        VBox progressPane = new VBox(12);
        progressPane.setAlignment(Pos.CENTER);
        progressPane.setPadding(new Insets(15));

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setPrefHeight(25);
        progressBar.setStyle(
                "-fx-accent: linear-gradient(to right, #667eea, #764ba2);" +
                        "-fx-background-color: #2a2a3a;" +
                        "-fx-background-radius: 12;"
        );

        progressLabel = new Label("Ready to stream");
        progressLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        progressLabel.setTextFill(Color.web("#9ca3af"));

        progressPane.getChildren().addAll(progressBar, progressLabel);
        return progressPane;
    }

    private Pane createClientListPane() {
        VBox clientPane = new VBox(12);
        clientPane.setPadding(new Insets(15));

        clientTable = new TableView<>();
        clientTable.setItems(clientList);
        clientTable.setPrefHeight(150);
        clientTable.setPlaceholder(new Label("No clients connected"));

        // IP Column
        TableColumn<ClientInfo, String> ipCol = new TableColumn<>("IP Address");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        ipCol.setPrefWidth(150);

        // Duration Column
        TableColumn<ClientInfo, String> durationCol = new TableColumn<>("Duration");
        durationCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getConnectionDuration()));
        durationCol.setPrefWidth(100);

        // Packets Column
        TableColumn<ClientInfo, String> packetsCol = new TableColumn<>("Packets");
        packetsCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getPacketsReceived())));
        packetsCol.setPrefWidth(80);

        // Success Rate Column
        TableColumn<ClientInfo, String> rateCol = new TableColumn<>("Success Rate");
        rateCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFormattedSuccessRate()));
        rateCol.setPrefWidth(100);

        // Status Column
        TableColumn<ClientInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "🟢 Active" : "🔴 Left"));
        statusCol.setPrefWidth(80);

        clientTable.getColumns().addAll(ipCol, durationCol, packetsCol, rateCol, statusCol);

        clientPane.getChildren().add(clientTable);
        return clientPane;
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

    private Region createSeparator() {
        Region separator = new Region();
        separator.setPrefWidth(2);
        separator.setStyle("-fx-background-color: #3a3a4a;");
        return separator;
    }

    // === Actions ===

    private void selectVideoFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Video File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mkv", "*.mov")
        );

        selectedVideoFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedVideoFile != null) {
            fileNameLabel.setText("📹 " + selectedVideoFile.getName());
            fileNameLabel.setTextFill(Color.web("#10b981"));
            log("✅ File selected: " + selectedVideoFile.getName());
            NotificationManager.success("Video file selected!", primaryStage);
        }
    }

    private void addToQueue() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add Videos to Queue");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mkv", "*.mov")
        );

        List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);
        if (files != null && !files.isEmpty()) {
            videoQueue.addAll(files);
            updateQueueDisplay();
            log("✅ Added " + files.size() + " video(s) to queue");
            NotificationManager.success(files.size() + " videos added to queue", primaryStage);
        }
    }

    private void updateQueueDisplay() {
        ObservableList<String> items = FXCollections.observableArrayList();
        int index = 1;
        for (File file : videoQueue) {
            items.add(index++ + ". 📹 " + file.getName());
        }
        queueListView.setItems(items);
        queueCountLabel.setText("Queue: " + videoQueue.size() + " video(s)");
    }

    private void startStreaming() {
        if (selectedVideoFile == null && videoQueue.isEmpty()) {
            showAlert("No File Selected", "Please select a video file or add videos to queue!", Alert.AlertType.WARNING);
            NotificationManager.warning("No video selected!", primaryStage);
            return;
        }

        File fileToStream = selectedVideoFile;
        if (fileToStream == null && !videoQueue.isEmpty()) {
            fileToStream = videoQueue.poll();
            updateQueueDisplay();
        }

        if (fileToStream != null) {
            server.startStreaming(fileToStream.getAbsolutePath());
            bandwidthMonitor.reset();

            startBtn.setDisable(true);
            pauseBtn.setDisable(false);
            stopBtn.setDisable(false);
            selectFileBtn.setDisable(true);

            NotificationManager.info("Stream started!", primaryStage);
        }
    }

    private void togglePause() {
        if (server.isPaused()) {
            server.resumeStreaming();
            pauseBtn.setText("⏸️ Pause");
            NotificationManager.info("Stream resumed", primaryStage);
        } else {
            server.pauseStreaming();
            pauseBtn.setText("▶️ Resume");
            NotificationManager.warning("Stream paused", primaryStage);
        }
    }

    private void stopStreaming() {
        server.stopStreaming();

        startBtn.setDisable(false);
        pauseBtn.setDisable(true);
        stopBtn.setDisable(true);
        selectFileBtn.setDisable(false);
        pauseBtn.setText("⏸️ Pause");

        NotificationManager.info("Stream stopped", primaryStage);
    }

    // === Callbacks ===

    @Override
    public void onServerStarted() {
        Platform.runLater(() -> {
            statusLabel.setText("🟢 Ready");
            statusLabel.setTextFill(Color.web("#10b981"));
        });
    }

    @Override
    public void onStreamStarted(int totalPackets) {
        Platform.runLater(() -> {
            statusLabel.setText("🔴 Streaming");
            statusLabel.setTextFill(Color.web("#ef4444"));
            packetLabel.setText("0 / " + totalPackets);
            progressBar.setProgress(0);
            progressLabel.setText("Streaming in progress...");
            log("▶️ Stream started - Total packets: " + totalPackets);
        });
    }

    @Override
    public void onPacketSent(int sequenceNumber, int total) {
        Platform.runLater(() -> {
            packetLabel.setText(sequenceNumber + " / " + total);
            double progress = (double) sequenceNumber / total;
            progressBar.setProgress(progress);
            progressLabel.setText(String.format("%.1f%% completed", progress * 100));

            // Update bandwidth (assume 60KB per packet)
            bandwidthMonitor.addData(60000);
            bandwidthLabel.setText(bandwidthMonitor.getFormattedBandwidth());
            totalDataLabel.setText(bandwidthMonitor.getTotalTransferred());
        });
    }

    @Override
    public void onStreamPaused() {
        Platform.runLater(() -> {
            statusLabel.setText("⏸️ Paused");
            statusLabel.setTextFill(Color.web("#f59e0b"));
            progressLabel.setText("Stream paused");
            log("⏸️ Stream paused");
        });
    }

    @Override
    public void onStreamResumed() {
        Platform.runLater(() -> {
            statusLabel.setText("🔴 Streaming");
            statusLabel.setTextFill(Color.web("#ef4444"));
            progressLabel.setText("Streaming resumed...");
            log("▶️ Stream resumed");
        });
    }

    @Override
    public void onStreamStopped() {
        Platform.runLater(() -> {
            statusLabel.setText("🟢 Ready");
            statusLabel.setTextFill(Color.web("#10b981"));
            progressBar.setProgress(1.0);
            progressLabel.setText("Stream completed successfully");
            log("⏹️ Stream stopped");

            startBtn.setDisable(false);
            pauseBtn.setDisable(true);
            stopBtn.setDisable(true);
            selectFileBtn.setDisable(false);
            pauseBtn.setText("⏸️ Pause");

            // Auto-start next in queue
            if (!videoQueue.isEmpty()) {
                NotificationManager.info("Next video starting in 3 seconds...", primaryStage);
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Platform.runLater(this::startStreaming);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        });
    }

    @Override
    public void onError(String error) {
        Platform.runLater(() -> {
            statusLabel.setText("❌ Error");
            statusLabel.setTextFill(Color.web("#ef4444"));
            progressLabel.setText("Error occurred");
            log("❌ ERROR: " + error);
            showAlert("Error", error, Alert.AlertType.ERROR);
            NotificationManager.error(error, primaryStage);

            startBtn.setDisable(false);
            pauseBtn.setDisable(true);
            stopBtn.setDisable(true);
            selectFileBtn.setDisable(false);
        });
    }

    public void onClientConnected(String clientIp) {
        Platform.runLater(() -> {
            ClientInfo client = new ClientInfo(clientIp);
            clientList.add(client);
            clientCountLabel.setText(String.valueOf(clientList.size()));
            log("👤 Client connected: " + clientIp);
            NotificationManager.info("Client connected: " + clientIp, primaryStage);
        });
    }

    public void onClientDisconnected(String clientIp) {
        Platform.runLater(() -> {
            clientList.removeIf(c -> c.getIpAddress().equals(clientIp));
            clientCountLabel.setText(String.valueOf(clientList.size()));
            log("👤 Client disconnected: " + clientIp);
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