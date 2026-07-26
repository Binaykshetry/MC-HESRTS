package com.heartss.listeners;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class MenuListener implements Listener {

    private final Heartss plugin;

    public MenuListener(Heartss plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Fetch titles from files to match
        String playerMenuTitle = ConfigManager.color(plugin.getConfigManager().getConfig("menus.yml").getString("menus.player-menu.title", "&c&lHearts Dashboard"));
        String adminMenuTitle = ConfigManager.color(plugin.getConfigManager().getConfig("menus.yml").getString("menus.admin-menu.title", "&c&lHeartss Admin Console"));
        String reviveMenuTitle = ConfigManager.color(plugin.getConfigManager().getConfig("menus.yml").getString("menus.revive-menu.title", "&d&lResurrection Chamber"));

        // Match titles exactly
        if (title.equals(playerMenuTitle)) {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 11) {
                // Trigger Withdraw 1 heart
                player.closeInventory();
                Bukkit.dispatchCommand(player, "heartss withdraw 1");
            } else if (slot == 13) {
                // View Recipes
                player.closeInventory();
                Bukkit.dispatchCommand(player, "heartss recipe heart");
            } else if (slot == 15) {
                // Open Revive Chamber
                plugin.getMenuManager().openReviveConfirmationMenu(player);
            }
        } 
        
        else if (title.equals(adminMenuTitle)) {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 10) {
                player.sendMessage(ConfigManager.color("&cPlease use administrative commands directly: /heartss set <player> <amount>"));
                player.closeInventory();
            } else if (slot == 12) {
                player.sendMessage(ConfigManager.color("&cPlease use: /heartss setmax <player> <amount>"));
                player.closeInventory();
            } else if (slot == 14) {
                player.sendMessage(ConfigManager.color("&eGiving you a Tier V Heart directly..."));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "heartss giveitem " + player.getName() + " heart-V");
                player.closeInventory();
            } else if (slot == 16) {
                player.sendMessage(ConfigManager.color("&bRunning diagnostic investigation on yourself..."));
                Bukkit.dispatchCommand(player, "heartss investigate " + player.getName());
                player.closeInventory();
            }
        } 
        
        else if (title.equals(reviveMenuTitle)) {
            event.setCancelled(true);
            if (clickedItem.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
                if (meta != null && meta.hasOwner()) {
                    String targetName = meta.getOwner();
                    player.closeInventory();
                    
                    // Run player revive command flow
                    Bukkit.dispatchCommand(player, "heartss revive " + targetName);
                }
            }
        }
    }
}
