package org.breachinthecontainment.launcher_client;

import javafx.application.Platform;
import javafx.stage.Stage;

public class Main {
    private static LauncherLogger logger;

    public static void main(String[] args) {
        String launcherDir = PlatformUtil.getLauncherDirectory();

        logger = new LauncherLogger(launcherDir);
        UI.setLogger(logger);

        logger.log("Launcher application starting.");
        logger.log("Launcher directory: " + launcherDir);

        // Check if it's the first launch or if a full setup is needed based on local release info
        boolean needsInitialSetup = Installer.isFirstLaunch(launcherDir, logger);
        logger.log("Initial setup needed? " + needsInitialSetup);

        if (needsInitialSetup) {
            System.out.println("Initial setup detected. Starting setup...");
            Platform.startup(() -> {
                Stage primaryStage = new Stage();
                primaryStage.hide(); // Keep it hidden until setup is complete

                UI.showProgressWindow(primaryStage, launcherDir, logger);
            });
        } else {
            System.out.println("Required files and release info found. Launching application directly.");
            logger.log("Required files and release info found. Launching main UI.");
            UI.launchApp();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (logger != null) {
                logger.log("Application shutting down via shutdown hook.");
                logger.close();
            }
        }));
    }
}
