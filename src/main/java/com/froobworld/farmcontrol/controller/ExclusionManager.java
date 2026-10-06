package com.froobworld.farmcontrol.controller;

import com.froobworld.farmcontrol.FarmControl;
import com.froobworld.farmcontrol.config.FcConfig;
import com.froobworld.farmcontrol.controller.entity.SnapshotEntity;
import org.bukkit.World;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class ExclusionManager {

    /**
     * Metadata keys marking an entity another plugin owns, excluded always, whatever
     * exclusion-settings.metadata says.
     *
     * <h2>Why this is in code rather than in the config</h2>
     * The mob limits in this fork are hardcoded (see {@link ProfileManager}), so an entity can be
     * killed by a profile that appears in no file at all. The config exclusion list is then the only
     * brake, and on a running server that list already exists: entries added to resources/config.yml
     * do not reach it on their own, because the config is upgraded through versioned patches. An
     * exclusion another plugin breaks without cannot depend on somebody remembering to edit a file.
     *
     * <h2>"Pet"</h2>
     * Pet plugins put an invisible carrier mob under the model and mark it with this key (Pety3D:
     * {@code entity.setMetadata("Pet", ...)}). Walking pets ride a tamed wolf, so the {@code tamed}
     * exclusion covered them. Flying ones ride a vex, which is neither tameable nor named but does
     * count towards the {@code category:enemy} limit, so on an island with a mob farm it died within
     * a second of being summoned. The {@code kill} action calls {@code setHealth(0)}, so the
     * {@code setInvulnerable(true)} the pet plugin sets did not stop it.
     */
    private static final Set<String> ALWAYS_EXCLUDED_METADATA = Set.of("Pet");

    private final FarmControl farmControl;

    public ExclusionManager(FarmControl farmControl) {
        this.farmControl = farmControl;
    }

    public Predicate<SnapshotEntity> getExclusionPredicate(World world) {
        FcConfig.WorldSettings.ExclusionSettings exclusionSettings = farmControl.getFcConfig().worldSettings.of(world).exclusionSettings;
        boolean excludeLeashed = exclusionSettings.leashed.get();
        boolean excludeLoveMode = exclusionSettings.loveMode.get();
        List<String> excludeMeta = exclusionSettings.metadata.get();
        boolean excludeNamed = exclusionSettings.named.get();
        boolean excludeTamed = exclusionSettings.tamed.get();
        boolean excludePatrolLeaders = exclusionSettings.patrolLeader.get();
        List<String> excludeType = exclusionSettings.type.get();
        long excludeTicksLived = exclusionSettings.youngerThan.get();
        boolean excludePickupable = exclusionSettings.pickupable.get();
        boolean excludeMounted = exclusionSettings.mounted.get();
        return snapshotEntity -> {
            if (excludeLeashed && snapshotEntity.isLeashed()) {
                return true;
            }

            if (excludeLoveMode && snapshotEntity.isLoveMode()) {
                return true;
            }

            if (excludeNamed && snapshotEntity.hasCustomName()) {
                return true;
            }

            if (excludeTamed && snapshotEntity.isTamed()) {
                return true;
            }

            if (excludePatrolLeaders && snapshotEntity.isPatrolLeader()) {
                return true;
            }

            if (excludePickupable && snapshotEntity.isPickupable()) {
                return true;
            }

            if (excludeMounted && snapshotEntity.isMounted()) {
                return true;
            }

            if (snapshotEntity.getTicksLived() < excludeTicksLived) {
                return true;
            }

            for (String meta : ALWAYS_EXCLUDED_METADATA) {
                if (snapshotEntity.hasMetadata(meta)) {
                    return true;
                }
            }

            for (String meta : excludeMeta) {
                if (snapshotEntity.hasMetadata(meta)) {
                    return true;
                }
            }

            for (String type : excludeType) {
                if (snapshotEntity.getEntityType().toString().equalsIgnoreCase(type)) {
                    return true;
                }
            }

            return false;
        };
    }
}
