package com.heartss.integrations;

import com.heartss.Heartss;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.UUID;

public class LuckPermsHook {

    private static LuckPerms luckPerms;
    private static boolean enabled = false;

    public static void init() {
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                enabled = true;
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            enabled = false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the highest maxhearts permission value assigned to a player.
     * Checks nodes: hearts.maxhearts.<value> or heartss.maxhearts.<value>
     */
    public static OptionalInt getMaxHeartsPermission(UUID playerUUID) {
        if (!enabled || luckPerms == null) {
            return OptionalInt.empty();
        }

        try {
            User user = luckPerms.getUserManager().getUser(playerUUID);
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(playerUUID).join();
            }

            if (user != null) {
                int highest = -1;
                for (Node node : user.getNodes()) {
                    if (node instanceof PermissionNode) {
                        String key = node.getKey();
                        if (key.startsWith("hearts.maxhearts.") || key.startsWith("heartss.maxhearts.")) {
                            try {
                                int len = key.startsWith("heartss.maxhearts.") ? "heartss.maxhearts.".length() : "hearts.maxhearts.".length();
                                int val = Integer.parseInt(key.substring(len));
                                if (val > highest) {
                                    highest = val;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                if (highest != -1) {
                    return OptionalInt.of(highest);
                }
            }
        } catch (Exception ignored) {}

        return OptionalInt.empty();
    }

    /**
     * Set a permission node for a player.
     */
    public static boolean addPermission(UUID playerUUID, String permission) {
        if (!enabled || luckPerms == null) {
            return false;
        }
        try {
            User user = luckPerms.getUserManager().getUser(playerUUID);
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(playerUUID).join();
            }
            if (user != null) {
                PermissionNode node = PermissionNode.builder(permission).value(true).build();
                user.data().add(node);
                luckPerms.getUserManager().saveUser(user);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Remove a permission node from a player.
     */
    public static boolean removePermission(UUID playerUUID, String permission) {
        if (!enabled || luckPerms == null) {
            return false;
        }
        try {
            User user = luckPerms.getUserManager().getUser(playerUUID);
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(playerUUID).join();
            }
            if (user != null) {
                PermissionNode node = PermissionNode.builder(permission).build();
                user.data().remove(node);
                luckPerms.getUserManager().saveUser(user);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Check if a player has a specific permission node using LuckPerms directly.
     */
    public static boolean hasPermission(UUID playerUUID, String permission) {
        if (!enabled || luckPerms == null) {
            return false;
        }
        try {
            User user = luckPerms.getUserManager().getUser(playerUUID);
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(playerUUID).join();
            }
            if (user != null) {
                return user.getNodes().contains(PermissionNode.builder(permission).build());
            }
        } catch (Exception ignored) {}
        return false;
    }
}
