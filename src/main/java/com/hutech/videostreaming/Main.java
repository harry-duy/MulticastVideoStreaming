package com.hutech.videostreaming;

import com.hutech.videostreaming.gui.ClientGUI;
import com.hutech.videostreaming.gui.ServerGUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

/**
 * Main Launcher Application
 * Cho phép người dùng chọn chạy Server hoặc Client
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);");

        // ===== Logo/Icon Area =====
        Label icon = new Label("🎬");
        icon.setFont(Font.font("Segoe UI Emoji", FontWeight.NORMAL, 80));
        icon.setTextFill(Color.WHITE);

        // ===== Title =====
        Label title = new Label("MULTICAST VIDEO STREAMING");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 32));
        title.setTextFill(Color.WHITE);
        title.setTextAlignment(TextAlignment.CENTER);

        // ===== Subtitle =====
        Label subtitle = new Label("HUTECH University - Java Network Programming Project");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setTextFill(Color.web("#e0e0e0"));
        subtitle.setTextAlignment(TextAlignment.CENTER);

        // ===== Divider =====
        VBox divider = new VBox(5);
        divider.setAlignment(Pos.CENTER);
        divider.setMaxWidth(400);

        Label dividerLine = new Label("━━━━━━━━━━━━━━━━━━━━━━");
        dividerLine.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        dividerLine.setTextFill(Color.web("#e0e0e0", 0.5));

        Label selectLabel = new Label("Select Mode");
        selectLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        selectLabel.setTextFill(Color.web("#e0e0e0"));

        divider.getChildren().addAll(dividerLine, selectLabel);

        // ===== Server Button =====
        Button serverBtn = createLauncherButton("🎬 Launch Server", "#10b981",
                "Start streaming video to multiple clients");
        serverBtn.setOnAction(e -> {
            launchServer();
            primaryStage.close();
        });

        // ===== Client Button =====
        Button clientBtn = createLauncherButton("📺 Launch Client", "#3b82f6",
                "Connect and receive video stream");
        clientBtn.setOnAction(e -> {
            launchClient();
            primaryStage.close();
        });

        // ===== Exit Button =====
        Button exitBtn = createLauncherButton("❌ Exit", "#ef4444",
                "Close the application");
        exitBtn.setOnAction(e -> {
            Platform.exit();
            System.exit(0);
        });

        // ===== Info Label =====
        Label infoLabel = new Label("Version 1.0 | Created by [Your Name]");
        infoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        infoLabel.setTextFill(Color.web("#e0e0e0", 0.7));
        infoLabel.setTextAlignment(TextAlignment.CENTER);

        // Add all components
        root.getChildren().addAll(
                icon,
                title,
                subtitle,
                divider,
                serverBtn,
                clientBtn,
                exitBtn,
                infoLabel
        );

        // Apply fade-in animation
        applyFadeInAnimation(root);

        // Scene setup
        Scene scene = new Scene(root, 650, 700);
        scene.setFill(Color.TRANSPARENT);

        primaryStage.setTitle("Multicast Video Streaming - Launcher");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Tạo button có style đẹp với tooltip
     */
    private Button createLauncherButton(String text, String color, String description) {
        VBox buttonContainer = new VBox(5);
        buttonContainer.setAlignment(Pos.CENTER);

        Button button = new Button(text);
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        button.setTextFill(Color.WHITE);
        button.setPrefWidth(400);
        button.setPrefHeight(60);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 5);"
        );

        // Description label
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        descLabel.setTextFill(Color.web("#e0e0e0", 0.8));
        descLabel.setVisible(false);

        // Hover effects
        button.setOnMouseEntered(e -> {
            button.setStyle(
                    "-fx-background-color: derive(" + color + ", -15%);" +
                            "-fx-background-radius: 12;" +
                            "-fx-cursor: hand;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15, 0, 0, 8);"
            );

            // Scale animation
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), button);
            scaleTransition.setToX(1.05);
            scaleTransition.setToY(1.05);
            scaleTransition.play();

            descLabel.setVisible(true);
        });

        button.setOnMouseExited(e -> {
            button.setStyle(
                    "-fx-background-color: " + color + ";" +
                            "-fx-background-radius: 12;" +
                            "-fx-cursor: hand;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 5);"
            );

            // Scale back
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), button);
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();

            descLabel.setVisible(false);
        });

        // Click animation
        button.setOnMousePressed(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(100), button);
            scaleTransition.setToX(0.95);
            scaleTransition.setToY(0.95);
            scaleTransition.play();
        });

        button.setOnMouseReleased(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(100), button);
            scaleTransition.setToX(1.05);
            scaleTransition.setToY(1.05);
            scaleTransition.play();
        });

        return button;
    }

    /**
     * Khởi chạy Server GUI trong thread riêng
     */
    private void launchServer() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        Stage serverStage = new Stage();
                        ServerGUI serverGUI = new ServerGUI();
                        serverGUI.start(serverStage);
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("Failed to launch Server: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Khởi chạy Client GUI trong thread riêng
     */
    private void launchClient() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        Stage clientStage = new Stage();
                        ClientGUI clientGUI = new ClientGUI();
                        clientGUI.start(clientStage);
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("Failed to launch Client: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Áp dụng hiệu ứng fade-in cho giao diện
     */
    private void applyFadeInAnimation(VBox root) {
        root.setOpacity(0);
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(800), root);
        fadeTransition.setFromValue(0);
        fadeTransition.setToValue(1);
        fadeTransition.play();
    }

    /**
     * Hiển thị thông báo lỗi
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle("Error");
            alert.setHeaderText("Launch Failed");
            alert.setContentText(message);

            // Style the alert
            javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setStyle("-fx-background-color: #262637;");
            dialogPane.lookup(".content.label").setStyle("-fx-text-fill: #e0e0e0;");

            alert.showAndWait();
        });
    }

    /**
     * Entry point của ứng dụng
     */
    public static void main(String[] args) {
        // Set system properties cho JavaFX
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // Launch JavaFX application
        launch(args);
    }
}