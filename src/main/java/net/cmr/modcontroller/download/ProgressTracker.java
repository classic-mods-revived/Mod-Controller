
package net.cmr.modcontroller.download;

/**
 * Thread-safe progress tracker that can be accessed from both
 * the early loading phase and the mod loading phase.
 */
public class ProgressTracker {
    private static volatile String mainPhase = "";
    private static volatile String subPhase = "";
    private static volatile int currentFile = 0;
    private static volatile int totalFiles = 0;
    private static volatile long currentFileBytes = 0;
    private static volatile long currentFileTotal = 0;
    private static volatile boolean isActive = false;

    public static void startDownload(int total) {
        isActive = true;
        totalFiles = total;
        currentFile = 0;
        mainPhase = "Downloading Files";
        subPhase = "Initializing...";
    }

    public static void updateFile(int fileIndex, String fileName) {
        currentFile = fileIndex;
        subPhase = String.format("[%d/%d] %s", fileIndex, totalFiles, fileName);
        currentFileBytes = 0;
        currentFileTotal = 0;
    }

    public static void updateFileProgress(long bytesDownloaded, long totalBytes) {
        currentFileBytes = bytesDownloaded;
        currentFileTotal = totalBytes;
    }

    public static void finish() {
        isActive = false;
        mainPhase = "";
        subPhase = "";
    }

    // Getters
    public static boolean isActive() { return isActive; }
    public static String getMainPhase() { return mainPhase; }
    public static String getSubPhase() { return subPhase; }
    public static int getCurrentFile() { return currentFile; }
    public static int getTotalFiles() { return totalFiles; }
    public static float getMainProgress() { 
        return totalFiles > 0 ? (float) currentFile / totalFiles : 0;
    }
    public static float getFileProgress() {
        return currentFileTotal > 0 ? (float) currentFileBytes / currentFileTotal : 0;
    }
}
