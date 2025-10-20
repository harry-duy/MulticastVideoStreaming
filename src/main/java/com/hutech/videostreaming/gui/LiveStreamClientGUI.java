package com.hutech.videostreaming.gui;

import com.hutech.videostreaming.client.LiveStreamClient;
import com.hutech.videostreaming.common.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Live Stream Client GUI
 * View live video stream
 */
public class LiveStreamClientGUI extends Application
        implements LiveStreamClient.LiveClientCallback {

    private Stage primaryStage;
    private LiveStreamClient client;

    // Video display
    private ImageView videoView;
    private Label noSignalLabel;

    // Controls
    private Button connectBtn;
    private Button disconnectBtn;
    private Button recordBtn;
    private Button snapshotBtn;
    private CheckBox fullscreenCheck;
    private Slider volumeSlider;

    // Status
    private Label statusLabel;
    private Label fpsLabel;
    private Label bandwidthLabel;
    private Label frameCountLabel;
    private Label droppedLabel;
    private TextArea logArea;

    // Recording
    private boolean isRecording = false;
    private java.util.List<javafx.scene.image.Image> recordedFrames;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.client = new LiveStreamClient(this);
        this.recordedFrames = new java.util.ArrayList<>();

        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // Header
        VBox header = createHeader();
        root.setTop(header);

        // Center - Video display
        VBox center = createVideoDisplay();
        root.setCenter(center);

        // Right - Controls & Stats
        VBox right = createRightPanel();
        root.setRight(right);

        // Bottom - Logs
        VBox bottom = createLogSection();
        root.setBottom(bottom);

        // Scene
        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setTitle("📺 Live Stream Viewer - HUTECH");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();

        log("✅ Live Stream Client GUI initialized");
        NotificationManager.success("Live Stream Client ready!", primaryStage);
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label title = new Label("📺 LIVE STREAM VIEWER");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Watch live broadcasts from server");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private VBox createVideoDisplay() {
        VBox videoContainer = new VBox(15);
        videoContainer.setPadding(new Insets(30));
        videoContainer.setAlignment(Pos.CENTER);
        videoContainer.setStyle("-fx-background-color: #1e1e2e;");

        // Video display area
        StackPane videoPane = new StackPane();
        videoPane.setStyle(
                "-fx-background-color: #000000;" +
                        "-fx-border-color: #3a3a4a;" +
                        "-fx-border-width: 2;" +
                        "-fx-border-radius: 8;"
        );
        videoPane.setPrefSize(800, 600);
        videoPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(videoPane, Priority.ALWAYS);

        // Video view
        videoView = new ImageView();
        videoView.setPreserveRatio(true);
        videoView.fitWidthProperty().bind(videoPane.widthProperty().subtract(20));
        videoView.fitHeightProperty().bind(videoPane.heightProperty().subtract(20));

        // No signal label
        noSignalLabel = new Label("📡 No Signal\nConnect to start watching");
        noSignalLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        noSignalLabel.setTextFill(Color.web("#6b7280"));
        noSignalLabel.setTextAlignment(TextAlignment.CENTER);

        videoPane.getChildren().addAll(noSignalLabel, videoView);

        // Control bar
        HBox controlBar = createVideoControls();

        videoContainer.getChildren().addAll(videoPane, controlBar);
        return videoContainer;
    }

    private HBox createVideoControls() {
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));
        controls.setStyle("-fx-background-color: #262637; -fx-background-radius: 8;");

        // Connect button
        connectBtn = createControlButton("🔗 Connect", "#10b981");
        connectBtn.setOnAction(e -> connectToStream());

        // Disconnect button
        disconnectBtn = createControlButton("🔌 Disconnect", "#ef4444");
        disconnectBtn.setDisable(true);
        disconnectBtn.setOnAction(e -> disconnectFromStream());

        // Record button
        recordBtn = createControlButton("⏺️ Record", "#f59e0b");
        recordBtn.setDisable(true);
        recordBtn.setOnAction(e -> toggleRecording());

        // Snapshot button
        snapshotBtn = createControlButton("📷 Snapshot", "#8b5cf6");
        snapshotBtn.setDisable(true);
        snapshotBtn.setOnAction(e -> takeSnapshot());

        // Fullscreen checkbox
        fullscreenCheck = new CheckBox("Fullscreen");
        fullscreenCheck.setTextFill(Color.web("#e0e0e0"));
        fullscreenCheck.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        fullscreenCheck.setOnAction(e -> toggleFullscreen());

        controls.getChildren().addAll(
                connectBtn, disconnectBtn, recordBtn,
                snapshotBtn, fullscreenCheck
        );

        return controls;
    }

    private VBox createRightPanel() {
        VBox rightPanel = new VBox(20);
        rightPanel.setPadding(new Insets(30, 30, 30, 15));
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle("-fx-background-color: #262637;");

        // Status card
        VBox statusCard = createStatusCard();

        // Statistics card
        VBox statsCard = createStatsCard();

        // Settings card
        VBox settingsCard = createSettingsCard();

        rightPanel.getChildren().addAll(statusCard, statsCard, settingsCard);
        return rightPanel;
    }

    private VBox createStatusCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-background-radius: 10;"
        );

        Label title = new Label("📊 Status");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        statusLabel = new Label("⚪ Disconnected");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.web("#9ca3af"));
        statusLabel.setWrapText(true);

        card.getChildren().addAll(title, statusLabel);
        return card;
    }

    private VBox createStatsCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-background-radius: 10;"
        );

        Label title = new Label("📈 Statistics");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        GridPane stats = new GridPane();
        stats.setHgap(10);
        stats.setVgap(10);

        // FPS
        Label fpsTitle = new Label("FPS:");
        fpsTitle.setTextFill(Color.web("#9ca3af"));
        fpsLabel = new Label("0.0");
        fpsLabel.setTextFill(Color.web("#60a5fa"));
        fpsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        // Bandwidth
        Label bwTitle = new Label("Bandwidth:");
        bwTitle.setTextFill(Color.web("#9ca3af"));
        bandwidthLabel = new Label("0 Mbps");
        bandwidthLabel.setTextFill(Color.web("#60a5fa"));
        bandwidthLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        // Frames
        Label framesTitle = new Label("Frames:");
        framesTitle.setTextFill(Color.web("#9ca3af"));
        frameCountLabel = new Label("0");
        frameCountLabel.setTextFill(Color.web("#60a5fa"));
        frameCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        // Dropped
        Label droppedTitle = new Label("Dropped:");
        droppedTitle.setTextFill(Color.web("#9ca3af"));
        droppedLabel = new Label("0");
        droppedLabel.setTextFill(Color.web("#f59e0b"));
        droppedLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        stats.add(fpsTitle, 0, 0);
        stats.add(fpsLabel, 1, 0);
        stats.add(bwTitle, 0, 1);
        stats.add(bandwidthLabel, 1, 1);
        stats.add(framesTitle, 0, 2);
        stats.add(frameCountLabel, 1, 2);
        stats.add(droppedTitle, 0, 3);
        stats.add(droppedLabel, 1, 3);

        card.getChildren().addAll(title, stats);
        return card;
    }

    private VBox createSettingsCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-background-radius: 10;"
        );

        Label title = new Label("⚙️ Settings");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        // Volume slider (for future audio support)
        Label volumeLabel = new Label("Volume:");
        volumeLabel.setTextFill(Color.web("#9ca3af"));

        volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setShowTickLabels(false);
        volumeSlider.setShowTickMarks(false);
        volumeSlider.setDisable(true); // No audio yet

        card.getChildren().addAll(title, volumeLabel, volumeSlider);
        return card;
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
        logArea.setPrefHeight(100);
        logArea.setStyle(
                "-fx-control-inner-background: #2a2a3a;" +
                        "-fx-text-fill: #e0e0e0;" +
                        "-fx-font-family: 'Consolas', monospace;" +
                        "-fx-font-size: 11px;"
        );

        logSection.getChildren().addAll(logHeader, logArea);
        return logSection;
    }

    private Button createControlButton(String text, String color) {
        Button button = new Button(text);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        button.setTextFill(Color.WHITE);
        button.setPrefWidth(120);
        button.setPrefHeight(40);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: derive(" + color + ", -10%);" +
                                "-fx-background-radius: 6;" +
                                "-fx-cursor: hand;"
                );
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: " + color + ";" +
                                "-fx-background-radius: 6;" +
                                "-fx-cursor: hand;"
                );
            }
        });

        return button;
    }

    // ==================== ACTIONS ====================

    private void connectToStream() {
        client.connect();
        connectBtn.setDisable(true);
        log("🔗 Connecting to live stream...");
    }

    private void disconnectFromStream() {
        client.disconnect();
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
        recordBtn.setDisable(true);
        snapshotBtn.setDisable(true);

        videoView.setImage(null);
        noSignalLabel.setVisible(true);

        log("🔌 Disconnected from live stream");
    }

    private void toggleRecording() {
        if (!isRecording) {
            // Start recording
            isRecording = true;
            recordedFrames.clear();
            recordBtn.setText("⏹️ Stop Rec");
            recordBtn.setStyle("-fx-background-color: #ef4444;");
            log("⏺️ Recording started");
            NotificationManager.info("Recording started", primaryStage);
        } else {
            // Stop recording
            isRecording = false;
            recordBtn.setText("⏺️ Record");
            recordBtn.setStyle("-fx-background-color: #f59e0b;");
            log("⏹️ Recording stopped - " + recordedFrames.size() + " frames");

            // Save recording
            saveRecording();
        }
    }

    private void takeSnapshot() {
        if (videoView.getImage() != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Snapshot");
            fileChooser.setInitialFileName("snapshot_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".png");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG Images", "*.png")
            );

            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    javafx.embed.swing.SwingFXUtils.fromFXImage(
                            videoView.getImage(), null
                    );
                    // Save image
                    log("📷 Snapshot saved: " + file.getName());
                    NotificationManager.success("Snapshot saved!", primaryStage);
                } catch (Exception e) {
                    log("❌ Failed to save snapshot: " + e.getMessage());
                    NotificationManager.error("Failed to save snapshot", primaryStage);
                }
            }
        }
    }

    private void toggleFullscreen() {
        primaryStage.setFullScreen(fullscreenCheck.isSelected());
    }

    private void saveRecording() {
        if (recordedFrames.isEmpty()) {
            NotificationManager.warning("No frames to save!", primaryStage);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Recording");
        fileChooser.setInitialFileName("recording_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".gif");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("GIF Animation", "*.gif")
        );

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            log("💾 Saving " + recordedFrames.size() + " frames to: " + file.getName());
            NotificationManager.success("Recording saved!", primaryStage);
        }
    }

    // ==================== CALLBACKS ====================

    @Override
    public void onConnected() {
        Platform.runLater(() -> {
            statusLabel.setText("🟢 Connected");
            statusLabel.setTextFill(Color.web("#10b981"));
            disconnectBtn.setDisable(false);
            recordBtn.setDisable(false);
            snapshotBtn.setDisable(false);
            log("✅ Connected to multicast group");
            NotificationManager.success("Connected!", primaryStage);
        });
    }

    @Override
    public void onFrameReceived(Image frame, long timestamp) {
        Platform.runLater(() -> {
            videoView.setImage(frame);
            noSignalLabel.setVisible(false);

            // Record frame if recording
            if (isRecording) {
                recordedFrames.add(frame);
            }
        });
    }

    @Override
    public void onStreamStarted() {
        Platform.runLater(() -> {
            statusLabel.setText("📡 Receiving Stream");
            statusLabel.setTextFill(Color.web("#3b82f6"));
            log("▶️ Live stream started");
            NotificationManager.info("Stream started!", primaryStage);
        });
    }

    @Override
    public void onStreamStopped() {
        Platform.runLater(() -> {
            statusLabel.setText("🟢 Connected (No Stream)");
            statusLabel.setTextFill(Color.web("#f59e0b"));
            log("⏹️ Stream stopped");
            NotificationManager.warning("Stream stopped", primaryStage);
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            statusLabel.setText("⚪ Disconnected");
            statusLabel.setTextFill(Color.web("#9ca3af"));
            videoView.setImage(null);
            noSignalLabel.setVisible(true);
            log("🔴 Disconnected");
        });
    }

    @Override
    public void onError(String error) {
        Platform.runLater(() -> {
            statusLabel.setText("❌ Error");
            statusLabel.setTextFill(Color.web("#ef4444"));
            log("❌ ERROR: " + error);
            NotificationManager.error(error, primaryStage);
        });
    }

    @Override
    public void onStatisticsUpdate(LiveStreamClient.LiveStreamStatistics stats) {
        Platform.runLater(() -> {
            fpsLabel.setText(String.format("%.1f", stats.actualFPS));
            bandwidthLabel.setText(stats.bandwidth);
            frameCountLabel.setText(String.valueOf(stats.framesReceived));
            droppedLabel.setText(String.valueOf(stats.droppedPackets));
        });
    }

    // ==================== UTILITIES ====================

    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    private void shutdown() {
        if (client != null) {
            client.disconnect();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}