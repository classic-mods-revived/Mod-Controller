
package net.cmr.modcontroller.download.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ModrinthAPI {
    private static final String API_BASE = "https://api.modrinth.com/v2";
    private final String apiKey;

    public ModrinthAPI(String apiKey) {
        this.apiKey = apiKey;
    }

    public DownloadInfo getVersionDownload(String versionId) throws IOException {
        String urlString = API_BASE + "/version/" + versionId;
        HttpURLConnection conn = createConnection(urlString);

        try (InputStream in = conn.getInputStream()) {
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // Get primary file
            JsonObject file = json.getAsJsonArray("files").get(0).getAsJsonObject();

            DownloadInfo info = new DownloadInfo();
            info.url = file.get("url").getAsString();
            info.filename = file.get("filename").getAsString();
            info.size = file.get("size").getAsLong();
            
            if (file.has("hashes")) {
                JsonObject hashes = file.getAsJsonObject("hashes");
                if (hashes.has("sha512")) {
                    info.sha512 = hashes.get("sha512").getAsString();
                }
                if (hashes.has("sha1")) {
                    info.sha1 = hashes.get("sha1").getAsString();
                }
            }

            return info;
        } finally {
            conn.disconnect();
        }
    }

    public DownloadInfo getProjectLatestVersion(String projectId, String gameVersion, String loader) throws IOException {
        String urlString = String.format("%s/project/%s/version?game_versions=[\"%s\"]&loaders=[\"%s\"]",
                API_BASE, projectId, gameVersion, loader);
        HttpURLConnection conn = createConnection(urlString);

        try (InputStream in = conn.getInputStream()) {
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject version = JsonParser.parseString(response).getAsJsonArray().get(0).getAsJsonObject();

            String versionId = version.get("id").getAsString();
            return getVersionDownload(versionId);
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ModController/1.0");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", apiKey);
        }

        return conn;
    }

    public static class DownloadInfo {
        public String url;
        public String filename;
        public long size;
        public String sha512;
        public String sha1;
    }
}
