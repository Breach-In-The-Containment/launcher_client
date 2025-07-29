// Installer;java

package org.breachinthecontainment.launcher_client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList; // Added import for ArrayList
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.breachinthecontainment.launcher_client.GitHubReleaseChecker.ReleaseInfo;
import org.breachinthecontainment.launcher_client.GitHubReleaseChecker.TreeParseResult;
import org.breachinthecontainment.launcher_client.GitHubReleaseChecker.AssetInfo;

public class Installer {

    // These should match the filenames defined in GitHubReleaseChecker
    public static final String DATA_ZIP_FILE_NAME = "data.zip"; // Made public for Installer to use

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
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @param percentageProgressUpdater A Consumer to update the UI with download percentage (0.0 to 1.0).
     * @param logger An instance of LauncherLogger for file logging.
     * @return The SetupResult indicating success, failure, or a miscount error.
     */
    public static SetupResult setup(String outputDir, Consumer<String> textProgressUpdater, Consumer<Double> percentageProgressUpdater, LauncherLogger logger) {
        logger.log("Starting setup process in directory: " + outputDir);
        textProgressUpdater.accept("Starting setup...");
        percentageProgressUpdater.accept(0.0); // Reset progress bar

        Path tempDirectory = null;
        Path launcherDir = Paths.get(outputDir);

        try {
            // Create the main launcher directory if it doesn't exist
            if (!Files.exists(launcherDir)) {
                textProgressUpdater.accept("Creating launcher directory: " + launcherDir.getFileName());
                logger.log("Creating directory: " + launcherDir.toAbsolutePath());
                Files.createDirectories(launcherDir);
                textProgressUpdater.accept("Directory created.");
                logger.log("Directory created successfully.");
            } else {
                textProgressUpdater.accept("Launcher directory already exists.");
                logger.log("Launcher directory already exists: " + launcherDir.toAbsolutePath());
            }

            // Create a temporary directory for downloads within the launcher directory
            tempDirectory = Files.createTempDirectory(launcherDir, "temp_download_");
            logger.log("Created temporary download directory: " + tempDirectory.toAbsolutePath());

            GitHubReleaseChecker releaseChecker = new GitHubReleaseChecker(outputDir, logger, tempDirectory);
            String localReleaseTag = releaseChecker.getLocalReleaseTag();
            ReleaseInfo latestRelease = releaseChecker.getLatestRelease(textProgressUpdater);

            if (latestRelease == null) {
                logger.log("Could not retrieve latest release information. Setup aborted.");
                textProgressUpdater.accept("Error: Could not get latest release info. Check logs.");
                return SetupResult.FAILURE;
            }

            boolean needsFullDownload = false;
            if (localReleaseTag == null || !localReleaseTag.equals(latestRelease.tag)) {
                logger.log("New release detected or no local release info. Local: " + (localReleaseTag != null ? localReleaseTag : "N/A") + ", Latest: " + latestRelease.tag);
                textProgressUpdater.accept("New release found: " + latestRelease.tag + ". Preparing download...");
                needsFullDownload = true;
            } else {
                logger.log("Local release tag matches latest. No full download needed based on tag. Proceeding to verify files.");
                textProgressUpdater.accept("Release is up-to-date. Verifying files...");
            }

            Path dataZipTempPath = tempDirectory.resolve(DATA_ZIP_FILE_NAME);

            // Download and extract data.zip if needed (new release OR verification fails)
            // Initial download of data.zip only happens if needsFullDownload is true,
            // or if a subsequent verification step determines a re-download is required.
            if (needsFullDownload) {
                if (!releaseChecker.downloadDataZip(latestRelease.dataZipAssetInfo, textProgressUpdater, percentageProgressUpdater)) {
                    textProgressUpdater.accept("Failed to download data.zip!");
                    logger.log("Failed to download data.zip. Setup aborted.");
                    return SetupResult.FAILURE;
                }
                // After successful download, extract it
                try {
                    textProgressUpdater.accept("Extracting data.zip...");
                    logger.log("Attempting to extract data.zip from: " + dataZipTempPath.toAbsolutePath());
                    extractZipFromFile(dataZipTempPath, launcherDir, textProgressUpdater, logger); // Extract to launcherDir
                    textProgressUpdater.accept("Extraction complete!");
                    logger.log("data.zip extracted successfully.");
                    // Save the new release tag after successful download and extraction
                    releaseChecker.saveLocalReleaseTag(latestRelease.tag);
                } catch (IOException e) {
                    String errorMessage = "Failed to extract data.zip: " + e.getMessage();
                    System.err.println(errorMessage);
                    logger.log(errorMessage);
                    textProgressUpdater.accept("Extraction failed: " + e.getMessage());
                    return SetupResult.FAILURE;
                }
            }


            // --- Verify all files against tree.txt ---
            logger.log("Verifying local files against tree.txt...");
            textProgressUpdater.accept("Verifying files...");
            percentageProgressUpdater.accept(0.0); // Reset progress for verification step

            TreeParseResult treeParseResult = releaseChecker.downloadAndParseTreeFile(latestRelease.treeFileAssetInfo, textProgressUpdater, percentageProgressUpdater);

            if (treeParseResult.filePaths.isEmpty() && treeParseResult.totalEntriesCount == 0) {
                logger.log("Failed to get expected file list from tree.txt or tree.txt was empty. Cannot verify integrity.");
                textProgressUpdater.accept("Error: Could not verify files (tree.txt issue).");
                return SetupResult.FAILURE; // Treat as failure if tree.txt is unreadable/empty
            }

            Set<String> expectedFileSet = new HashSet<>(treeParseResult.filePaths);
            List<String> actualFilePaths = collectRelativeFilePaths(launcherDir, logger);
            Set<String> actualFileSet = new HashSet<>(actualFilePaths);

            boolean missingFiles = false;
            for (String expectedPath : expectedFileSet) {
                if (!actualFileSet.contains(expectedPath)) {
                    logger.log("Missing expected file: " + expectedPath);
                    missingFiles = true;
                    break;
                }
            }

            boolean unexpectedFiles = false;
            for (String actualPath : actualFileSet) {
                if (!expectedFileSet.contains(actualPath)) {
                    logger.log("Unexpected file found: " + actualPath);
                    unexpectedFiles = true;
                    // Note: We don't break here, we log all unexpected files.
                }
            }


            if (missingFiles || unexpectedFiles || actualFileSet.size() != expectedFileSet.size()) {
                logger.log("File integrity check failed. Missing files: " + missingFiles + ", Unexpected files: " + unexpectedFiles + ", Expected count: " + expectedFileSet.size() + ", Actual count: " + actualFileSet.size());
                textProgressUpdater.accept("File integrity check failed. Re-downloading and re-extracting data.");
                percentageProgressUpdater.accept(0.0); // Reset progress for re-download

                // Ensure data.zip is downloaded if not already (e.g., if only verification failed)
                if (!Files.exists(dataZipTempPath) || !releaseChecker.downloadDataZip(latestRelease.dataZipAssetInfo, textProgressUpdater, percentageProgressUpdater)) {
                    if (!releaseChecker.downloadDataZip(latestRelease.dataZipAssetInfo, textProgressUpdater, percentageProgressUpdater)) {
                        textProgressUpdater.accept("Failed to re-download data.zip!");
                        logger.log("Failed to re-download data.zip. Setup aborted.");
                        return SetupResult.FAILURE;
                    }
                }

                try {
                    textProgressUpdater.accept("Re-extracting data.zip...");
                    logger.log("Attempting to re-extract data.zip from: " + dataZipTempPath.toAbsolutePath());
                    // Before re-extraction, clear existing game files in outputDir
                    textProgressUpdater.accept("Cleaning old files...");
                    cleanInstallationDirectory(launcherDir, treeParseResult.filePaths, logger); // Only delete files/dirs present in tree.txt
                    textProgressUpdater.accept("Extracting new files...");
                    extractZipFromFile(dataZipTempPath, launcherDir, textProgressUpdater, logger);
                    textProgressUpdater.accept("Re-extraction complete!");
                    logger.log("data.zip re-extracted successfully.");

                    // Re-verify after re-extraction
                    actualFilePaths = collectRelativeFilePaths(launcherDir, logger);
                    actualFileSet = new HashSet<>(actualFilePaths);
                    missingFiles = false;
                    for (String expectedPath : expectedFileSet) {
                        if (!actualFileSet.contains(expectedPath)) {
                            logger.log("Still missing expected file after re-extraction: " + expectedPath);
                            missingFiles = true;
                        }
                    }
                    unexpectedFiles = false; // Re-check for unexpected files too
                    for (String actualPath : actualFileSet) {
                        if (!expectedFileSet.contains(actualPath)) {
                            logger.log("Still unexpected file after re-extraction: " + actualPath);
                            unexpectedFiles = true;
                        }
                    }

                    if (missingFiles || unexpectedFiles || actualFileSet.size() != expectedFileSet.size()) {
                        logger.log("Files still do not match after re-extraction. MISCOUNT ERROR.");
                        return SetupResult.MISCOUNT_ERROR; // Even after re-extract, something is wrong
                    }
                    // If everything is fine after re-extraction, save the tag
                    releaseChecker.saveLocalReleaseTag(latestRelease.tag);
                } catch (IOException e) {
                    String errorMessage = "Failed to re-extract data.zip: " + e.getMessage();
                    System.err.println(errorMessage);
                    logger.log(errorMessage);
                    textProgressUpdater.accept("Re-extraction failed: " + e.getMessage());
                    return SetupResult.FAILURE;
                }
            } else {
                logger.log("All expected files are present and no unexpected files found. Installation is verified.");
                textProgressUpdater.accept("All files verified.");
            }

            logger.log("Setup process finished successfully.");
            textProgressUpdater.accept("Setup complete!");
            percentageProgressUpdater.accept(1.0); // Ensure 100% on success
            return SetupResult.SUCCESS;

        } catch (IOException e) {
            String errorMessage = "Setup process failed due to I/O error: " + e.getMessage();
            System.err.println(errorMessage);
            logger.log(errorMessage);
            textProgressUpdater.accept("Setup failed: " + e.getMessage());
            percentageProgressUpdater.accept(0.0); // Indicate failure
            return SetupResult.FAILURE;
        } finally {
            // Clean up the temporary directory
            if (tempDirectory != null && Files.exists(tempDirectory)) {
                try {
                    deleteDirectory(tempDirectory, logger);
                    logger.log("Temporary download directory deleted: " + tempDirectory.toAbsolutePath());
                } catch (IOException e) {
                    logger.log("Failed to delete temporary directory: " + tempDirectory.toAbsolutePath() + ": " + e.getMessage());
                    System.err.println("Failed to delete temp directory: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Extracts a zip file from a given path to a specified output directory.
     *
     * @param zipFilePath The absolute path to the zip file to extract.
     * @param outputDir The directory where the contents of the zip should be extracted.
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @param logger An instance of LauncherLogger for file logging.
     * @throws IOException If an I/O error occurs during extraction.
     */
    private static void extractZipFromFile(Path zipFilePath, Path outputDir, Consumer<String> textProgressUpdater, LauncherLogger logger) throws IOException {
        try (InputStream stream = new FileInputStream(zipFilePath.toFile());
             ZipInputStream zis = new ZipInputStream(stream)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newFilePath = outputDir.resolve(entry.getName());
                logger.log("Extracting entry: " + entry.getName());
                textProgressUpdater.accept("Extracting: " + entry.getName());

                if (entry.isDirectory()) {
                    if (!Files.exists(newFilePath)) {
                        Files.createDirectories(newFilePath);
                        logger.log("Created directory: " + newFilePath.toAbsolutePath());
                    }
                } else {
                    Path parent = newFilePath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                        logger.log("Created parent directory: " + parent.toAbsolutePath());
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFilePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        logger.log("Extracted file: " + newFilePath.toAbsolutePath());
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Recursively collects all relative file paths (including directories) within a given base path,
     * excluding hidden files (starting with '.') that are not part of the base launcher directory itself.
     *
     * @param basePath The root path from which to start collecting.
     * @param logger An instance of LauncherLogger for file logging.
     * @return A list of relative paths, or an empty list if an error occurs.
     */
    private static List<String> collectRelativeFilePaths(Path basePath, LauncherLogger logger) {
        List<String> relativePaths = new ArrayList<>();
        if (!Files.exists(basePath)) {
            logger.log("collectRelativeFilePaths: Base path does not exist: " + basePath);
            return relativePaths;
        }

        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(path -> !path.equals(basePath)) // Exclude the base path itself
                    .forEach(path -> {
                        String relativePathStr = basePath.relativize(path).toString();
                        // On Windows, paths use '\', normalize to '/' for consistency with tree.txt
                        relativePathStr = relativePathStr.replace("\\", "/");

                        // Filter out hidden files/directories like .DS_Store, Thumbs.db unless they are expected
                        // (This simplified filter assumes such files are not explicitly listed in tree.txt)
                        if (!relativePathStr.startsWith(".") && !relativePathStr.contains("/.")) { // Basic hidden file filter
                            relativePaths.add(relativePathStr);
                        }
                    });
        } catch (IOException e) {
            logger.log("Error collecting relative file paths in " + basePath + ": " + e.getMessage());
            System.err.println("Error collecting relative file paths: " + e.getMessage());
            return new ArrayList<>(); // Return empty list on error
        }
        return relativePaths;
    }

    /**
     * Deletes files and directories within the installation path that are listed in the expectedPaths,
     * effectively cleaning up before a re-extraction.
     *
     * @param installationDir The main installation directory.
     * @param expectedPaths A list of relative paths from tree.txt, used to identify what to delete.
     * @param logger An instance of LauncherLogger for file logging.
     * @throws IOException If an I/O error occurs during deletion.
     */
    private static void cleanInstallationDirectory(Path installationDir, List<String> expectedPaths, LauncherLogger logger) throws IOException {
        logger.log("Cleaning installation directory: " + installationDir.toAbsolutePath());
        // To avoid deleting non-game files, we only delete paths that are listed in tree.txt.
        // This is safer than just deleting everything in mods/configs.
        for (String relativePath : expectedPaths) {
            Path absolutePath = installationDir.resolve(relativePath);
            if (Files.exists(absolutePath)) {
                try {
                    if (Files.isDirectory(absolutePath)) {
                        deleteDirectory(absolutePath, logger);
                    } else {
                        Files.delete(absolutePath);
                        logger.log("Deleted file during clean: " + absolutePath.toAbsolutePath());
                    }
                } catch (IOException e) {
                    logger.log("Failed to delete " + absolutePath.toAbsolutePath() + " during clean: " + e.getMessage());
                    // Don't rethrow, try to continue cleaning
                }
            }
        }
        logger.log("Installation directory clean-up complete.");
    }


    /**
     * Deletes a directory and all its contents recursively.
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
            walk.sorted(Comparator.reverseOrder()) // Sort in reverse to delete files before their parent directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.log("Deleted: " + path.toAbsolutePath());
                        } catch (IOException e) {
                            logger.log("Failed to delete " + path.toAbsolutePath() + ": " + e.getMessage());
                            System.err.println("Failed to delete " + path.toAbsolutePath() + ": " + e.getMessage());
                            // Important: Do not re-throw here, allow the loop to continue
                            // The outer method should handle overall failure if needed.
                        }
                    });
        }
    }

    /**
     * Enum to represent the result of the setup process.
     */
    public enum SetupResult {
        SUCCESS,
        FAILURE,
        MISCOUNT_ERROR
    }
}
