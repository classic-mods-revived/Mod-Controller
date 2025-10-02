
package net.cmr.modcontroller.locator;

import com.google.gson.Gson;
import net.cmr.modcontroller.config.ModConfig;
import net.cmr.modcontroller.download.DownloadManager;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ModControllerLocator implements IModFileCandidateLocator {

    private static final Gson GSON = new Gson();
    private static boolean hasRun = false;

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        if (!hasRun) {
            hasRun = true;
            runDownloadsWithHelperWindow();
        }
    }

    private void runDownloadsWithHelperWindow() {
        System.out.println("========================================");
        System.out.println("MOD CONTROLLER LOCATOR: Running BEFORE mod discovery");
        System.out.println("========================================");

        Path progressFile = null;
        Path commandFile = null;
        try {
            Path gameDir = getGameDirectory();
            ModConfig config = ModConfig.load(gameDir);
            DownloadManager dm = new DownloadManager(gameDir, config);

            progressFile = gameDir.resolve("modcontroller_progress.json");
            commandFile = gameDir.resolve("modcontroller_command.json");
            safeDelete(commandFile);

            // Consent gate
            if (config.requireConsentBeforeDownloads) {
                writeProgress(progressFile, "Consent Needed", 0,
                        "This modpack will download files required by the author. Do you consent?", false, "consent");

                Process helper = launchHelper(progressFile.toAbsolutePath().toString(), commandFile.toAbsolutePath().toString());
                System.out.println("ModController: Consent helper launched");

                String consentDecision = waitForDecision(commandFile);
                if (!"accept".equalsIgnoreCase(consentDecision)) {
                    writeProgress(progressFile, "Exiting", 0, "Consent not granted. Exiting...", true, null);
                    Thread.sleep(300);
                    System.exit(0);
                }
                writeProgress(progressFile, "Consent Granted", 0, "Preparing downloads...", false, null);
                // reset command file for later prompts (e.g., failures)
                safeDelete(commandFile);
            }

            if (!dm.shouldRunDownloads()) {
                System.out.println("ModController: No downloads needed");
                return;
            }

            writeProgress(progressFile, "Initializing", 0, "Starting download process...", false, null);

            // Launch helper JVM for progress/failure prompts
            Process helper = launchHelper(progressFile.toAbsolutePath().toString(), commandFile.toAbsolutePath().toString());
            System.out.println("ModController: Progress helper launched");

            final Path progressPath = progressFile;
            final Path commandPath = commandFile;

            DownloadManager.ProgressCallback cb = (phase, progressPercent, message) -> {
                try {
                    writeProgress(progressPath, phase, progressPercent, message, false, null);
                } catch (Exception e) {
                    System.err.println("ModController: Failed to write progress: " + e.getMessage());
                }
            };
            dm.setProgressCallback(cb);

            DownloadManager.RunResult result = dm.runDownloads();
            int failedCount = result.failed;
            int successCount = result.success;
            int skippedCount = result.skipped;

            if (failedCount > 0) {
                writeProgress(progressPath, "Failed", 100,
                        String.format("%d download(s) failed. Continue without them or exit?", failedCount),
                        false, "prompt");

                String decision = waitForDecision(commandPath);
                if ("exit".equalsIgnoreCase(decision)) {
                    writeProgress(progressPath, "Exiting", 100, "Closing the game...", true, null);
                    Thread.sleep(300);
                    System.exit(1);
                } else {
                    writeProgress(progressPath, "Continuing", 100,
                            String.format("Continuing without %d failed download(s).", failedCount),
                            true, null);
                }
            } else {
                writeProgress(progressPath, "Complete", 100,
                        String.format("Downloaded %d file(s) (%d skipped)", successCount, skippedCount),
                        true, null);
                Thread.sleep(200);
            }

            System.out.println("========================================");
            System.out.println("MOD CONTROLLER: success=" + successCount +
                               " failed=" + failedCount + " skipped=" + skippedCount);
            System.out.println("No restart required!");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("ModController: ERROR during pre-discovery downloads");
            e.printStackTrace();
            try {
                if (progressFile != null) {
                    writeProgress(progressFile, "Error", 0, e.getMessage(), true, null);
                }
            } catch (Exception ignored) {}
        }
    }

    private void writeProgress(Path progressFile, String phase, int progress, String message, boolean done, String mode) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", phase);
        map.put("progress", progress);
        map.put("message", message);
        map.put("done", done);
        if (mode != null) map.put("mode", mode); // when "prompt", UI shows buttons
        Files.writeString(progressFile, GSON.toJson(map));
    }

    private String waitForDecision(Path commandFile) throws Exception {
        // Wait until commandFile contains {"action":"continue"} or {"action":"exit"}
        long start = System.currentTimeMillis();
        while (true) {
            if (Files.exists(commandFile)) {
                try {
                    String json = Files.readString(commandFile);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = GSON.fromJson(json, Map.class);
                    Object a = m != null ? m.get("action") : null;
                    if (a != null) return String.valueOf(a);
                } catch (Exception ignored) {}
            }
            // avoid infinite stall: but requirement is to stall until user decides
            Thread.sleep(100);
            // optional: add timeout if desired
            if (System.getProperty("modcontroller.failTimeout") != null) {
                long maxMs = Long.parseLong(System.getProperty("modcontroller.failTimeout"));
                if (System.currentTimeMillis() - start > maxMs) return "continue";
            }
        }
    }

    private Process launchHelper(String progressFilePath, String commandFilePath) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        for (String arg : getSafeJvmArgs()) cmd.add(arg);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(ProgressUiHelper.class.getName());
        cmd.add(progressFilePath);
        cmd.add(commandFilePath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        pb.directory(new File(System.getProperty("user.dir")));
        return pb.start();
    }

    private List<String> getSafeJvmArgs() {
        try {
            List<String> in = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            List<String> out = new ArrayList<>();
            for (String a : in) {
                if (a.startsWith("-agentlib:")) continue;
                if (a.startsWith("-javaagent:")) continue;
                if (a.startsWith("-Xdebug")) continue;
                out.add(a);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void safeDelete(Path p) {
        try { if (p != null && Files.exists(p)) Files.delete(p); } catch (Exception ignored) {}
    }

    private Path getGameDirectory() {
        String gameDir = System.getProperty("minecraft.gameDir");
        if (gameDir == null) gameDir = System.getProperty("user.dir");
        return Paths.get(gameDir).toAbsolutePath();
    }

    @Override
    public int getPriority() {
        return 1000;
    }
}
