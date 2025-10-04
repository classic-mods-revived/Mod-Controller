## Hello!

---

Primarily designed for use in modpacks, this mod automates the downloading of files from the web. It's super configurable and supports both headless and gui environments, with a consent screen, error handling (by the user), and update handling (Modpack Developers: Be gentle with this, we are checking by file name, and that can mess up if the pathing changes).

Supports the Curseforge API (you need to have an API key though, and still only supports 3rd party download capable mods), the Modrinth API, and by URL, and can place the downloaded file in any place within the game directory.

## Config


Below is an example config file, that generates on load if it doesn't exist.
```json
{
  "downloads": [
    {
      "name": "Example Mod (URL)",
      "source_type": "url",
      "url": "https://example.com/mod.jar",
      "destination": "mods/example-mod.jar",
      "replace_if_exists": false,
      "enabled": false
    },
    {
      "name": "Example Mod (Modrinth)",
      "source_type": "modrinth",
      "project_id": "project-slug-or-id",
      "version_id": "version-id",
      "destination": "mods/example-modrinth.jar",
      "replace_if_exists": false,
      "enabled": false
    },
    {
      "name": "Example Mod (CurseForge)",
      "source_type": "curseforge",
      "project_id": "project-id",
      "file_id": "file-id",
      "destination": "mods/example-curseforge.jar",
      "replace_if_exists": false,
      "enabled": false
    },
    {
      "name": "Server Config",
      "source_type": "url",
      "url": "https://example.com/server-config.toml",
      "destination": "config/serverconfig.toml",
      "replace_if_exists": true,
      "enabled": false
    }
  ],
  "download_on_first_launch_only": true,
  "check_for_updates": true,
  "backup_replaced_files": true,
  "require_consent_before_downloads": true,
  "modrinth_api_key": "",
  "curseforge_api_key": ""
}
```

