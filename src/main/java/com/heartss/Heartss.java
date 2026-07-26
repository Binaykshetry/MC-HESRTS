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

        getLogger().log(Level.INFO, "Heartss Lifesteal Core successfully loaded all subsystems!");
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
