package com.froobworld.farmcontrol.controller;

import com.froobworld.farmcontrol.utils.EntityCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProfileManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recognisesCurrentAndLegacyLimitProfileNames() {
        assertTrue(ProfileManager.isHardcodedLimitProfile("hardcoded-passive-mob-limit"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("HARDCODED-HOSTILE-MOB-LIMIT"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("hardcoded-villager-limit"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("limit-mobs-per-chunk"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("limit-villagers-per-chunk"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("trim-animal-farms"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("TRIM-SPARSE-ANIMAL-FARMS"));
        assertTrue(ProfileManager.isHardcodedLimitProfile("trim-villager-chunks"));
        assertFalse(ProfileManager.isHardcodedLimitProfile("soft-nerf-animal-farms"));
    }

    @Test
    public void hostileCategoryCoversAllBukkitEnemies() {
        EntityCategory hostileMobs = EntityCategory.ofName("category:enemy");
        assertNotNull(hostileMobs);
        assertTrue(hostileMobs.isMember(EntityType.ZOMBIE));
        assertTrue(hostileMobs.isMember(EntityType.GHAST));
        assertTrue(hostileMobs.isMember(EntityType.SLIME));
        assertFalse(hostileMobs.isMember(EntityType.COW));
        assertFalse(hostileMobs.isMember(EntityType.VILLAGER));
    }

    @Test
    public void deletesConfiguredProfilesFile() throws Exception {
        File file = temporaryFolder.newFile("profiles.yml");
        Files.writeString(file.toPath(), """
                profiles:
                  custom-profile:
                    group:
                      count: 7
                """);

        assertTrue(ProfileManager.deleteProfilesFile(file));
        assertFalse(file.exists());
        assertFalse(ProfileManager.deleteProfilesFile(file));
    }

    @Test
    public void removesLimitAssignmentsFromEveryWorldAndMode() throws Exception {
        File file = temporaryFolder.newFile("config.yml");
        Files.writeString(file.toPath(), """
                world-settings:
                  default:
                    profiles:
                      proactive:
                        - soft-nerf-animal-farms
                        - limit-mobs-per-chunk
                        - hardcoded-villager-limit
                        - trim-villager-chunks
                      reactive:
                        - hardcoded-hostile-mob-limit
                        - freeze-animal-farms
                        - trim-sparse-animal-farms
                  skyblock:
                    profiles:
                      proactive:
                        - LIMIT-VILLAGERS-PER-CHUNK
                        - custom-profile
                        - TRIM-ANIMAL-FARMS
                """);

        assertEquals(7, ProfileManager.purgeConfigFile(file));

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        assertEquals(List.of("soft-nerf-animal-farms"),
                configuration.getStringList("world-settings.default.profiles.proactive"));
        assertEquals(List.of("freeze-animal-farms"),
                configuration.getStringList("world-settings.default.profiles.reactive"));
        assertEquals(List.of("custom-profile"),
                configuration.getStringList("world-settings.skyblock.profiles.proactive"));
    }
}
