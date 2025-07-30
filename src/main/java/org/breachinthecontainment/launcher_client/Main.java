package org.breachinthecontainment.launcher_client;

import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application { // Main extends Application

    private static LauncherLogger logger;
    private static String launcherDir;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // This is the first method called on the JavaFX Application Thread.
        // Initialize common resources here.
        launcherDir = PlatformUtil.getLauncherDirectory();
        logger = new LauncherLogger(launcherDir);
        UI.setLogger(logger); // Pass the logger to the UI class

        // Delegate the core application flow logic to the UI class
        // UI will decide whether to show a setup window or the main window.
        UI.startApplicationFlow(primaryStage, launcherDir, logger);
    }

    public static void main(String[] args) {
        launch(args); // This is the standard way to start a JavaFX application.
        // It calls the start() method on the JavaFX Application Thread.
    }
}
