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
    private ProgressCallback progressCallback;

    public DownloadManager(Path gameDir, ModConfig config) {
        this(gameDir, config, null);
    }

    public DownloadManager(Path gameDir, ModConfig config, ProgressCallback progressCallback) {
        this.gameDir = gameDir;
        this.config = config;
        this.progressCallback = progressCallback;
        this.downloader = new FileDownloader(
            config.modrinthApiKey,
            config.curseforgeApiKey,
            config.backupReplacedFiles
        );
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
        System.out.println("ModController: Progress callback set!");
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
            System.out.println("ModController: runDownloads() called");
            reportProgress("Initializing", 5, "Starting download process...");
            Thread.sleep(100); // Give UI time to update
            
            System.out.println("========================================");
            System.out.println("MOD CONTROLLER: Starting downloads");
            System.out.println("========================================");

            List<DownloadEntry> files = config.downloads.stream()
                .filter(e -> e.enabled)
                .toList();

            if (files.isEmpty()) {
                System.out.println("ModController: No enabled downloads in config.");
                createMarker();
                reportProgress("Complete", 100, "No downloads configured");
                Thread.sleep(1000);
                return 0;
            }

            // Start progress tracking
            ProgressTracker.startDownload(files.size());

            System.out.println("ModController: " + files.size() + " file(s) queued for download");
            System.out.println("========================================");
            
            reportProgress("Preparing", 10, "Loading file list...");
            Thread.sleep(200); // Give UI time to update

            int successCount = 0;

            for (int i = 0; i < files.size(); i++) {
                DownloadEntry entry = files.get(i);
                
                // Calculate progress (start at 10%, end at 90%, leave 10% for completion)
                int overallProgress = 10 + (int) ((i / (float) files.size()) * 80);
                
                // Update progress tracker
                ProgressTracker.updateFile(i + 1, entry.name);
                
                String progressMessage = String.format("[%d/%d] %s", i + 1, files.size(), entry.name);
                System.out.println("\nModController: Reporting progress: " + overallProgress + "% - " + progressMessage);
                reportProgress("Downloading Files", overallProgress, progressMessage);
                Thread.sleep(100); // Give UI time to update
                
                System.out.println(String.format("\n[%d/%d] %s", i + 1, files.size(), entry.name));

                boolean success = downloader.downloadEntry(entry, gameDir);

                if (success) {
                    successCount++;
                }

                // Delay so progress is visible
                Thread.sleep(400);
            }

            reportProgress("Complete", 100,
                String.format("Downloaded %d/%d files", successCount, files.size()));
            Thread.sleep(100); // Give UI time to update

            System.out.println("\n========================================");
            System.out.println("DOWNLOADS COMPLETE: " + successCount + "/" + files.size() + " successful");
            System.out.println("========================================");

            // Finish progress tracking
            ProgressTracker.finish();

            createMarker();
            return successCount;

        } catch (Exception e) {
            System.err.println("ModController: ERROR during downloads");
            e.printStackTrace();
            reportProgress("Error", 0, "Download failed: " + e.getMessage());
            ProgressTracker.finish();
            return 0;
        }
    }

    private void reportProgress(String phase, int progress, String message) {
        System.out.println(String.format("ModController: reportProgress called - phase='%s', progress=%d%%, message='%s', callback=%s",
            phase, progress, message, (progressCallback != null ? "SET" : "NULL")));
        
        if (progressCallback != null) {
            try {
                progressCallback.onProgress(phase, progress, message);
                System.out.println("ModController: Progress callback executed successfully");
            } catch (Exception e) {
                System.err.println("ModController: Error in progress callback: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("ModController: Progress callback is NULL - not reporting");
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

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(String phase, int progressPercent, String message);
    }
}
