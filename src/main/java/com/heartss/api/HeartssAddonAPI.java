package com.heartss.api;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class HeartssAddonAPI {

    private static HeartssAddonAPI instance;
    private final Heartss plugin;
    private final Map<String, ItemStack> registeredCustomItems = new HashMap<>();

    public HeartssAddonAPI(Heartss plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static HeartssAddonAPI getInstance() {
        if (instance == null) {
            Heartss pluginInstance = (Heartss) Bukkit.getPluginManager().getPlugin("Heartss");
            if (pluginInstance != null) {
                instance = new HeartssAddonAPI(pluginInstance);
            }
        }
        return instance;
    }

    /**
     * Get player's current virtual hearts.
     */
    public int getPlayerHearts(UUID playerUUID) {
        return plugin.getHeartManager().getPlayerData(playerUUID).hearts;
    }

    /**
     * Set player's current virtual hearts.
     */
    public void setPlayerHearts(UUID playerUUID, int amount) {
        plugin.getHeartManager().setHearts(playerUUID, amount);
    }

    /**
     * Modify player's current hearts.
     */
    public void changePlayerHearts(UUID playerUUID, int delta) {
        plugin.getHeartManager().changeHearts(playerUUID, delta);
    }

    /**
     * Registers a custom item that external plugins can use or give.
     */
    public void registerCustomItem(String itemId, ItemStack item) {
        registeredCustomItems.put(itemId, item);
    }

    /**
     * Gets a custom item by its configured ID.
     */
    public ItemStack getCustomItem(String itemId) {
        if (registeredCustomItems.containsKey(itemId)) {
            return registeredCustomItems.get(itemId).clone();
        }

        FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");
        String path = "items." + itemId;
        if (!itemsYml.contains(path)) {
            path = "items.scrolls." + itemId;
        }
        if (!itemsYml.contains(path)) {
            if (itemsYml.contains("items." + itemId)) {
                path = "items." + itemId;
            } else {
                return null;
            }
        }

        String matName = itemsYml.getString(path + ".material", "RED_DYE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.RED_DYE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ConfigManager.color(itemsYml.getString(path + ".display-name")));
            List<String> lore = itemsYml.getStringList(path + ".lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ConfigManager.color(line));
            }
            meta.setLore(coloredLore);

            int customModelData = itemsYml.getInt(path + ".custom-model-data", -1);
            if (customModelData != -1) {
                meta.setCustomModelData(customModelData);
            }

            // Support textured skulls for Player Head hearts
            String base64Texture = itemsYml.getString(path + ".texture-base64", "");
            if (mat == Material.PLAYER_HEAD && !base64Texture.isEmpty() && meta instanceof SkullMeta) {
                applySkullTexture((SkullMeta) meta, base64Texture);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Helper to apply custom textured skins to a SkullMeta/Player Head.
     */
    public static void applySkullTexture(SkullMeta skullMeta, String base64) {
        if (base64 == null || base64.isEmpty()) return;
        try {
            // Paper PlayerProfile API
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.getProperties().add(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
            skullMeta.setPlayerProfile(profile);
            return;
        } catch (Throwable ignored) {}

        try {
            // Spigot Reflection Fallback for GameProfile
            java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            java.lang.reflect.Constructor<?> profileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object profile = profileConstructor.newInstance(UUID.randomUUID(), "");
            
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            java.lang.reflect.Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
            Object property = propertyConstructor.newInstance("textures", base64);
            
            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);
            
            profileField.set(skullMeta, profile);
        } catch (Throwable ignored) {}
    }

    /**
     * Exports a complete resource pack directory structure for custom textures.
     * Generates:
     * - pack.mcmeta
     * - assets/minecraft/models/item/<material>.json (including overrides)
     * - assets/heartss/models/item/heart_tier_X.json (item models)
     * - assets/heartss/textures/item/heart_tier_X.png (placeholder references)
     */
    public boolean exportResourcePack() {
        File rpDir = new File(plugin.getDataFolder(), "resourcepack");
        if (!rpDir.exists()) {
            rpDir.mkdirs();
        }

        // 1. Write pack.mcmeta
        File packMcMeta = new File(rpDir, "pack.mcmeta");
        try (FileWriter writer = new FileWriter(packMcMeta)) {
            writer.write("{\n" +
                    "  \"pack\": {\n" +
                    "    \"pack_format\": 15,\n" +
                    "    \"description\": \"Heartss Lifesteal Custom Textures Pack\"\n" +
                    "  }\n" +
                    "}");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export pack.mcmeta: " + e.getMessage());
            return false;
        }

        FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");
        if (!itemsYml.contains("items")) return true;

        // Group custom items by material
        Map<String, List<Map<String, Object>>> materialOverrides = new HashMap<>();

        for (String key : itemsYml.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key;
            if (itemsYml.isConfigurationSection(path)) {
                String matName = itemsYml.getString(path + ".material", "").toLowerCase();
                int customModelData = itemsYml.getInt(path + ".custom-model-data", -1);
                if (!matName.isEmpty() && customModelData != -1) {
                    materialOverrides.putIfAbsent(matName, new ArrayList<>());
                    Map<String, Object> map = new HashMap<>();
                    map.put("key", key);
                    map.put("custom-model-data", customModelData);
                    materialOverrides.get(matName).add(map);
                }
            }
        }

        // Export individual item overrides inside assets/minecraft/models/item/
        File modelsDir = new File(rpDir, "assets/minecraft/models/item");
        modelsDir.mkdirs();

        for (Map.Entry<String, List<Map<String, Object>>> entry : materialOverrides.entrySet()) {
            String mat = entry.getKey();
            File modelFile = new File(modelsDir, mat + ".json");
            try (FileWriter writer = new FileWriter(modelFile)) {
                writer.write("{\n" +
                        "  \"parent\": \"minecraft:item/generated\",\n" +
                        "  \"textures\": {\n" +
                        "    \"layer0\": \"minecraft:item/" + mat + "\"\n" +
                        "  },\n" +
                        "  \"overrides\": [\n");

                List<Map<String, Object>> overrides = entry.getValue();
                for (int i = 0; i < overrides.size(); i++) {
                    Map<String, Object> o = overrides.get(i);
                    String itemKey = (String) o.get("key");
                    int cmd = (int) o.get("custom-model-data");
                    writer.write("    { \"predicate\": { \"custom_model_data\": " + cmd + " }, \"model\": \"heartss:item/" + itemKey + "\" }" +
                            (i < overrides.size() - 1 ? ",\n" : "\n"));
                }
                writer.write("  ]\n" +
                        "}");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to export " + mat + ".json override: " + e.getMessage());
            }
        }

        // Export custom models folder inside assets/heartss/models/item/
        File customModelsDir = new File(rpDir, "assets/heartss/models/item");
        customModelsDir.mkdirs();

        for (String key : itemsYml.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key;
            if (itemsYml.isConfigurationSection(path)) {
                int cmd = itemsYml.getInt(path + ".custom-model-data", -1);
                if (cmd != -1) {
                    File itemModel = new File(customModelsDir, key + ".json");
                    try (FileWriter writer = new FileWriter(itemModel)) {
                        writer.write("{\n" +
                                "  \"parent\": \"minecraft:item/generated\",\n" +
                                "  \"textures\": {\n" +
                                "    \"layer0\": \"heartss:item/" + key + "\"\n" +
                                "  }\n" +
                                "}");
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to export custom model " + key + ".json: " + e.getMessage());
                    }
                }
            }
        }

        return true;
    }
}
