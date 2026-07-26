package com.heartss;

import com.heartss.commands.HeartsCommand;
import com.heartss.config.ConfigManager;
import com.heartss.listeners.MenuListener;
import com.heartss.listeners.PlayerListener;
import com.heartss.menu.MenuManager;
import com.heartss.system.EliminationManager;
import com.heartss.system.ExploitManager;
import com.heartss.system.HeartManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Heartss extends JavaPlugin {

    private static Heartss instance;
    
    @Override
    public void onLoad() {
        com.heartss.integrations.WorldGuardHook.registerFlag();
    }
    
    private ConfigManager configManager;
    private HeartManager heartManager;
    private EliminationManager eliminationManager;
    private ExploitManager exploitManager;
    private MenuManager menuManager;
    private com.heartss.system.DiscordWebhookManager discordWebhookManager;

    // Optional integrations status
    private boolean luckPermsEnabled = false;
    private boolean placeholderApiEnabled = false;
    private boolean worldGuardEnabled = false;
    private boolean vaultEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        // Print elegant startup banner
        printBanner();

        // 1. Initialize Configuration Files
        this.configManager = new ConfigManager(this);
        this.configManager.initFiles();

        // 2. Detect and Initialize Soft-Dependencies
        checkIntegrations();

        // 3. Initialize Core Subsystems
        this.heartManager = new HeartManager(this);
        this.heartManager.setupStorage();

        this.eliminationManager = new EliminationManager(this);
        this.exploitManager = new ExploitManager(this);
        this.menuManager = new MenuManager(this);
        this.discordWebhookManager = new com.heartss.system.DiscordWebhookManager(this);

        // 4. Register Event Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);

        // 5. Register Commands and Tab Completer
        HeartsCommand heartsCommand = new HeartsCommand(this);
        for (String cmdName : new String[]{"hearts", "lsrecipe", "withdraw", "eliminate", "revive", "setlife"}) {
            if (getCommand(cmdName) != null) {
                getCommand(cmdName).setExecutor(heartsCommand);
                getCommand(cmdName).setTabCompleter(heartsCommand);
            }
        }

        // 6. Complete Initializations (Load online players)
        this.heartManager.loadOnlinePlayers();
        registerCustomRecipes();

        getLogger().log(Level.INFO, "Heartss Lifesteal Core successfully loaded all subsystems!");
    }

    /**
     * Dynamically registers custom recipes loaded from recipes.yml.
     */
    public void registerCustomRecipes() {
        org.bukkit.configuration.file.FileConfiguration recipesYml = getConfigManager().getConfig("recipes.yml");
        if (recipesYml == null || !recipesYml.contains("recipes")) return;

        // Clear existing registered recipe keys associated with the plugin
        for (String keyStr : recipesYml.getConfigurationSection("recipes").getKeys(false)) {
            try {
                org.bukkit.NamespacedKey nKey = new org.bukkit.NamespacedKey(this, "recipe_" + keyStr.toLowerCase());
                Bukkit.removeRecipe(nKey);
            } catch (Throwable ignored) {}
        }

        for (String recipeKey : recipesYml.getConfigurationSection("recipes").getKeys(false)) {
            String path = "recipes." + recipeKey;
            if (!recipesYml.getBoolean(path + ".enabled", true)) continue;

            try {
                // Get result item stack
                org.bukkit.inventory.ItemStack resultStack = null;
                if (recipeKey.equals("enchanted-golden-apple")) {
                    resultStack = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_GOLDEN_APPLE, recipesYml.getInt(path + ".result-amount", 1));
                } else {
                    resultStack = com.heartss.api.HeartssAddonAPI.getInstance().getCustomItem(recipeKey);
                    if (resultStack != null) {
                        resultStack.setAmount(recipesYml.getInt(path + ".result-amount", 1));
                    }
                }

                if (resultStack == null) {
                    getLogger().warning("Failed to find custom item for recipe result: " + recipeKey);
                    continue;
                }

                org.bukkit.NamespacedKey recipeNamespacedKey = new org.bukkit.NamespacedKey(this, "recipe_" + recipeKey.toLowerCase());
                org.bukkit.inventory.ShapedRecipe shapedRecipe = new org.bukkit.inventory.ShapedRecipe(recipeNamespacedKey, resultStack);

                java.util.List<String> shapeLines = recipesYml.getStringList(path + ".shape");
                if (shapeLines == null || shapeLines.size() != 3) {
                    getLogger().warning("Invalid shape for recipe: " + recipeKey);
                    continue;
                }

                shapedRecipe.shape(shapeLines.get(0), shapeLines.get(1), shapeLines.get(2));

                org.bukkit.configuration.ConfigurationSection ingredients = recipesYml.getConfigurationSection(path + ".ingredients");
                if (ingredients == null) continue;

                for (String ingredientKey : ingredients.getKeys(false)) {
                    String val = ingredients.getString(ingredientKey);
                    if (val == null || val.isEmpty()) continue;

                    char keyChar = ingredientKey.charAt(0);
                    if (val.startsWith("CUSTOM:")) {
                        String customId = val.substring(7);
                        org.bukkit.inventory.ItemStack customIngredient = com.heartss.api.HeartssAddonAPI.getInstance().getCustomItem(customId);
                        if (customIngredient != null) {
                            try {
                                shapedRecipe.setIngredient(keyChar, new org.bukkit.inventory.RecipeChoice.ExactChoice(customIngredient));
                            } catch (Throwable err) {
                                // Fallback for older Spigot versions that do not support RecipeChoice.ExactChoice
                                shapedRecipe.setIngredient(keyChar, customIngredient.getType());
                            }
                        } else {
                            getLogger().warning("Custom ingredient '" + customId + "' not found for recipe '" + recipeKey + "'");
                        }
                    } else {
                        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(val);
                        if (mat != null) {
                            shapedRecipe.setIngredient(keyChar, mat);
                        } else {
                            getLogger().warning("Material '" + val + "' not found for recipe '" + recipeKey + "'");
                        }
                    }
                }

                // Add to server
                Bukkit.addRecipe(shapedRecipe);
                getLogger().info("Successfully registered custom crafting recipe: " + recipeNamespacedKey.getKey());

            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Error registering custom recipe: " + recipeKey, t);
            }
        }
    }

    @Override
    public void onDisable() {
        // Safe database flush and cleanup
        if (this.heartManager != null) {
            this.heartManager.saveOnlinePlayers();
            this.heartManager.shutdownStorage();
        }
        getLogger().log(Level.INFO, "Heartss successfully flushed database and disabled subsystems.");
    }

    public static Heartss getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HeartManager getHeartManager() {
        return heartManager;
    }

    public EliminationManager getEliminationManager() {
        return eliminationManager;
    }

    public ExploitManager getExploitManager() {
        return exploitManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public com.heartss.system.DiscordWebhookManager getDiscordWebhookManager() {
        // Trigger synchronization with GitHub
        return discordWebhookManager;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    private void checkIntegrations() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            this.luckPermsEnabled = true;
            com.heartss.integrations.LuckPermsHook.init();
            getLogger().info("Successfully integrated with LuckPerms for tiered permissions!");
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderApiEnabled = true;
            new com.heartss.integrations.HeartsPlaceholderExpansion(this).register();
            new com.heartss.integrations.LifestealzPlaceholderExpansion(this).register();
            getLogger().info("Successfully integrated with PlaceholderAPI for variables!");
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            this.worldGuardEnabled = true;
            getLogger().info("Successfully integrated with WorldGuard for beacon claims!");
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            this.vaultEnabled = true;
            getLogger().info("Successfully integrated with Vault economy!");
        }
    }

    private void printBanner() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', 
            "\n" +
            "&d ██╗  ██╗███████╗ █████╗ ██████╗ ████████╗███████╗███████╗\n" +
            "&d ██║  ██║██╔════╝██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██╔════╝\n" +
            "&d ███████║█████╗  ███████║██████╔╝   ██║   ███████╗███████╗\n" +
            "&d ██╔══██║██╔══╝  ██╔══██║██╔══██╗   ██║   ╚════██║╚════██║\n" +
            "&d ██║  ██║███████╗██║  ██║██║  ██║   ██║   ███████║███████║\n" +
            "&d ╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚══════╝╚══════╝\n" +
            "&d        » Ultimate Merged Lifesteal Core (Java 17+) «\n"
        ));
    }
}
