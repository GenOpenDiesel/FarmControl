package com.froobworld.farmcontrol.controller;

import com.froobworld.farmcontrol.FarmControl;
import com.froobworld.farmcontrol.debug.MobRemovalLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immediately kills newly spawned iron golems when this server is running in skyblock mode.
 */
public final class SkyblockIronGolemKillListener implements Listener {

    private static final String SKYBLOCK_MODE = "skyblock";

    private final FarmControl farmControl;
    private final MobRemovalLogger mobRemovalLogger;
    private final Path modeFile;
    private boolean enabled;

    public SkyblockIronGolemKillListener(FarmControl farmControl, MobRemovalLogger mobRemovalLogger) {
        this.farmControl = farmControl;
        this.mobRemovalLogger = mobRemovalLogger;
        this.modeFile = farmControl.getServer().getWorldContainer().toPath().resolve("tryb.txt");
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, farmControl);
        reload();
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        try {
            enabled = isSkyblockMode(modeFile);
        } catch (IOException exception) {
            enabled = false;
            farmControl.getLogger().warning("Nie udalo sie odczytac pliku '" + modeFile
                    + "'. Natychmiastowe zabijanie iron golemow pozostaje wylaczone: "
                    + exception.getMessage());
        }

        farmControl.getLogger().info("Natychmiastowe zabijanie iron golemow: "
                + (enabled ? "wlaczone (tryb skyblock)." : "wylaczone (tryb inny niz skyblock)."));
    }

    static boolean isSkyblockMode(Path modeFile) throws IOException {
        if (!Files.isRegularFile(modeFile)) {
            return false;
        }

        String mode = Files.readString(modeFile, StandardCharsets.UTF_8).strip();
        if (!mode.isEmpty() && mode.charAt(0) == '\uFEFF') {
            mode = mode.substring(1).strip();
        }
        return SKYBLOCK_MODE.equalsIgnoreCase(mode);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!enabled || event.getEntityType() != EntityType.IRON_GOLEM) {
            return;
        }

        LivingEntity ironGolem = event.getEntity();
        mobRemovalLogger.logRemoval(ironGolem, "skyblock-instant-kill");
        ironGolem.setHealth(0.0);
    }
}
