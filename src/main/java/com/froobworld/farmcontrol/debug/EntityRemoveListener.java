package com.froobworld.farmcontrol.debug;

import com.froobworld.farmcontrol.FarmControl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Records mobs that vanish without a death event - despawns, transformations, merges and plugin removals.
 *
 * <p>Only registered when the server provides Paper's EntityRemoveEvent.</p>
 */
public class EntityRemoveListener implements Listener {

    private final FarmControl farmControl;
    private final MobRemovalLogger logger;
    private final DeathWatchListener deathWatchListener;

    public EntityRemoveListener(FarmControl farmControl, MobRemovalLogger logger, DeathWatchListener deathWatchListener) {
        this.farmControl = farmControl;
        this.logger = logger;
        this.deathWatchListener = deathWatchListener;
    }

    /**
     * @return true if this server has the API needed for removal tracking.
     */
    public static boolean isSupported() {
        try {
            Class.forName("org.bukkit.event.entity.EntityRemoveEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, farmControl);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!logger.isDeathWatchEnabled() || !logger.isLogRemovals()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Player || !logger.isWatchedType(entity)) {
            return;
        }

        if (logger.wasJustCulled(entity.getUniqueId())) {
            // FarmControl removed it, and has already logged that.
            deathWatchListener.forgetDamage(entity.getUniqueId());
            return;
        }

        String cause = event.getCause().name();
        if (event.getCause() == EntityRemoveEvent.Cause.DEATH) {
            // Already covered, in more detail, by the death listener.
            deathWatchListener.forgetDamage(entity.getUniqueId());
            return;
        }

        if (logger.isIgnoredRemovalCause(cause)) {
            deathWatchListener.forgetDamage(entity.getUniqueId());
            return;
        }

        Location location = entity.getLocation();
        WatchZone zone = logger.zoneOf(location);
        if (zone == null && !logger.isWatched(location)) {
            deathWatchListener.forgetDamage(entity.getUniqueId());
            return;
        }

        LossRecord.Builder builder = LossRecord.builder()
                .kind(LossRecord.Kind.REMOVAL)
                .zone(zone == null ? null : zone.getName())
                .victimType(entity.getType().name())
                .victimUuid(entity.getUniqueId().toString())
                .victimName(entity.getCustomName())
                .world(location.getWorld() == null ? "unknown" : location.getWorld().getName())
                .position(location.getX(), location.getY(), location.getZ())
                .cause("REMOVE_" + cause);

        // If something hurt it just before it vanished, name that attacker too - this is what catches
        // interactions like a frog eating a magma cube, where the mob is removed rather than killed.
        DeathWatchListener.DamageInfo damageInfo = deathWatchListener.getRememberedDamage(entity.getUniqueId());
        if (damageInfo != null && damageInfo.killerType != null) {
            builder.killerType(damageInfo.killerType)
                    .killerName(damageInfo.killerName)
                    .killerUuid(damageInfo.killerUuid)
                    .weapon(damageInfo.weapon);
            if (damageInfo.killerLocation != null
                    && location.getWorld() != null
                    && location.getWorld().equals(damageInfo.killerLocation.getWorld())) {
                builder.killerDistance(damageInfo.killerLocation.distance(location));
            }
            builder.extra("lastDamage=" + damageInfo.cause);
        }

        if (event.getCause() == EntityRemoveEvent.Cause.PLUGIN || event.getCause() == EntityRemoveEvent.Cause.DISCARD) {
            String responsiblePlugin = findResponsiblePlugin();
            if (responsiblePlugin != null) {
                builder.extra("plugin=" + responsiblePlugin);
            }
        }

        deathWatchListener.forgetDamage(entity.getUniqueId());
        logger.record(builder.build());
    }

    /**
     * Walks the call stack to find which plugin asked for the removal.
     */
    private String findResponsiblePlugin() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("java.")
                    || className.startsWith("org.bukkit.")
                    || className.startsWith("net.minecraft.")
                    || className.startsWith("io.papermc.")
                    || className.startsWith("ca.spottedleaf.")
                    || className.startsWith("org.spigotmc.")
                    || className.startsWith("com.destroystokyo.")
                    || className.startsWith("com.froobworld.farmcontrol.debug.")) {
                continue;
            }

            try {
                Plugin plugin = JavaPlugin.getProvidingPlugin(Class.forName(className));
                return plugin.getName() + " (" + element.getClassName() + "#" + element.getMethodName() + ")";
            } catch (Throwable ignored) {
                // Not a plugin class - keep walking.
            }
        }

        return null;
    }
}
