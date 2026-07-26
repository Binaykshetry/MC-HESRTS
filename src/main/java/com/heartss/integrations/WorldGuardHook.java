package com.heartss.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHook {

    private static StateFlag HEART_LOSS_FLAG;

    public static void registerFlag() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StateFlag flag = new StateFlag("heartloss", true);
            registry.register(flag);
            HEART_LOSS_FLAG = flag;
        } catch (FlagConflictException e) {
            try {
                HEART_LOSS_FLAG = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("heartloss");
            } catch (Exception ignored) {}
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    public static boolean isHeartLossAllowed(Player player, Location location) {
        if (HEART_LOSS_FLAG == null) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
            StateFlag.State state = set.queryState(null, HEART_LOSS_FLAG);
            if (state == StateFlag.State.DENY) {
                return false;
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
        return true;
    }
}
