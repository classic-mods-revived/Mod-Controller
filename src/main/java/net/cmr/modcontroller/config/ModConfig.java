package net.cmr.modcontroller.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/modcontroller.json";

    @SerializedName("downloads")
    public List<DownloadEntry> downloads = new ArrayList<>();

    @SerializedName("download_on_first_launch_only")
    public boolean downloadOnFirstLaunchOnly = true;

    @SerializedName("check_for_updates")
    public boolean checkForUpdates = true;

    @SerializedName("backup_replaced_files")
    public boolean backupReplacedFiles = true;

    @SerializedName("modrinth_api_key")
    public String modrinthApiKey = ""; // Optional, for rate limit increases

    @SerializedName("curseforge_api_key")
    public String curseforgeApiKey = ""; // Required for CurseForge API

    public static ModConfig load(Path gameDir) {
        Path configFile = gameDir.resolve(CONFIG_FILE);

        try {
            if (!Files.exists(configFile)) {
                System.out.println("ModController: Config not found, creating default...");
                ModConfig defaultConfig = createDefault();
                defaultConfig.save(gameDir);
                return defaultConfig;
            }

            String json = Files.readString(configFile);
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            System.out.println("ModController: Config loaded with " + config.downloads.size() + " entries");
            return config;

        } catch (Exception e) {
            System.err.println("ModController: Failed to load config, using default");
            e.printStackTrace();
            return createDefault();
        }
    }

    public void save(Path gameDir) throws IOException {
        Path configFile = gameDir.resolve(CONFIG_FILE);
        Files.createDirectories(configFile.getParent());

        String json = GSON.toJson(this);
        Files.writeString(configFile, json);
        System.out.println("ModController: Config saved to " + configFile);
    }

    private static ModConfig createDefault() {
        ModConfig config = new ModConfig();
        config.downloadOnFirstLaunchOnly = true;
        config.checkForUpdates = true;
        config.backupReplacedFiles = true;

        // Example entries (commented out by having them disabled)
        DownloadEntry urlExample = new DownloadEntry(
            "Example Mod (URL)",
            "https://example.com/mod.jar",
            "mods/example-mod.jar"
        );
        urlExample.enabled = false;
        config.downloads.add(urlExample);

        DownloadEntry modrinthExample = DownloadEntry.forModrinth(
            "Example Mod (Modrinth)",
            "project-slug-or-id",
            "version-id",
            "mods/example-modrinth.jar"
        );
        modrinthExample.enabled = false;
        config.downloads.add(modrinthExample);

        DownloadEntry curseforgeExample = DownloadEntry.forCurseForge(
            "Example Mod (CurseForge)",
            "project-id",
            "file-id",
            "mods/example-curseforge.jar"
        );
        curseforgeExample.enabled = false;
        config.downloads.add(curseforgeExample);

        // Example config file
        DownloadEntry configExample = new DownloadEntry(
            "Server Config",
            "https://example.com/server-config.toml",
            "config/serverconfig.toml"
        );
        configExample.replaceIfExists = true;
        configExample.enabled = false;
        config.downloads.add(configExample);

        return config;
    }
}
