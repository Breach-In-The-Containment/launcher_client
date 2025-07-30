package org.breachinthecontainment.launcher_client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class SumChecker {

    // Replace this with the actual checksum you generate for your original data.zip
    public static final String EXPECTED_CHECKSUM = "6fbf31c2b37a54981cbc39923fd95d74c493ab56c038584caccfbc2864f8270b";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java SumChecker <path-to-data.zip>");
            System.exit(1);
        }

        String filePath = args[0];

        try {
            String actualChecksum = calculateSHA256(filePath);
            System.out.println("Calculated checksum: " + actualChecksum);

            if (actualChecksum.equalsIgnoreCase(EXPECTED_CHECKSUM)) {
                System.out.println("Checksum matches! Data integrity verified.");
            } else {
                System.out.println("Checksum does NOT match! Data may have been tampered with.");
            }
        } catch (Exception e) {
            System.err.println("Error calculating checksum: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Calculates the SHA-256 checksum of a file located at the given filepath.
     *
     * @param filepath The path to the file.
     * @return The SHA-256 checksum as a hex string.
     * @throws Exception If an error occurs while reading the file or computing the digest.
     */
    public static String calculateSHA256(String filepath) throws Exception {
        try (InputStream fis = new FileInputStream(filepath)) {
            return calculateSHA256(fis);
        }
    }

    /**
     * Calculates the SHA-256 checksum from an InputStream.
     * Useful for resources bundled inside the JAR.
     *
     * @param is InputStream of the data to checksum.
     * @return The SHA-256 checksum as a hex string.
     * @throws Exception If an error occurs while reading the stream or computing the digest.
     */
    public static String calculateSHA256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] byteBuffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(byteBuffer)) != -1) {
            digest.update(byteBuffer, 0, bytesRead);
        }
        byte[] hashedBytes = digest.digest();

        // Convert the byte array to a hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hashedBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
