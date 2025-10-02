
package net.cmr.modcontroller.locator;

import net.cmr.modcontroller.config.ModConfig;
import net.cmr.modcontroller.download.DownloadManager;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Custom mod locator that runs BEFORE mod discovery.
 * This allows us to download mods before they are scanned.
 */
public class ModControllerLocator implements IModFileCandidateLocator {

    private static boolean hasRun = false;

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // Run downloads before any mod discovery happens
        if (!hasRun) {
            hasRun = true;
            runDownloads();
        }
        
        // We don't locate any mods ourselves, just trigger downloads
        // The ModsFolderLocator will pick up our downloaded files
    }

    private void runDownloads() {
        System.out.println("========================================");
        System.out.println("MOD CONTROLLER LOCATOR: Running BEFORE mod discovery");
        System.out.println("========================================");

        try {
            Path gameDir = getGameDirectory();
            ModConfig config = ModConfig.load(gameDir);
            DownloadManager downloadManager = new DownloadManager(gameDir, config);

            if (downloadManager.shouldRunDownloads()) {
                int downloaded = downloadManager.runDownloads();

                if (downloaded > 0) {
                    System.out.println("========================================");
                    System.out.println("MOD CONTROLLER: Downloaded " + downloaded + " file(s)");
                    System.out.println("Newly downloaded mods will be loaded in THIS session!");
                    System.out.println("No restart required!");
                    System.out.println("========================================");
                } else {
                    System.out.println("ModController: No files were downloaded");
                }
            } else {
                System.out.println("ModController: No downloads needed");
            }

        } catch (Exception e) {
            System.err.println("ModController: ERROR during pre-discovery downloads");
            e.printStackTrace();
        }
    }

    private Path getGameDirectory() {
        String gameDir = System.getProperty("minecraft.gameDir");
        if (gameDir == null) {
            gameDir = System.getProperty("user.dir");
        }
        return Paths.get(gameDir).toAbsolutePath();
    }

    @Override
    public int getPriority() {
        // Run with high priority to ensure we download before other locators scan
        return 1000;
    }
}
