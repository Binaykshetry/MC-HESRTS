package com.heartss.system;

import com.heartss.Heartss;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class DiscordWebhookManager {

    private final Heartss plugin;

    public DiscordWebhookManager(Heartss plugin) {
        // Trigger synchronization with GitHub
        this.plugin = plugin;
    }

    /**
     * Sends a custom payload to the Discord Webhook asynchronously.
     */
    private void sendWebhookAsync(String payload) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
                if (!config.getBoolean("discord-webhook.enabled", false)) return;

                String webhookUrl = config.getString("discord-webhook.url", "");
                if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_DISCORD_WEBHOOK_URL")) return;

                URL url = new URL(webhookUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Heartss Lifesteal Webhook Client)");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Discord Webhook responded with code " + code);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send Discord Webhook: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Escapes critical characters to ensure valid JSON payload formatting.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Sends a Discord webhook message when a player dies.
     */
    public void sendDeathWebhook(Player victim, Player killer, int loss, int gain) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        if (!config.getBoolean("discord-webhook.events.player-death", true)) return;

        String username = escapeJson(config.getString("discord-webhook.username", "Heartss Lifesteal"));
        String avatarUrl = escapeJson(config.getString("discord-webhook.avatar-url", ""));

        String victimName = escapeJson(victim.getName());
        String title;
        String description;
        String fieldsJson;

        if (killer != null) {
            String killerName = escapeJson(killer.getName());
            title = "⚔️ Player Slain";
            description = "**" + victimName + "** was killed by **" + killerName + "**!";
            fieldsJson = "[" +
                    "{\"name\": \"Victim\", \"value\": \"" + victimName + "\", \"inline\": true}," +
                    "{\"name\": \"Killer\", \"value\": \"" + killerName + "\", \"inline\": true}," +
                    "{\"name\": \"Victim Heart Loss\", \"value\": \"-" + loss + " ❤\", \"inline\": true}," +
                    "{\"name\": \"Killer Heart Gain\", \"value\": \"+" + gain + " ❤\", \"inline\": true}" +
                    "]";
        } else {
            title = "💀 Environmental Death";
            description = "**" + victimName + "** died of natural/environmental causes!";
            fieldsJson = "[" +
                    "{\"name\": \"Victim\", \"value\": \"" + victimName + "\", \"inline\": true}," +
                    "{\"name\": \"Heart Loss\", \"value\": \"-" + loss + " ❤\", \"inline\": true}" +
                    "]";
        }

        String avatarPart = avatarUrl.isEmpty() ? "" : "\"avatar_url\": \"" + avatarUrl + "\", ";
        String payload = "{" +
                avatarPart +
                "\"username\": \"" + username + "\"," +
                "\"embeds\": [{" +
                "\"title\": \"" + title + "\"," +
                "\"description\": \"" + description + "\"," +
                "\"color\": 16724821," + // rose-red
                "\"thumbnail\": {\"url\": \"https://minotar.net/helm/" + victimName + "/100.png\"}," +
                "\"fields\": " + fieldsJson + "," +
                "\"footer\": {\"text\": \"Heartss Lifesteal Core\"}" +
                "}]" +
                "}";

        sendWebhookAsync(payload);
    }

    /**
     * Sends a Discord webhook message when a player is eliminated (reaches 0 hearts).
     */
    public void sendEliminationWebhook(Player player, int consecutiveCount) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        if (!config.getBoolean("discord-webhook.events.player-elimination", true)) return;

        String username = escapeJson(config.getString("discord-webhook.username", "Heartss Lifesteal"));
        String avatarUrl = escapeJson(config.getString("discord-webhook.avatar-url", ""));
        String playerName = escapeJson(player.getName());

        String title = "🚨 Player Eliminated";
        String description = "⛔ **" + playerName + "** has reached 0 hearts and was **ELIMINATED** from the server!";

        String fieldsJson = "[" +
                "{\"name\": \"Player\", \"value\": \"" + playerName + "\", \"inline\": true}," +
                "{\"name\": \"Consecutive Eliminations\", \"value\": \"" + consecutiveCount + "\", \"inline\": true}" +
                "]";

        String avatarPart = avatarUrl.isEmpty() ? "" : "\"avatar_url\": \"" + avatarUrl + "\", ";
        String payload = "{" +
                avatarPart +
                "\"username\": \"" + username + "\"," +
                "\"embeds\": [{" +
                "\"title\": \"" + title + "\"," +
                "\"description\": \"" + description + "\"," +
                "\"color\": 10027008," + // dark red
                "\"thumbnail\": {\"url\": \"https://minotar.net/helm/" + playerName + "/100.png\"}," +
                "\"fields\": " + fieldsJson + "," +
                "\"footer\": {\"text\": \"Heartss Lifesteal Core\"}" +
                "}]" +
                "}";

        sendWebhookAsync(payload);
    }

    /**
     * Sends a Discord webhook message when a player is revived.
     */
    public void sendReviveWebhook(Player reviver, String targetName, int startingHearts) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        if (!config.getBoolean("discord-webhook.events.player-revive", true)) return;

        String username = escapeJson(config.getString("discord-webhook.username", "Heartss Lifesteal"));
        String avatarUrl = escapeJson(config.getString("discord-webhook.avatar-url", ""));

        String reviverName = escapeJson(reviver.getName());
        String victimName = escapeJson(targetName);

        String title = "✨ Soul Revived";
        String description = "😇 **" + reviverName + "** has successfully completed a ritual to revive **" + victimName + "**!";

        String fieldsJson = "[" +
                "{\"name\": \"Reviver\", \"value\": \"" + reviverName + "\", \"inline\": true}," +
                "{\"name\": \"Revived Player\", \"value\": \"" + victimName + "\", \"inline\": true}," +
                "{\"name\": \"Starting Hearts\", \"value\": \"" + startingHearts + " ❤\", \"inline\": true}" +
                "]";

        String avatarPart = avatarUrl.isEmpty() ? "" : "\"avatar_url\": \"" + avatarUrl + "\", ";
        String payload = "{" +
                avatarPart +
                "\"username\": \"" + username + "\"," +
                "\"embeds\": [{" +
                "\"title\": \"" + title + "\"," +
                "\"description\": \"" + description + "\"," +
                "\"color\": 3394815," + // bright aqua
                "\"thumbnail\": {\"url\": \"https://minotar.net/helm/" + victimName + "/100.png\"}," +
                "\"fields\": " + fieldsJson + "," +
                "\"footer\": {\"text\": \"Heartss Lifesteal Core\"}" +
                "}]" +
                "}";

        sendWebhookAsync(payload);
    }

    /**
     * Sends a Discord webhook message when a player consumes a heart item to gain maximum hearts.
     */
    public void sendHeartConsumeWebhook(Player player, String tier, int heartsAdded, int currentHearts) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        if (!config.getBoolean("discord-webhook.events.heart-consume", true)) return;

        String username = escapeJson(config.getString("discord-webhook.username", "Heartss Lifesteal"));
        String avatarUrl = escapeJson(config.getString("discord-webhook.avatar-url", ""));
        String playerName = escapeJson(player.getName());

        String title = "💖 Heart Consumed";
        String description = "**" + playerName + "** consumed a custom **" + escapeJson(tier) + "** item!";

        String fieldsJson = "[" +
                "{\"name\": \"Player\", \"value\": \"" + playerName + "\", \"inline\": true}," +
                "{\"name\": \"Type/Tier\", \"value\": \"" + escapeJson(tier) + "\", \"inline\": true}," +
                "{\"name\": \"Hearts Gained\", \"value\": \"+" + heartsAdded + " ❤\", \"inline\": true}," +
                "{\"name\": \"New Total\", \"value\": \"" + currentHearts + " ❤\", \"inline\": true}" +
                "]";

        String avatarPart = avatarUrl.isEmpty() ? "" : "\"avatar_url\": \"" + avatarUrl + "\", ";
        String payload = "{" +
                avatarPart +
                "\"username\": \"" + username + "\"," +
                "\"embeds\": [{" +
                "\"title\": \"" + title + "\"," +
                "\"description\": \"" + description + "\"," +
                "\"color\": 16738028," + // pink
                "\"thumbnail\": {\"url\": \"https://minotar.net/helm/" + playerName + "/100.png\"}," +
                "\"fields\": " + fieldsJson + "," +
                "\"footer\": {\"text\": \"Heartss Lifesteal Core\"}" +
                "}]" +
                "}";

        sendWebhookAsync(payload);
    }
}
