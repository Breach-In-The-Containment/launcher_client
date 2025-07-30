package org.breachinthecontainment.launcher_client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Installer {

    // The name of the data zip file expected inside the application's resources
    private static final String DATA_ZIP_RESOURCE_PATH = "/data.zip"; // Path within the bundled app resources

    /**
     * Checks if the data has already been extracted to the launcher directory.
     * For simplicity, it checks if the launcher directory exists.
     * A more robust check might involve a marker file (e.g., "installation_complete.txt")
     * or checking for the presence of a critical game file within the launcher directory.
     *
     * @param dirPath The path to the launcher's base directory where data should be extracted.
     * @param logger An instance of LauncherLogger for file logging.
     * @return true if data is considered extracted, false otherwise.
     */
    public static boolean isDataExtracted(String dirPath, LauncherLogger logger) {
        Path launcherBasePath = Paths.get(dirPath);
        boolean exists = Files.exists(launcherBasePath);
        logger.log("isDataExtracted: Launcher base directory exists? " + exists);
        // You could add more robust checks here, e.g.:
        // Path markerFile = launcherBasePath.resolve("installation_complete.txt");
        // return Files.exists(markerFile);
        return exists; // Simple check for directory existence
    }

    /**
     * Sets up the launcher environment by extracting the bundled data.zip.
     * This method is called once at first launch or if data is missing.
     *
     * @param outputDir The directory where files should be extracted.
     * @param logger An instance of LauncherLogger for file logging.
     * @return true if setup is successful, false otherwise.
     */
    public static boolean setup(String outputDir, LauncherLogger logger) {
        logger.log("Starting local setup process in directory: " + outputDir);

        Path launcherDir = Paths.get(outputDir);

        // If the directory already exists and data is considered extracted, skip extraction
        if (isDataExtracted(outputDir, logger)) {
            logger.log("Data already extracted to " + outputDir + ". Skipping extraction.");
            return true;
        }

        // Create the main launcher directory if it doesn't exist
        try {
            if (!Files.exists(launcherDir)) {
                logger.log("Creating launcher directory: " + launcherDir.toAbsolutePath());
                Files.createDirectories(launcherDir);
                logger.log("Directory created successfully.");
            } else {
                logger.log("Launcher directory already exists: " + launcherDir.toAbsolutePath());
            }

            // Extract the bundled data.zip from the application's resources
            logger.log("Attempting to extract bundled data.zip from application resources.");
            extractZipFromResources(DATA_ZIP_RESOURCE_PATH, outputDir, logger);
            File extractedCheck = Paths.get(outputDir, "*.*").toFile();
            if (extractedCheck.exists()) {
                logger.log("Extraction verified. Found: " + extractedCheck.getAbsolutePath());
            } else {
                logger.log("Extraction MAY have failed — expected file not found.");
            }
            return true;

        } catch (IOException e) {
            String errorMessage = "Failed to extract bundled data.zip: " + e.getMessage();
            System.err.println(errorMessage);
            logger.log(errorMessage);
            return false;
        }
    }

    /**
     * Extracts a zip file from the application's resources to a specified output directory.
     *
     * @param resourcePath The path to the zip file resource within the application's bundle (e.g., "/data.zip").
     * @param outputDir The directory where the contents of the zip should be extracted.
     * @param logger An instance of LauncherLogger for file logging.
     * @throws IOException If an I/O error occurs during extraction.
     */
    private static void extractZipFromResources(String resourcePath, String outputDir, LauncherLogger logger) throws IOException {
        logger.log("Attempting to load resource: " + resourcePath);

        InputStream stream = Installer.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            logger.log("getResourceAsStream failed. Trying with ClassLoader...");
            stream = Installer.class.getClassLoader().getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        }

        if (stream == null) {
            String error = "Resource not found: " + resourcePath + ". Ensure data.zip is inside src/main/resources.";
            logger.log(error);
            throw new IOException(error);
        }

        try (ZipInputStream zis = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newFilePath = Paths.get(outputDir, entry.getName());
                logger.log("Extracting: " + entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(newFilePath);
                    logger.log("Created directory: " + newFilePath.toAbsolutePath());
                } else {
                    Path parent = newFilePath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                        logger.log("Created parent dir: " + parent.toAbsolutePath());
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFilePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }

                    logger.log("Extracted file: " + newFilePath.toAbsolutePath());
                }

                zis.closeEntry();
            }
        }
    }


    /**
     * Deletes a directory and all its contents recursively.
     * This is a utility method that might be useful for cleanup, though not directly
     * used in the simplified jpackage setup for initial extraction.
     *
     * @param directory The path to the directory to delete.
     * @param logger An instance of LauncherLogger for file logging.
     * @throws IOException If an I/O error occurs during deletion.
     */
    private static void deleteDirectory(Path directory, LauncherLogger logger) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.log("Deleted: " + path.toAbsolutePath());
                        } catch (IOException e) {
                            logger.log("Failed to delete " + path.toAbsolutePath() + ": " + e.getMessage());
                            System.err.println("Failed to delete " + path.toAbsolutePath() + ": " + e.getMessage());
                        }
                    });
        }
    }
}
