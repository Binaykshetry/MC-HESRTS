package com.heartss.menu;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MenuManager {

    private final Heartss plugin;

    public MenuManager(Heartss plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the personal player menu from menus.yml.
     */
    public void openPlayerMenu(Player player) {
        FileConfiguration menuYml = plugin.getConfigManager().getConfig("menus.yml");
        String title = ConfigManager.color(menuYml.getString("menus.player-menu.title", "&c&lHearts Dashboard"));
        int size = menuYml.getInt("menus.player-menu.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection slotsSection = menuYml.getConfigurationSection("menus.player-menu.slots");
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                int slot = Integer.parseInt(key);
                String materialName = slotsSection.getString(key + ".material", "RED_DYE");
                String displayName = ConfigManager.color(slotsSection.getString(key + ".display-name"));
                List<String> lore = slotsSection.getStringList(key + ".lore");

                Material mat = Material.matchMaterial(materialName);
                if (mat == null) mat = Material.RED_DYE;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(displayName);

                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) {
                        coloredLore.add(ConfigManager.color(line));
                    }
                    meta.setLore(coloredLore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    /**
     * Opens the administrative menu from menus.yml.
     */
    public void openAdminMenu(Player player) {
        FileConfiguration menuYml = plugin.getConfigManager().getConfig("menus.yml");
        String title = ConfigManager.color(menuYml.getString("menus.admin-menu.title", "&c&lHeartss Admin Console"));
        int size = menuYml.getInt("menus.admin-menu.size", 36);

        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection slotsSection = menuYml.getConfigurationSection("menus.admin-menu.slots");
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                int slot = Integer.parseInt(key);
                String materialName = slotsSection.getString(key + ".material", "STONE");
                String displayName = ConfigManager.color(slotsSection.getString(key + ".display-name"));
                List<String> lore = slotsSection.getStringList(key + ".lore");

                Material mat = Material.matchMaterial(materialName);
                if (mat == null) mat = Material.STONE;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(displayName);

                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) {
                        coloredLore.add(ConfigManager.color(line));
                    }
                    meta.setLore(coloredLore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    /**
     * Opens the dynamic revive list GUI displaying offline/eliminated heads.
     */
    public void openReviveConfirmationMenu(Player player) {
        FileConfiguration menuYml = plugin.getConfigManager().getConfig("menus.yml");
        String title = ConfigManager.color(menuYml.getString("menus.revive-menu.title", "&d&lResurrection Chamber"));
        int size = menuYml.getInt("menus.revive-menu.size", 54);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Populate borders
        ConfigurationSection borderSec = menuYml.getConfigurationSection("menus.revive-menu.decorations.border");
        if (borderSec != null) {
            String borderMatName = borderSec.getString("material", "GRAY_STAINED_GLASS_PANE");
            Material borderMat = Material.matchMaterial(borderMatName);
            if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;

            ItemStack borderItem = new ItemStack(borderMat);
            ItemMeta borderMeta = borderItem.getItemMeta();
            if (borderMeta != null) {
                borderMeta.setDisplayName(ConfigManager.color(borderSec.getString("display-name", " ")));
                borderItem.setItemMeta(borderMeta);
            }

            List<Integer> borderSlots = borderSec.getIntegerList("slots");
            for (int slot : borderSlots) {
                if (slot < size) {
                    inv.setItem(slot, borderItem);
                }
            }
        }

        // Dynamically populate banned skulls in available middle slots (slots 10 to 43)
        int currentSlot = 10;
        for (BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
            if (currentSlot >= 44) break;

            // Skip border slots
            if (currentSlot == 17 || currentSlot == 18 || currentSlot == 26 || currentSlot == 27 || currentSlot == 35 || currentSlot == 36) {
                currentSlot += 2; // skip borders
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwner(entry.getTarget());
                meta.setDisplayName(ConfigManager.color("&d&l" + entry.getTarget()));
                
                List<String> lore = new ArrayList<>();
                lore.add(ConfigManager.color("&8&m---------------------"));
                lore.add(ConfigManager.color("&7Status: &c&lELIMINATED"));
                lore.add(ConfigManager.color("&7Ban Reason: &e" + entry.getReason()));
                lore.add(ConfigManager.color("&8&m---------------------"));
                lore.add(ConfigManager.color("&eClick to initiate resurrection ritual!"));
                meta.setLore(lore);
                
                head.setItemMeta(meta);
            }

            inv.setItem(currentSlot, head);
            currentSlot++;
        }

        player.openInventory(inv);
    }
}
