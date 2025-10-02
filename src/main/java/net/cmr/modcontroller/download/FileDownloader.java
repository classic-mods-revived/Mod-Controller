package net.cmr.modcontroller.download;

import net.cmr.modcontroller.config.DownloadEntry;
import net.cmr.modcontroller.download.api.CurseForgeAPI;
import net.cmr.modcontroller.download.api.ModrinthAPI;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileDownloader {
    private final ModrinthAPI modrinthAPI;
    private final CurseForgeAPI curseForgeAPI;
    private final boolean backupReplacedFiles;

    public FileDownloader(String modrinthKey, String curseForgeKey, boolean backupReplacedFiles) {
        this.modrinthAPI = new ModrinthAPI(modrinthKey);
        this.curseForgeAPI = new CurseForgeAPI(curseForgeKey);
        this.backupReplacedFiles = backupReplacedFiles;
    }

    public boolean downloadEntry(DownloadEntry entry, Path gameDir, ProgressCallback callback) {
        try {
            if (!entry.enabled) {
                System.out.println("  Skipping (disabled): " + entry.name);
                return false;
            }

            callback.onStatusUpdate("Resolving: " + entry.name);

            String downloadUrl;
            String expectedHash = null;
            String hashType = null;

            switch (entry.sourceType) {
                case URL:
                    downloadUrl = entry.url;
                    expectedHash = entry.sha1Hash != null ? entry.sha1Hash : entry.sha512Hash;
                    hashType = entry.sha1Hash != null ? "SHA-1" : "SHA-512";
                    break;

                case MODRINTH:
                    ModrinthAPI.DownloadInfo modrinthInfo = modrinthAPI.getVersionDownload(entry.versionId);
                    downloadUrl = modrinthInfo.url;
                    expectedHash = modrinthInfo.sha512 != null ? modrinthInfo.sha512 : modrinthInfo.sha1;
                    hashType = modrinthInfo.sha512 != null ? "SHA-512" : "SHA-1";
                    System.out.println("  Resolved from Modrinth: " + modrinthInfo.filename);
                    break;

                case CURSEFORGE:
                    CurseForgeAPI.DownloadInfo curseForgeInfo = curseForgeAPI.getFileDownload(
                        entry.projectId, entry.fileId);
                    downloadUrl = curseForgeInfo.url;
                    expectedHash = curseForgeInfo.sha1;
                    hashType = "SHA-1";
                    System.out.println("  Resolved from CurseForge: " + curseForgeInfo.filename);
                    break;

                default:
                    throw new IllegalStateException("Unknown source type: " + entry.sourceType);
            }

            Path destination = gameDir.resolve(entry.destination);

            // Check if file exists and handle replacement
            if (Files.exists(destination)) {
                if (!entry.replaceIfExists) {
                    System.out.println("  File exists and replacement disabled, skipping: " + entry.name);
                    return false;
                }

                // Verify if file needs updating
                if (expectedHash != null) {
                    String existingHash = calculateHash(destination, hashType);
                    if (existingHash.equalsIgnoreCase(expectedHash)) {
                        System.out.println("  File already up to date (hash matches): " + entry.name);
                        return false;
                    }
                }

                // Backup existing file
                if (backupReplacedFiles) {
                    backupFile(destination);
                }
            }

            callback.onStatusUpdate("Downloading: " + entry.name);
            System.out.println("  Downloading from: " + downloadUrl);

            // Download file
            Files.createDirectories(destination.getParent());
            URL url = new URL(downloadUrl);
            
            try (InputStream in = url.openStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            // Verify hash if provided
            if (expectedHash != null && !expectedHash.isEmpty()) {
                String actualHash = calculateHash(destination, hashType);
                if (!actualHash.equalsIgnoreCase(expectedHash)) {
                    System.err.println("  WARNING: Hash mismatch for " + entry.name);
                    System.err.println("  Expected: " + expectedHash);
                    System.err.println("  Got:      " + actualHash);
                    // Don't fail the download, just warn
                }
            }

            System.out.println("  ✓ SUCCESS: " + entry.name);
            callback.onStatusUpdate("✓ " + entry.name);
            return true;

        } catch (Exception e) {
            System.err.println("  ✗ FAILED: " + entry.name);
            System.err.println("  Error: " + e.getMessage());
            e.printStackTrace();
            callback.onStatusUpdate("✗ Failed: " + entry.name);
            return false;
        }
    }

    private void backupFile(Path file) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupDir = file.getParent().resolve(".modcontroller-backups");
        Files.createDirectories(backupDir);

        String filename = file.getFileName().toString();
        Path backupPath = backupDir.resolve(filename + "." + timestamp + ".backup");

        Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("  Backed up existing file to: " + backupPath.getFileName());
    }

    private String calculateHash(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public interface ProgressCallback {
        void onStatusUpdate(String status);
    }
}
