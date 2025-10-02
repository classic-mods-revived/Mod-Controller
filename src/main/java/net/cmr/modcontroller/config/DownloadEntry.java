package net.cmr.modcontroller.config;

import com.google.gson.annotations.SerializedName;

public class DownloadEntry {
    @SerializedName("name")
    public String name;

    @SerializedName("source_type")
    public SourceType sourceType = SourceType.URL;

    // For URL downloads
    @SerializedName("url")
    public String url;

    // For API downloads
    @SerializedName("project_id")
    public String projectId;

    @SerializedName("version_id")
    public String versionId;

    @SerializedName("file_id")
    public String fileId; // CurseForge specific

    // Destination
    @SerializedName("destination")
    public String destination;

    // Optional fields
    @SerializedName("sha1")
    public String sha1Hash; // For verification

    @SerializedName("sha512")
    public String sha512Hash; // Modrinth uses sha512

    @SerializedName("version_tag")
    public String versionTag; // Track version for updates

    @SerializedName("replace_if_exists")
    public boolean replaceIfExists = false;

    @SerializedName("enabled")
    public boolean enabled = true;

    public enum SourceType {
        @SerializedName("url")
        URL,
        @SerializedName("modrinth")
        MODRINTH,
        @SerializedName("curseforge")
        CURSEFORGE
    }

    // Constructors
    public DownloadEntry() {}

    public DownloadEntry(String name, String url, String destination) {
        this.name = name;
        this.sourceType = SourceType.URL;
        this.url = url;
        this.destination = destination;
    }

    public static DownloadEntry forModrinth(String name, String projectId, String versionId, String destination) {
        DownloadEntry entry = new DownloadEntry();
        entry.name = name;
        entry.sourceType = SourceType.MODRINTH;
        entry.projectId = projectId;
        entry.versionId = versionId;
        entry.destination = destination;
        return entry;
    }

    public static DownloadEntry forCurseForge(String name, String projectId, String fileId, String destination) {
        DownloadEntry entry = new DownloadEntry();
        entry.name = name;
        entry.sourceType = SourceType.CURSEFORGE;
        entry.projectId = projectId;
        entry.fileId = fileId;
        entry.destination = destination;
        return entry;
    }
}
