// WARNING: this contains sensible data (i think)

package org.breachinthecontainment.launcher_client;

import com.google.gson.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

public class MicrosoftAuth {

    private static final String CLIENT_ID = "68fb900d-002e-4fe1-b2e7-3d6c3753055a";
    private static final String CLIENT_SECRET = "v5J8Q~O8Ak~LXNzEGwEDOESYU7WCzGZ2cueFkczZ";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";
    private static final String AUTH_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final Gson gson = new Gson();

    private static String authorizationCode;

    public static boolean authenticate(Stage parentStage) {
        try {
            // 1. Open login URL
            String authUri = AUTH_URL + "?client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                    "&response_mode=query" +
                    "&scope=" + URLEncoder.encode("XboxLive.signin offline_access openid profile", "UTF-8");

            Desktop.getDesktop().browse(new URI(authUri));

            // 2. Start local server to listen for redirect
            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            CompletableFuture<String> codeFuture = new CompletableFuture<>();

            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("code=")) {
                    authorizationCode = query.split("code=")[1].split("&")[0];
                    String response = "<html><body><h2>You can close this window.</h2></body></html>";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    codeFuture.complete(authorizationCode);
                    server.stop(0);
                }
            });

            server.start();
            codeFuture.get(60, TimeUnit.SECONDS); // Wait for user to log in

            if (authorizationCode == null) return false;

            // 3. Exchange code for Microsoft access token
            String accessToken = getMicrosoftAccessToken(authorizationCode);
            if (accessToken == null) return false;

            // 4. Authenticate with Xbox Live
            XboxAuth xbox = authenticateWithXboxLive(accessToken);
            if (xbox == null) return false;

            // 5. Authenticate with XSTS
            XstsAuth xsts = authenticateWithXSTS(xbox.token);
            if (xsts == null) return false;

            // 6. Check Minecraft ownership
            boolean ownsMinecraft = checkMinecraftOwnership(xsts.uhs, xsts.token);
            return ownsMinecraft;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getMicrosoftAccessToken(String code) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String form = "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8") +
                "&code=" + URLEncoder.encode(code, "UTF-8") +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                "&grant_type=authorization_code" +
                "&scope=" + URLEncoder.encode("XboxLive.signin offline_access openid profile", "UTF-8");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        return json.has("access_token") ? json.get("access_token").getAsString() : null;
    }

    private static XboxAuth authenticateWithXboxLive(String accessToken) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + accessToken);
        json.add("Properties", properties);
        json.addProperty("RelyingParty", "http://auth.xboxlive.com");
        json.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
        if (resp.has("Token") && resp.has("DisplayClaims")) {
            String token = resp.get("Token").getAsString();
            String uhs = resp.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0).getAsJsonObject()
                    .get("uhs").getAsString();
            return new XboxAuth(token, uhs);
        }
        return null;
    }

    private static XstsAuth authenticateWithXSTS(String xboxToken) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xboxToken);
        properties.add("UserTokens", tokens);
        json.add("Properties", properties);
        json.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        json.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
        if (resp.has("Token") && resp.has("DisplayClaims")) {
            String token = resp.get("Token").getAsString();
            String uhs = resp.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0).getAsJsonObject()
                    .get("uhs").getAsString();
            return new XstsAuth(token, uhs);
        }
        return null;
    }

    private static boolean checkMinecraftOwnership(String uhs, String xstsToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "XBL3.0 x=" + uhs + ";" + xstsToken)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    private static class XboxAuth {
        String token, uhs;
        XboxAuth(String token, String uhs) {
            this.token = token;
            this.uhs = uhs;
        }
    }

    private static class XstsAuth {
        String token, uhs;
        XstsAuth(String token, String uhs) {
            this.token = token;
            this.uhs = uhs;
        }
    }
}
