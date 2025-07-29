// GitHubReleaseChecker.java

package org.breachinthecontainment.launcher_client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer; // Added import for ByteBuffer
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility class to interact with GitHub Releases API to fetch release information,
 * download assets, and manage local release metadata.
 */
public class GitHubReleaseChecker {

    // IMPORTANT: Replaced with your actual GitHub repository details
    private static final String GITHUB_REPO_OWNER = "Breach-In-The-Containment";
    private static final String GITHUB_REPO_NAME = "get-mods";
    private static final String GITHUB_RELEASES_API_URL = "https://api.github.com/repos/" + GITHUB_REPO_OWNER + "/" + GITHUB_REPO_NAME + "/releases/latest";

    // IMPORTANT: These should match the actual filenames of your assets in the GitHub release
    private static final String TREE_FILE_NAME = "tree.txt";
    private static final String DATA_ZIP_FILE_NAME = "data.zip";

    private final String launcherDirectory;
    private final LauncherLogger logger;
    private final Path releaseInfoFilePath; // Path to store local release info (e.g., last tag)
    private final Path tempDirectoryPath; // Path for temporary downloads

    public GitHubReleaseChecker(String launcherDirectory, LauncherLogger logger, Path tempDirectoryPath) {
        this.launcherDirectory = launcherDirectory;
        this.logger = logger;
        this.tempDirectoryPath = tempDirectoryPath; // Set the temporary directory path
        this.releaseInfoFilePath = Paths.get(launcherDirectory, "release_info.txt");
    }

    /**
     * Fetches the latest release information from GitHub.
     * @return A JSONObject containing the latest release data, or null if an error occurs.
     */
    private JSONObject fetchLatestReleaseInfo() throws IOException {
        logger.log("Fetching latest GitHub release info from: " + GITHUB_RELEASES_API_URL);
        URL url = new URL(GITHUB_RELEASES_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "JavaLauncherClient"); // GitHub API often requires a User-Agent header

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                logger.log("Successfully fetched release info.");
                return new JSONObject(response.toString());
            }
        } else {
            logger.log("Failed to fetch release info. HTTP Code: " + responseCode);
            throw new IOException("Failed to fetch GitHub release info. HTTP Code: " + responseCode);
        }
    }

    /**
     * Retrieves the download URL and size for a specific asset from the release JSON.
     * @param releaseJson The JSONObject representing the GitHub release.
     * @param assetName The name of the asset to find (e.g., "data.zip").
     * @return An AssetInfo object containing the download URL and size, or null if not found.
     */
    private AssetInfo getAssetDownloadInfo(JSONObject releaseJson, String assetName) {
        JSONArray assets = releaseJson.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.getString("name").equals(assetName)) {
                return new AssetInfo(asset.getString("browser_download_url"), asset.getLong("size"));
            }
        }
        logger.log("Asset '" + assetName + "' not found in release.");
        return null;
    }

    /**
     * Downloads a file from a given URL to a specified destination path, reporting progress.
     * @param downloadUrl The URL of the file to download.
     * @param destinationPath The Path object representing the local destination.
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @param percentageProgressUpdater A Consumer to update the UI with download percentage (0.0 to 1.0).
     * @param totalBytes The total size of the file in bytes for progress calculation.
     * @return true if download is successful, false otherwise.
     */
    private boolean downloadFile(String downloadUrl, Path destinationPath, Consumer<String> textProgressUpdater, Consumer<Double> percentageProgressUpdater, long totalBytes) {
        logger.log("Downloading " + downloadUrl + " to " + destinationPath.toAbsolutePath());
        textProgressUpdater.accept("Downloading " + destinationPath.getFileName() + "...");
        percentageProgressUpdater.accept(0.0); // Start at 0%

        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.log("Download failed. HTTP Code: " + responseCode + " for " + downloadUrl);
                return false;
            }

            long actualTotalBytes = totalBytes > 0 ? totalBytes : connection.getContentLengthLong();
            if (actualTotalBytes <= 0) {
                logger.log("Could not determine total file size for progress: " + downloadUrl);
                percentageProgressUpdater.accept(-1.0); // Indicate indeterminate progress
            }

            try (InputStream in = connection.getInputStream();
                 ReadableByteChannel rbc = Channels.newChannel(in);
                 FileOutputStream fos = new FileOutputStream(destinationPath.toFile())) {

                long bytesRead = 0;
                ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 8); // 8KB buffer

                while (rbc.read(buffer) != -1) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        bytesRead += fos.getChannel().write(buffer);
                    }
                    buffer.clear();

                    if (actualTotalBytes > 0) {
                        double progress = (double) bytesRead / actualTotalBytes;
                        percentageProgressUpdater.accept(progress);
                    }
                }
            }
            logger.log("Download complete: " + destinationPath.getFileName());
            percentageProgressUpdater.accept(1.0); // Ensure 100% on completion
            return true;
        } catch (IOException e) {
            logger.log("Download failed for " + destinationPath.getFileName() + ": " + e.getMessage());
            System.err.println("Download failed: " + e.getMessage());
            percentageProgressUpdater.accept(0.0); // Reset or indicate failure
            return false;
        } finally {
            percentageProgressUpdater.accept(-1.0); // Reset to indeterminate after download finishes/fails
        }
    }

    /**
     * Reads the locally stored last known release tag.
     * @return The last known release tag, or null if not found.
     */
    public String getLocalReleaseTag() {
        if (Files.exists(releaseInfoFilePath)) {
            try {
                String tag = Files.readString(releaseInfoFilePath).trim();
                logger.log("Local release tag found: " + tag);
                return tag;
            } catch (IOException e) {
                logger.log("Failed to read local release info: " + e.getMessage());
                System.err.println("Error reading local release info: " + e.getMessage());
            }
        }
        logger.log("No local release tag found.");
        return null;
    }

    /**
     * Saves the current release tag locally.
     * @param tag The release tag to save.
     */
    public void saveLocalReleaseTag(String tag) {
        try {
            Files.writeString(releaseInfoFilePath, tag);
            logger.log("Saved local release tag: " + tag);
        } catch (IOException e) {
            logger.log("Failed to save local release info: " + e.getMessage());
            System.err.println("Error saving local release info: " + e.getMessage());
        }
    }

    /**
     * Fetches the latest release tag and asset URLs.
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @return A ReleaseInfo object containing the tag, tree.txt URL, and data.zip URL, or null on failure.
     */
    public ReleaseInfo getLatestRelease(Consumer<String> textProgressUpdater) {
        try {
            JSONObject releaseJson = fetchLatestReleaseInfo();
            if (releaseJson == null) {
                return null;
            }

            String latestTag = releaseJson.getString("tag_name");
            AssetInfo treeAssetInfo = getAssetDownloadInfo(releaseJson, TREE_FILE_NAME);
            AssetInfo dataZipAssetInfo = getAssetDownloadInfo(releaseJson, DATA_ZIP_FILE_NAME);

            if (treeAssetInfo == null || dataZipAssetInfo == null) {
                logger.log("Required assets (" + TREE_FILE_NAME + " or " + DATA_ZIP_FILE_NAME + ") not found in the latest release.");
                textProgressUpdater.accept("Error: Required assets not found in release.");
                return null;
            }

            logger.log("Latest release tag: " + latestTag);
            logger.log("Tree.txt URL: " + treeAssetInfo.url + ", Size: " + treeAssetInfo.size);
            logger.log("Data.zip URL: " + dataZipAssetInfo.url + ", Size: " + dataZipAssetInfo.size);

            return new ReleaseInfo(latestTag, treeAssetInfo, dataZipAssetInfo);

        } catch (IOException e) {
            logger.log("Error getting latest release info: " + e.getMessage());
            textProgressUpdater.accept("Error fetching release info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Downloads the tree.txt file and parses its content into a list of relative file paths
     * and counts the total number of entries (files and directories) listed.
     * @param treeAssetInfo The AssetInfo for tree.txt (URL and size).
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @param percentageProgressUpdater A Consumer to update the UI with download percentage (0.0 to 1.0).
     * @return A TreeParseResult object containing the list of expected file paths and the total count,
     * or an empty result on failure.
     */
    public TreeParseResult downloadAndParseTreeFile(AssetInfo treeAssetInfo, Consumer<String> textProgressUpdater, Consumer<Double> percentageProgressUpdater) {
        Path treeFilePath = tempDirectoryPath.resolve(TREE_FILE_NAME);
        List<String> expectedFilePaths = new ArrayList<>();
        int totalEntriesCount = 0;

        if (!downloadFile(treeAssetInfo.url, treeFilePath, textProgressUpdater, percentageProgressUpdater, treeAssetInfo.size)) {
            return new TreeParseResult(expectedFilePaths, 0); // Return empty result if download fails
        }

        logger.log("Parsing tree.txt from: " + treeFilePath.toAbsolutePath());
        textProgressUpdater.accept("Parsing file list...");

        try (BufferedReader reader = new BufferedReader(new FileReader(treeFilePath.toFile()))) {
            String line;
            Pattern filePattern = Pattern.compile(".*(?:├──|└──)\\s+([^\\s]+(?:\\.[a-zA-Z0-9]+)+)");
            Pattern dirPattern = Pattern.compile(".*(?:├──|└──)\\s+([^\\s]+)");

            List<String> pathSegments = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equals(".")) {
                    continue;
                }

                if (line.startsWith("├──") || line.startsWith("└──")) {
                    totalEntriesCount++;
                }

                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ' || c == '│') {
                        indent++;
                    } else {
                        break;
                    }
                }
                indent = indent / 4;

                while (pathSegments.size() > indent) {
                    pathSegments.remove(pathSegments.size() - 1);
                }

                Matcher fileMatcher = filePattern.matcher(line);
                if (fileMatcher.matches()) {
                    String fileName = fileMatcher.group(1);
                    List<String> currentPathList = new ArrayList<>(pathSegments);
                    currentPathList.add(fileName);
                    expectedFilePaths.add(String.join("/", currentPathList));
                } else {
                    Matcher dirMatcher = dirPattern.matcher(line);
                    if (dirMatcher.matches()) {
                        String dirName = dirMatcher.group(1); // Corrected: removed .Matcher
                        if (!dirName.contains(".")) {
                            if (pathSegments.size() == indent) {
                                pathSegments.add(dirName);
                            } else if (pathSegments.size() < indent) {
                                pathSegments.add(dirName);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.log("Error parsing tree.txt: " + e.getMessage());
            System.err.println("Error parsing tree.txt: " + e.getMessage());
        } finally {
            if (Files.exists(treeFilePath)) {
                try {
                    Files.delete(treeFilePath);
                    logger.log("Deleted temporary tree file: " + treeFilePath.toAbsolutePath());
                } catch (IOException e) {
                    logger.log("Failed to delete temporary tree file: " + treeFilePath.toAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        return new TreeParseResult(expectedFilePaths, totalEntriesCount);
    }

    /**
     * Downloads the data.zip file to the temporary directory.
     * @param dataZipAssetInfo The AssetInfo for data.zip (URL and size).
     * @param textProgressUpdater A Consumer to update the UI with text messages.
     * @param percentageProgressUpdater A Consumer to update the UI with download percentage (0.0 to 1.0).
     * @return true if download is successful, false otherwise.
     */
    public boolean downloadDataZip(AssetInfo dataZipAssetInfo, Consumer<String> textProgressUpdater, Consumer<Double> percentageProgressUpdater) {
        Path dataZipPath = tempDirectoryPath.resolve(DATA_ZIP_FILE_NAME);
        return downloadFile(dataZipAssetInfo.url, dataZipPath, textProgressUpdater, percentageProgressUpdater, dataZipAssetInfo.size);
    }

    /**
     * Simple class to hold release information.
     */
    public static class ReleaseInfo {
        public final String tag;
        public final AssetInfo treeFileAssetInfo;
        public final AssetInfo dataZipAssetInfo;

        public ReleaseInfo(String tag, AssetInfo treeFileAssetInfo, AssetInfo dataZipAssetInfo) {
            this.tag = tag;
            this.treeFileAssetInfo = treeFileAssetInfo;
            this.dataZipAssetInfo = dataZipAssetInfo;
        }
    }

    /**
     * Simple class to hold asset information (URL and size).
     */
    public static class AssetInfo {
        public final String url;
        public final long size;

        public AssetInfo(String url, long size) {
            this.url = url;
            this.size = size;
        }
    }

    /**
     * Simple class to hold the result of parsing the tree.txt file.
     */
    public static class TreeParseResult {
        public final List<String> filePaths; // List of relative paths to files
        public final int totalEntriesCount; // Total count of files AND directories listed in tree.txt

        public TreeParseResult(List<String> filePaths, int totalEntriesCount) {
            this.filePaths = filePaths;
            this.totalEntriesCount = totalEntriesCount;
        }
    }
}
