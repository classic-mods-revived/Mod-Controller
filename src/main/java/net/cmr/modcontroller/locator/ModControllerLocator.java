
package net.cmr.modcontroller.locator;

import com.google.gson.Gson;
import net.cmr.modcontroller.config.ModConfig;
import net.cmr.modcontroller.download.DownloadManager;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class ModControllerLocator implements IModFileCandidateLocator {

    private static final Gson GSON = new Gson();
    private static boolean hasRun = false;
    private Process uiProcess; // track helper

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
            // Move working files under gameDir/modcontroller/
            Path mcDir = gameDir.resolve("modcontroller");
            Files.createDirectories(mcDir); // ensure directory exists BEFORE any writes

            ModConfig config = ModConfig.load(gameDir);
            DownloadManager dm = new DownloadManager(gameDir, config);

            progressFile = mcDir.resolve("modcontroller_progress.json");
            commandFile = mcDir.resolve("modcontroller_command.json");
            safeDelete(commandFile);

            // Detect logical environment (client vs dedicated server)
            final boolean isClientEnv = isClientEnvironment();

            // Consent gate
            if (config.requireConsentBeforeDownloads) {
                if (isClientEnv) {
                    System.out.println("Mod Controller: Consent required (client). Opening consent window...");
                    writeProgress(progressFile, "Consent Needed", 0,
                            "This modpack will download files required by the author. Do you consent?", false, "consent");

                    if (uiProcess == null || !uiProcess.isAlive()) {
                        uiProcess = launchHelper(progressFile.toAbsolutePath().toString(), commandFile.toAbsolutePath().toString());
                        System.out.println("Mod Controller: Consent helper launched");
                    }

                    String consentDecision = waitForDecision(commandFile);
                    if (!"accept".equalsIgnoreCase(consentDecision)) {
                        System.out.println("Mod Controller: Consent denied. Exiting.");
                        writeProgress(progressFile, "Exiting", 0, "Consent not granted. Exiting...", true, null);
                        try { if (uiProcess != null && uiProcess.isAlive()) uiProcess.destroy(); } catch (Exception ignored) {}
                        Thread.sleep(300);
                        System.exit(0);
                    }
                    System.out.println("Mod Controller: Consent granted. Preparing downloads...");
                    writeProgress(progressFile, "Consent Granted", 0, "Preparing downloads...", false, null);
                    safeDelete(commandFile);
                } else {
                    System.out.println("Mod Controller: This server will download required files. Do you consent? (y/n):");
                    String decision = promptTerminalYesNo(
                            "Mod Controller: This server will download required files. Do you consent? (y/n): ",
                            /*defaultYes=*/false);
                    if (!"y".equalsIgnoreCase(decision)) {
                        System.out.println("Mod Controller: Consent not granted. Exiting...");
                        System.exit(0);
                    }
                    System.out.println("Mod Controller: Consent granted. Preparing downloads...");
                }
            }

            if (!dm.shouldRunDownloads()) {
                System.out.println("Mod Controller: No downloads needed");
                try { if (uiProcess != null && uiProcess.isAlive()) uiProcess.destroy(); } catch (Exception ignored) {}
                return;
            }

            if (isClientEnv) {
                System.out.println("Mod Controller: Initializing downloads (client)...");
                writeProgress(progressFile, "Initializing", 0, "Starting download process...", false, null);
                if (uiProcess == null || !uiProcess.isAlive()) {
                    uiProcess = launchHelper(progressFile.toAbsolutePath().toString(), commandFile.toAbsolutePath().toString());
                    System.out.println("Mod Controller: Progress helper launched");
                }
                final Path progressPath = progressFile;
                DownloadManager.ProgressCallback cb = (phase, progressPercent, message) -> {
                    try { writeProgress(progressPath, phase, progressPercent, message, false, null); }
                    catch (Exception e) { System.err.println("Mod Controller: Failed to write progress: " + e.getMessage()); }
                };
                dm.setProgressCallback(cb);
            } else {
                System.out.println("Mod Controller: Initializing downloads (server)...");
                DownloadManager.ProgressCallback cb = (phase, progressPercent, message) -> {
                    System.out.println("Mod Controller: " + phase + " - " + message + " (" + progressPercent + "%)");
                };
                dm.setProgressCallback(cb);
            }

            DownloadManager.RunResult result = dm.runDownloads();
            int failedCount = result.failed;
            int successCount = result.success;
            int skippedCount = result.skipped;

            if (failedCount > 0) {
                if (isClientEnv) {
                    System.out.println("Mod Controller: One or more downloads failed (client). Prompting user...");
                    writeProgress(progressFile, "Failed", 100,
                            String.format("%d download(s) failed. Continue without them or exit?", failedCount),
                            false, "prompt");

                    String decision = waitForDecision(commandFile);
                    if ("exit".equalsIgnoreCase(decision)) {
                        System.out.println("Mod Controller: User chose to exit due to failures.");
                        writeProgress(progressFile, "Exiting", 100, "Closing the game...", true, null);
                        try { if (uiProcess != null && uiProcess.isAlive()) uiProcess.destroy(); } catch (Exception ignored) {}
                        Thread.sleep(300);
                        System.exit(1);
                    } else {
                        System.out.println("Mod Controller: User chose to continue without failed downloads.");
                        writeProgress(progressFile, "Continuing", 100,
                                String.format("Continuing without %d failed download(s).", failedCount),
                                true, null);
                    }
                } else {
                    System.out.println("Mod Controller: %d download(s) failed. Continue without them? (y = continue / n = exit): ");
                    String decision = promptTerminalYesNo(
                            String.format("Mod Controller: %d download(s) failed. Continue without them? (y = continue / n = exit): ", failedCount),
                            /*defaultYes=*/true);
                    if (!"y".equalsIgnoreCase(decision)) {
                        System.out.println("Mod Controller: Exiting due to failed downloads.");
                        System.exit(1);
                    }
                    System.out.println("Mod Controller: Continuing without failed downloads.");
                }
            } else {
                if (isClientEnv) {
                    System.out.println("Mod Controller: Downloads complete (client).");
                    writeProgress(progressFile, "Complete", 100,
                            String.format("Downloaded %d file(s) (%d skipped)", successCount, skippedCount),
                            true, null);
                } else {
                    System.out.println("Mod Controller: Downloads complete (server).");
                    System.out.println(String.format("Mod Controller: Downloaded %d file(s) (%d skipped).", successCount, skippedCount));
                }
                Thread.sleep(200);
            }

            try { if (uiProcess != null && uiProcess.isAlive()) uiProcess.destroy(); } catch (Exception ignored) {}

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

    // Detect if we are running on the client (graphics available) vs dedicated server.
    // Heuristics: presence of GLFW/graphics system properties or absence of 'neoforge.launcher.type=server'.
    private boolean isClientEnvironment() {
        try {
            // Strongest signal: dedicated server flag set by launchers/run configs
            String launchType = System.getProperty("neoforge.launcher.type", "");
            if ("server".equalsIgnoreCase(launchType)) return false;

            // Also honor common server flags
            if ("true".equalsIgnoreCase(System.getProperty("nogui"))) return false;
            if ("true".equalsIgnoreCase(System.getProperty("server"))) return false;

            // If Minecraft is telling us it's a server by its run directory markers, prefer server
            String gameDir = System.getProperty("minecraft.gameDir", "");
            if (gameDir != null && !gameDir.isBlank()) {
                Path gd = java.nio.file.Paths.get(gameDir);
                // Heuristic: presence of server.properties or world folder usually means server context
                if (java.nio.file.Files.exists(gd.resolve("server.properties")) ||
                    java.nio.file.Files.exists(gd.resolve("world"))) {
                    return false;
                }
            }

            // Headless graphics implies server
            if (java.awt.GraphicsEnvironment.isHeadless()) return false;

            // Otherwise assume client
            return true;
        } catch (Throwable t) {
            // Fail closed: treat as server to avoid opening UI
            return false;
        }
    }

    // Simple terminal prompt that returns "y" or "n".
    private String promptTerminalYesNo(String prompt, boolean defaultYes) {
        try {
            System.out.print(prompt);
            System.out.flush();
            var in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            String line = in.readLine();
            if (line == null || line.isBlank()) return defaultYes ? "y" : "n";
            line = line.trim().toLowerCase();
            if (line.startsWith("y")) return "y";
            if (line.startsWith("n")) return "n";
            // re-prompt once
            System.out.print("(y/n): ");
            System.out.flush();
            line = in.readLine();
            if (line == null || line.isBlank()) return defaultYes ? "y" : "n";
            line = line.trim().toLowerCase();
            return line.startsWith("y") ? "y" : "n";
        } catch (Exception e) {
            return defaultYes ? "y" : "n";
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

        Path gameDir = getGameDirectory();
        Path mcDir = gameDir.resolve("modcontroller");
        Files.createDirectories(mcDir); // ensure base dir exists for helper artifacts

        Path helperJar = mcDir.resolve("modcontroller-ui.jar");
        Path helperLibDir = mcDir.resolve("modcontroller-ui-libs");
        Path gsonJar = helperLibDir.resolve("gson.jar");

        try {
            Files.deleteIfExists(helperJar);
            Files.createDirectories(helperLibDir);
            extractHelperJar(helperJar);
            extractGsonTo(gsonJar);
        } catch (Exception e) {
            System.err.println("ModController: Failed to refresh helper or libs: " + e.getMessage());
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        for (String arg : getSafeJvmArgs()) cmd.add(arg);
        cmd.add("-Djava.awt.headless=false");
        cmd.add("-Dapple.awt.application.name=Mod Controller");
        cmd.add("-Dapple.awt.application.appearance=system");

        // Use -cp: helper + gson
        String cp = helperJar.toAbsolutePath() + File.pathSeparator + gsonJar.toAbsolutePath();
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add("net.cmr.modcontroller.locator.ProgressUiHelper");
        cmd.add(progressFilePath); cmd.add(commandFilePath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // Remove redirects for err/out logs as requested
        pb.directory(new File(System.getProperty("user.dir")));
        System.out.println("ModController: launching UI helper: " + String.join(" ", cmd));
        try {
            return pb.start();
        } catch (Exception ex) {
            System.err.println("ModController: Failed to start helper process: " + ex.getMessage());
            tryInlineConsentDialog(progressFilePath, commandFilePath);
            return null;
        }
    }

    private void extractGsonTo(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (var in = ModControllerLocator.class.getResourceAsStream("/modcontroller/gson.jar")) {
            if (in == null) throw new IOException("Resource '/modcontroller/gson.jar' not found");
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Extracts a minimal shaded helper jar shipped as a resource
    private void extractHelperJar(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (var in = ModControllerLocator.class.getResourceAsStream("/modcontroller/modcontroller-ui.jar")) {
            if (in == null) {
                throw new IOException("Resource '/modcontroller/modcontroller-ui.jar' not found in mod jar");
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void tryInlineConsentDialog(String progressFilePath, String commandFilePath) {
        try {
            // Only for the consent/prompt phases. Non-blocking: write a minimal prompt inline.
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("ModController: Headless environment; inline dialog unavailable.");
                return;
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignore) {}
                String[] options = {"Continue", "Exit"};
                int choice = javax.swing.JOptionPane.showOptionDialog(
                        null,
                        "Downloads are required. Continue?",
                        "Mod Controller",
                        javax.swing.JOptionPane.DEFAULT_OPTION,
                        javax.swing.JOptionPane.QUESTION_MESSAGE,
                        null,
                        options, options[0]
                );
                Path commandFile = Path.of(commandFilePath);
                String action = (choice == 1) ? "exit" : "continue";
                try {
                    Files.writeString(commandFile, "{\"action\":\"" + action + "\"}");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            System.err.println("ModController: Inline dialog failed: " + e.getMessage());
        }
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
