package com.froobworld.farmcontrol.debug;

import com.froobworld.farmcontrol.FarmControl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records who or what killed the mobs inside the configured watch zones.
 *
 * <p>Damage is remembered for a short while so that a mob which is later removed rather than killed outright
 * (a frog eating a magma cube, a mob merging plugin, ...) can still be attributed to its attacker.</p>
 */
public class DeathWatchListener implements Listener {

    private static final long DAMAGE_MEMORY_MILLIS = 30_000L;
    private static final int DAMAGE_MEMORY_MAX_ENTRIES = 4096;

    private final FarmControl farmControl;
    private final MobRemovalLogger logger;
    private final Map<UUID, DamageInfo> lastDamage = new ConcurrentHashMap<>();

    public DeathWatchListener(FarmControl farmControl, MobRemovalLogger logger) {
        this.farmControl = farmControl;
        this.logger = logger;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, farmControl);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        lastDamage.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!logger.isDeathWatchEnabled()) {
            return;
        }

        Entity victim = event.getEntity();
        if (!logger.isWatchedType(victim) || !logger.isWatched(victim.getLocation())) {
            return;
        }

        purgeStaleDamage();
        lastDamage.put(victim.getUniqueId(), DamageInfo.of(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!logger.isDeathWatchEnabled() || !logger.isLogDeaths()) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !logger.isWatchedType(victim)) {
            return;
        }

        if (logger.wasJustCulled(victim.getUniqueId())) {
            // FarmControl killed it, and has already logged that.
            lastDamage.remove(victim.getUniqueId());
            return;
        }

        Location location = victim.getLocation();
        WatchZone zone = logger.zoneOf(location);
        if (zone == null && !logger.isWatched(location)) {
            return;
        }

        DamageInfo damageInfo = lastDamage.remove(victim.getUniqueId());
        if (damageInfo == null) {
            damageInfo = DamageInfo.of(victim.getLastDamageCause());
        }

        LossRecord.Builder builder = LossRecord.builder()
                .kind(LossRecord.Kind.DEATH)
                .zone(zone == null ? null : zone.getName())
                .victimType(victim.getType().name())
                .victimUuid(victim.getUniqueId().toString())
                .victimName(victim.getCustomName())
                .world(location.getWorld() == null ? "unknown" : location.getWorld().getName())
                .position(location.getX(), location.getY(), location.getZ());

        applyDamageInfo(builder, damageInfo, location);
        logger.record(builder.build());
    }

    /**
     * Fills in the "what got it" half of the record.
     */
    void applyDamageInfo(LossRecord.Builder builder, DamageInfo damageInfo, Location victimLocation) {
        if (damageInfo == null) {
            builder.cause("UNKNOWN");
            return;
        }

        builder.cause(damageInfo.cause)
                .damage(damageInfo.damage)
                .killerType(damageInfo.killerType)
                .killerName(damageInfo.killerName)
                .killerUuid(damageInfo.killerUuid)
                .weapon(damageInfo.weapon)
                .extra(damageInfo.extra);

        if (damageInfo.killerLocation != null
                && victimLocation.getWorld() != null
                && victimLocation.getWorld().equals(damageInfo.killerLocation.getWorld())) {
            builder.killerDistance(damageInfo.killerLocation.distance(victimLocation));
        }
    }

    DamageInfo getRememberedDamage(UUID uuid) {
        DamageInfo damageInfo = lastDamage.get(uuid);
        if (damageInfo != null && System.currentTimeMillis() - damageInfo.timestamp > DAMAGE_MEMORY_MILLIS) {
            lastDamage.remove(uuid);
            return null;
        }

        return damageInfo;
    }

    void forgetDamage(UUID uuid) {
        lastDamage.remove(uuid);
    }

    private void purgeStaleDamage() {
        if (lastDamage.size() < DAMAGE_MEMORY_MAX_ENTRIES) {
            return;
        }

        long cutoff = System.currentTimeMillis() - DAMAGE_MEMORY_MILLIS;
        lastDamage.values().removeIf(info -> info.timestamp < cutoff);
    }

    /**
     * A snapshot of the last damage an entity took, resolved down to the mob actually responsible.
     */
    static class DamageInfo {
        final long timestamp = System.currentTimeMillis();
        String cause = "UNKNOWN";
        double damage;
        String killerType;
        String killerName;
        String killerUuid;
        String weapon;
        String extra;
        Location killerLocation;

        static DamageInfo of(EntityDamageEvent event) {
            DamageInfo info = new DamageInfo();
            if (event == null) {
                return info;
            }

            info.cause = event.getCause().name();
            info.damage = event.getFinalDamage();

            if (event instanceof EntityDamageByBlockEvent blockEvent && blockEvent.getDamager() != null) {
                info.extra = "block=" + blockEvent.getDamager().getType().name();
                return info;
            }

            if (event instanceof EntityDamageByEntityEvent entityEvent) {
                info.describeDamager(entityEvent.getDamager());
            }

            return info;
        }

        /**
         * Walks through projectiles, potion clouds, TNT and evoker fangs to find the mob behind the damage.
         */
        private void describeDamager(Entity damager) {
            Entity culprit = damager;
            if (damager instanceof Projectile projectile) {
                weapon = projectile.getType().name();
                ProjectileSource source = projectile.getShooter();
                if (source instanceof Entity shooter) {
                    culprit = shooter;
                } else {
                    extra = "shooter=" + (source == null ? "unknown" : source.getClass().getSimpleName());
                }
            } else if (damager instanceof AreaEffectCloud cloud) {
                weapon = "AREA_EFFECT_CLOUD";
                if (cloud.getSource() instanceof Entity source) {
                    culprit = source;
                }
            } else if (damager instanceof TNTPrimed tnt) {
                weapon = "TNT";
                if (tnt.getSource() != null) {
                    culprit = tnt.getSource();
                }
            } else if (damager instanceof EvokerFangs fangs) {
                weapon = "EVOKER_FANGS";
                if (fangs.getOwner() != null) {
                    culprit = fangs.getOwner();
                }
            }

            killerType = culprit.getType().name();
            killerUuid = culprit.getUniqueId().toString();
            killerLocation = culprit.getLocation();

            if (culprit instanceof Player player) {
                killerName = player.getName();
            } else if (culprit.getCustomName() != null) {
                killerName = culprit.getCustomName();
            }

            if (weapon == null && culprit instanceof LivingEntity living && living.getEquipment() != null) {
                ItemStack handItem = living.getEquipment().getItemInMainHand();
                if (handItem.getType() != org.bukkit.Material.AIR) {
                    weapon = handItem.getType().name().toLowerCase(Locale.ROOT);
                }
            }
        }
    }
}
