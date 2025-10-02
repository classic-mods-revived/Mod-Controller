package net.cmr.modcontroller.download.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CurseForgeAPI {
    private static final String API_BASE = "https://api.curseforge.com/v1";
    private final String apiKey;

    public CurseForgeAPI(String apiKey) {
        this.apiKey = apiKey;
    }

    public DownloadInfo getFileDownload(String projectId, String fileId) throws IOException {
        String urlString = String.format("%s/mods/%s/files/%s", API_BASE, projectId, fileId);
        HttpURLConnection conn = createConnection(urlString);

        try (InputStream in = conn.getInputStream()) {
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            DownloadInfo info = new DownloadInfo();
            info.url = data.get("downloadUrl").getAsString();
            info.filename = data.get("fileName").getAsString();
            info.size = data.get("fileLength").getAsLong();

            if (data.has("hashes")) {
                for (var hash : data.getAsJsonArray("hashes")) {
                    JsonObject hashObj = hash.getAsJsonObject();
                    int algo = hashObj.get("algo").getAsInt();
                    String value = hashObj.get("value").getAsString();
                    
                    if (algo == 1) { // SHA1
                        info.sha1 = value;
                    }
                }
            }

            return info;
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ModController/1.0");
        conn.setRequestProperty("x-api-key", apiKey);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("CurseForge API returned code " + responseCode + 
                                  ". Check your API key in config/modcontroller.json");
        }

        return conn;
    }

    public static class DownloadInfo {
        public String url;
        public String filename;
        public long size;
        public String sha1;
    }
}
