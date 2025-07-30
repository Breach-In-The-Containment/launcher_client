package org.breachinthecontainment.launcher_client;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Theme {

    public enum Mode {
        DARK, LIGHT
    }

    public static Mode detectSystemTheme() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("mac")) {
                Process process = Runtime.getRuntime().exec(new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                if (result != null && result.trim().equalsIgnoreCase("Dark")) {
                    return Mode.DARK;
                }
            } else if (os.contains("win")) {
                Process process = Runtime.getRuntime().exec(
                        "reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize /v AppsUseLightTheme"
                );
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        String[] parts = line.trim().split("\\s+");
                        String value = parts[parts.length - 1];
                        return value.equals("0x0") ? Mode.DARK : Mode.LIGHT;
                    }
                }
            } else if (os.contains("linux")) {
                Process process = Runtime.getRuntime().exec("gsettings get org.gnome.desktop.interface color-scheme");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                if (result != null && result.contains("dark")) {
                    return Mode.DARK;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not detect system theme: " + e.getMessage());
        }

        return Mode.LIGHT; // Fallback
    }
}
