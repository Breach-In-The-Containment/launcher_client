// Ignore the JSON errors, it can still compile, somehow...

package org.breachinthecontainment.launcher_client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader; // Added import for InputStreamReader
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer; // Added import for Consumer
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

    public GitHubReleaseChecker(String launcherDirectory, LauncherLogger logger) {
        this.launcherDirectory = launcherDirectory;
        this.logger = logger;
        // Define where to store the local release information (e.g., a simple text file with the tag)
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
     * Retrieves the download URL for a specific asset from the release JSON.
     * @param releaseJson The JSONObject representing the GitHub release.
     * @param assetName The name of the asset to find (e.g., "data.zip").
     * @return The download URL of the asset, or null if not found.
     */
    private String getAssetDownloadUrl(JSONObject releaseJson, String assetName) {
        JSONArray assets = releaseJson.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.getString("name").equals(assetName)) {
                return asset.getString("browser_download_url");
            }
        }
        logger.log("Asset '" + assetName + "' not found in release.");
        return null;
    }

    /**
     * Downloads a file from a given URL to a specified destination.
     * @param downloadUrl The URL of the file to download.
     * @param destinationFile The File object representing the local destination.
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @return true if download is successful, false otherwise.
     */
    private boolean downloadFile(String downloadUrl, File destinationFile, Consumer<String> progressUpdater) {
        logger.log("Downloading " + downloadUrl + " to " + destinationFile.getAbsolutePath());
        progressUpdater.accept("Downloading " + destinationFile.getName() + "...");
        try {
            URL url = new URL(downloadUrl);
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fos = new FileOutputStream(destinationFile);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
            logger.log("Download complete: " + destinationFile.getName());
            return true;
        } catch (IOException e) {
            logger.log("Download failed for " + destinationFile.getName() + ": " + e.getMessage());
            System.err.println("Download failed: " + e.getMessage());
            return false;
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
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @return A ReleaseInfo object containing the tag, tree.txt URL, and data.zip URL, or null on failure.
     */
    public ReleaseInfo getLatestRelease(Consumer<String> progressUpdater) {
        try {
            JSONObject releaseJson = fetchLatestReleaseInfo();
            if (releaseJson == null) {
                return null;
            }

            String latestTag = releaseJson.getString("tag_name");
            String treeUrl = getAssetDownloadUrl(releaseJson, TREE_FILE_NAME);
            String dataZipUrl = getAssetDownloadUrl(releaseJson, DATA_ZIP_FILE_NAME);

            if (treeUrl == null || dataZipUrl == null) {
                logger.log("Required assets (" + TREE_FILE_NAME + " or " + DATA_ZIP_FILE_NAME + ") not found in the latest release.");
                progressUpdater.accept("Error: Required assets not found in release.");
                return null;
            }

            logger.log("Latest release tag: " + latestTag);
            logger.log("Tree.txt URL: " + treeUrl);
            logger.log("Data.zip URL: " + dataZipUrl);

            return new ReleaseInfo(latestTag, treeUrl, dataZipUrl);

        } catch (IOException e) {
            logger.log("Error getting latest release info: " + e.getMessage());
            progressUpdater.accept("Error fetching release info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Downloads the tree.txt file and parses its content into a list of relative file paths
     * and counts the total number of entries (files and directories) listed.
     * @param treeUrl The download URL for tree.txt.
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @return A TreeParseResult object containing the list of expected file paths and the total count,
     * or an empty result on failure.
     */
    public TreeParseResult downloadAndParseTreeFile(String treeUrl, Consumer<String> progressUpdater) {
        File treeFile = Paths.get(launcherDirectory, "temp_tree.txt").toFile();
        List<String> expectedFilePaths = new ArrayList<>();
        int totalEntriesCount = 0;

        progressUpdater.accept("Downloading file list...");
        if (!downloadFile(treeUrl, treeFile, progressUpdater)) {
            return new TreeParseResult(expectedFilePaths, 0); // Return empty result if download fails
        }

        logger.log("Parsing tree.txt from: " + treeFile.getAbsolutePath());
        progressUpdater.accept("Parsing file list...");

        try (BufferedReader reader = new BufferedReader(new FileReader(treeFile))) {
            String line;
            // Regex to capture file paths from 'tree' command output.
            // It looks for lines that end with a file extension (e.g., .cfg, .jar, .zip, .json, .txt, .properties, .toml)
            // and extracts the path relative to the root.
            Pattern filePattern = Pattern.compile(".*(?:├──|└──)\\s+([^\\s]+(?:\\.[a-zA-Z0-9]+)+)");

            // Pattern to capture directory names (no extension)
            Pattern dirPattern = Pattern.compile(".*(?:├──|└──)\\s+([^\\s]+)");

            List<String> pathSegments = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equals(".")) {
                    continue;
                }

                // Count every line that starts with '├──' or '└──' as an entry
                if (line.startsWith("├──") || line.startsWith("└──")) {
                    totalEntriesCount++;
                }

                // Determine indentation level for path reconstruction
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ' || c == '│') {
                        indent++;
                    } else {
                        break;
                    }
                }
                indent = indent / 4; // Assuming 4 spaces per indent level

                // Remove path segments that are at deeper indent levels than the current line
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
                    // Check if it's a directory
                    Matcher dirMatcher = dirPattern.matcher(line);
                    if (dirMatcher.matches()) {
                        String dirName = dirMatcher.group(1);
                        // Simple heuristic: if it doesn't contain a '.', treat it as a directory
                        if (!dirName.contains(".")) {
                            if (pathSegments.size() == indent) {
                                pathSegments.add(dirName);
                            } else if (pathSegments.size() < indent) {
                                // This case implies a parent directory was somehow skipped in pathSegments,
                                // which shouldn't happen with well-formed tree output.
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
            // Clean up the temporary tree file
            if (treeFile.exists()) {
                if (treeFile.delete()) {
                    logger.log("Deleted temporary tree file: " + treeFile.getAbsolutePath());
                } else {
                    logger.log("Failed to delete temporary tree file: " + treeFile.getAbsolutePath());
                }
            }
        }
        return new TreeParseResult(expectedFilePaths, totalEntriesCount);
    }

    /**
     * Downloads the data.zip file to the specified launcher directory.
     * @param dataZipUrl The download URL for data.zip.
     * @param progressUpdater A Consumer to update the UI with progress messages.
     * @return true if download is successful, false otherwise.
     */
    public boolean downloadDataZip(String dataZipUrl, Consumer<String> progressUpdater) {
        File dataZipFile = Paths.get(launcherDirectory, DATA_ZIP_FILE_NAME).toFile();
        progressUpdater.accept("Downloading " + DATA_ZIP_FILE_NAME + "...");
        return downloadFile(dataZipUrl, dataZipFile, progressUpdater);
    }

    /**
     * Simple class to hold release information.
     */
    public static class ReleaseInfo {
        public final String tag;
        public final String treeFileUrl;
        public final String dataZipUrl;

        public ReleaseInfo(String tag, String treeFileUrl, String dataZipUrl) {
            this.tag = tag;
            this.treeFileUrl = treeFileUrl;
            this.dataZipUrl = dataZipUrl;
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
