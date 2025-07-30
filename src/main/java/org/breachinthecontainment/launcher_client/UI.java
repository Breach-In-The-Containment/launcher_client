package org.breachinthecontainment.launcher_client;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class UI { // Does not extend Application

    private static LauncherLogger logger;

    public static void setLogger(LauncherLogger appLogger) {
        logger = appLogger;
    }

    /**
     * Entry point for the UI application flow.
     * This method is called from Main.java after JavaFX is initialized.
     * It initiates the local setup (data.zip extraction) or proceeds to the main window.
     * @param primaryStage The primary stage provided by JavaFX.
     * @param launcherDirectory The application's installation directory.
     * @param appLogger The logger instance.
     */
    public static void startApplicationFlow(Stage primaryStage, String launcherDirectory, LauncherLogger appLogger) {
        logger = appLogger;

        // Perform the setup (extraction) directly
        logger.log("UI.startApplicationFlow: Initiating local setup (data.zip extraction).");
        boolean setupSuccess = Installer.setup(launcherDirectory, logger); // Installer.setup now returns boolean

        if (setupSuccess) {
            logger.log("UI.startApplicationFlow: Local setup completed successfully. Displaying main window.");
            showMainWindow(primaryStage);
        } else {
            logger.log("UI.startApplicationFlow: Local setup failed. Showing error and exiting.");
            showSimpleAlertDialog("Setup Failed", "Failed to prepare game data. Please check logs for details.", appLogger);
            if (logger != null) logger.close();
            Platform.exit();
            System.exit(0);
        }
    }

    /**
     * Initializes and displays the main application window.
     * @param stage The primary stage to configure and show.
     */
    public static void showMainWindow(Stage stage) {
        // Set the common close request handler for the primary stage
        stage.setOnCloseRequest(event -> {
            if (logger != null) {
                logger.log("Main application window closed via X button. Shutting down.");
                logger.close();
            }
            Platform.exit();
            System.exit(0);
        });

        // Add application icon
        try {
            Image icon = new Image(UI.class.getResourceAsStream("/icon.png"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + e.getMessage());
            if (logger != null) logger.log("Failed to load main window icon: " + e.getMessage());
        }

        Label title = new Label("BREACH IN THE CONTAINMENT");
        title.setFont(Font.font("Arial", 24));
        Label subtitle = new Label("Launcher [CLIENT]");
        subtitle.setFont(Font.font("Arial", 18));

        Button playBtn = new Button("Play!");
        playBtn.setStyle("-fx-font-size: 16pt");

        playBtn.setOnAction(event -> {
            if (logger != null) logger.log("Play button clicked. Showing placeholder error.");
            showPlaceholderErrorWindow(stage);
        });

        Button quitBtn = new Button("Quit");
        quitBtn.setStyle("-fx-font-size: 12pt; -fx-padding: 5 10;");
        quitBtn.setOnAction(event -> {
            if (logger != null) {
                logger.log("Quit button clicked. Shutting down application.");
                logger.close();
            }
            Platform.exit();
            System.exit(0);
        });

        BorderPane root = new BorderPane();
        HBox topLeftBox = new HBox(quitBtn);
        topLeftBox.setAlignment(Pos.TOP_LEFT);
        topLeftBox.setStyle("-fx-padding: 10;");
        root.setTop(topLeftBox);

        VBox centerLayout = new VBox(15, title, subtitle, playBtn);
        centerLayout.setAlignment(Pos.TOP_CENTER);
        centerLayout.setStyle("-fx-padding: 30;");
        root.setCenter(centerLayout);

        Scene scene = new Scene(root, 450, 300);
        stage.setTitle("Espresso Loader");
        stage.setScene(scene);
        stage.show();
        logger.log("Main application window shown.");
    }

    /**
     * Displays a simple alert dialog.
     * @param title The title of the alert.
     * @param message The message to display.
     * @param appLogger The logger instance.
     */
    private static void showSimpleAlertDialog(String title, String message, LauncherLogger appLogger) {
        Stage alertStage = new Stage();
        alertStage.initModality(Modality.APPLICATION_MODAL);
        alertStage.setTitle(title);
        alertStage.setResizable(false);
        try {
            Image icon = new Image(UI.class.getResourceAsStream("/icon.png"));
            alertStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon for alert window: " + e.getMessage());
            if (appLogger != null) appLogger.log("Failed to load alert window icon: " + e.getMessage());
        }

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setAlignment(Pos.CENTER);
        msgLabel.setStyle("-fx-padding: 10;");

        Button okButton = new Button("OK");
        okButton.setOnAction(e -> alertStage.close());

        VBox layout = new VBox(15, msgLabel, okButton);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20;");
        Scene scene = new Scene(layout, 400, 180);
        alertStage.setScene(scene);
        alertStage.showAndWait();
    }

    /**
     * Displays a placeholder error window.
     * @param ownerStage The parent stage.
     */
    private static void showPlaceholderErrorWindow(Stage ownerStage) {
        Stage errorWindow = new Stage();
        errorWindow.initModality(Modality.APPLICATION_MODAL);
        errorWindow.initOwner(ownerStage);
        errorWindow.setTitle("A Placeholder Error");

        try {
            Image icon = new Image(UI.class.getResourceAsStream("/icon.png"));
            errorWindow.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon for error window: " + e.getMessage());
            if (logger != null) logger.log("Failed to load error window icon: " + e.getMessage());
        }

        Label errorMessage = new Label("This is a placeholder error because nothing has been developed yet!");
        errorMessage.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        errorMessage.setWrapText(true);
        errorMessage.setAlignment(Pos.CENTER);

        VBox errorLayout = new VBox(10, errorMessage);
        errorLayout.setAlignment(Pos.CENTER);
        errorLayout.setStyle("-fx-padding: 20;");

        Scene errorScene = new Scene(errorLayout, 380, 160);
        errorWindow.setScene(errorScene);
        errorWindow.showAndWait();
    }
}
