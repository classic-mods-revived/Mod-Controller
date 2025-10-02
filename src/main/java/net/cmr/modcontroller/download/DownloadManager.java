package net.cmr.modcontroller.download;

import net.cmr.modcontroller.config.DownloadEntry;
import net.cmr.modcontroller.config.ModConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DownloadManager {
    private static final String MARKER_FILE = "config/modcontroller.marker";

    private final Path gameDir;
    private final ModConfig config;
    private final FileDownloader downloader;

    private volatile String currentStatus = "Initializing...";
    private volatile int currentFileIndex = 0;
    private volatile int totalFiles = 0;
    private volatile boolean isDownloading = false;

    public DownloadManager(Path gameDir, ModConfig config) {
        this.gameDir = gameDir;
        this.config = config;
        this.downloader = new FileDownloader(
            config.modrinthApiKey,
            config.curseforgeApiKey,
            config.backupReplacedFiles
        );
    }

    public boolean shouldRunDownloads() {
        Path markerFile = gameDir.resolve(MARKER_FILE);

        if (Files.exists(markerFile)) {
            if (config.downloadOnFirstLaunchOnly) {
                System.out.println("ModController: Not first launch, skipping downloads.");
                return false;
            }

            if (config.checkForUpdates) {
                System.out.println("ModController: Checking for file updates...");
                return true;
            }

            return false;
        }

        return true;
    }

    public int runDownloads() {
        try {
            System.out.println("========================================");
            System.out.println("MOD CONTROLLER: Starting downloads");
            System.out.println("========================================");

            List<DownloadEntry> files = config.downloads.stream()
                .filter(e -> e.enabled)
                .toList();

            if (files.isEmpty()) {
                System.out.println("ModController: No enabled downloads in config.");
                createMarker();
                return 0;
            }

            totalFiles = files.size();
            isDownloading = true;

            System.out.println("ModController: " + totalFiles + " file(s) queued for download");
            System.out.println("========================================");

            int successCount = 0;

            for (int i = 0; i < totalFiles; i++) {
                currentFileIndex = i + 1;
                DownloadEntry entry = files.get(i);

                System.out.println(String.format("\n[%d/%d] %s", i + 1, totalFiles, entry.name));

                boolean success = downloader.downloadEntry(entry, gameDir, status -> {
                    currentStatus = status;
                });

                if (success) {
                    successCount++;
                }

                // Small delay so progress is visible
                Thread.sleep(300);
            }

            System.out.println("\n========================================");
            System.out.println("DOWNLOADS COMPLETE: " + successCount + "/" + totalFiles + " successful");
            System.out.println("========================================");

            createMarker();
            isDownloading = false;

            return successCount;

        } catch (Exception e) {
            System.err.println("ModController: ERROR during downloads");
            e.printStackTrace();
            isDownloading = false;
            return 0;
        }
    }

    private void createMarker() {
        try {
            Path markerFile = gameDir.resolve(MARKER_FILE);
            Files.createDirectories(markerFile.getParent());
            Files.writeString(markerFile, "This marker indicates ModController has run at least once.");
            System.out.println("ModController: Marker file created.");
        } catch (IOException e) {
            System.err.println("ModController: Failed to create marker file!");
            e.printStackTrace();
        }
    }

    // Getters for progress tracking
    public String getCurrentStatus() { return currentStatus; }
    public int getCurrentFileIndex() { return currentFileIndex; }
    public int getTotalFiles() { return totalFiles; }
    public boolean isDownloading() { return isDownloading; }
}
