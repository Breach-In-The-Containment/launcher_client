package org.breachinthecontainment.launcher_client;

public class Main {
    public static void main(String[] args) {
        String launcherDir = PlatformUtil.getLauncherDirectory();

        if (Installer.isFirstLaunch(launcherDir)) {
            System.out.println("First launch detected. Extracting data.zip...");
            Installer.setup(launcherDir);
        } else {
            System.out.println("Data already extracted.");
        }

        UI.launchApp();
    }
}
