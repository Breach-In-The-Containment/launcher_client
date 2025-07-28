package org.breachinthecontainment.launcher_client;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Installer {

    public static boolean isFirstLaunch(String dirPath) {
        File dir = new File(dirPath);
        File modsDir = new File(dir, "mods");
        return !dir.exists() || !modsDir.exists();
    }

    public static void setup(String outputDir) {
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        try {
            extractZipFromJar("/data.zip", outputDir);
        } catch (IOException e) {
            System.err.println("Failed to extract data.zip: " + e.getMessage());
        }
    }

    private static void extractZipFromJar(String resourcePath, String outputDir) throws IOException {
        try (InputStream stream = Installer.class.getResourceAsStream(resourcePath);
             ZipInputStream zis = new ZipInputStream(stream)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
