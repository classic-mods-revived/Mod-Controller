package net.cmr.modcontroller;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Mod("modcontroller")
public class ModController {

    private static final String MARKER_FILE = "modcontroller.marker";
    private static boolean shouldRestart = false;
    private static boolean hasShownDialog = false;
    
    // Progress tracking for downloads
    private static volatile String currentDownloadStatus = "Initializing...";
    private static volatile int currentFileIndex = 0;
    private static volatile int totalFiles = 0;
    private static volatile boolean isDownloading = true;

    // Static initializer - runs when class is first loaded
    static {
        System.out.println("========================================");
        System.out.println("MOD CONTROLLER INITIALIZING");
        System.out.println("========================================");

        shouldRestart = runDownloads();

        System.out.println("Should restart: " + shouldRestart);
        isDownloading = false; // Downloads complete
    }

    public ModController(IEventBus modEventBus) {
        System.out.println("========================================");
        System.out.println("MOD CONTROLLER CONSTRUCTOR");
        System.out.println("Should restart: " + shouldRestart);
        System.out.println("========================================");

        // Register screen event to show download progress on loading screen
        NeoForge.EVENT_BUS.addListener(this::onScreenRender);

        // Register screen open event to intercept title screen
        if (shouldRestart) {
            System.out.println("Registering screen event handler...");
            NeoForge.EVENT_BUS.addListener(this::onScreenOpen);
        }
    }

    public void onScreenRender(ScreenEvent.Render.Post event) {
        // Only show progress while downloading
        if (!isDownloading || totalFiles == 0) {
            return;
        }

        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        
        // Get font from Minecraft instance
        var font = Minecraft.getInstance().font;

        // Draw download progress overlay on any screen during loading
        int centerX = screen.width / 2;
        int centerY = screen.height / 2;

        // Semi-transparent background
        graphics.fill(centerX - 150, centerY - 40, centerX + 150, centerY + 40, 0xCC000000);

        // Title
        graphics.drawCenteredString(
            font,
            Component.literal("§6Downloading Mods..."),
            centerX,
            centerY - 25,
            0xFFFFFF
        );

        // Progress
        String progressText = String.format("File %d/%d", currentFileIndex, totalFiles);
        graphics.drawCenteredString(
            font,
            Component.literal(progressText),
            centerX,
            centerY - 10,
            0xAAAAAA
        );

        // Current file status
        graphics.drawCenteredString(
            font,
            Component.literal(currentDownloadStatus),
            centerX,
            centerY + 10,
            0xFFFFFF
        );
    }

    public void onScreenOpen(ScreenEvent.Opening event) {
        if (hasShownDialog) {
            return;
        }

        // Check if the title screen is opening
        if (event.getNewScreen() instanceof TitleScreen) {
            System.out.println("========================================");
            System.out.println("TITLE SCREEN OPENING - SHOWING RESTART DIALOG");
            System.out.println("========================================");

            hasShownDialog = true;

            // Replace the title screen with our restart screen
            event.setCanceled(true);
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new RestartScreen());
            });
        }
    }

    private static boolean runDownloads() {
        try {
            Path gameDir = getGameDirectory();
            Path markerFile = gameDir.resolve(MARKER_FILE);

            if (Files.exists(markerFile)) {
                System.out.println("Mod Controller: Not first launch, skipping downloads.");
                return false;
            }

            System.out.println("========================================");
            System.out.println("MOD CONTROLLER: FIRST LAUNCH DETECTED");
            System.out.println("Starting file downloads...");
            System.out.println("========================================");

            List<DownloadEntry> files = getDownloadList();

            if (files.isEmpty()) {
                System.out.println("Mod Controller: No files configured for download.");
                createMarker(gameDir);
                return false;
            }

            totalFiles = files.size();
            System.out.println("Mod Controller: " + totalFiles + " file(s) queued for download");
            System.out.println("========================================");

            int successCount = 0;

            for (int i = 0; i < totalFiles; i++) {
                currentFileIndex = i + 1;
                DownloadEntry entry = files.get(i);

                currentDownloadStatus = "Downloading: " + entry.name;
                System.out.println(String.format("\n[%d/%d] %s", i + 1, totalFiles, entry.name));
                System.out.println("  URL: " + entry.url);

                Path destination = gameDir.resolve(entry.destination);
                System.out.println("  Destination: " + destination.toAbsolutePath());

                try {
                    System.out.println("  Downloading...");
                    downloadFile(entry.url, destination);
                    currentDownloadStatus = "✓ " + entry.name;
                    System.out.println("  ✓ SUCCESS");
                    successCount++;
                    
                    // Small delay so users can see the progress
                    Thread.sleep(500);
                } catch (Exception e) {
                    currentDownloadStatus = "✗ Failed: " + entry.name;
                    System.err.println("  ✗ FAILED: " + e.getMessage());
                    e.printStackTrace();
                    Thread.sleep(1000);
                }
            }

            currentDownloadStatus = "Download complete!";
            System.out.println("\n========================================");
            System.out.println("DOWNLOADS COMPLETE: " + successCount + "/" + totalFiles + " successful");
            System.out.println("========================================");

            createMarker(gameDir);

            return successCount > 0;

        } catch (Exception e) {
            System.err.println("Mod Controller: ERROR during downloads");
            e.printStackTrace();
            isDownloading = false;
            return false;
        }
    }

    private static Path getGameDirectory() {
        String gameDir = System.getProperty("minecraft.gameDir");
        if (gameDir == null) {
            gameDir = System.getProperty("user.dir");
        }
        return Paths.get(gameDir).toAbsolutePath();
    }

    private static void downloadFile(String urlString, Path destination) throws IOException {
        URL url = new URL(urlString);
        Files.createDirectories(destination.getParent());

        try (InputStream in = url.openStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void createMarker(Path gameDir) {
        try {
            Path markerFile = gameDir.resolve(MARKER_FILE);
            Files.createFile(markerFile);
            System.out.println("Mod Controller: Marker file created.");
        } catch (IOException e) {
            System.err.println("Mod Controller: Failed to create marker file!");
            e.printStackTrace();
        }
    }

    private static List<DownloadEntry> getDownloadList() {
        List<DownloadEntry> files = new ArrayList<>();

        // ============================================
        // ADD YOUR MOD DOWNLOADS HERE
        // ============================================

        // Example - uncomment to test:
        files.add(new DownloadEntry(
                "The Twilight Forest",
                "https://mediafilez.forgecdn.net/files/6472/889/twilightforest-1.21.1-4.7.3196-universal.jar",
                "mods/twilightforest-1.21.1-4.7.3196-universal.jar"
        ));

        return files;
    }

    private static class DownloadEntry {
        final String name;
        final String url;
        final String destination;

        DownloadEntry(String name, String url, String destination) {
            this.name = name;
            this.url = url;
            this.destination = destination;
        }
    }

    // In-game restart screen
    public static class RestartScreen extends Screen {
        private int countdown = 10;
        private int ticks = 0;

        public RestartScreen() {
            super(Component.literal("Restart Required"));
            System.out.println(">>> RestartScreen created!");
        }

        @Override
        protected void init() {
            super.init();
            System.out.println(">>> RestartScreen.init() - adding buttons");

            // Add restart button (only one button now)
            this.addRenderableWidget(
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Restart Now"),
                    button -> performRestart()
                )
                .bounds(this.width / 2 - 100, this.height / 2 + 40, 200, 20)
                .build()
            );
        }

        @Override
        public void tick() {
            super.tick();
            ticks++;

            // Every 20 ticks = 1 second
            if (ticks % 20 == 0 && countdown > 0) {
                countdown--;

                if (countdown <= 0) {
                    performRestart();
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Render dark background
            this.renderBackground(graphics, mouseX, mouseY, partialTick);

            // Render buttons FIRST (so they're behind text)
            super.render(graphics, mouseX, mouseY, partialTick);

            // Now render text on top with proper z-level
            // Title
            graphics.drawCenteredString(
                this.font,
                Component.literal("§6§lMod Controller - Setup Complete"),
                this.width / 2,
                this.height / 2 - 60,
                0xFFFFFF
            );

            // Message
            graphics.drawCenteredString(
                this.font,
                Component.literal("New mods have been downloaded."),
                this.width / 2,
                this.height / 2 - 30,
                0xAAAAAA
            );

            graphics.drawCenteredString(
                this.font,
                Component.literal("Minecraft needs to restart to load them."),
                this.width / 2,
                this.height / 2 - 15,
                0xAAAAAA
            );

            // Countdown - only show if greater than 0
            if (countdown > 0) {
                Component countdownText;
                int color;

                if (countdown > 5) {
                    countdownText = Component.literal("Preparing to restart in " + countdown + " seconds...");
                    color = 0xFFFFFF;
                } else {
                    countdownText = Component.literal("§c§lRestarting in " + countdown + " seconds...");
                    color = 0xFF5555;
                }

                graphics.drawCenteredString(
                    this.font,
                    countdownText,
                    this.width / 2,
                    this.height / 2 + 10,
                    color
                );
            } else {
                // Show "Restarting..." when countdown reaches 0
                graphics.drawCenteredString(
                    this.font,
                    Component.literal("§c§lRestarting..."),
                    this.width / 2,
                    this.height / 2 + 10,
                    0xFF5555
                );
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        private void performRestart() {
            System.out.println(">>> Performing restart...");

            new Thread(() -> {
                try {
                    List<String> command = getRestartCommand();

                    if (command != null) {
                        System.out.println(">>> Restart command: " + command);

                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.directory(new File(System.getProperty("user.dir")));
                        pb.start();

                        Thread.sleep(1000);
                        System.exit(0);
                    } else {
                        System.err.println(">>> Could not determine restart command - manual restart required");
                    }

                } catch (Exception e) {
                    System.err.println(">>> Restart failed!");
                    e.printStackTrace();
                }
            }).start();
        }
        
        private List<String> getRestartCommand() {
            try {
                String javaHome = System.getProperty("java.home");
                String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
                String classpath = System.getProperty("java.class.path");
                String mainClass = System.getProperty("sun.java.command");
                
                if (mainClass == null) {
                    return null;
                }
                
                String[] parts = mainClass.split(" ", 2);
                String main = parts[0];
                String args = parts.length > 1 ? parts[1] : "";
                
                List<String> command = new ArrayList<>();
                command.add(javaBin);
                command.add("-cp");
                command.add(classpath);
                
                // Add JVM arguments
                try {
                    java.lang.management.RuntimeMXBean runtimeMxBean = 
                        java.lang.management.ManagementFactory.getRuntimeMXBean();
                    List<String> arguments = runtimeMxBean.getInputArguments();
                    
                    for (String arg : arguments) {
                        if (!arg.startsWith("-agentlib:") && !arg.startsWith("-javaagent:")) {
                            command.add(arg);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                command.add(main);
                
                if (!args.isEmpty()) {
                    String[] argArray = args.split(" ");
                    for (String arg : argArray) {
                        command.add(arg);
                    }
                }
                
                return command;
                
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
