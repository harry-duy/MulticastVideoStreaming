package com.hutech.videostreaming.gui;

import com.github.sarxos.webcam.Webcam;
import com.hutech.videostreaming.common.*;
import com.hutech.videostreaming.live.LiveStreamManager;
import com.hutech.videostreaming.live.ScreenRegionSelector;
import com.hutech.videostreaming.server.LiveVideoServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/**
 * Live Streaming GUI - Webcam & Screen Capture
 */
public class LiveStreamGUI extends Application implements LiveStreamManager.StreamDataCallback {

    private Stage primaryStage;
    private LiveStreamManager liveManager;
    private LiveVideoServer liveServer;

    // Controls
    private ComboBox<String> sourceSelector;
    private ComboBox<String> resolutionSelector;
    private Slider fpsSlider;
    private Slider qualitySlider;
    private CheckBox compressionCheckbox;
    private Button startBtn;
    private Button stopBtn;
    private Button selectRegionBtn;
    private Button previewBtn;

    // Status
    private Label statusLabel;
    private Label fpsLabel;
    private Label dataRateLabel;
    private Label framesLabel;
    private TextArea logArea;

    // Preview
    private ImageView previewImageView;
    private CheckBox autoPreviewCheckbox;

    // Region selection
    private Rectangle selectedRegion;

    // Statistics
    private long frameCount = 0;
    private long totalBytes = 0;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize managers
        liveManager = new LiveStreamManager(this);
        liveServer = new LiveVideoServer();

        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // Header
        VBox header = createHeader();
        root.setTop(header);

        // Center content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");

        VBox center = createMainContent();
        scrollPane.setContent(center);
        root.setCenter(scrollPane);

        // Bottom logs
        VBox bottom = createLogSection();
        root.setBottom(bottom);

        // Scene
        Scene scene = new Scene(root, 1100, 900);
        primaryStage.setTitle("📹 Live Streaming - HUTECH");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();

        log("✅ Live Streaming GUI initialized");
        NotificationManager.success("Live Streaming ready!", primaryStage);
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label title = new Label("📹 LIVE STREAMING");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Webcam & Screen Capture Broadcasting");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private VBox createMainContent() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.setAlignment(Pos.TOP_CENTER);

        // Cards
        VBox statusCard = createCard("📊 Status", createStatusPane());
        VBox sourceCard = createCard("🎥 Source Selection", createSourcePane());
        VBox settingsCard = createCard("⚙️ Stream Settings", createSettingsPane());
        VBox previewCard = createCard("👁️ Preview", createPreviewPane());
        VBox controlCard = createCard("🎮 Controls", createControlPane());
        VBox statsCard = createCard("📈 Statistics", createStatsPane());

        container.getChildren().addAll(
                statusCard, sourceCard, settingsCard,
                previewCard, controlCard, statsCard
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

        statusLabel = new Label("⚪ Ready");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        statusLabel.setTextFill(Color.web("#10b981"));

        statusBox.getChildren().addAll(statusTitle, statusLabel);
        statusPane.getChildren().add(statusBox);
        return statusPane;
    }

    private Pane createSourcePane() {
        VBox sourcePane = new VBox(15);
        sourcePane.setPadding(new Insets(15));

        // Source selector
        HBox sourceBox = new HBox(15);
        sourceBox.setAlignment(Pos.CENTER);

        Label sourceLabel = new Label("Source:");
        sourceLabel.setTextFill(Color.web("#e0e0e0"));
        sourceLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        sourceSelector = new ComboBox<>();
        sourceSelector.getItems().addAll(
                "📹 Webcam",
                "🖥️ Full Screen",
                "✂️ Screen Region"
        );
        sourceSelector.setValue("📹 Webcam");
        sourceSelector.setPrefWidth(200);

        sourceBox.getChildren().addAll(sourceLabel, sourceSelector);

        // Resolution selector (for webcam)
        HBox resolutionBox = new HBox(15);
        resolutionBox.setAlignment(Pos.CENTER);

        Label resLabel = new Label("Resolution:");
        resLabel.setTextFill(Color.web("#e0e0e0"));
        resLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        resolutionSelector = new ComboBox<>();
        resolutionSelector.getItems().addAll(
                "640x480 (VGA)",
                "800x600 (SVGA)",
                "1280x720 (HD)",
                "1920x1080 (Full HD)"
        );
        resolutionSelector.setValue("640x480 (VGA)");
        resolutionSelector.setPrefWidth(200);

        resolutionBox.getChildren().addAll(resLabel, resolutionSelector);

        // Region selection button
        selectRegionBtn = createStyledButton("✂️ Select Screen Region", "#f59e0b");
        selectRegionBtn.setPrefWidth(250);
        selectRegionBtn.setDisable(true);
        selectRegionBtn.setOnAction(e -> selectScreenRegion());

        // Enable/disable based on source
        sourceSelector.setOnAction(e -> {
            String source = sourceSelector.getValue();
            selectRegionBtn.setDisable(!source.contains("Region"));
            resolutionSelector.setDisable(!source.contains("Webcam"));
        });

        sourcePane.getChildren().addAll(sourceBox, resolutionBox, selectRegionBtn);
        return sourcePane;
    }

    private Pane createSettingsPane() {
        VBox settingsPane = new VBox(20);
        settingsPane.setPadding(new Insets(15));

        // FPS slider
        VBox fpsBox = new VBox(10);
        HBox fpsHeader = new HBox(10);
        fpsHeader.setAlignment(Pos.CENTER_LEFT);

        Label fpsTitle = new Label("Frame Rate (FPS):");
        fpsTitle.setTextFill(Color.web("#e0e0e0"));
        fpsTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        fpsLabel = new Label("25");
        fpsLabel.setTextFill(Color.web("#60a5fa"));
        fpsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        fpsHeader.getChildren().addAll(fpsTitle, fpsLabel);

        fpsSlider = new Slider(5, 60, 25);
        fpsSlider.setShowTickMarks(true);
        fpsSlider.setShowTickLabels(true);
        fpsSlider.setMajorTickUnit(10);
        fpsSlider.setMinorTickCount(4);
        fpsSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            fpsLabel.setText(String.valueOf(newVal.intValue()));
        });

        fpsBox.getChildren().addAll(fpsHeader, fpsSlider);

        // Quality slider
        VBox qualityBox = new VBox(10);
        HBox qualityHeader = new HBox(10);
        qualityHeader.setAlignment(Pos.CENTER_LEFT);

        Label qualityTitle = new Label("Quality:");
        qualityTitle.setTextFill(Color.web("#e0e0e0"));
        qualityTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        Label qualityValue = new Label("80%");
        qualityValue.setTextFill(Color.web("#60a5fa"));
        qualityValue.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        qualityHeader.getChildren().addAll(qualityTitle, qualityValue);

        qualitySlider = new Slider(10, 100, 80);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setShowTickLabels(true);
        qualitySlider.setMajorTickUnit(20);
        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            qualityValue.setText(String.valueOf(newVal.intValue()) + "%");
        });

        qualityBox.getChildren().addAll(qualityHeader, qualitySlider);

        // Compression
        compressionCheckbox = new CheckBox("Enable Compression");
        compressionCheckbox.setSelected(true);
        compressionCheckbox.setTextFill(Color.web("#e0e0e0"));
        compressionCheckbox.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));

        settingsPane.getChildren().addAll(fpsBox, qualityBox, compressionCheckbox);
        return settingsPane;
    }

    private Pane createPreviewPane() {
        VBox previewPane = new VBox(15);
        previewPane.setPadding(new Insets(15));
        previewPane.setAlignment(Pos.CENTER);

        // Preview image
        previewImageView = new ImageView();
        previewImageView.setFitWidth(640);
        previewImageView.setFitHeight(480);
        previewImageView.setPreserveRatio(true);
        previewImageView.setStyle("-fx-background-color: #2a2a3a;");

        // Default placeholder
        previewImageView.setImage(createPlaceholderImage());

        // Auto preview checkbox
        autoPreviewCheckbox = new CheckBox("Auto Preview");
        autoPreviewCheckbox.setSelected(false);
        autoPreviewCheckbox.setTextFill(Color.web("#e0e0e0"));
        autoPreviewCheckbox.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));

        // Preview button
        previewBtn = createStyledButton("📷 Take Snapshot", "#8b5cf6");
        previewBtn.setPrefWidth(200);
        previewBtn.setOnAction(e -> log("Preview feature - capture current frame"));

        previewPane.getChildren().addAll(
                previewImageView,
                autoPreviewCheckbox,
                previewBtn
        );
        return previewPane;
    }

    private Pane createControlPane() {
        HBox controlPane = new HBox(15);
        controlPane.setAlignment(Pos.CENTER);
        controlPane.setPadding(new Insets(15));

        startBtn = createStyledButton("▶️ Start Stream", "#10b981");
        startBtn.setPrefWidth(180);
        startBtn.setOnAction(e -> startLiveStream());

        stopBtn = createStyledButton("⏹️ Stop Stream", "#ef4444");
        stopBtn.setPrefWidth(180);
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> stopLiveStream());

        controlPane.getChildren().addAll(startBtn, stopBtn);
        return controlPane;
    }

    private Pane createStatsPane() {
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(15);
        statsPane.setAlignment(Pos.CENTER);
        statsPane.setPadding(new Insets(15));

        // FPS stat
        VBox fpsBox = createStatBox("📊 Actual FPS", "0.0");
        fpsLabel = (Label) fpsBox.getChildren().get(1);

        // Data rate
        VBox dataBox = createStatBox("🌐 Data Rate", "0 KB/s");
        dataRateLabel = (Label) dataBox.getChildren().get(1);

        // Frames
        VBox frameBox = createStatBox("🎬 Frames", "0");
        framesLabel = (Label) frameBox.getChildren().get(1);

        statsPane.add(fpsBox, 0, 0);
        statsPane.add(dataBox, 1, 0);
        statsPane.add(frameBox, 2, 0);

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

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        valueLabel.setTextFill(Color.web("#60a5fa"));

        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
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

    // ==================== ACTIONS ====================

    private void startLiveStream() {
        String source = sourceSelector.getValue();
        int fps = (int) fpsSlider.getValue();
        double quality = qualitySlider.getValue() / 100.0;
        boolean compression = compressionCheckbox.isSelected();

        // Apply settings
        liveManager.setQuality(quality, fps);
        liveManager.setCompression(compression);

        // Start based on source
        try {
            if (source.contains("Webcam")) {
                liveManager.startWebcamStream();
                log("📹 Started webcam streaming");
            } else if (source.contains("Full Screen")) {
                liveManager.startScreenCapture();
                log("🖥️ Started full screen capture");
            } else if (source.contains("Region")) {
                if (selectedRegion != null) {
                    liveManager.startScreenCapture(selectedRegion);
                    log("✂️ Started screen region capture");
                } else {
                    NotificationManager.warning("Please select a screen region first!", primaryStage);
                    return;
                }
            }

            // Start server
            liveServer.startStreaming(liveManager);

            // Update UI
            statusLabel.setText("🔴 Streaming");
            statusLabel.setTextFill(Color.web("#ef4444"));
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            sourceSelector.setDisable(true);

            NotificationManager.success("Live streaming started!", primaryStage);

        } catch (Exception e) {
            log("❌ Failed to start: " + e.getMessage());
            NotificationManager.error("Failed to start streaming: " + e.getMessage(), primaryStage);
        }
    }

    private void stopLiveStream() {
        liveManager.stopStreaming();
        liveServer.stopStreaming();

        statusLabel.setText("⚪ Ready");
        statusLabel.setTextFill(Color.web("#10b981"));
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        sourceSelector.setDisable(false);

        log("⏹️ Streaming stopped");
        NotificationManager.info("Streaming stopped", primaryStage);
    }

    private void selectScreenRegion() {
        log("✂️ Select screen region...");

        ScreenRegionSelector selector = new ScreenRegionSelector();
        selectedRegion = selector.selectRegion();

        if (selectedRegion != null) {
            log("✅ Region selected: " + selectedRegion.width + "x" + selectedRegion.height +
                    " at (" + selectedRegion.x + "," + selectedRegion.y + ")");
            NotificationManager.success("Region selected!", primaryStage);
        } else {
            log("❌ Region selection cancelled");
        }
    }

    // ==================== CALLBACKS ====================

    @Override
    public void onFrameReady(byte[] frameData, long timestamp) {
        frameCount++;
        totalBytes += frameData.length;

        // Update preview if enabled
        if (autoPreviewCheckbox.isSelected() && frameCount % 10 == 0) {
            Platform.runLater(() -> updatePreview(frameData));
        }
    }

    @Override
    public void onError(String error) {
        Platform.runLater(() -> {
            log("❌ ERROR: " + error);
            NotificationManager.error(error, primaryStage);
        });
    }

    @Override
    public void onStatisticsUpdate(LiveStreamManager.StreamStatistics stats) {
        Platform.runLater(() -> {
            fpsLabel.setText(String.format("%.1f", stats.actualFPS));

            double dataRate = (totalBytes / 1024.0) / (stats.framesCaptures / stats.actualFPS);
            dataRateLabel.setText(String.format("%.1f KB/s", dataRate));

            framesLabel.setText(String.valueOf(stats.framesCaptures));
        });
    }

    // ==================== UTILITIES ====================

    private void updatePreview(byte[] imageData) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (bufferedImage != null) {
                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                previewImageView.setImage(fxImage);
            }
        } catch (Exception e) {
            log("⚠️ Preview update failed: " + e.getMessage());
        }
    }

    private Image createPlaceholderImage() {
        WritableImage image = new WritableImage(640, 480);
        PixelWriter writer = image.getPixelWriter();

        for (int y = 0; y < 480; y++) {
            for (int x = 0; x < 640; x++) {
                writer.setColor(x, y, Color.web("#2a2a3a"));
            }
        }

        return image;
    }

    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    private void shutdown() {
        if (liveManager != null) {
            liveManager.shutdown();
        }
        if (liveServer != null) {
            liveServer.close();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}