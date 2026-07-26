package com.heartss.system;

import com.heartss.Heartss;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class HeartManager {

    private final Heartss plugin;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    
    private Connection connection;
    private boolean useDatabase = false;

    public HeartManager(Heartss plugin) {
        this.plugin = plugin;
    }

    public static class PlayerData {
        public int hearts;
        public int maxHeartsOverride;
        public int consecutiveEliminations;
        public int totalRevives;
        public String language;
        public long gracePeriodEnd;

        public PlayerData(int hearts, int maxHeartsOverride, int consecutiveEliminations, int totalRevives, String language) {
            this(hearts, maxHeartsOverride, consecutiveEliminations, totalRevives, language, 0L);
        }

        public PlayerData(int hearts, int maxHeartsOverride, int consecutiveEliminations, int totalRevives, String language, long gracePeriodEnd) {
            this.hearts = hearts;
            this.maxHeartsOverride = maxHeartsOverride;
            this.consecutiveEliminations = consecutiveEliminations;
            this.totalRevives = totalRevives;
            this.language = language;
            this.gracePeriodEnd = gracePeriodEnd;
        }
    }

    private synchronized Connection getConnection() {
        if (!useDatabase) {
            return null;
        }
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                reconnect();
            }
        } catch (SQLException e) {
            reconnect();
        }
        return connection;
    }

    private synchronized void reconnect() {
        org.bukkit.configuration.file.FileConfiguration storageCfg = plugin.getConfigManager().getConfig("storage.yml");
        String backendStr = storageCfg.getString("backend");
        if (backendStr == null) {
            storageCfg = plugin.getConfigManager().getConfig("config.yml");
            backendStr = storageCfg.getString("storage.backend", "SQLite");
        }
        String backend = backendStr.toUpperCase();

        try {
            if (connection != null && !connection.isClosed()) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }

            if (backend.equals("SQLITE")) {
                File dataFolder = new File(plugin.getDataFolder(), "data.db");
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            } else {
                String host = storageCfg.getString("storage.mysql.host");
                if (host == null) host = storageCfg.getString("mysql.host", "localhost");
                
                int port = storageCfg.getInt("storage.mysql.port", 0);
                if (port == 0) port = storageCfg.getInt("mysql.port", 3306);
                
                String db = storageCfg.getString("storage.mysql.database");
                if (db == null) db = storageCfg.getString("mysql.database", "lifesteal");
                
                String user = storageCfg.getString("storage.mysql.username");
                if (user == null) user = storageCfg.getString("mysql.username", "root");
                
                String pass = storageCfg.getString("storage.mysql.password");
                if (pass == null) pass = storageCfg.getString("mysql.password", "");
                
                boolean useSSLBool = storageCfg.getBoolean("storage.mysql.useSSL", false) || storageCfg.getBoolean("mysql.useSSL", false);
                String useSSL = useSSLBool ? "true" : "false";

                String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + useSSL + 
                             "&autoReconnect=true&failOverReadOnly=false&maxReconnects=5&connectTimeout=5000&socketTimeout=30000";

                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(url, user, pass);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Database connection/reconnection failed: " + e.getMessage());
            connection = null;
        }
    }

    public void setupStorage() {
        org.bukkit.configuration.file.FileConfiguration storageCfg = plugin.getConfigManager().getConfig("storage.yml");
        String backendStr = storageCfg.getString("backend");
        if (backendStr == null) {
            storageCfg = plugin.getConfigManager().getConfig("config.yml");
            backendStr = storageCfg.getString("storage.backend", "SQLite");
        }
        String backend = backendStr.toUpperCase();
        
        if (backend.equals("SQLITE") || backend.equals("MYSQL")) {
            useDatabase = true;
            reconnect();
            
            Connection conn = getConnection();
            if (conn != null) {
                try (Statement statement = conn.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS heartss_players (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "hearts INT, " +
                            "max_hearts_override INT, " +
                            "consecutive_eliminations INT, " +
                            "total_revives INT, " +
                            "language VARCHAR(10), " +
                            "grace_period_end BIGINT DEFAULT 0" +
                            ")");
                    try {
                        statement.executeUpdate("ALTER TABLE heartss_players ADD COLUMN grace_period_end BIGINT DEFAULT 0");
                    } catch (Exception ignored) {}
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to verify/initialize database table structure: " + e.getMessage());
                }
                
                // Keep the database connection alive and fresh to prevent any wait_timeout dropouts
                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                    try {
                        Connection aliveConn = getConnection();
                        if (aliveConn != null && !aliveConn.isClosed()) {
                            try (Statement s = aliveConn.createStatement()) {
                                s.executeQuery("SELECT 1;");
                            }
                        }
                    } catch (Exception ignored) {}
                }, 6000L, 6000L); // Runs every 5 minutes (6000 ticks)
                
                plugin.getLogger().info("Successfully connected to database storage backend (" + backend + ").");
            } else {
                plugin.getLogger().log(Level.SEVERE, "Failed to connect to configured SQL database. Falling back to local YAML storage backup.");
                useDatabase = false;
            }
        } else {
            plugin.getLogger().info("Using flat-file YAML storage backend.");
        }
    }

    public void shutdownStorage() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
    }

    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
            applyHeartsToPlayer(player);
        }
    }

    public void saveOnlinePlayers() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        if (!cache.containsKey(uuid)) {
            loadPlayer(uuid);
        }
        return cache.get(uuid);
    }

    private void saveToLocalYaml(UUID uuid, PlayerData data) {
        try {
            File playerFile = new File(plugin.getDataFolder() + "/players/", uuid + ".yml");
            if (!playerFile.getParentFile().exists()) playerFile.getParentFile().mkdirs();
            org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
            yaml.set("hearts", data.hearts);
            yaml.set("max_hearts_override", data.maxHeartsOverride);
            yaml.set("consecutive_eliminations", data.consecutiveEliminations);
            yaml.set("total_revives", data.totalRevives);
            yaml.set("language", data.language);
            yaml.set("grace_period_end", data.gracePeriodEnd);
            yaml.save(playerFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving player data to YAML file: " + e.getMessage());
        }
    }

    private void saveToDatabaseAsync(UUID uuid, PlayerData data) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO heartss_players (uuid, hearts, max_hearts_override, consecutive_eliminations, total_revives, language, grace_period_end) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE hearts = VALUES(hearts), max_hearts_override = VALUES(max_hearts_override), " +
                                    "consecutive_eliminations = VALUES(consecutive_eliminations), total_revives = VALUES(total_revives), language = VALUES(language), grace_period_end = VALUES(grace_period_end)")) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, data.hearts);
                        ps.setInt(3, data.maxHeartsOverride);
                        ps.setInt(4, data.consecutiveEliminations);
                        ps.setInt(5, data.totalRevives);
                        ps.setString(6, data.language);
                        ps.setLong(7, data.gracePeriodEnd);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        try (PreparedStatement psSqlite = conn.prepareStatement(
                                "INSERT OR REPLACE INTO heartss_players (uuid, hearts, max_hearts_override, consecutive_eliminations, total_revives, language, grace_period_end) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                            psSqlite.setString(1, uuid.toString());
                            psSqlite.setInt(2, data.hearts);
                            psSqlite.setInt(3, data.maxHeartsOverride);
                            psSqlite.setInt(4, data.consecutiveEliminations);
                            psSqlite.setInt(5, data.totalRevives);
                            psSqlite.setString(6, data.language);
                            psSqlite.setLong(7, data.gracePeriodEnd);
                            psSqlite.executeUpdate();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public void loadPlayer(UUID uuid) {
        int defHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.default-hearts", 10);
        String defLang = plugin.getConfigManager().getConfig("messages.yml").getString("default-language", "en");

        long defaultGpEnd = 0L;
        if (plugin.getConfigManager().getConfig("config.yml").getBoolean("grace-period.enabled", true)) {
            int duration = plugin.getConfigManager().getConfig("config.yml").getInt("grace-period.duration-seconds", 120);
            defaultGpEnd = System.currentTimeMillis() + (duration * 1000L);
        }

        // 1. Establish the ultimate fail-safe baseline data from YAML file if exists
        PlayerData localFallbackData = new PlayerData(defHearts, -1, 0, 0, defLang, defaultGpEnd);
        File playerFile = new File(plugin.getDataFolder() + "/players/", uuid + ".yml");
        if (playerFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(playerFile);
                localFallbackData = new PlayerData(
                        yaml.getInt("hearts", defHearts),
                        yaml.getInt("max_hearts_override", -1),
                        yaml.getInt("consecutive_eliminations", 0),
                        yaml.getInt("total_revives", 0),
                        yaml.getString("language", defLang),
                        yaml.getLong("grace_period_end", 0L)
                );
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load local backup for " + uuid + " (" + e.getMessage() + ")");
            }
        }

        // 2. Try loading from the SQL database if database support is enabled
        if (useDatabase) {
            try {
                Connection conn = getConnection();
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM heartss_players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                PlayerData dbData = new PlayerData(
                                        rs.getInt("hearts"),
                                        rs.getInt("max_hearts_override"),
                                        rs.getInt("consecutive_eliminations"),
                                        rs.getInt("total_revives"),
                                        rs.getString("language"),
                                        rs.getLong("grace_period_end")
                                );
                                cache.put(uuid, dbData);
                                // Ensure local replica is perfectly identical with DB
                                saveToLocalYaml(uuid, dbData);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Database load failed for " + uuid + " (" + e.getMessage() + "). Falling back to local YAML replica to ensure zero data loss.");
            }
        }

        // 3. Fallback to local replica (or default profile for new players)
        cache.put(uuid, localFallbackData);
        
        // Save back to DB asynchronously if it was a default/fresh record
        if (useDatabase) {
            saveToDatabaseAsync(uuid, localFallbackData);
        }
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        // 1. Dual-write: Always save to local YAML backup first to prevent any data loss
        saveToLocalYaml(uuid, data);

        // 2. Try saving to SQL database
        if (useDatabase) {
            try {
                Connection conn = getConnection();
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO heartss_players (uuid, hearts, max_hearts_override, consecutive_eliminations, total_revives, language, grace_period_end) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE hearts = VALUES(hearts), max_hearts_override = VALUES(max_hearts_override), " +
                                    "consecutive_eliminations = VALUES(consecutive_eliminations), total_revives = VALUES(total_revives), language = VALUES(language), grace_period_end = VALUES(grace_period_end)")) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, data.hearts);
                        ps.setInt(3, data.maxHeartsOverride);
                        ps.setInt(4, data.consecutiveEliminations);
                        ps.setInt(5, data.totalRevives);
                        ps.setString(6, data.language);
                        ps.setLong(7, data.gracePeriodEnd);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        // SQLite syntax fallback
                        try (PreparedStatement psSqlite = conn.prepareStatement(
                                "INSERT OR REPLACE INTO heartss_players (uuid, hearts, max_hearts_override, consecutive_eliminations, total_revives, language, grace_period_end) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                            psSqlite.setString(1, uuid.toString());
                            psSqlite.setInt(2, data.hearts);
                            psSqlite.setInt(3, data.maxHeartsOverride);
                            psSqlite.setInt(4, data.consecutiveEliminations);
                            psSqlite.setInt(5, data.totalRevives);
                            psSqlite.setString(6, data.language);
                            psSqlite.setLong(7, data.gracePeriodEnd);
                            psSqlite.executeUpdate();
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error saving data for player " + uuid + " to SQL database (" + e.getMessage() + "). Saved safely to local fallback database.");
            }
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    /**
     * Resolves the maximum heart capacity for a player based on global settings, commands overrides,
     * and permission nodes like 'heartss.maxhearts.25'.
     */
    public int getMaxHeartsLimit(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data.maxHeartsOverride > 0) {
            return data.maxHeartsOverride;
        }

        int maxLimit = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.max-hearts", 20);

        // Try querying via the LuckPerms API Hook if enabled
        if (plugin.isLuckPermsEnabled()) {
            java.util.OptionalInt lpMax = com.heartss.integrations.LuckPermsHook.getMaxHeartsPermission(player.getUniqueId());
            if (lpMax.isPresent()) {
                if (lpMax.getAsInt() > maxLimit) {
                    maxLimit = lpMax.getAsInt();
                }
            }
        }

        // Fallback or auxiliary check: Scan effective permissions
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("hearts.maxhearts.") || perm.startsWith("heartss.maxhearts.")) {
                try {
                    int prefixLen = perm.startsWith("heartss.maxhearts.") ? "heartss.maxhearts.".length() : "hearts.maxhearts.".length();
                    int val = Integer.parseInt(perm.substring(prefixLen));
                    if (val > maxLimit) {
                        maxLimit = val; // Find highest permission value
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return maxLimit;
    }

    public void applyHeartsToPlayer(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        
        // Ensure bounds validation
        int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
        int maxHearts = getMaxHeartsLimit(player);

        if (data.hearts < minHearts) data.hearts = minHearts;
        if (data.hearts > maxHearts) data.hearts = maxHearts;

        double hp = data.hearts * 2.0;
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(hp);
        } else {
            player.setMaxHealth(hp);
        }

        // Limit current health if it exceeds max
        if (player.getHealth() > hp) {
            player.setHealth(hp);
        }
    }

    public void setHearts(UUID uuid, int hearts) {
        PlayerData data = getPlayerData(uuid);
        data.hearts = hearts;
        
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            applyHeartsToPlayer(onlinePlayer);
        } else {
            savePlayer(uuid);
        }
    }

    public void changeHearts(UUID uuid, int delta) {
        PlayerData data = getPlayerData(uuid);
        setHearts(uuid, data.hearts + delta);
    }

    public String getLanguage(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data != null ? data.language : "en";
    }

    public void setLanguage(UUID uuid, String lang) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.language = lang;
            savePlayer(uuid);
        }
    }
}
