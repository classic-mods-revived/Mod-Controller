
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

        Process helper = null;
        Path progressFile = null;
        try {
            Path gameDir = getGameDirectory();
            ModConfig config = ModConfig.load(gameDir);
            DownloadManager dm = new DownloadManager(gameDir, config);

            if (!dm.shouldRunDownloads()) {
                System.out.println("ModController: No downloads needed");
                return;
            }

            // Progress file path
            progressFile = gameDir.resolve("modcontroller_progress.json");
            writeProgress(progressFile, "Initializing", 0, "Starting download process...", false);

            // Launch helper JVM
            helper = launchHelper(progressFile);
            System.out.println("ModController: Progress helper launched");

            // Attach progress callback that writes to the file (read by helper)
            final Path progressPath = progressFile; // make effectively final for lambda
            DownloadManager.ProgressCallback cb = (phase, progressPercent, message) -> {
                try {
                    writeProgress(progressPath, phase, progressPercent, message, false);
                } catch (Exception e) {
                    System.err.println("ModController: Failed to write progress: " + e.getMessage());
                }
            };
            dm.setProgressCallback(cb);

            int downloaded = dm.runDownloads();

            // Finalize
            writeProgress(progressFile, "Complete", 100, "Downloaded " + downloaded + " file(s)", true);
            Thread.sleep(250);

            System.out.println("========================================");
            System.out.println("MOD CONTROLLER: Downloaded " + downloaded + " file(s)");
            System.out.println("No restart required!");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("ModController: ERROR during pre-discovery downloads");
            e.printStackTrace();
            try {
                if (progressFile != null) {
                    writeProgress(progressFile, "Error", 0, e.getMessage(), true);
                }
            } catch (Exception ignored) {}
        } finally {
            // best-effort: allow helper to exit on its own reading "done": true
        }
    }

    private void writeProgress(Path progressFile, String phase, int progress, String message, boolean done) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", phase);
        map.put("progress", progress);
        map.put("message", message);
        map.put("done", done);
        Files.writeString(progressFile, GSON.toJson(map));
    }

    private Process launchHelper(Path progressFile) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        // Build classpath (reuse current)
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        // Strip agents to avoid moddev/IDE agents leaking to child
        for (String arg : getSafeJvmArgs()) {
            cmd.add(arg);
        }
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(ProgressUiHelper.class.getName());
        cmd.add(progressFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO(); // helpful logs if needed
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
