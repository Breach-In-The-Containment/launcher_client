package org.breachinthecontainment.launcher_client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream; // Import for Stream
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.breachinthecontainment.launcher_client.GitHubReleaseChecker.ReleaseInfo;
import org.breachinthecontainment.launcher_client.GitHubReleaseChecker.TreeParseResult; // Import TreeParseResult

public class Installer {

    /**
     * Checks if this is the first launch by verifying the existence of the launcher directory
     * and the presence of the local release info file.
     * This is a high-level check to determine if a full initial setup (including fetching latest release) is needed.
     *
     * @param dirPath The path to the launcher's base directory.
     * @param logger An instance of LauncherLogger for file logging.
     * @return true if it's the first launch (launcher directory doesn't exist or local release info is missing), false otherwise.
     */
    public static boolean isFirstLaunch(String dirPath, LauncherLogger logger) {
        Path launcherBasePath = Paths.get(dirPath);
        Path releaseInfoFilePath = Paths.get(dirPath, "release_info.txt"); // Must match GitHubReleaseChecker's path

        if (!Files.exists(launcherBasePath)) {
            logger.log("isFirstLaunch: Launcher base directory does not exist. First launch detected.");
            return true;
        }

        if (!Files.exists(releaseInfoFilePath)) {
            logger.log("isFirstLaunch: Local release info file does not exist. First launch detected.");
            return true;
        }

        logger.log("isFirstLaunch: Launcher base directory and local release info found. Not a first launch by initial check.");
        return false;
    }

    /**
     * Sets up the launcher environment, including checking for new releases,
     * downloading/extracting data, and verifying file integrity.
     *
     * @param outputDir The directory where files should be extracted.
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @param logger An instance of LauncherLogger for file logging.
     * @return The SetupResult indicating success, failure, or a miscount error.
     */
    public static SetupResult setup(String outputDir, Consumer<String> progressUpdater, LauncherLogger logger) {
        logger.log("Starting setup process in directory: " + outputDir);
        progressUpdater.accept("Starting setup...");

        File dir = new File(outputDir);
        if (!dir.exists()) {
            progressUpdater.accept("Creating launcher directory: " + dir.getName());
            logger.log("Creating directory: " + dir.getAbsolutePath());
            if (dir.mkdirs()) {
                progressUpdater.accept("Directory created.");
                logger.log("Directory created successfully.");
            } else {
                progressUpdater.accept("Failed to create directory!");
                logger.log("Failed to create directory: " + dir.getAbsolutePath());
                System.err.println("Failed to create launcher directory: " + dir.getAbsolutePath());
                return SetupResult.FAILURE; // Exit if directory creation fails
            }
        } else {
            progressUpdater.accept("Launcher directory already exists.");
            logger.log("Launcher directory already exists: " + dir.getAbsolutePath());
        }

        GitHubReleaseChecker releaseChecker = new GitHubReleaseChecker(outputDir, logger);
        String localReleaseTag = releaseChecker.getLocalReleaseTag();
        ReleaseInfo latestRelease = releaseChecker.getLatestRelease(progressUpdater);

        if (latestRelease == null) {
            logger.log("Could not retrieve latest release information. Setup aborted.");
            progressUpdater.accept("Error: Could not get latest release info. Check logs.");
            return SetupResult.FAILURE;
        }

        boolean needsFullDownload = false;
        if (localReleaseTag == null || !localReleaseTag.equals(latestRelease.tag)) {
            logger.log("New release detected or no local release info. Local: " + localReleaseTag + ", Latest: " + latestRelease.tag);
            progressUpdater.accept("New release found: " + latestRelease.tag + ". Downloading...");
            needsFullDownload = true;
        } else {
            logger.log("Local release tag matches latest. No full download needed based on tag.");
            progressUpdater.accept("Release is up-to-date. Verifying files...");
        }

        // If a new release is detected or data.zip is missing, download and extract
        File dataZipFile = Paths.get(outputDir, "data.zip").toFile();
        if (needsFullDownload || !dataZipFile.exists()) {
            if (!releaseChecker.downloadDataZip(latestRelease.dataZipUrl, progressUpdater)) {
                progressUpdater.accept("Failed to download data.zip!");
                logger.log("Failed to download data.zip. Setup aborted.");
                return SetupResult.FAILURE;
            }
            // After successful download, extract it
            try {
                progressUpdater.accept("Extracting data.zip...");
                logger.log("Attempting to extract data.zip.");
                extractZipFromFile(dataZipFile.getAbsolutePath(), outputDir, progressUpdater, logger);
                progressUpdater.accept("Extraction complete!");
                logger.log("data.zip extracted successfully.");
            } catch (IOException e) {
                String errorMessage = "Failed to extract data.zip: " + e.getMessage();
                System.err.println(errorMessage);
                logger.log(errorMessage);
                progressUpdater.accept("Extraction failed: " + e.getMessage());
                return SetupResult.FAILURE;
            }
            // Save the new release tag after successful download and extraction
            releaseChecker.saveLocalReleaseTag(latestRelease.tag);
        }

        // --- Verify all files and count against tree.txt ---
        logger.log("Verifying local files against tree.txt...");
        progressUpdater.accept("Verifying files...");
        TreeParseResult treeParseResult = releaseChecker.downloadAndParseTreeFile(latestRelease.treeFileUrl, progressUpdater);

        if (treeParseResult.filePaths.isEmpty() && treeParseResult.totalEntriesCount == 0) {
            logger.log("Failed to get expected file list from tree.txt or tree.txt was empty. Cannot verify integrity.");
            progressUpdater.accept("Error: Could not verify files (tree.txt issue).");
            return SetupResult.FAILURE; // Treat as failure if tree.txt is unreadable/empty
        }

        boolean allFilesPresent = true;
        for (String relativePath : treeParseResult.filePaths) { // Only checking files for presence
            Path absolutePath = Paths.get(outputDir, relativePath);
            if (!Files.exists(absolutePath)) {
                logger.log("Missing file detected: " + relativePath);
                progressUpdater.accept("Missing file: " + relativePath + ". Re-downloading data...");
                allFilesPresent = false;
                break; // Found a missing file, so re-extract the whole thing
            }
        }

        if (!allFilesPresent) {
            logger.log("Missing files detected. Re-downloading and re-extracting data.zip.");
            progressUpdater.accept("Missing files detected. Re-downloading data...");
            if (!releaseChecker.downloadDataZip(latestRelease.dataZipUrl, progressUpdater)) {
                progressUpdater.accept("Failed to re-download data.zip!");
                logger.log("Failed to re-download data.zip. Setup aborted.");
                return SetupResult.FAILURE;
            }
            try {
                progressUpdater.accept("Re-extracting data.zip...");
                logger.log("Attempting to re-extract data.zip.");
                extractZipFromFile(dataZipFile.getAbsolutePath(), outputDir, progressUpdater, logger);
                progressUpdater.accept("Re-extraction complete!");
                logger.log("data.zip re-extracted successfully.");
            } catch (IOException e) {
                String errorMessage = "Failed to re-extract data.zip: " + e.getMessage();
                System.err.println(errorMessage);
                logger.log(errorMessage);
                progressUpdater.accept("Re-extraction failed: " + e.getMessage());
                return SetupResult.FAILURE;
            }
        } else {
            logger.log("All expected files are present based on tree.txt file paths.");
            progressUpdater.accept("All files verified.");
        }

        // --- Final count comparison ---
        int actualTotalEntries = countFilesAndDirectories(Paths.get(outputDir), logger);
        logger.log("Expected total entries from tree.txt: " + treeParseResult.totalEntriesCount);
        logger.log("Actual total entries in directory: " + actualTotalEntries);

        // Subtract 1 from actualTotalEntries if the root directory itself is counted by walk.
        // Files.walk counts the starting path itself, so we need to adjust if the tree.txt doesn't count the root.
        // The tree output you provided starts with '.', which means the root is counted, so no adjustment needed for now.
        // If your tree.txt output format changes or doesn't include the root, you might need to adjust this.

        if (actualTotalEntries == -1) { // Error during counting
            logger.log("Error occurred during actual file counting. Cannot perform miscount check.");
            return SetupResult.FAILURE;
        }

        if (actualTotalEntries != treeParseResult.totalEntriesCount) {
            logger.log("MISCOUNT ERROR: Actual entries (" + actualTotalEntries + ") do not match expected entries (" + treeParseResult.totalEntriesCount + ")");
            progressUpdater.accept("Miscount error detected!");
            return SetupResult.MISCOUNT_ERROR;
        }

        logger.log("Setup process finished successfully. All counts match.");
        return SetupResult.SUCCESS;
    }

    /**
     * Extracts a zip file from a given path to a specified output directory.
     *
     * @param zipFilePath The absolute path to the zip file to extract.
     * @param outputDir The directory where the contents of the zip should be extracted.
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @param logger An instance of LauncherLogger for file logging.
     * @throws IOException If an I/O error occurs during extraction.
     */
    private static void extractZipFromFile(String zipFilePath, String outputDir, Consumer<String> progressUpdater, LauncherLogger logger) throws IOException {
        try (InputStream stream = new FileInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(stream)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(outputDir, entry.getName());
                logger.log("Extracting entry: " + entry.getName());
                progressUpdater.accept("Extracting: " + entry.getName());

                if (entry.isDirectory()) {
                    if (!newFile.exists()) {
                        if (newFile.mkdirs()) {
                            logger.log("Created directory: " + newFile.getAbsolutePath());
                        } else {
                            logger.log("Failed to create directory: " + newFile.getAbsolutePath());
                            throw new IOException("Failed to create directory " + newFile.getAbsolutePath());
                        }
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.exists()) {
                        if (parent.mkdirs()) {
                            logger.log("Created parent directory: " + parent.getAbsolutePath());
                        } else {
                            logger.log("Failed to create parent directory: " + parent.getAbsolutePath());
                            throw new IOException("Failed to create parent directory " + parent.getAbsolutePath());
                        }
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        logger.log("Extracted file: " + newFile.getAbsolutePath());
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Recursively counts the total number of files and directories within a given path,
     * excluding the starting path itself.
     *
     * @param startPath The root path from which to start counting.
     * @param logger An instance of LauncherLogger for file logging.
     * @return The total count of files and directories, or -1 if an error occurs.
     */
    private static int countFilesAndDirectories(Path startPath, LauncherLogger logger) {
        if (!Files.exists(startPath)) {
            return 0;
        }
        final int[] count = {0}; // Use array for mutable int in lambda
        try (Stream<Path> walk = Files.walk(startPath)) {
            walk.forEach(path -> {
                if (!path.equals(startPath)) { // Don't count the root directory itself
                    count[0]++;
                }
            });
        } catch (IOException e) {
            logger.log("Error counting files/directories in " + startPath + ": " + e.getMessage());
            System.err.println("Error counting files/directories: " + e.getMessage());
            return -1; // Indicate error
        }
        return count[0];
    }
}
