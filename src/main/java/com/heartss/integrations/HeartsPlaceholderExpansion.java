package com.heartss.integrations;

import com.heartss.Heartss;
import com.heartss.system.HeartManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HeartsPlaceholderExpansion extends PlaceholderExpansion {

    protected final Heartss plugin;

    public HeartsPlaceholderExpansion(Heartss plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Heartss";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hearts";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return "0";
        }

        switch (params.toLowerCase()) {
            case "hearts":
                return String.valueOf(data.hearts);
            case "maxhearts":
                if (player.isOnline()) {
                    return String.valueOf(plugin.getHeartManager().getMaxHeartsLimit((Player) player));
                }
                return String.valueOf(data.maxHeartsOverride > 0 ? data.maxHeartsOverride : plugin.getConfigManager().getConfig("config.yml").getInt("hearts.max-hearts", 20));
            case "health":
                if (player.isOnline()) {
                    Player onlinePlayer = (Player) player;
                    return String.valueOf((int) Math.ceil(onlinePlayer.getHealth() / 2.0));
                }
                return String.valueOf(data.hearts);
            case "revived":
                return String.valueOf(data.totalRevives);
            case "isingraceperiod":
                boolean inGrace = data.gracePeriodEnd > System.currentTimeMillis();
                return String.valueOf(inGrace);
            case "graceperiodremaining":
                long remaining = (data.gracePeriodEnd - System.currentTimeMillis()) / 1000;
                return String.valueOf(Math.max(0, remaining));
            default:
                return null;
        }
    }
}
