// LaunchLogger.java

package org.breachinthecontainment.launcher_client;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A simple logging utility for the launcher.
 * Writes timestamped messages to a log file within the launcher's logs directory.
 */
public class LauncherLogger {

    private final File logFile;
    private PrintWriter writer;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initializes the logger.
     * Creates the logs directory and opens the log file for appending.
     *
     * @param launcherDirectory The base directory of the launcher.
     */
    public LauncherLogger(String launcherDirectory) {
        // Define the path for the logs directory
        File logsDir = Paths.get(launcherDirectory, "logs").toFile();
        if (!logsDir.exists()) {
            // Create the logs directory if it doesn't exist
            if (logsDir.mkdirs()) {
                System.out.println("Created logs directory: " + logsDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create logs directory: " + logsDir.getAbsolutePath());
            }
        }

        // Define the path for the log file
        this.logFile = Paths.get(logsDir.getAbsolutePath(), "launcher.log").toFile();

        try {
            // Initialize a PrintWriter to write to the log file, appending to it if it exists
            this.writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
            log("LauncherLogger initialized."); // Log initialization
        } catch (IOException e) {
            System.err.println("Failed to initialize LauncherLogger: " + e.getMessage());
            e.printStackTrace();
            // If logger cannot be initialized, further log calls will silently fail or print to console
        }
    }

    /**
     * Logs a message with a timestamp to the log file.
     *
     * @param message The message to log.
     */
    public void log(String message) {
        if (writer != null) {
            String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String logEntry = String.format("[%s] %s", timestamp, message);
            writer.println(logEntry);
            writer.flush(); // Flush immediately to ensure messages are written to disk
            System.out.println(logEntry); // Also print to console for immediate feedback
        } else {
            System.err.println("Logger not initialized. Could not log: " + message);
        }
    }

    /**
     * Closes the log file writer.
     * This should be called when the application is shutting down to ensure all logs are saved.
     */
    public void close() {
        if (writer != null) {
            log("LauncherLogger closing."); // Log closing
            writer.close();
            System.out.println("LauncherLogger closed.");
        }
    }
}
