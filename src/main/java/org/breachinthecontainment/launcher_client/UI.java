package org.breachinthecontainment.launcher_client;

// Removed import javafx.application.Application; as UI no longer extends it
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class UI { // No longer extends Application

    private static LauncherLogger logger;

    public static void setLogger(LauncherLogger appLogger) {
        logger = appLogger;
    }

    // The start(Stage primaryStage) method is removed from UI.java
    // as Main.java now handles the Application lifecycle.

    /**
     * Entry point for the UI application flow, determining whether to show setup or main window.
     * This method is called from Main.java after JavaFX is initialized.
     * @param primaryStage The primary stage provided by JavaFX.
     * @param launcherDirectory The application's installation directory.
     * @param appLogger The logger instance.
     */
    public static void startApplicationFlow(Stage primaryStage, String launcherDirectory, LauncherLogger appLogger) {
        logger = appLogger;

        if (Installer.isFirstLaunch(launcherDirectory, logger)) {
            logger.log("UI.startApplicationFlow: First launch detected. Initiating setup flow.");
            startSetupFlow(primaryStage, launcherDirectory, logger);
        } else {
            logger.log("UI.startApplicationFlow: Not first launch. Displaying main window directly.");
            showMainWindow(primaryStage);
        }
    }

    /**
     * Initializes and displays the main application window.
     * This method is called directly if not a first launch, or after setup completes.
     * @param stage The primary stage to configure and show.
     */
    public static void showMainWindow(Stage stage) {
        // Set the common close request handler for the primary stage here,
        // as this is where the primary stage is actually shown.
        stage.setOnCloseRequest(event -> {
            if (logger != null) {
                logger.log("Main application window closed via X button. Shutting down.");
                logger.close(); // Ensure logs are saved
            }
            Platform.exit(); // Exit the JavaFX application
            System.exit(0); // Ensure JVM exits
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
        stage.setTitle("Modpack Loader");
        stage.setScene(scene);
        stage.show();
        logger.log("Main application window shown.");
    }

    /**
     * Initiates the first-launch setup flow, showing a progress window first.
     * @param ownerStage The primary stage of the application, which will be hidden temporarily.
     * @param launcherDirectory The directory path for setup.
     * @param appLogger The logger instance.
     */
    public static void startSetupFlow(Stage ownerStage, String launcherDirectory, LauncherLogger appLogger) {
        ownerStage.hide();

        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.initOwner(ownerStage);
        progressStage.setTitle("First Launch Setup");
        progressStage.setResizable(false);

        try {
            Image icon = new Image(UI.class.getResourceAsStream("/icon.png"));
            progressStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon for progress window: " + e.getMessage());
            if (logger != null) logger.log("Failed to load progress window icon: " + e.getMessage());
        }

        Label progressLabel = new Label("Initializing...");
        progressLabel.setFont(Font.font("Arial", 14));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        DoubleProperty progressProperty = new SimpleDoubleProperty(0.0);
        progressBar.progressProperty().bind(progressProperty);

        VBox layout = new VBox(15, progressLabel, progressBar);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20;");

        Scene scene = new Scene(layout, 350, 150);
        progressStage.setScene(scene);
        progressStage.show();

        javafx.concurrent.Task<Installer.SetupResult> setupTask = new javafx.concurrent.Task<Installer.SetupResult>() {
            @Override
            protected Installer.SetupResult call() throws Exception {
                Consumer<String> textProgressUpdater = message -> Platform.runLater(() -> {
                    progressLabel.setText(message);
                });

                Consumer<Double> percentageProgressUpdater = percentage -> Platform.runLater(() -> {
                    if (percentage >= 0.0 && percentage <= 1.0) {
                        progressProperty.set(percentage);
                    } else {
                        progressProperty.set(-1.0);
                    }
                });

                return Installer.setup(launcherDirectory, textProgressUpdater, percentageProgressUpdater, appLogger);
            }
        };

        setupTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                Installer.SetupResult result = setupTask.getValue();
                progressStage.close();

                if (result == Installer.SetupResult.SUCCESS) {
                    appLogger.log("First launch setup completed successfully.");
                    showMainWindow(ownerStage);
                } else if (result == Installer.SetupResult.MISCOUNT_ERROR) {
                    appLogger.log("Miscount error detected after setup.");
                    showMiscountErrorWindow(ownerStage, appLogger,
                            () -> {
                                if (logger != null) {
                                    logger.log("Miscount error: Exit button clicked.");
                                    logger.close();
                                }
                                Platform.exit();
                                System.exit(0);
                            },
                            () -> {
                                if (logger != null) logger.log("Miscount error: Launch Anyway button clicked.");
                                showMainWindow(ownerStage);
                            }
                    );
                } else {
                    appLogger.log("First launch setup failed.");
                    showSimpleAlertDialog("Setup Failed", "The initial setup encountered an error. Please check the logs for more details.", appLogger);
                    if (logger != null) logger.close();
                    Platform.exit();
                    System.exit(0);
                }
            });
        });

        setupTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                String errorMsg = "First launch setup failed due to unexpected exception: " + setupTask.getException().getMessage();
                appLogger.log(errorMsg);
                progressStage.close();
                showSimpleAlertDialog("Setup Error", "An unexpected error occurred during setup: " + setupTask.getException().getMessage() + ". Please check the logs.", appLogger);
                if (logger != null) logger.close();
                Platform.exit();
                System.exit(0);
            });
        });

        new Thread(setupTask).start();
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

    /**
     * Displays a miscount error window with "Exit" and "Launch anyway" options.
     * @param ownerStage The parent stage (main launcher window).
     * @param logger The logger instance.
     * @param onExit Runnable to execute when "Exit" is clicked.
     * @param onLaunchAnyway Runnable to execute when "Launch anyway" is clicked.
     */
    public static void showMiscountErrorWindow(Stage ownerStage, LauncherLogger logger, Runnable onExit, Runnable onLaunchAnyway) {
        Stage miscountWindow = new Stage();
        miscountWindow.initModality(Modality.APPLICATION_MODAL);
        miscountWindow.initOwner(ownerStage);
        miscountWindow.setTitle("Miscount error");
        miscountWindow.setResizable(false);

        try {
            Image icon = new Image(UI.class.getResourceAsStream("/icon.png"));
            miscountWindow.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon for miscount window: " + e.getMessage());
            if (logger != null) logger.log("Failed to load miscount window icon: " + e.getMessage());
        }

        Label errorMessage = new Label("Uh oh! We found a miscount error with the release and actual number of mods + configs.\nExit or Launch anyway?");
        errorMessage.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        errorMessage.setWrapText(true);
        errorMessage.setAlignment(Pos.CENTER);

        Button exitButton = new Button("Exit");
        exitButton.setStyle("-fx-font-size: 14pt; -fx-padding: 8 20;");
        exitButton.setOnAction(e -> {
            miscountWindow.close();
            onExit.run();
        });

        Button launchAnywayButton = new Button("Launch anyway");
        launchAnywayButton.setStyle("-fx-font-size: 14pt; -fx-padding: 8 20;");
        launchAnywayButton.setOnAction(e -> {
            miscountWindow.close();
            onLaunchAnyway.run();
        });

        HBox buttonLayout = new HBox(15, exitButton, launchAnywayButton);
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.setStyle("-fx-padding: 10;");

        VBox layout = new VBox(15, errorMessage, buttonLayout);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20;");

        Scene scene = new Scene(layout, 450, 200);
        miscountWindow.setScene(scene);
        miscountWindow.showAndWait();
    }
}
