package com.hutech.videostreaming.live;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Webcam Selector - JavaCV Version
 * Allows user to select from available webcams with preview
 */
public class WebcamSelector {

    private JavaCVWebcamCapture selectedCapture;
    private JavaCVWebcamCapture.WebcamInfo selectedWebcam;
    private int selectedWidth = 640;
    private int selectedHeight = 480;
    private CountDownLatch latch;
    private Stage stage;
    private ScheduledExecutorService previewExecutor;
    private volatile boolean isPreviewRunning = false;

    /**
     * Show selector and wait for user selection
     */
    public WebcamSelection selectWebcam() {
        latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                showSelector();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (selectedWebcam != null) {
            return new WebcamSelection(selectedWebcam, selectedWidth, selectedHeight);
        }

        return null;
    }

    /**
     * Show webcam selector
     */
    private void showSelector() {
        stage = new Stage();
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("Select Webcam");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");

        // Header
        VBox header = createHeader();
        root.setTop(header);

        // Center - Webcam list and preview
        HBox center = createCenterContent();
        root.setCenter(center);

        // Bottom - Actions
        HBox bottom = createBottomActions();
        root.setBottom(bottom);

        Scene scene = new Scene(root, 900, 600);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            cleanup();
            latch.countDown();
        });
        stage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label title = new Label("📹 Select Webcam");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Choose your webcam and resolution (JavaCV)");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private HBox createCenterContent() {
        HBox center = new HBox(20);
        center.setPadding(new Insets(30));

        // Left - Webcam list
        VBox leftPanel = createWebcamList();
        leftPanel.setPrefWidth(350);

        // Right - Preview
        VBox rightPanel = createPreviewPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        center.getChildren().addAll(leftPanel, rightPanel);
        return center;
    }

    private VBox createWebcamList() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: #262637;" +
                        "-fx-background-radius: 10;"
        );

        Label title = new Label("Available Webcams");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        // Get webcams
        List<JavaCVWebcamCapture.WebcamInfo> webcams = JavaCVWebcamCapture.listWebcams();

        if (webcams.isEmpty()) {
            Label noWebcam = new Label("❌ No webcams found");
            noWebcam.setTextFill(Color.web("#ef4444"));
            noWebcam.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));

            Label troubleshoot = new Label("\nTroubleshooting:\n" +
                    "• Check webcam connection\n" +
                    "• Close other apps using webcam\n" +
                    "• Check system permissions");
            troubleshoot.setTextFill(Color.web("#9ca3af"));
            troubleshoot.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));

            panel.getChildren().addAll(title, noWebcam, troubleshoot);
            return panel;
        }

        // Webcam list
        ListView<WebcamItem> webcamListView = new ListView<>();
        webcamListView.setPrefHeight(150);
        webcamListView.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-control-inner-background: #2a2a3a;"
        );

        // Add webcams
        for (JavaCVWebcamCapture.WebcamInfo webcam : webcams) {
            webcamListView.getItems().add(new WebcamItem(webcam));
        }

        // Select first by default
        if (!webcamListView.getItems().isEmpty()) {
            webcamListView.getSelectionModel().select(0);
        }

        // Resolution selector
        Label resolutionTitle = new Label("Resolution");
        resolutionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resolutionTitle.setTextFill(Color.web("#e0e0e0"));

        ComboBox<ResolutionItem> resolutionCombo = new ComboBox<>();
        resolutionCombo.setMaxWidth(Double.MAX_VALUE);
        resolutionCombo.getItems().addAll(
                new ResolutionItem("VGA (640x480)", 640, 480),
                new ResolutionItem("SVGA (800x600)", 800, 600),
                new ResolutionItem("HD (1280x720)", 1280, 720),
                new ResolutionItem("Full HD (1920x1080)", 1920, 1080)
        );
        resolutionCombo.setValue(resolutionCombo.getItems().get(0));

        // Preview button
        Button previewBtn = new Button("👁️ Start Preview");
        previewBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        previewBtn.setMaxWidth(Double.MAX_VALUE);
        previewBtn.setPrefHeight(45);
        previewBtn.setStyle(
                "-fx-background-color: #3b82f6;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );

        // Store references for later
        previewBtn.setUserData(new PreviewData(webcamListView, resolutionCombo));

        previewBtn.setOnAction(e -> {
            WebcamItem selected = webcamListView.getSelectionModel().getSelectedItem();
            ResolutionItem resolution = resolutionCombo.getValue();

            if (selected != null && resolution != null) {
                selectedWebcam = selected.webcamInfo;
                selectedWidth = resolution.width;
                selectedHeight = resolution.height;
                startPreview(selectedWebcam.id, resolution.width, resolution.height);

                previewBtn.setText("⏹️ Stop Preview");
                previewBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
            }
        });

        panel.getChildren().addAll(
                title, webcamListView,
                resolutionTitle, resolutionCombo,
                previewBtn
        );

        return panel;
    }

    private VBox createPreviewPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: #262637;" +
                        "-fx-background-radius: 10;"
        );
        panel.setAlignment(Pos.CENTER);

        Label title = new Label("Preview");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));

        // Preview area
        StackPane previewPane = new StackPane();
        previewPane.setStyle(
                "-fx-background-color: #000000;" +
                        "-fx-border-color: #3a3a4a;" +
                        "-fx-border-width: 2;" +
                        "-fx-border-radius: 8;"
        );
        previewPane.setPrefSize(500, 400);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        ImageView previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);
        previewImageView.fitWidthProperty().bind(previewPane.widthProperty().subtract(20));
        previewImageView.fitHeightProperty().bind(previewPane.heightProperty().subtract(20));

        Label placeholderLabel = new Label("📹\n\nSelect a webcam and click\n'Start Preview'");
        placeholderLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        placeholderLabel.setTextFill(Color.web("#6b7280"));
        placeholderLabel.setTextAlignment(TextAlignment.CENTER);

        previewPane.getChildren().addAll(placeholderLabel, previewImageView);

        // Info label
        Label infoLabel = new Label("Preview updates at 10 FPS to reduce CPU usage");
        infoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        infoLabel.setTextFill(Color.web("#9ca3af"));

        panel.getChildren().addAll(title, previewPane, infoLabel);

        // Store reference for preview updates
        panel.setUserData(new PreviewPanelData(previewImageView, placeholderLabel));

        return panel;
    }

    private HBox createBottomActions() {
        HBox bottom = new HBox(15);
        bottom.setPadding(new Insets(20, 30, 20, 30));
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setStyle("-fx-background-color: #262637;");

        Button cancelBtn = new Button("Cancel");
        styleButton(cancelBtn, "#6b7280");
        cancelBtn.setOnAction(e -> {
            cleanup();
            selectedWebcam = null;
            stage.close();
            latch.countDown();
        });

        Button selectBtn = new Button("✓ Select");
        styleButton(selectBtn, "#10b981");
        selectBtn.setOnAction(e -> {
            cleanup();
            stage.close();
            latch.countDown();
        });

        bottom.getChildren().addAll(cancelBtn, selectBtn);
        return bottom;
    }

    private void styleButton(Button button, String color) {
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setPrefWidth(120);
        button.setPrefHeight(40);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );

        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: derive(" + color + ", -10%);" +
                                "-fx-text-fill: white;" +
                                "-fx-background-radius: 6;"
                );
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) {
                button.setStyle(
                        "-fx-background-color: " + color + ";" +
                                "-fx-text-fill: white;" +
                                "-fx-background-radius: 6;"
                );
            }
        });
    }

    /**
     * Start webcam preview
     */
    private void startPreview(int deviceId, int width, int height) {
        // Stop previous preview
        stopPreview();

        try {
            selectedCapture = new JavaCVWebcamCapture(deviceId);
            selectedCapture.setResolution(width, height);
            selectedCapture.setFrameRate(10); // Low FPS for preview
            selectedCapture.start();

            System.out.println("📹 [SELECTOR] Starting preview: Device " + deviceId +
                    " @ " + width + "x" + height);

            isPreviewRunning = true;
            previewExecutor = Executors.newScheduledThreadPool(1);

            previewExecutor.scheduleAtFixedRate(() -> {
                if (!isPreviewRunning) return;

                try {
                    java.awt.image.BufferedImage image = selectedCapture.captureFrame();

                    if (image != null) {
                        javafx.scene.image.Image fxImage =
                                SwingFXUtils.toFXImage(image, null);

                        Platform.runLater(() -> updatePreview(fxImage));
                    }

                } catch (Exception e) {
                    System.err.println("Preview error: " + e.getMessage());
                }

            }, 0, 100, TimeUnit.MILLISECONDS); // 10 FPS

        } catch (Exception e) {
            System.err.println("❌ [SELECTOR] Failed to start preview: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stop preview
     */
    private void stopPreview() {
        isPreviewRunning = false;

        if (previewExecutor != null && !previewExecutor.isShutdown()) {
            previewExecutor.shutdown();
            try {
                if (!previewExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    previewExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                previewExecutor.shutdownNow();
            }
        }

        if (selectedCapture != null) {
            try {
                selectedCapture.release();
                selectedCapture = null;
            } catch (Exception e) {
                System.err.println("Error releasing webcam: " + e.getMessage());
            }
        }
    }

    /**
     * Update preview image
     */
    private void updatePreview(javafx.scene.image.Image image) {
        // Find preview panel
        BorderPane root = (BorderPane) stage.getScene().getRoot();
        HBox center = (HBox) root.getCenter();

        for (javafx.scene.Node node : center.getChildren()) {
            if (node instanceof VBox && node.getUserData() instanceof PreviewPanelData) {
                PreviewPanelData data = (PreviewPanelData) node.getUserData();
                data.imageView.setImage(image);
                data.placeholderLabel.setVisible(false);
                break;
            }
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        stopPreview();
    }

    // ==================== HELPER CLASSES ====================

    private static class WebcamItem {
        JavaCVWebcamCapture.WebcamInfo webcamInfo;

        WebcamItem(JavaCVWebcamCapture.WebcamInfo info) {
            this.webcamInfo = info;
        }

        @Override
        public String toString() {
            return "📹 " + webcamInfo.name + " (" + webcamInfo.width + "x" + webcamInfo.height + ")";
        }
    }

    private static class ResolutionItem {
        String label;
        int width;
        int height;

        ResolutionItem(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class PreviewData {
        ListView<WebcamItem> listView;
        ComboBox<ResolutionItem> resolutionCombo;

        PreviewData(ListView<WebcamItem> listView, ComboBox<ResolutionItem> combo) {
            this.listView = listView;
            this.resolutionCombo = combo;
        }
    }

    private static class PreviewPanelData {
        ImageView imageView;
        Label placeholderLabel;

        PreviewPanelData(ImageView imageView, Label placeholderLabel) {
            this.imageView = imageView;
            this.placeholderLabel = placeholderLabel;
        }
    }

    /**
     * Webcam selection result
     */
    public static class WebcamSelection {
        public JavaCVWebcamCapture.WebcamInfo webcamInfo;
        public int width;
        public int height;

        public WebcamSelection(JavaCVWebcamCapture.WebcamInfo info, int width, int height) {
            this.webcamInfo = info;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("Webcam: %s, Resolution: %dx%d",
                    webcamInfo.name, width, height);
        }
    }

    /**
     * Test application
     */
    public static void main(String[] args) {
        javafx.application.Application.launch(TestApp.class, args);
    }

    public static class TestApp extends javafx.application.Application {
        @Override
        public void start(Stage primaryStage) {
            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(50));
            root.setStyle("-fx-background-color: #1e1e2e;");

            Label title = new Label("🧪 Webcam Selector Test");
            title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
            title.setStyle("-fx-text-fill: white;");

            Button selectBtn = new Button("Select Webcam");
            selectBtn.setPrefWidth(200);
            selectBtn.setPrefHeight(50);
            selectBtn.setStyle(
                    "-fx-background-color: #3b82f6;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 16px;" +
                            "-fx-font-weight: bold;"
            );

            Label resultLabel = new Label("No webcam selected");
            resultLabel.setStyle("-fx-text-fill: #9ca3af;");
            resultLabel.setWrapText(true);
            resultLabel.setMaxWidth(400);
            resultLabel.setAlignment(Pos.CENTER);

            selectBtn.setOnAction(e -> {
                WebcamSelector selector = new WebcamSelector();
                WebcamSelection selection = selector.selectWebcam();

                if (selection != null) {
                    resultLabel.setText("✅ Selected:\n" + selection.toString());
                    resultLabel.setStyle("-fx-text-fill: #10b981;");
                    System.out.println("Selected: " + selection);
                } else {
                    resultLabel.setText("❌ Selection cancelled");
                    resultLabel.setStyle("-fx-text-fill: #ef4444;");
                    System.out.println("Selection cancelled");
                }
            });

            root.getChildren().addAll(title, selectBtn, resultLabel);

            Scene scene = new Scene(root, 600, 400);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Webcam Selector Test");
            primaryStage.show();
        }
    }
}