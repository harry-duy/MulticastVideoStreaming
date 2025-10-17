package com.hutech.videostreaming.common;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

/**
 * Hiển thị notifications đẹp mắt kiểu toast
 */
public class NotificationManager {

    public enum NotificationType {
        SUCCESS("#10b981", "✅"),
        ERROR("#ef4444", "❌"),
        WARNING("#f59e0b", "⚠️"),
        INFO("#3b82f6", "ℹ️");

        public final String color;
        public final String icon;

        NotificationType(String color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }

    /**
     * Hiển thị notification
     */
    public static void show(String message, NotificationType type, Stage owner) {
        Stage toast = new Stage();
        toast.initOwner(owner);
        toast.initStyle(StageStyle.TRANSPARENT);
        toast.setAlwaysOnTop(true);

        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new javafx.geometry.Insets(15, 25, 15, 25));
        container.setStyle(
                "-fx-background-color: " + type.color + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 15, 0, 0, 5);"
        );

        Label icon = new Label(type.icon);
        icon.setFont(Font.font(24));

        Label text = new Label(message);
        text.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        text.setTextFill(Color.WHITE);
        text.setWrapText(true);
        text.setMaxWidth(300);

        container.getChildren().addAll(icon, text);

        Scene scene = new Scene(container);
        scene.setFill(Color.TRANSPARENT);
        toast.setScene(scene);

        // Position at top right
        if (owner != null) {
            toast.setX(owner.getX() + owner.getWidth() - 400);
            toast.setY(owner.getY() + 80);
        }

        // Animation
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), container);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.seconds(3));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> toast.close());

        SequentialTransition sequence = new SequentialTransition(fadeIn, pause, fadeOut);

        toast.show();
        sequence.play();
    }

    // Convenience methods
    public static void success(String message, Stage owner) {
        show(message, NotificationType.SUCCESS, owner);
    }

    public static void error(String message, Stage owner) {
        show(message, NotificationType.ERROR, owner);
    }

    public static void warning(String message, Stage owner) {
        show(message, NotificationType.WARNING, owner);
    }

    public static void info(String message, Stage owner) {
        show(message, NotificationType.INFO, owner);
    }
}