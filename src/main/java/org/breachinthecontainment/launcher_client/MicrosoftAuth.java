package org.breachinthecontainment.launcher_client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.litarvan.openauth.microsoft.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class MicrosoftAuth {
    private static MicrosoftAuthResult authResult = null;
    private static final Gson gson = new Gson();
    private static Path sessionFile;

    public static void init(String launcherDir) {
        sessionFile = Path.of(launcherDir, "session", "session.json");
        tryAutoLogin();
    }

    public static boolean signIn() {
        MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
        try {
            authResult = authenticator.loginWithWebview();
            saveSession(authResult);
            return ownsMinecraft();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isSignedIn() { // boolean to check if you're signed in
        return authResult != null && authResult.getProfile() != null;
    }

    public static String getUsername() { // mc username
        return authResult != null ? authResult.getProfile().getName() : "Unknown";
    }

    public static MicrosoftAuthResult getAuthResult() {
        return authResult;
    }

    private static void saveSession(MicrosoftAuthResult result) { // save sessions for auto login
        try {
            if (!Files.exists(sessionFile.getParent())) {
                Files.createDirectories(sessionFile.getParent());
            }

            JsonObject json = new JsonObject();
            json.addProperty("refresh_token", result.getRefreshToken());

            try (Writer writer = new FileWriter(sessionFile.toFile())) {
                gson.toJson(json, writer);
            }

            System.out.println("Session saved.");
        } catch (IOException e) {
            System.err.println("Failed to save session: " + e.getMessage());
        }
    }

    private static void tryAutoLogin() { // auto login
        if (sessionFile == null || !Files.exists(sessionFile)) return;

        try (Reader reader = new FileReader(sessionFile.toFile())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            String refreshToken = json.get("refresh_token").getAsString();

            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            authResult = authenticator.loginWithRefreshToken(refreshToken);

            System.out.println("Auto-login successful. Welcome back, " + authResult.getProfile().getName());
        } catch (Exception e) {
            System.err.println("Auto-login failed: " + e.getMessage());
        }
    }

    private static boolean ownsMinecraft() { // check if minecraft is actually owned
        return authResult != null && authResult.getProfile() != null;
    }

    public static void logout() { // log out
        authResult = null;
        try {
            if (sessionFile != null && Files.exists(sessionFile)) {
                Files.delete(sessionFile);
                System.out.println("Session file deleted.");
            }
        } catch (IOException e) {
            System.err.println("Failed to delete session file: " + e.getMessage());
        }
    }

}
