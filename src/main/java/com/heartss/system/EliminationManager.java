package com.heartss.system;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class EliminationManager {

    private final Heartss plugin;
    private final Map<UUID, ActiveRitual> activeRituals = new HashMap<>();

    public EliminationManager(Heartss plugin) {
        this.plugin = plugin;
    }

    public static class ActiveRitual {
        public final Player reviver;
        public final Location location;
        public final String targetPlayerName;
        public final BossBar bossBar;
        public final BukkitTask task;

        public ActiveRitual(Player reviver, Location location, String targetPlayerName, BossBar bossBar, BukkitTask task) {
            this.reviver = reviver;
            this.location = location;
            this.targetPlayerName = targetPlayerName;
            this.bossBar = bossBar;
            this.task = task;
        }
    }

    /**
     * Checks if a player has hit the elimination floor and triggers ban sequences.
     */
    public void checkElimination(Player player) {
        int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
        HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());

        if (data.hearts <= minHearts) {
            triggerElimination(player);
        }
    }

    private void triggerElimination(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());
        String lang = data.language;

        // Increase consecutive elimination count
        data.consecutiveEliminations++;
        plugin.getHeartManager().savePlayer(player.getUniqueId());

        // Epic server-wide announcement
        String announce = plugin.getConfigManager().getMessage("elimination-broadcast", "en", Map.of("%player%", player.getName()));
        Bukkit.broadcastMessage(announce);

        // Subtitle alerts
        player.sendTitle(
                plugin.getConfigManager().getMessage("elimination-title", lang),
                plugin.getConfigManager().getMessage("elimination-subtitle", lang),
                10, 70, 20
        );

        // Trigger custom admin commands
        List<String> commands = config.getStringList("elimination.custom-commands");
        if (commands != null) {
            for (String cmd : commands) {
                String parsed = cmd.replace("%player%", player.getName()).replace("&player&", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        // Handle Banning
        if (config.getBoolean("elimination.ban-on-zero-hearts", true) && !config.getBoolean("elimination.disable-player-ban-on-elimination", false)) {
            executeBan(player, data);
        }
    }

    private void executeBan(Player player, HeartManager.PlayerData data) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        int durationMinutes = config.getInt("elimination.default-ban-duration", 1440);
        
        // Check permission-based overrides like: heartss.bantime.30m, heartss.bantime.2h, heartss.bantime.5d
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("heartss.bantime.")) {
                String sub = perm.substring("heartss.bantime.".length());
                int overrideVal = parseDurationToMinutes(sub);
                if (overrideVal > 0) {
                    durationMinutes = overrideVal;
                }
            }
        }

        boolean enforcePermanent = false;
        int permThreshold = config.getInt("elimination.escalating-ban-threshold", 3);
        if (permThreshold > 0 && data.consecutiveEliminations >= permThreshold) {
            enforcePermanent = true;
        }

        String kickMessage;
        Date expiryDate = null;

        if (enforcePermanent || durationMinutes == -1) {
            kickMessage = ConfigManager.color(plugin.getConfigManager().getMessage("permanent-ban-kick-message", data.language));
        } else {
            kickMessage = ConfigManager.color(plugin.getConfigManager().getMessage("ban-kick-message", data.language, Map.of("%duration%", String.valueOf(durationMinutes))));
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, durationMinutes);
            expiryDate = cal.getTime();
        }

        // Apply Player Name Ban
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), kickMessage, expiryDate, "Heartss Core");
        
        // Apply IP Ban if enabled
        if (config.getBoolean("elimination.use-ip-bans", false)) {
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
            if (ip != null) {
                Bukkit.getBanList(BanList.Type.IP).addBan(ip, kickMessage, expiryDate, "Heartss Core");
            }
        }

        // Kick online player safely
        new BukkitRunnable() {
            @Override
            public void run() {
                player.kickPlayer(kickMessage);
            }
        }.runTask(plugin);
    }

    private int parseDurationToMinutes(String input) {
        try {
            if (input.endsWith("m")) {
                return Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("h")) {
                return Integer.parseInt(input.substring(0, input.length() - 1)) * 60;
            } else if (input.endsWith("d")) {
                return Integer.parseInt(input.substring(0, input.length() - 1)) * 1440;
            } else {
                return Integer.parseInt(input);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Attempts to initiate a revive ritual using the Revive Beacon block.
     */
    public void startReviveBeaconRitual(Player reviver, Location location) {
        String lang = plugin.getHeartManager().getLanguage(reviver.getUniqueId());
        reviver.sendMessage(plugin.getConfigManager().getMessage("revive-initiated", lang));
        
        // We will store an active state waiting for them to type the banned name in chat
        activeRituals.put(reviver.getUniqueId(), new ActiveRitual(reviver, location, null, null, null));
    }

    /**
     * Handles the text typing during the chat-based revive validation flow.
     */
    public boolean handleReviveChatInput(Player reviver, String inputName) {
        ActiveRitual partial = activeRituals.get(reviver.getUniqueId());
        if (partial == null || partial.targetPlayerName != null) {
            return false; // Not in wait state or already running countdown
        }

        String lang = plugin.getHeartManager().getLanguage(reviver.getUniqueId());

        // Validate Banned List
        boolean isBanned = Bukkit.getBanList(BanList.Type.NAME).isBanned(inputName);
        if (!isBanned) {
            reviver.sendMessage(plugin.getConfigManager().getMessage("player-not-banned", lang, Map.of("%player%", inputName)));
            activeRituals.remove(reviver.getUniqueId());
            partial.location.getBlock().setType(Material.AIR); // Revert placed block
            return true;
        }

        // Check Max Revive limit configured
        UUID targetUUID = Bukkit.getOfflinePlayer(inputName).getUniqueId();
        HeartManager.PlayerData targetData = plugin.getHeartManager().getPlayerData(targetUUID);
        int maxRevives = plugin.getConfigManager().getConfig("config.yml").getInt("revive.max-revives-per-player", -1);
        if (maxRevives > 0 && targetData.totalRevives >= maxRevives) {
            if (!reviver.hasPermission("hearts.bypassrevivelimit") && !reviver.hasPermission("heartss.bypassrevivelimit")) {
                reviver.sendMessage(plugin.getConfigManager().getMessage("revive-limit-reached", lang, Map.of("%limit%", String.valueOf(maxRevives))));
                activeRituals.remove(reviver.getUniqueId());
                partial.location.getBlock().setType(Material.AIR);
                return true;
            }
        }

        // Consume cost from reviver (e.g., 2 hearts)
        HeartManager.PlayerData reviverData = plugin.getHeartManager().getPlayerData(reviver.getUniqueId());
        int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
        if (reviverData.hearts - 2 <= minHearts) {
            reviver.sendMessage(plugin.getConfigManager().getMessage("revive-fail-cost", lang, Map.of("%cost%", "2")));
            activeRituals.remove(reviver.getUniqueId());
            partial.location.getBlock().setType(Material.AIR);
            return true;
        }

        // Deduct cost and save
        plugin.getHeartManager().changeHearts(reviver.getUniqueId(), -2);

        // Start Countdown Ritual
        int countdownSeconds = plugin.getConfigManager().getConfig("config.yml").getInt("revive.beacon-countdown-seconds", 30);
        BossBar bossBar = Bukkit.createBossBar(
                ConfigManager.color("&bSummoning Lost Soul &f" + inputName + " &b..."),
                BarColor.BLUE,
                BarStyle.SOLID
        );
        bossBar.addPlayer(reviver);

        // Broadcast to server
        Bukkit.broadcastMessage(plugin.getConfigManager().getMessage("revive-ritual-broadcast", "en", Map.of("%player%", reviver.getName())));

        BukkitTask task = new BukkitRunnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    completeRevive(reviver, targetUUID, inputName, partial.location, bossBar);
                    cancel();
                    return;
                }

                // Visual particle cues at beacon location
                partial.location.getWorld().spawnParticle(
                        org.bukkit.Particle.PORTAL,
                        partial.location.clone().add(0.5, 1.2, 0.5),
                        15, 0.3, 0.3, 0.3, 0.1
                );

                bossBar.setProgress((double) remaining / countdownSeconds);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeRituals.put(reviver.getUniqueId(), new ActiveRitual(reviver, partial.location, inputName, bossBar, task));
        return true;
    }

    private void completeRevive(Player reviver, UUID targetUUID, String targetName, Location loc, BossBar bossBar) {
        String lang = plugin.getHeartManager().getLanguage(reviver.getUniqueId());
        bossBar.removeAll();

        // Remove Name Ban
        Bukkit.getBanList(BanList.Type.NAME).pardon(targetName);

        // Restore target fresh hearts stats
        int freshHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.fresh-hearts", 10);
        HeartManager.PlayerData targetData = plugin.getHeartManager().getPlayerData(targetUUID);
        targetData.hearts = freshHearts;
        targetData.totalRevives++;
        plugin.getHeartManager().savePlayer(targetUUID);

        // Break Beacon Altar
        loc.getBlock().setType(org.bukkit.Material.AIR);
        loc.getWorld().strikeLightningEffect(loc);

        // Send alert messages
        reviver.sendMessage(plugin.getConfigManager().getMessage("revive-success", lang, Map.of("%player%", targetName)));
        activeRituals.remove(reviver.getUniqueId());
    }

    /**
     * Cancels any active ritual if the placed beacon is broken or player disconnects.
     */
    public void cancelRitual(Player reviver) {
        ActiveRitual ritual = activeRituals.remove(reviver.getUniqueId());
        if (ritual != null) {
            if (ritual.task != null) ritual.task.cancel();
            if (ritual.bossBar != null) ritual.bossBar.removeAll();
            ritual.location.getBlock().setType(org.bukkit.Material.AIR);
        }
    }

    public ActiveRitual getActiveRitualAt(Location loc) {
        for (ActiveRitual r : activeRituals.values()) {
            if (r.location.equals(loc)) return r;
        }
        return null;
    }
}
