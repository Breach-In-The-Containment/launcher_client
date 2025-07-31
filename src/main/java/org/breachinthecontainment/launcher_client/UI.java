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

import java.io.InputStream;

public class UI {

    private static LauncherLogger logger;
    private static boolean isSignedIn = false;

    public static void setLogger(LauncherLogger appLogger) {
        logger = appLogger;
    }

    public static void startApplicationFlow(Stage primaryStage, String launcherDirectory, LauncherLogger appLogger) {
        logger = appLogger;

        try (InputStream dataZipStream = UI.class.getResourceAsStream("/data.zip")) {
            if (dataZipStream == null) {
                logger.log("Failed to find data.zip resource.");
                showSimpleAlertDialog("Setup Failed", "data.zip resource not found.", logger);
                cleanExit();
                return;
            }

            String checksum = SumChecker.calculateSHA256(dataZipStream);
            logger.log("Calculated checksum for embedded data.zip: " + checksum);

            if (!checksum.equalsIgnoreCase(SumChecker.EXPECTED_CHECKSUM)) {
                logger.log("Checksum mismatch! Data integrity verification failed.");
                showSimpleAlertDialog("Setup Failed", "Embedded data.zip checksum mismatch. Setup aborted.", logger);
                cleanExit();
                return;
            }

            boolean setupSuccess = Installer.setup(launcherDirectory, logger);
            if (setupSuccess) {
                showMainWindow(primaryStage);
            } else {
                showSimpleAlertDialog("Setup Failed", "Failed to prepare game data. Please check logs for details.", logger);
                cleanExit();
            }
        } catch (Exception e) {
            logger.log("Exception during checksum verification: " + e.getMessage());
            e.printStackTrace();
            showSimpleAlertDialog("Setup Failed", "Error verifying data.zip checksum: " + e.getMessage(), logger);
            cleanExit();
        }
    }

    private static void cleanExit() {
        if (logger != null) logger.close();
        Platform.exit();
        System.exit(0);
    }

    public static void showMainWindow(Stage stage) {
        stage.setOnCloseRequest(event -> cleanExit());

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

        Button signInBtn = new Button("Sign in with Microsoft");
        signInBtn.setStyle("-fx-font-size: 12pt;");
        signInBtn.setOnAction(event -> {
            if (logger != null) logger.log("Sign In button clicked.");
            boolean result = MicrosoftAuth.signIn();
            isSignedIn = result;
            showSimpleAlertDialog("Microsoft Sign-In", isSignedIn
                    ? "You are connected to Microsoft!"
                    : "You are not connected to Microsoft.", logger);
        });

        playBtn.setOnAction(event -> {
            if (!isSignedIn) {
                showSimpleAlertDialog("Microsoft Account Error",
                        "You need to connect to your Microsoft account to verify that you actually have Minecraft.\n\n" +
                                "Please press the \"Sign In\" button and sign into your Microsoft account.\n\n" +
                                "WARNING: Your data is not logged. It's only to prevent illegal copies of Minecraft Java Edition 1.12.2.",
                        logger);
            } else {
                showPlaceholderErrorWindow(stage);
            }
        });

        Button quitBtn = new Button("Quit");
        quitBtn.setStyle("-fx-font-size: 12pt; -fx-padding: 5 10;");
        quitBtn.setOnAction(event -> cleanExit());

        BorderPane root = new BorderPane();
        HBox topLeftBox = new HBox(quitBtn);
        topLeftBox.setAlignment(Pos.TOP_LEFT);
        topLeftBox.setStyle("-fx-padding: 10;");
        root.setTop(topLeftBox);

        VBox centerLayout = new VBox(15, title, subtitle, playBtn, signInBtn);
        centerLayout.setAlignment(Pos.TOP_CENTER);
        centerLayout.setStyle("-fx-padding: 30;");
        root.setCenter(centerLayout);

        Scene scene = new Scene(root, 450, 300);
        stage.setTitle("Espresso Loader");
        stage.setScene(scene);
        stage.show();

        logger.log("Main application window shown.");

        Theme.Mode mode = Theme.detectSystemTheme();
        String stylesheet = switch (mode) {
            case DARK -> "/styles/dark.css";
            case LIGHT -> "/styles/light.css";
        };
        scene.getStylesheets().add(UI.class.getResource(stylesheet).toExternalForm());
    }

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
        Scene scene = new Scene(layout, 400, 200);
        alertStage.setScene(scene);
        alertStage.showAndWait();
    }

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
