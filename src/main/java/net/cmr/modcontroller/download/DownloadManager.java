package net.cmr.modcontroller.download;

import net.cmr.modcontroller.config.DownloadEntry;
import net.cmr.modcontroller.config.ModConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DownloadManager {
    private static final String MARKER_FILE = "modcontroller/modcontroller.marker";

    public static final class RunResult {
        public final int success;
        public final int failed;
        public final int skipped;
        public RunResult(int success, int failed, int skipped) {
            this.success = success;
            this.failed = failed;
            this.skipped = skipped;
        }
    }

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

    public RunResult runDownloads() {
        try {
            System.out.println("ModController: runDownloads() called");
            reportProgress("Initializing", 5, "Starting download process...");
            Thread.sleep(100);

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
                Thread.sleep(300);
                return new RunResult(0, 0, 0);
            }

            ProgressTracker.startDownload(files.size());
            System.out.println("ModController: " + files.size() + " file(s) queued for download");
            System.out.println("========================================");

            int successCount = 0;
            int failCount = 0;
            int skipCount = 0;

            for (int i = 0; i < files.size(); i++) {
                DownloadEntry entry = files.get(i);

                int overallProgress = 10 + (int) ((i / (float) files.size()) * 80);
                ProgressTracker.updateFile(i + 1, entry.name);
                String progressMessage = String.format("[%d/%d] %s", i + 1, files.size(), entry.name);
                reportProgress("Downloading Files", overallProgress, progressMessage);

                System.out.println(String.format("\n[%d/%d] %s", i + 1, files.size(), entry.name));

                FileDownloader.Result result = downloader.downloadEntry(entry, gameDir);
                switch (result) {
                    case SUCCESS -> successCount++;
                    case FAILED -> failCount++;
                    case SKIPPED -> skipCount++;
                }

                Thread.sleep(150);
            }

            reportProgress("Complete", 100,
                String.format("Downloaded %d/%d files (%d skipped, %d failed)",
                    successCount, files.size(), skipCount, failCount));

            System.out.println("\n========================================");
            System.out.println("DOWNLOADS COMPLETE: success=" + successCount +
                               " failed=" + failCount + " skipped=" + skipCount);
            System.out.println("========================================");

            ProgressTracker.finish();
            createMarker();

            return new RunResult(successCount, failCount, skipCount);

        } catch (Exception e) {
            System.err.println("ModController: ERROR during downloads");
            e.printStackTrace();
            reportProgress("Error", 0, "Download failed: " + e.getMessage());
            ProgressTracker.finish();
            return new RunResult(0, 1, 0); // signal a failure occurred
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
