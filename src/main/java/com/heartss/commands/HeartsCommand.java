package com.heartss.commands;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import com.heartss.system.HeartManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class HeartsCommand implements CommandExecutor, TabCompleter {

    private final Heartss plugin;

    public HeartsCommand(Heartss plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // Check viewrecipes permission for /lsrecipe shorthand
        if (cmdName.equalsIgnoreCase("lsrecipe")) {
            if (!sender.hasPermission("hearts.viewrecipes")) {
                String lang = sender instanceof Player ? plugin.getHeartManager().getLanguage(((Player) sender).getUniqueId()) : "en";
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ConfigManager.color("&cOnly players can open the recipe viewer!"));
                return true;
            }
            showRecipeMessage((Player) sender, "heart-I");
            return true;
        }

        // Direct command routing to subcommands
        if (cmdName.equals("withdraw")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "withdraw";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        } else if (cmdName.equals("eliminate")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "eliminate";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        } else if (cmdName.equals("revive")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "revive";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        } else if (cmdName.equals("setlife")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "set";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        }

        // Base player menu/hearts status check
        if (args.length == 0) {
            if (!sender.hasPermission("hearts.viewhearts")) {
                String lang = sender instanceof Player ? plugin.getHeartManager().getLanguage(((Player) sender).getUniqueId()) : "en";
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ConfigManager.color("&cUsage: /heartss <subcommand>"));
                return true;
            }
            Player player = (Player) sender;
            plugin.getMenuManager().openPlayerMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        String lang = sender instanceof Player ? plugin.getHeartManager().getLanguage(((Player) sender).getUniqueId()) : "en";

        // Help menu check
        if (sub.equals("help")) {
            if (!sender.hasPermission("hearts.help")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                return true;
            }
            showHelpMessage(sender);
            return true;
        }

        switch (sub) {
            case "menu":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-only", lang));
                    return true;
                }
                plugin.getMenuManager().openPlayerMenu((Player) sender);
                return true;

            case "check":
                if (args.length < 2) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss check <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                int heartsVal = plugin.getHeartManager().getPlayerData(target.getUniqueId()).hearts;
                int maxHeartsVal = plugin.getHeartManager().getMaxHeartsLimit(target);
                sender.sendMessage(plugin.getConfigManager().getMessage("check-hearts", lang, Map.of(
                        "%player%", target.getName(),
                        "%hearts%", String.valueOf(heartsVal),
                        "%max_hearts%", String.valueOf(maxHeartsVal)
                )));
                return true;

            case "withdraw":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-only", lang));
                    return true;
                }
                Player p = (Player) sender;
                if (!p.hasPermission("hearts.withdraw") && !p.hasPermission("heartss.withdraw")) {
                    p.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                int amount = 1;
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        p.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                        return true;
                    }
                }
                if (amount <= 0) {
                    p.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                    return true;
                }

                int minLimit = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
                HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(p.getUniqueId());

                if (data.hearts - amount <= minLimit) {
                    p.sendMessage(plugin.getConfigManager().getMessage("withdraw-fail-min", lang));
                    return true;
                }

                // Deduct hearts
                plugin.getHeartManager().changeHearts(p.getUniqueId(), -amount);

                // Give heart item
                String itemTier = "heart-I";
                if (amount == 2) itemTier = "heart-II";
                else if (amount == 3) itemTier = "heart-III";
                else if (amount == 4) itemTier = "heart-IV";
                else if (amount >= 5) itemTier = "heart-V";

                ItemStack heartItem = buildCustomItem(itemTier);
                if (heartItem != null) {
                    p.getInventory().addItem(heartItem);
                }

                p.sendMessage(plugin.getConfigManager().getMessage("withdraw-success", lang, Map.of("%amount%", String.valueOf(amount))));
                return true;

            case "language":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-only", lang));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss language <en|es|de>"));
                    return true;
                }
                String code = args[1].toLowerCase();
                if (List.of("en", "es", "de").contains(code)) {
                    plugin.getHeartManager().setLanguage(((Player) sender).getUniqueId(), code);
                    sender.sendMessage(plugin.getConfigManager().getMessage("language-changed", code));
                } else {
                    sender.sendMessage(ConfigManager.color("&cUnsupported language code. Use en, es, or de."));
                }
                return true;

            case "recipe":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-only", lang));
                    return true;
                }
                String recipeType = "heart-I";
                if (args.length >= 2) {
                    recipeType = args[1];
                }
                showRecipeMessage((Player) sender, recipeType);
                return true;

            // ADMINISTRATIVE SUBCOMMANDS (hearts.admin / OP)
            case "set":
                if (!sender.hasPermission("hearts.admin.setlife") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss set <player> <amount>"));
                    return true;
                }
                Player tSet = Bukkit.getPlayer(args[1]);
                if (tSet == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    plugin.getHeartManager().setHearts(tSet.getUniqueId(), val);
                    sender.sendMessage(plugin.getConfigManager().getMessage("set-hearts", lang, Map.of("%player%", tSet.getName(), "%amount%", String.valueOf(val))));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                }
                return true;

            case "add":
                if (!sender.hasPermission("hearts.admin.setlife") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss add <player> <amount>"));
                    return true;
                }
                Player tAdd = Bukkit.getPlayer(args[1]);
                if (tAdd == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    plugin.getHeartManager().changeHearts(tAdd.getUniqueId(), val);
                    sender.sendMessage(plugin.getConfigManager().getMessage("add-hearts", lang, Map.of("%player%", tAdd.getName(), "%amount%", String.valueOf(val))));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                }
                return true;

            case "remove":
                if (!sender.hasPermission("hearts.admin.setlife") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss remove <player> <amount>"));
                    return true;
                }
                Player tRemove = Bukkit.getPlayer(args[1]);
                if (tRemove == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    plugin.getHeartManager().changeHearts(tRemove.getUniqueId(), -val);
                    sender.sendMessage(plugin.getConfigManager().getMessage("remove-hearts", lang, Map.of("%player%", tRemove.getName(), "%amount%", String.valueOf(val))));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                }
                return true;

            case "setmax":
                if (!sender.hasPermission("hearts.admin.setlife") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss setmax <player> <amount>"));
                    return true;
                }
                Player tMax = Bukkit.getPlayer(args[1]);
                if (tMax == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    HeartManager.PlayerData pd = plugin.getHeartManager().getPlayerData(tMax.getUniqueId());
                    pd.maxHeartsOverride = val;
                    plugin.getHeartManager().applyHeartsToPlayer(tMax);
                    sender.sendMessage(plugin.getConfigManager().getMessage("set-max-hearts", lang, Map.of("%player%", tMax.getName(), "%amount%", String.valueOf(val))));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount", lang));
                }
                return true;

            case "giveitem":
                if (!sender.hasPermission("hearts.admin.giveitem") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss giveitem <player> <item-id>"));
                    return true;
                }
                Player tGive = Bukkit.getPlayer(args[1]);
                if (tGive == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                String itemId = args[2];
                ItemStack item = buildCustomItem(itemId);
                if (item == null) {
                    sender.sendMessage(ConfigManager.color("&cItem not found inside items.yml. Check config ids."));
                    return true;
                }
                tGive.getInventory().addItem(item);
                sender.sendMessage(ConfigManager.color("&aGave &f" + itemId + " &ato " + tGive.getName() + "."));
                return true;

            case "investigate":
                if (!sender.hasPermission("hearts.admin") && !sender.hasPermission("hearts.admin.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /heartss investigate <player>"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Map<String, Object> report = plugin.getExploitManager().performInvestigation(op.getUniqueId());
                
                sender.sendMessage(ConfigManager.color("&8&m========================================="));
                sender.sendMessage(ConfigManager.color("&d&lINVESTIGATION DOSSIER: &f" + op.getName()));
                sender.sendMessage(ConfigManager.color("&7Recorded IP connection history:"));
                for (String ipStr : (Set<String>) report.get("ips")) {
                    sender.sendMessage(ConfigManager.color("  &8- &7" + ipStr));
                }
                sender.sendMessage(ConfigManager.color("&7Suspected associated Alt accounts:"));
                Set<UUID> alts = (Set<UUID>) report.get("alts");
                if (alts.isEmpty()) {
                    sender.sendMessage(ConfigManager.color("  &aNone detected."));
                } else {
                    for (UUID altId : alts) {
                        sender.sendMessage(ConfigManager.color("  &c- " + Bukkit.getOfflinePlayer(altId).getName()));
                    }
                }
                sender.sendMessage(ConfigManager.color("&8&m========================================="));
                return true;

            case "reload":
                if (!sender.hasPermission("hearts.admin.reload") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                plugin.getConfigManager().reloadAll();
                plugin.getHeartManager().loadOnlinePlayers(); // Re-apply immediately
                sender.sendMessage(plugin.getConfigManager().getMessage("success-reload", lang));
                return true;

            case "eliminate":
                if (!sender.hasPermission("hearts.admin.eliminate") && !sender.hasPermission("hearts.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /eliminate <player>"));
                    return true;
                }
                Player tElim = Bukkit.getPlayer(args[1]);
                if (tElim == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
                plugin.getHeartManager().setHearts(tElim.getUniqueId(), minHearts);
                plugin.getEliminationManager().checkElimination(tElim);
                sender.sendMessage(ConfigManager.color("&aPlayer &f" + tElim.getName() + " &awas successfully eliminated!"));
                return true;

            case "revive":
                if (!sender.hasPermission("hearts.admin.revive") && !sender.hasPermission("hearts.revive") && !sender.hasPermission("heartss.revive")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ConfigManager.color("&cUsage: /revive <player>"));
                    return true;
                }
                String targetRev = args[1];
                boolean isBanned = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(targetRev);
                if (!isBanned) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-banned", lang, Map.of("%player%", targetRev)));
                    return true;
                }
                // Pardon the ban
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(targetRev);
                // Restore their hearts
                int freshHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.fresh-hearts", 10);
                UUID targetRevUUID = Bukkit.getOfflinePlayer(targetRev).getUniqueId();
                HeartManager.PlayerData targetRevData = plugin.getHeartManager().getPlayerData(targetRevUUID);
                targetRevData.hearts = freshHearts;
                targetRevData.totalRevives++;
                plugin.getHeartManager().savePlayer(targetRevUUID);

                sender.sendMessage(plugin.getConfigManager().getMessage("revive-success", lang, Map.of("%player%", targetRev)));
                return true;

            case "bypass":
                if (!sender.hasPermission("hearts.bypass.check") && !sender.hasPermission("hearts.bypass")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                if (args.length < 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ConfigManager.color("&cConsole must specify a player! /heartss bypass <player>"));
                        return true;
                    }
                    Player pSelf = (Player) sender;
                    boolean selfBypass = pSelf.hasPermission("hearts.bypass");
                    sender.sendMessage(ConfigManager.color("&7Your death bypass status: " + (selfBypass ? "&aENABLED" : "&cDISABLED")));
                    return true;
                }
                if (!sender.hasPermission("hearts.bypass.check")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                Player tBypass = Bukkit.getPlayer(args[1]);
                if (tBypass == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", lang, Map.of("%player%", args[1])));
                    return true;
                }
                boolean bypassStatus = tBypass.hasPermission("hearts.bypass");
                sender.sendMessage(ConfigManager.color("&7Bypass status for &e" + tBypass.getName() + "&7: " + (bypassStatus ? "&aENABLED" : "&cDISABLED")));
                return true;

            case "resourcepack":
                if (!sender.hasPermission("hearts.admin") && !sender.hasPermission("hearts.admin.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission", lang));
                    return true;
                }
                boolean exported = com.heartss.api.HeartssAddonAPI.getInstance().exportResourcePack();
                if (exported) {
                    sender.sendMessage(ConfigManager.color("&aSuccessfully exported custom Model overrides and Pack configs to &eplugins/Heartss/resourcepack/&a!"));
                } else {
                    sender.sendMessage(ConfigManager.color("&cAn error occurred while exporting the resource pack templates."));
                }
                return true;

            default:
                sender.sendMessage(ConfigManager.color("&cUnknown subcommand. Use /heartss to open the interface."));
                return true;
        }
    }

    private void showHelpMessage(CommandSender sender) {
        sender.sendMessage(ConfigManager.color("&8&m========================================="));
        sender.sendMessage(ConfigManager.color("            &#ff3333&lHeartss Lifesteal Help"));
        sender.sendMessage(ConfigManager.color("&8&m========================================="));
        sender.sendMessage(ConfigManager.color("&e/hearts &7- Open main personal GUI status"));
        sender.sendMessage(ConfigManager.color("&e/hearts help &7- Displays this help menu"));
        sender.sendMessage(ConfigManager.color("&e/hearts withdraw <amount> &7- Withdraw hearts"));
        sender.sendMessage(ConfigManager.color("&e/hearts recipe [item] &7- View custom recipe grid"));
        sender.sendMessage(ConfigManager.color("&e/hearts language <code> &7- Change personal locale"));
        if (sender.hasPermission("hearts.admin") || sender.hasPermission("hearts.admin.reload")) {
            sender.sendMessage(ConfigManager.color("&c/hearts set <player> <amount> &7- Set player hearts"));
            sender.sendMessage(ConfigManager.color("&c/hearts add/remove <player> <amount> &7- Modify hearts"));
            sender.sendMessage(ConfigManager.color("&c/hearts setmax <player> <amount> &7- Override max-cap"));
            sender.sendMessage(ConfigManager.color("&c/hearts giveitem <player> <item> &7- Spawn custom items"));
            sender.sendMessage(ConfigManager.color("&c/hearts eliminate <player> &7- Banish player instantly"));
            sender.sendMessage(ConfigManager.color("&c/hearts revive <player> &7- Pardon and revive player"));
            sender.sendMessage(ConfigManager.color("&c/hearts investigate <player> &7- Check alt account log"));
            sender.sendMessage(ConfigManager.color("&c/hearts resourcepack &7- Export resource pack template"));
            sender.sendMessage(ConfigManager.color("&c/hearts reload &7- Reload all configurations"));
        }
        sender.sendMessage(ConfigManager.color("&8&m========================================="));
    }

    private void showRecipeMessage(Player player, String recipeKey) {
        FileConfiguration recipesYml = plugin.getConfigManager().getConfig("recipes.yml");
        String path = "recipes." + recipeKey;

        if (!recipesYml.contains(path)) {
            player.sendMessage(ConfigManager.color("&cRecipe for '" + recipeKey + "' was not found inside recipes.yml!"));
            return;
        }

        List<String> shape = recipesYml.getStringList(path + ".shape");
        player.sendMessage(ConfigManager.color("&8&m-----------------------------------------"));
        player.sendMessage(ConfigManager.color("&d&lRecipe Grid for &f" + recipeKey + ":"));
        for (String row : shape) {
            player.sendMessage(ConfigManager.color("  &b[ &f" + row.charAt(0) + " &b] [ &f" + row.charAt(1) + " &b] [ &f" + row.charAt(2) + " &b]"));
        }
        player.sendMessage(ConfigManager.color("&7Ingredients list:"));
        ConfigurationSection ingSec = recipesYml.getConfigurationSection(path + ".ingredients");
        if (ingSec != null) {
            for (String ch : ingSec.getKeys(false)) {
                player.sendMessage(ConfigManager.color("  &e" + ch + ": &f" + ingSec.getString(ch)));
            }
        }
        player.sendMessage(ConfigManager.color("&8&m-----------------------------------------"));
    }

    private ItemStack buildCustomItem(String itemId) {
        return com.heartss.api.HeartssAddonAPI.getInstance().getCustomItem(itemId);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("menu", "check", "withdraw", "language", "recipe", "help"));
            if (sender.hasPermission("hearts.admin") || sender.hasPermission("hearts.admin.reload") || sender.hasPermission("hearts.admin.setlife")) {
                options.addAll(List.of("set", "add", "remove", "setmax", "giveitem", "investigate", "reload", "eliminate", "revive", "bypass", "resourcepack"));
            }
            return filterList(options, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("check", "set", "add", "remove", "setmax", "giveitem", "investigate", "eliminate", "bypass").contains(sub)) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return filterList(playerNames, args[1]);
            }
            if (sub.equals("revive")) {
                List<String> bannedNames = new ArrayList<>();
                for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                    bannedNames.add(entry.getTarget());
                }
                return filterList(bannedNames, args[1]);
            }
            if (sub.equals("language")) {
                return filterList(List.of("en", "es", "de"), args[1]);
            }
            if (sub.equals("recipe")) {
                return filterList(List.of("heart-I", "heart-II", "heart-III", "revive-crystal", "revive-beacon"), args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("giveitem")) {
                return filterList(List.of("heart-I", "heart-II", "heart-III", "heart-IV", "heart-V", "warrior-scroll", "healing-scroll", "revive-crystal", "revive-beacon", "doomsday-sword"), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterList(List<String> list, String query) {
        List<String> filtered = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(query.toLowerCase())) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
