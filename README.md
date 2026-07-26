# ❤️ Heartss Lifesteal Core

[![Java Version](https://img.shields.io/badge/Java-21%2B-red.svg?style=flat-square)](https://adoptium.net/)
[![Platform](https://img.shields.io/badge/Platform-Paper%20/%20Spigot-gold.svg?style=flat-square)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-2.1.0--Paper-emerald.svg?style=flat-square)](#)

**Heartss** is a high-performance, feature-rich Minecraft Lifesteal plugin designed specifically for modern Spigot and Paper servers (versions **1.21** through **1.26.2**). It supports **Java 21+** and features a dual-write backend storage system, custom tiered items with textured heads, robust anti-exploit mechanisms, an interactive GUI, and a comprehensive developer integration API.

---

## 🌟 Key Features

*   **Java 21+ Optimized**: Compiled using modern Java standards to achieve peak execution speed and minimal heap allocation.
*   **1.21 - 1.26.2 Compatibility**: Built on top of the latest Paper API to utilize performance advantages while retaining backward compatibility.
*   **Fail-Safe Dual-Write Persistence**: Automatically syncs database records synchronously to local YAML configurations. If your remote MySQL server is offline or experiencing network hiccups, the plugin automatically falls back to local storage, ensuring **zero data loss** for player stats.
*   **Custom Texture Skins & Custom Model Data**: Supports custom player head skins (base64) for items like Heart Fragments, allowing unique inventory textures without forcing complex resource packs.
*   **Resource Pack Generator**: Generates complete resource pack structures, including `pack.mcmeta` and model overrides, instantly in the `/plugins/Heartss/resourcepack/` folder.
*   **Exploit Shield**: Proactively detects and stops duplication glitches, simultaneous login exploits, frame manipulation, and unsafe async inventory modification attempts.
*   **Extermination & Revive Rituals**: Includes custom mechanics to instantly banish players upon losing all hearts, alongside configurable revive items and GUI pardon rituals.
*   **Robust Command Autocomplete**: Admins receive comprehensive tab-completion for easy server management while standard players are restricted only to their own commands.

---

## 💬 Placeholders (PlaceholderAPI)

Heartss provides first-class support for **PlaceholderAPI**. Two registration namespaces are registered: `%hearts_...%` and the legacy compatibility namespace `%lifestealz_...%`.

| Placeholder | Legacy Placeholder | Description |
| :--- | :--- | :--- |
| `%hearts_hearts%` | `%lifestealz_hearts%` | Returns the player's current heart count. |
| `%hearts_maxhearts%` | `%lifestealz_maxhearts%` | Returns the player's maximum possible heart limit. |
| `%hearts_health%` | `%lifestealz_health%` | Returns the player's actual health represented in hearts (e.g. `10`). |
| `%hearts_revived%` | `%lifestealz_revived%` | Returns the total count of times this player has been revived. |
| `%hearts_isingraceperiod%` | `%lifestealz_isingraceperiod%` | Returns `true`/`false` depending on player login protection. |
| `%hearts_graceperiodremaining%` | `%lifestealz_graceperiodremaining%` | Returns the seconds remaining in the login protection grace period. |

---

## 🛠️ Commands & Permissions

All administrative commands require either the super-permission `hearts.admin` or the granular permissions listed below.

### Player Commands

*   **`/hearts menu`**
    *   *Permission:* `hearts.use` (Default)
    *   *Description:* Opens the main interactive GUI interface where players can view their statistics, withdraw hearts, or consult crafting recipes.
*   **`/hearts help`**
    *   *Permission:* `hearts.use` (Default)
    *   *Description:* Displays the help menu with all available commands matching the sender's permissions.

### Admin Commands

*   **`/hearts set <player> <amount>`**
    *   *Permission:* `hearts.admin` or `hearts.admin.setlife`
    *   *Description:* Overwrites a player's current hearts count instantly.
*   **`/hearts add <player> <amount>`**
    *   *Permission:* `hearts.admin` or `hearts.admin.setlife`
    *   *Description:* Gives a specific number of hearts to a player.
*   **`/hearts remove <player> <amount>`**
    *   *Permission:* `hearts.admin` or `hearts.admin.setlife`
    *   *Description:* Revokes a specific number of hearts from a player.
*   **`/hearts setmax <player> <amount>`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Sets a player-specific maximum hearts limit override.
*   **`/hearts giveitem <player> <item_id> [amount]`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Spawns and gives custom items (e.g. `heart-I`, `heart-II`, custom scrolls) defined in `items.yml`.
*   **`/hearts investigate <player>`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Queries player registration connections and prints suspected alt accounts sharing identical IP subnets.
*   **`/hearts eliminate <player>`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Banishes and eliminates a player immediately, triggering death broadcasts and custom server rules.
*   **`/hearts revive <player>`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Pardons, revives, and reinstates an eliminated player with standard starting hearts.
*   **`/hearts bypass <player> [true/false]`**
    *   *Permission:* `hearts.admin`
    *   *Description:* Toggles lifesteal bypass mode for a player (exempting them from heart loss on death).
*   **`/hearts resourcepack`**
    *   *Permission:* `hearts.admin` or `hearts.admin.reload`
    *   *Description:* Exports custom configurations and custom item models to `plugins/Heartss/resourcepack/`.
*   **`/hearts reload`**
    *   *Permission:* `hearts.admin` or `hearts.admin.reload`
    *   *Description:* Reloads all server configuration files (`config.yml`, `items.yml`, `messages.yml`, `storage.yml`) safely.

---

## 💻 Developer Addon API

Other plugins can tap directly into the Lifesteal engine. Heartss ships with a dedicated developer API singleton (`HeartssAddonAPI`).

### Getting the API Instance
```java
import com.heartss.api.HeartssAddonAPI;

HeartssAddonAPI api = HeartssAddonAPI.getInstance();
```

### Reading & Modifying Player States
```java
UUID uuid = player.getUniqueId();

// Retrieve player's current virtual hearts count
int currentHearts = api.getPlayerHearts(uuid);

// Set player hearts directly
api.setPlayerHearts(uuid, 15);

// Modify player hearts with relative delta changes (add/subtract)
api.changePlayerHearts(uuid, -2);
```

### Retrieving Custom Core Items
```java
// Fetch custom items configured in items.yml with custom textures automatically applied
ItemStack vitalHeartItem = api.getCustomItem("heart-III");

if (vitalHeartItem != null) {
    player.getInventory().addItem(vitalHeartItem);
}
```

### Registering Custom External Items
```java
ItemStack customizedSword = new ItemStack(Material.DIAMOND_SWORD);
// Register an external item to the API manager
api.registerCustomItem("doomsday_sword", customizedSword);
```

### Exporting Resources
```java
// Forces a write of the custom resources structure to /plugins/Heartss/resourcepack/
boolean exported = api.exportResourcePack();
```

---

## 📁 Configurations

*   **`config.yml`**: Define global limits, starting hearts, ban command presets, grace period times, and default settings.
*   **`items.yml`**: Configure materials, custom models data, Display Names (supporting full RGB hex colors like `&#ff5555`), item lore, and Base64 texture hashes.
*   **`messages.yml`**: Offers full multi-language translations and custom localized strings for every command action or error state.
*   **`storage.yml`**: Setup local SQLite databases or direct remote high-performance MySQL engines:
    ```yaml
    backend: "MYSQL" # Or SQLite
    storage:
      mysql:
        host: "localhost"
        port: 3306
        database: "lifesteal"
        username: "root"
        password: ""
        useSSL: false
    ```

---

## 🚀 GitHub Actions Compilation & Build

A GitHub Actions build workflow has been set up at `.github/workflows/build.yml`. On every code change pushed to `main`, `master`, or `dev` branches, the server will:
1.  Check out your repository.
2.  Set up **JDK 21** using the Eclipse Temurin distribution.
3.  Cache the local Maven `.m2` repository to speed up builds.
4.  Execute a clean Maven compilation and packaging: `mvn clean package -B`
5.  Upload the compiled plugin as a JAR artifact named `Heartss-Lifesteal-Jar`.
