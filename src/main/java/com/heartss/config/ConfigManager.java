package com.heartss.config;

import com.heartss.Heartss;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {

    private final Heartss plugin;
    private final Map<String, File> files = new HashMap<>();
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    
    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&#|#|<#)([A-Fa-f0-9]{6})>?");

    public ConfigManager(Heartss plugin) {
        this.plugin = plugin;
    }

    public void initFiles() {
        setupFile("config.yml");
        setupFile("storage.yml");
        setupFile("items.yml");
        setupFile("recipes.yml");
        setupFile("messages.yml");
        setupFile("menus.yml");
    }

    private void setupFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(name, false);
        }
        files.put(name, file);
        configs.put(name, YamlConfiguration.loadConfiguration(file));
    }

    public FileConfiguration getConfig(String name) {
        if (!configs.containsKey(name)) {
            setupFile(name);
        }
        return configs.get(name);
    }

    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        File file = files.get(name);
        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save config to " + file.getName() + " due to: " + e.getMessage());
            }
        }
    }

    public void reloadAll() {
        for (String name : files.keySet()) {
            File file = files.get(name);
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
            
            // Look for defaults in jar resources to prevent issues with missing keys
            InputStream defStream = plugin.getResource(name);
            if (defStream != null) {
                InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8);
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                loaded.setDefaults(defConfig);
            }
            configs.put(name, loaded);
        }
    }

    /**
     * Translates standard color codes '&' and hex color codes '&#ffffff' to ChatColors.
     */
    public static String color(String text) {
        if (text == null) return "";
        
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Retrieves a localized message based on player preferred language.
     */
    public String getMessage(String path, String lang, Map<String, String> placeholders) {
        FileConfiguration messagesYml = getConfig("messages.yml");
        String text = messagesYml.getString("languages." + lang + "." + path);
        
        // Fallback to English if localized path doesn't exist
        if (text == null) {
            text = messagesYml.getString("languages.en." + path);
        }
        if (text == null) {
            return "Missing message: " + path;
        }

        // Apply prefix
        String prefix = messagesYml.getString("languages." + lang + ".prefix");
        if (prefix == null) {
            prefix = messagesYml.getString("languages.en.prefix", "&4&lHeartss &r&8» ");
        }

        if (text.contains("%prefix%")) {
            text = text.replace("%prefix%", prefix);
        } else {
            text = prefix + text;
        }

        // Apply placeholders
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
                // Support alternate format
                text = text.replace("{" + entry.getKey().replace("%", "") + "}", entry.getValue());
            }
        }

        return color(text);
    }

    public String getMessage(String path, String lang) {
        return getMessage(path, lang, null);
    }
}
