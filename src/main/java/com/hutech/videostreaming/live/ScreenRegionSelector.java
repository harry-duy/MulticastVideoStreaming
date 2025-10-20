package com.hutech.videostreaming.live;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;

/**
 * Screen Region Selector
 * Cho phép người dùng chọn vùng màn hình để capture
 */
public class ScreenRegionSelector {

    private Rectangle selectedRegion;
    private CountDownLatch latch;

    /**
     * Show selector and wait for user to select region
     */
    public Rectangle selectRegion() {
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
            latch.await(); // Wait for selection
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return selectedRegion;
    }

    /**
     * Show the selector overlay
     */
    private void showSelector() throws Exception {
        // Capture screenshot
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Robot robot = new Robot();
        BufferedImage screenshot = robot.createScreenCapture(
                new Rectangle(screenSize)
        );

        // Create stage
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setFullScreen(true);
        stage.setAlwaysOnTop(true);

        // Create canvas
        Canvas canvas = new Canvas(screenSize.width, screenSize.height);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Draw screenshot as background
        javafx.scene.image.WritableImage fxImage =
                javafx.embed.swing.SwingFXUtils.toFXImage(screenshot, null);
        gc.drawImage(fxImage, 0, 0);

        // Darken overlay
        gc.setGlobalAlpha(0.5);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, screenSize.width, screenSize.height);
        gc.setGlobalAlpha(1.0);

        // Info label
        Label infoLabel = new Label(
                "🖱️ Click and drag to select region\n" +
                        "Right-click or ESC to cancel"
        );
        infoLabel.setTextFill(Color.WHITE);
        infoLabel.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: rgba(0,0,0,0.7); " +
                        "-fx-padding: 20;"
        );

        StackPane root = new StackPane(canvas, infoLabel);
        StackPane.setAlignment(infoLabel, Pos.TOP_CENTER);
        StackPane.setMargin(infoLabel, new javafx.geometry.Insets(50, 0, 0, 0));

        // Selection variables
        final double[] startX = {0};
        final double[] startY = {0};
        final boolean[] isDragging = {false};

        // Mouse handlers
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                startX[0] = e.getX();
                startY[0] = e.getY();
                isDragging[0] = true;
            } else if (e.getButton() == MouseButton.SECONDARY) {
                // Cancel selection
                selectedRegion = null;
                stage.close();
                latch.countDown();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (isDragging[0]) {
                // Redraw background
                gc.clearRect(0, 0, screenSize.width, screenSize.height);
                gc.drawImage(fxImage, 0, 0);
                gc.setGlobalAlpha(0.5);
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, screenSize.width, screenSize.height);
                gc.setGlobalAlpha(1.0);

                // Draw selection rectangle
                double x = Math.min(startX[0], e.getX());
                double y = Math.min(startY[0], e.getY());
                double width = Math.abs(e.getX() - startX[0]);
                double height = Math.abs(e.getY() - startY[0]);

                // Clear selected area (remove darkening)
                gc.clearRect(x, y, width, height);
                gc.drawImage(fxImage, x, y, width, height, x, y, width, height);

                // Draw border
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(3);
                gc.strokeRect(x, y, width, height);

                // Draw dimensions
                String dimensions = String.format("%.0f x %.0f", width, height);
                gc.setFill(Color.WHITE);
                gc.fillText(dimensions, x + 10, y + 25);

                // Draw corner handles
                drawHandle(gc, x, y);                          // Top-left
                drawHandle(gc, x + width, y);                  // Top-right
                drawHandle(gc, x, y + height);                 // Bottom-left
                drawHandle(gc, x + width, y + height);         // Bottom-right
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (isDragging[0] && e.getButton() == MouseButton.PRIMARY) {
                double x = Math.min(startX[0], e.getX());
                double y = Math.min(startY[0], e.getY());
                double width = Math.abs(e.getX() - startX[0]);
                double height = Math.abs(e.getY() - startY[0]);

                if (width > 20 && height > 20) {
                    selectedRegion = new Rectangle(
                            (int) x, (int) y, (int) width, (int) height
                    );

                    System.out.println("✅ [SELECTOR] Region selected: " +
                            selectedRegion.width + "x" + selectedRegion.height +
                            " at (" + selectedRegion.x + "," + selectedRegion.y + ")");
                } else {
                    selectedRegion = null;
                    System.out.println("❌ [SELECTOR] Region too small");
                }

                stage.close();
                latch.countDown();
            }
        });

        // ESC to cancel
        Scene scene = new Scene(root, screenSize.width, screenSize.height);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                selectedRegion = null;
                stage.close();
                latch.countDown();
            }
        });

        stage.setScene(scene);
        stage.show();
    }

    /**
     * Draw corner handle
     */
    private void drawHandle(GraphicsContext gc, double x, double y) {
        double size = 8;
        gc.setFill(Color.CYAN);
        gc.fillRect(x - size/2, y - size/2, size, size);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(x - size/2, y - size/2, size, size);
    }

    /**
     * Quick test method
     */
    public static void main(String[] args) {
        javafx.application.Application.launch(TestApp.class, args);
    }

    /**
     * Test application
     */
    public static class TestApp extends javafx.application.Application {
        @Override
        public void start(javafx.stage.Stage primaryStage) {
            ScreenRegionSelector selector = new ScreenRegionSelector();

            javafx.scene.control.Button selectBtn =
                    new javafx.scene.control.Button("Select Region");
            selectBtn.setOnAction(e -> {
                Rectangle region = selector.selectRegion();
                if (region != null) {
                    System.out.println("Selected: " + region);
                } else {
                    System.out.println("Selection cancelled");
                }
            });

            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(20, selectBtn);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new javafx.geometry.Insets(50));

            Scene scene = new Scene(root, 400, 300);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Region Selector Test");
            primaryStage.show();
        }
    }
}