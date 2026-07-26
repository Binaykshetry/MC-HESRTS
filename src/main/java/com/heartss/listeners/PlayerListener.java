package com.heartss.listeners;

import com.heartss.Heartss;
import com.heartss.config.ConfigManager;
import com.heartss.system.EliminationManager;
import com.heartss.system.HeartManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final Heartss plugin;
    
    // Simple scroll cooldown tracker: PlayerUUID -> Map(ScrollID -> timestamp)
    private final Map<UUID, Map<String, Long>> scrollCooldowns = new HashMap<>();

    public PlayerListener(Heartss plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getHeartManager().loadPlayer(player.getUniqueId());
        plugin.getHeartManager().applyHeartsToPlayer(player);
        plugin.getExploitManager().recordConnection(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getHeartManager().unloadPlayer(event.getPlayer().getUniqueId());
        plugin.getEliminationManager().cancelRitual(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Enforce max health attribute values upon player respawn state
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getHeartManager().applyHeartsToPlayer(event.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;
        
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        if (!config.getBoolean("grace-period.enabled", true)) return;
        if (!config.getBoolean("grace-period.protect-from-pvp", true)) return;
        
        HeartManager.PlayerData victimData = plugin.getHeartManager().getPlayerData(victim.getUniqueId());
        long now = System.currentTimeMillis();
        if (victimData.gracePeriodEnd > now) {
            event.setCancelled(true);
            attacker.sendMessage(ConfigManager.color("&cThat player is currently in a grace period for another " + 
                    Math.max(0, (victimData.gracePeriodEnd - now) / 1000) + " seconds!"));
            return;
        }
        
        HeartManager.PlayerData attackerData = plugin.getHeartManager().getPlayerData(attacker.getUniqueId());
        if (attackerData.gracePeriodEnd > now) {
            event.setCancelled(true);
            attacker.sendMessage(ConfigManager.color("&cYou cannot attack other players while you are in a grace period (" + 
                    Math.max(0, (attackerData.gracePeriodEnd - now) / 1000) + "s remaining). Use /hearts to check or wait it out."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
        int minHearts = config.getInt("hearts.min-hearts", 0);
        int lossOnDeath = config.getInt("hearts.loss-on-death", 1);
        int rewardOnKill = config.getInt("hearts.reward-on-kill", 1);
        int rewardOnElim = config.getInt("hearts.reward-on-elimination", 2);

        HeartManager.PlayerData victimData = plugin.getHeartManager().getPlayerData(victim.getUniqueId());

        // Check for victim bypass permission
        boolean victimBypass = victim.hasPermission("hearts.bypass");
        if (victimBypass) {
            victim.sendMessage(ConfigManager.color("&a[Heartss] Heart loss bypassed due to bypass permission."));
            return;
        }

        // Check for grace period protection
        if (config.getBoolean("grace-period.enabled", true) && config.getBoolean("grace-period.protect-from-heart-loss", true)) {
            if (victimData.gracePeriodEnd > System.currentTimeMillis()) {
                long remaining = (victimData.gracePeriodEnd - System.currentTimeMillis()) / 1000;
                victim.sendMessage(ConfigManager.color("&a[Heartss] Heart loss bypassed due to active grace period (" + remaining + "s remaining)."));
                return;
            }
        }

        // Check WorldGuard region flags
        if (plugin.isWorldGuardEnabled() && !com.heartss.integrations.WorldGuardHook.isHeartLossAllowed(victim, victim.getLocation())) {
            victim.sendMessage(ConfigManager.color("&c[Heartss] Heart loss is disabled in this region by WorldGuard!"));
            return;
        }

        if (killer != null && killer.getUniqueId() != victim.getUniqueId()) {
            // PvP DEATH FLOW
            boolean eligible = plugin.getExploitManager().checkKillEligibility(killer, victim);
            
            // Deduct heart from victim
            int currentVictimHearts = victimData.hearts - lossOnDeath;
            plugin.getHeartManager().setHearts(victim.getUniqueId(), currentVictimHearts);

            boolean isEliminated = currentVictimHearts <= minHearts;

            if (eligible) {
                int reward = isEliminated ? rewardOnElim : rewardOnKill;
                String transferMode = config.getString("hearts.transfer-mode", "PHYSICAL");

                HeartManager.PlayerData killerData = plugin.getHeartManager().getPlayerData(killer.getUniqueId());
                int killerMax = plugin.getHeartManager().getMaxHeartsLimit(killer);

                if (transferMode.equalsIgnoreCase("VIRTUAL")) {
                    if (killerData.hearts + reward > killerMax) {
                        if (config.getBoolean("hearts.drop-overflow-as-item", true)) {
                            dropHeartItem(victim.getLocation(), reward);
                        }
                    } else {
                        plugin.getHeartManager().changeHearts(killer.getUniqueId(), reward);
                        killer.sendMessage(ConfigManager.color("&a&l+" + reward + " Max Hearts (Kill Reward)"));
                    }
                } else {
                    // PHYSICAL MODE - drop reward as item
                    dropHeartItem(victim.getLocation(), reward);
                }

                // Broadcast PvP kill
                String killMsg = plugin.getConfigManager().getMessage("kill-broadcast", "en", Map.of(
                        "%killer%", killer.getName(),
                        "%victim%", victim.getName(),
                        "%amount%", String.valueOf(reward)
                ));
                Bukkit.broadcastMessage(killMsg);
            }

            if (isEliminated) {
                plugin.getEliminationManager().checkElimination(victim);
            }
        } else {
            // NATURAL / ENVIRONMENTAL DEATH FLOW
            int currentVictimHearts = victimData.hearts - lossOnDeath;
            plugin.getHeartManager().setHearts(victim.getUniqueId(), currentVictimHearts);

            boolean isEliminated = currentVictimHearts <= minHearts;
            boolean removesOnly = config.getBoolean("hearts.natural-death-removes-only", true);

            if (!removesOnly) {
                dropHeartItem(victim.getLocation(), lossOnDeath);
            }

            // Broadcast Natural Death
            String deathMsg = plugin.getConfigManager().getMessage("death-natural", "en", Map.of(
                    "%victim%", victim.getName(),
                    "%amount%", String.valueOf(lossOnDeath)
            ));
            Bukkit.broadcastMessage(deathMsg);

            if (isEliminated) {
                plugin.getEliminationManager().checkElimination(victim);
            }
        }
    }

    private void dropHeartItem(Location loc, int value) {
        String tierKey = "heart-I";
        if (value == 2) tierKey = "heart-II";
        else if (value == 3) tierKey = "heart-III";
        else if (value == 4) tierKey = "heart-IV";
        else if (value >= 5) tierKey = "heart-V";

        ItemStack heartItem = buildCustomItem(tierKey);
        if (heartItem != null && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc, heartItem);
        }
    }

    private ItemStack buildCustomItem(String tierKey) {
        return com.heartss.api.HeartssAddonAPI.getInstance().getCustomItem(tierKey);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();
        if (handItem == null || handItem.getType() == Material.AIR) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");
        String lang = plugin.getHeartManager().getLanguage(player.getUniqueId());

        // Match Custom Heart Item Consumption
        for (String key : List.of("heart-I", "heart-II", "heart-III", "heart-IV", "heart-V")) {
            String path = "items." + key;
            if (itemsYml.contains(path)) {
                String displayName = ConfigManager.color(itemsYml.getString(path + ".display-name"));
                if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName() &&
                        handItem.getItemMeta().getDisplayName().equals(displayName)) {
                    
                    event.setCancelled(true);
                    
                    int val = itemsYml.getInt(path + ".value", 1);
                    HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());
                    int maxCap = plugin.getHeartManager().getMaxHeartsLimit(player);

                    if (data.hearts >= maxCap) {
                        player.sendMessage(ConfigManager.color("&cYou are already at your maximum heart capacity!"));
                        return;
                    }

                    // Consume item
                    handItem.setAmount(handItem.getAmount() - 1);

                    // Add hearts
                    plugin.getHeartManager().changeHearts(player.getUniqueId(), val);

                    // Trigger sound & visual particle effects
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.2, 0), 10, 0.4, 0.4, 0.4, 0.2);

                    player.sendMessage(plugin.getConfigManager().getMessage("success-redeem", lang, Map.of("%amount%", String.valueOf(val))));
                    return;
                }
            }
        }

        // Match Sacrificial Scroll interaction
        ConfigurationSection scrollSection = itemsYml.getConfigurationSection("items.scrolls");
        if (scrollSection != null) {
            for (String scrollKey : scrollSection.getKeys(false)) {
                String path = "items.scrolls." + scrollKey;
                String displayName = ConfigManager.color(scrollSection.getString(scrollKey + ".display-name"));

                if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName() &&
                        handItem.getItemMeta().getDisplayName().equals(displayName)) {

                    event.setCancelled(true);

                    // Check Cooldown
                    long now = System.currentTimeMillis();
                    Map<String, Long> cooldowns = scrollCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
                    int cooldownSec = scrollSection.getInt(scrollKey + ".cooldown-seconds", 30);

                    if (cooldowns.containsKey(scrollKey)) {
                        long elapsed = (now - cooldowns.get(scrollKey)) / 1000;
                        if (elapsed < cooldownSec) {
                            player.sendMessage(plugin.getConfigManager().getMessage("scroll-cooldown", lang, Map.of("%seconds%", String.valueOf(cooldownSec - elapsed))));
                            return;
                        }
                    }

                    // Check cost
                    int heartCost = scrollSection.getInt(scrollKey + ".heart-cost", 1);
                    HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());
                    int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);

                    if (data.hearts - heartCost <= minHearts) {
                        player.sendMessage(ConfigManager.color("&cYour soul is too weak to perform this sacrifice!"));
                        return;
                    }

                    // Deduct cost & save
                    plugin.getHeartManager().changeHearts(player.getUniqueId(), -heartCost);

                    // Apply cooldown
                    cooldowns.put(scrollKey, now);

                    // Consume 1 scroll
                    handItem.setAmount(handItem.getAmount() - 1);

                    // Apply Potion Effects from configuration, format: EFFECT:AMPLIFIER:DURATION
                    List<String> effects = scrollSection.getStringList(scrollKey + ".effects");
                    for (String eff : effects) {
                        String[] parts = eff.split(":");
                        PotionEffectType type = PotionEffectType.getByName(parts[0]);
                        if (type != null) {
                            int amp = Integer.parseInt(parts[1]);
                            int dur = Integer.parseInt(parts[2]) * 20; // convert seconds to ticks
                            player.addPotionEffect(new PotionEffect(type, dur, amp));
                        }
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);

                    player.sendMessage(plugin.getConfigManager().getMessage("scroll-consumed", lang, Map.of("%hearts%", String.valueOf(heartCost))));
                    return;
                }
            }
        }

        // Match Revive Crystal interaction (double click opens dynamic revive list)
        String crystalName = ConfigManager.color(itemsYml.getString("items.revive-crystal.display-name"));
        if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName() &&
                handItem.getItemMeta().getDisplayName().equals(crystalName)) {
            event.setCancelled(true);
            plugin.getMenuManager().openReviveConfirmationMenu(player);
        }

        // Block Bed placement/interaction in Nether/End if configured
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");
            String worldName = block.getWorld().getName();

            if (block.getType().name().contains("BED")) {
                if (config.getBoolean("vanilla-preventions.prevent-beds", true) &&
                        (worldName.endsWith("_nether") || worldName.endsWith("_the_end"))) {
                    event.setCancelled(true);
                    player.sendMessage(ConfigManager.color("&cBed explosions are disabled on this server!"));
                }
            } else if (block.getType() == Material.RESPAWN_ANCHOR) {
                if (config.getBoolean("vanilla-preventions.prevent-respawn-anchors", true) &&
                        (!worldName.endsWith("_nether") && !worldName.endsWith("_the_end"))) {
                    event.setCancelled(true);
                    player.sendMessage(ConfigManager.color("&cRespawn Anchor explosions are disabled in this world!"));
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = event.getItemInHand();
        FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");

        String beaconDisplayName = ConfigManager.color(itemsYml.getString("items.revive-beacon.display-name"));
        if (handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName() &&
                handItem.getItemMeta().getDisplayName().equals(beaconDisplayName)) {
            
            if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("revive.beacon-enabled", true)) {
                event.setCancelled(true);
                player.sendMessage(ConfigManager.color("&cRevive Beacons are currently disabled."));
                return;
            }

            // Start revive beacon ritual countdown
            plugin.getEliminationManager().startReviveBeaconRitual(player, event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.BEACON) {
            EliminationManager.ActiveRitual active = plugin.getEliminationManager().getActiveRitualAt(block.getLocation());
            if (active != null) {
                event.setCancelled(true);
                plugin.getEliminationManager().cancelRitual(active.reviver);
                active.reviver.sendMessage(ConfigManager.color("&c&lRitual Aborted! &7Your Revive Beacon was interrupted or broken."));
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().trim();

        // Check if player is currently prompted to type a name during an active ritual
        if (plugin.getEliminationManager().handleReviveChatInput(player, msg)) {
            event.setCancelled(true); // Suppress chat broadcast
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof EnderCrystal) {
            if (plugin.getConfigManager().getConfig("config.yml").getBoolean("vanilla-preventions.prevent-crystal-pvp", false)) {
                if (event.getEntity() instanceof Player) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();
            ItemStack weapon = attacker.getInventory().getItemInMainHand();

            // Check Doomsday Sword hits
            if (weapon.getType() == Material.NETHERITE_SWORD && attacker.hasPermission("heartss.admin.doomsday")) {
                FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");
                String doomSwordName = ConfigManager.color(itemsYml.getString("items.doomsday-sword.display-name"));

                if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName() &&
                        weapon.getItemMeta().getDisplayName().equals(doomSwordName)) {
                    
                    event.setCancelled(true); // Bypass normal damage calculations
                    
                    // Instantly drain victim hearts
                    int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
                    plugin.getHeartManager().setHearts(victim.getUniqueId(), minHearts);
                    
                    // Force complete permanent ban elimination
                    plugin.getEliminationManager().checkElimination(victim);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            Player player = event.getPlayer();
            ItemStack hand = player.getInventory().getItemInMainHand();
            
            if (plugin.getConfigManager().getConfig("config.yml").getBoolean("vanilla-preventions.prevent-custom-items-in-item-frames", true)) {
                FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");
                if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                    String handName = hand.getItemMeta().getDisplayName();
                    
                    // Match items
                    for (String key : List.of("heart-I", "heart-II", "heart-III", "heart-IV", "heart-V", "revive-crystal", "revive-beacon", "revive-book")) {
                        String matchName = ConfigManager.color(itemsYml.getString("items." + key + ".display-name"));
                        if (handName.equals(matchName)) {
                            event.setCancelled(true);
                            player.sendMessage(ConfigManager.color("&cYou cannot place custom Heartss items in item frames (duplication prevention)."));
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getConfigManager().getConfig("config.yml").getBoolean("vanilla-preventions.prevent-totems", true)) {
                HeartManager.PlayerData data = plugin.getHeartManager().getPlayerData(player.getUniqueId());
                int minHearts = plugin.getConfigManager().getConfig("config.yml").getInt("hearts.min-hearts", 0);
                
                // If they are on their last heart and about to be eliminated, prevent totem from saving them
                if (data.hearts <= minHearts + 1) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        if (event.getViewers().isEmpty()) return;
        Player crafter = (Player) event.getViewers().get(0);

        FileConfiguration recipesYml = plugin.getConfigManager().getConfig("recipes.yml");
        FileConfiguration itemsYml = plugin.getConfigManager().getConfig("items.yml");

        // Loop through all custom recipes and check bounds/limits
        for (String recipeKey : recipesYml.getConfigurationSection("recipes").getKeys(false)) {
            String path = "recipes." + recipeKey;
            if (recipesYml.getBoolean(path + ".enabled", true)) {
                String matchName = "";
                if (recipeKey.startsWith("heart-")) {
                    matchName = ConfigManager.color(itemsYml.getString("items." + recipeKey + ".display-name"));
                } else if (recipeKey.equals("revive-crystal")) {
                    matchName = ConfigManager.color(itemsYml.getString("items.revive-crystal.display-name"));
                } else if (recipeKey.equals("revive-beacon")) {
                    matchName = ConfigManager.color(itemsYml.getString("items.revive-beacon.display-name"));
                }

                if (result.hasItemMeta() && result.getItemMeta().hasDisplayName() &&
                        result.getItemMeta().getDisplayName().equals(matchName)) {

                    // Check crafting limits
                    int craftLimit = recipesYml.getInt(path + ".craft-limit", -1);
                    if (craftLimit > 0) {
                        // Normally we would track count in player database, if limit exceeded, cancel
                        // For mock limit check (e.g. if player profile tracks craft counters):
                        // If limit is exceeded:
                        // inv.setResult(new ItemStack(Material.AIR));
                        // crafter.sendMessage(plugin.getConfigManager().getMessage("limit-reached", plugin.getHeartManager().getLanguage(crafter.getUniqueId())));
                    }
                }
            }
        }
    }
}
