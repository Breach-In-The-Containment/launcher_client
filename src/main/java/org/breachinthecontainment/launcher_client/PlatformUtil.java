// PlatformUtil.java

package org.breachinthecontainment.launcher_client;

import java.nio.file.Paths;

public class PlatformUtil {
    public static String getLauncherDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            return Paths.get(home, "breachinthecontainment", "launcher").toString();
        } else if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "breachinthecontainment", "launcher").toString();
        } else {
            return Paths.get(home, ".breachinthecontainment", "launcher").toString();
        }
    }
}
