package org.breachinthecontainment.launcher_client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import java.util.function.Consumer;

public class UI extends Application {

    private static LauncherLogger logger;

    public static void setLogger(LauncherLogger appLogger) {
        logger = appLogger;
    }

    public static void launchApp() {
        launch();
    }

    @Override
    public void start(Stage stage) {
        // Add application icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icon.png"));
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

        // Create a Quit button
        Button quitBtn = new Button("Quit");
        quitBtn.setStyle("-fx-font-size: 12pt; -fx-padding: 5 10;");
        quitBtn.setOnAction(event -> {
            if (logger != null) {
                logger.log("Quit button clicked. Shutting down application.");
                logger.close(); // Ensure logs are saved
            }
            Platform.exit(); // Exit the JavaFX application
            System.exit(0); // Ensure JVM exits
        });

        // Use BorderPane for layout to place Quit button in top-left
        BorderPane root = new BorderPane();

        // Top-left corner for the Quit button
        HBox topLeftBox = new HBox(quitBtn);
        topLeftBox.setAlignment(Pos.TOP_LEFT);
        topLeftBox.setStyle("-fx-padding: 10;"); // Add some padding

        root.setTop(topLeftBox);

        // Center content (title, subtitle, play button)
        VBox centerLayout = new VBox(15, title, subtitle, playBtn);
        centerLayout.setAlignment(Pos.TOP_CENTER);
        centerLayout.setStyle("-fx-padding: 30;"); // Keep padding for the main content

        root.setCenter(centerLayout);

        Scene scene = new Scene(root, 450, 300);
        stage.setTitle("Modpack Loader");
        stage.setScene(scene);
        stage.show();

        // Ensure logger is closed when the main window is closed
        stage.setOnCloseRequest(event -> {
            if (logger != null) {
                logger.log("Main window closed. Shutting down application.");
                logger.close();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    /**
     * Displays a placeholder error window.
     * @param ownerStage The parent stage.
     */
    private void showPlaceholderErrorWindow(Stage ownerStage) {
        Stage errorWindow = new Stage();
        errorWindow.initModality(Modality.APPLICATION_MODAL);
        errorWindow.initOwner(ownerStage);
        errorWindow.setTitle("A Placeholder Error");

        // Add icon to error window
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icon.png"));
            errorWindow.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load icon for error window: " + e.getMessage());
            if (logger != null) logger.log("Failed to load error window icon: " + e.getMessage());
        }

        Label errorMessage = new Label("This is a placeholder error because nothing has been developed yet!");
        errorMessage.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        errorMessage.setWrapText(true);

        VBox errorLayout = new VBox(10, errorMessage);
        errorLayout.setAlignment(Pos.CENTER);
        errorLayout.setStyle("-fx-padding: 20;");

        Scene errorScene = new Scene(errorLayout, 380, 160);
        errorWindow.setScene(errorScene);
        errorWindow.showAndWait();
    }

    /**
     * Creates and shows a progress window for first-time setup.
     * @param ownerStage The primary stage of the application, which will be hidden while this is shown.
     * @param launcherDirectory The directory path for setup.
     * @param appLogger The logger instance.
     * @return The Stage of the progress window.
     */
    public static Stage showProgressWindow(Stage ownerStage, String launcherDirectory, LauncherLogger appLogger) {
        logger = appLogger; // Ensure logger is set for UI class
        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.initOwner(ownerStage);
        progressStage.setTitle("First Launch Setup");
        progressStage.setResizable(false); // Prevent resizing

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

        VBox layout = new VBox(15, progressLabel, progressBar);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20;");

        Scene scene = new Scene(layout, 350, 150);
        progressStage.setScene(scene);
        progressStage.show();

        // Start the setup task in a background thread
        javafx.concurrent.Task<SetupResult> setupTask = new javafx.concurrent.Task<SetupResult>() {
            @Override
            protected SetupResult call() throws Exception {
                Consumer<String> uiProgressUpdater = message -> Platform.runLater(() -> {
                    progressLabel.setText(message);
                });

                return Installer.setup(launcherDirectory, uiProgressUpdater, logger);
            }
        };

        setupTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                SetupResult result = setupTask.getValue();
                progressStage.close(); // Close progress window regardless of outcome

                if (result == SetupResult.SUCCESS) {
                    appLogger.log("First launch setup completed successfully.");
                    ownerStage.show(); // Show main window
                } else if (result == SetupResult.MISCOUNT_ERROR) {
                    appLogger.log("Miscount error detected after setup.");
                    // Show the miscount error window, passing runnables for button actions
                    showMiscountErrorWindow(ownerStage, appLogger,
                            () -> { // On Exit
                                if (logger != null) {
                                    logger.log("Miscount error: Exit button clicked.");
                                    logger.close();
                                }
                                Platform.exit();
                                System.exit(0);
                            },
                            () -> { // On Launch Anyway
                                if (logger != null) logger.log("Miscount error: Launch Anyway button clicked.");
                                ownerStage.show(); // Show main window
                            }
                    );
                } else { // SetupResult.FAILURE
                    appLogger.log("First launch setup failed.");
                    progressLabel.setText("Setup Failed!");
                    progressBar.setStyle("-fx-accent: red;");
                    progressBar.setProgress(1.0);
                    // Optionally, show a more detailed error dialog or keep the window open for debugging
                    System.err.println("Setup failed for unknown reason or critical error.");
                    // For now, it will just close the progress window and not show the main window.
                    // You might want a separate error dialog here.
                }
            });
        });

        setupTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                String errorMsg = "First launch setup failed due to exception: " + setupTask.getException().getMessage();
                appLogger.log(errorMsg);
                progressLabel.setText("Error: " + errorMsg);
                progressBar.setStyle("-fx-accent: red;");
                progressBar.setProgress(1.0);
                System.err.println(errorMsg);
                // Keep progress window open for user to see error or close it after a delay
                // For now, it will just close and not show the main window.
                progressStage.close();
            });
        });

        new Thread(setupTask).start(); // Start the background thread

        return progressStage;
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
        errorMessage.setAlignment(Pos.CENTER); // Center the text within the label

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

        HBox buttonLayout = new HBox(15, exitButton, launchAnywayButton); // 15px spacing between buttons
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.setStyle("-fx-padding: 10;");

        VBox layout = new VBox(15, errorMessage, buttonLayout);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20;");

        Scene scene = new Scene(layout, 450, 200); // Adjusted size to fit text and buttons
        miscountWindow.setScene(scene);
        miscountWindow.showAndWait();
    }
}
