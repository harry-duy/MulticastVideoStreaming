package com.hutech.videostreaming.live;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
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
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;

/**
 * Webcam Selector
 * Allows user to select from available webcams with preview
 */
public class WebcamSelector {

    private Webcam selectedWebcam;
    private Dimension selectedResolution;
    private CountDownLatch latch;
    private Stage stage;
    private ScheduledExecutorService previewExecutor;

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

        if (selectedWebcam != null && selectedResolution != null) {
            return new WebcamSelection(selectedWebcam, selectedResolution);
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

        Label subtitle = new Label("Choose your webcam and resolution");
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
        leftPanel.setPrefWidth(300);

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
        List<Webcam> webcams = Webcam.getWebcams();

        if (webcams.isEmpty()) {
            Label noWebcam = new Label("❌ No webcams found");
            noWebcam.setTextFill(Color.web("#ef4444"));
            noWebcam.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
            panel.getChildren().addAll(title, noWebcam);
            return panel;
        }

        // Webcam list
        ListView<WebcamItem> webcamListView = new ListView<>();
        webcamListView.setPrefHeight(200);
        webcamListView.setStyle(
                "-fx-background-color: #2a2a3a;" +
                        "-fx-control-inner-background: #2a2a3a;"
        );

        // Add webcams
        for (Webcam webcam : webcams) {
            webcamListView.getItems().add(new WebcamItem(webcam));
        }

        // Resolution selector
        Label resolutionTitle = new Label("Resolution");
        resolutionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resolutionTitle.setTextFill(Color.web("#e0e0e0"));

        ComboBox<ResolutionItem> resolutionCombo = new ComboBox<>();
        resolutionCombo.setMaxWidth(Double.MAX_VALUE);
        resolutionCombo.getItems().addAll(
                new ResolutionItem("VGA (640x480)", WebcamResolution.VGA.getSize()),
                new ResolutionItem("SVGA (800x600)", WebcamResolution.SVGA.getSize()),
                new ResolutionItem("HD (1280x720)", WebcamResolution.HD.getSize()),
                new ResolutionItem("Full HD (1920x1080)", new Dimension(1920, 1080))
        );
        resolutionCombo.setValue(resolutionCombo.getItems().get(0));

        // Preview button
        Button previewBtn = new Button("👁️ Preview");
        previewBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        previewBtn.setMaxWidth(Double.MAX_VALUE);
        previewBtn.setStyle(
                "-fx-background-color: #3b82f6;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );

        previewBtn.setOnAction(e -> {
            WebcamItem selected = webcamListView.getSelectionModel().getSelectedItem();
            ResolutionItem resolution = resolutionCombo.getValue();

            if (selected != null && resolution != null) {
                startPreview(selected.webcam, resolution.dimension);
            }
        });

        panel.getChildren().addAll(
                title, webcamListView,
                resolutionTitle, resolutionCombo,
                previewBtn
        );

        // Store selections when user clicks preview
        previewBtn.setOnAction(e -> {
            WebcamItem selected = webcamListView.getSelectionModel().getSelectedItem();
            ResolutionItem resolution = resolutionCombo.getValue();

            if (selected != null && resolution != null) {
                selectedWebcam = selected.webcam;
                selectedResolution = resolution.dimension;
                startPreview(selectedWebcam, selectedResolution);
            }
        });

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

        Label placeholderLabel = new Label("📹\nSelect a webcam and click Preview");
        placeholderLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        placeholderLabel.setTextFill(Color.web("#6b7280"));
        placeholderLabel.setTextAlignment(TextAlignment.CENTER);

        previewPane.getChildren().addAll(placeholderLabel, previewImageView);

        // Info label
        Label infoLabel = new Label("Preview will update automatically");
        infoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        infoLabel.setTextFill(Color.web("#9ca3af"));

        panel.getChildren().addAll(title, previewPane, infoLabel);

        // Store reference for preview updates
        panel.setUserData(new PreviewData(previewImageView, placeholderLabel));

        return panel;
    }

    private HBox createBottomActions() {
        HBox bottom = new HBox(15);
        bottom.setPadding(new Insets(20, 30, 20, 30));
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setStyle("-fx-background-color: #262637;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        cancelBtn.setPrefWidth(120);
        cancelBtn.setStyle(
                "-fx-background-color: #6b7280;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );
        cancelBtn.setOnAction(e -> {
            cleanup();
            selectedWebcam = null;
            selectedResolution = null;
            stage.close();
            latch.countDown();
        });

        Button selectBtn = new Button("Select");
        selectBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        selectBtn.setPrefWidth(120);
        selectBtn.setStyle(
                "-fx-background-color: #10b981;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );
        selectBtn.setOnAction(e -> {
            cleanup();
            stage.close();
            latch.countDown();
        });

        bottom.getChildren().addAll(cancelBtn, selectBtn);
        return bottom;
    }

    /**
     * Start webcam preview
     */
    private void startPreview(Webcam webcam, Dimension resolution) {
        // Stop previous preview
        stopPreview();

        try {
            webcam.setViewSize(resolution);
            webcam.open();

            System.out.println("📹 [SELECTOR] Starting preview: " + webcam.getName() +
                    " @ " + resolution.width + "x" + resolution.height);

            previewExecutor = Executors.newScheduledThreadPool(1);
            previewExecutor.scheduleAtFixedRate(() -> {
                try {
                    BufferedImage image = webcam.getImage();

                    if (image != null) {
                        javafx.scene.image.Image fxImage =
                                SwingFXUtils.toFXImage(image, null);

                        Platform.runLater(() -> updatePreview(fxImage));
                    }

                } catch (Exception e) {
                    System.err.println("Preview error: " + e.getMessage());
                }

            }, 0, 100, TimeUnit.MILLISECONDS); // 10 FPS preview

        } catch (Exception e) {
            System.err.println("❌ [SELECTOR] Failed to start preview: " + e.getMessage());
        }
    }

    /**
     * Stop preview
     */
    private void stopPreview() {
        if (previewExecutor != null && !previewExecutor.isShutdown()) {
            previewExecutor.shutdown();
        }

        if (selectedWebcam != null && selectedWebcam.isOpen()) {
            selectedWebcam.close();
        }
    }

    /**
     * Update preview image
     */
    private void updatePreview(javafx.scene.image.Image image) {
        // Find preview panel
        VBox previewPanel = (VBox) stage.getScene().getRoot().lookup(".center .right-panel");

        if (previewPanel != null && previewPanel.getUserData() instanceof PreviewData) {
            PreviewData data = (PreviewData) previewPanel.getUserData();
            data.imageView.setImage(image);
            data.placeholderLabel.setVisible(false);
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
        Webcam webcam;

        WebcamItem(Webcam webcam) {
            this.webcam = webcam;
        }

        @Override
        public String toString() {
            return "📹 " + webcam.getName();
        }
    }

    private static class ResolutionItem {
        String label;
        Dimension dimension;

        ResolutionItem(String label, Dimension dimension) {
            this.label = label;
            this.dimension = dimension;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class PreviewData {
        ImageView imageView;
        Label placeholderLabel;

        PreviewData(ImageView imageView, Label placeholderLabel) {
            this.imageView = imageView;
            this.placeholderLabel = placeholderLabel;
        }
    }

    /**
     * Webcam selection result
     */
    public static class WebcamSelection {
        public Webcam webcam;
        public Dimension resolution;

        public WebcamSelection(Webcam webcam, Dimension resolution) {
            this.webcam = webcam;
            this.resolution = resolution;
        }

        @Override
        public String toString() {
            return String.format("Webcam: %s, Resolution: %dx%d",
                    webcam.getName(), resolution.width, resolution.height);
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
            Button selectBtn = new Button("Select Webcam");
            selectBtn.setOnAction(e -> {
                WebcamSelector selector = new WebcamSelector();
                WebcamSelection selection = selector.selectWebcam();

                if (selection != null) {
                    System.out.println("Selected: " + selection);
                } else {
                    System.out.println("Selection cancelled");
                }
            });

            VBox root = new VBox(20, selectBtn);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(50));

            Scene scene = new Scene(root, 400, 300);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Webcam Selector Test");
            primaryStage.show();
        }
    }
}