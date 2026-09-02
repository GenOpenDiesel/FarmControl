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
    public void removesLimitProfilesButKeepsOtherProfiles() throws Exception {
        File file = temporaryFolder.newFile("profiles.yml");
        Files.writeString(file.toPath(), """
                profiles:
                  limit-mobs-per-chunk:
                    group: {}
                  HARDCODED-VILLAGER-LIMIT:
                    group: {}
                  custom-profile:
                    group:
                      count: 7
                """);

        assertEquals(2, ProfileManager.purgeProfilesFile(file));

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        assertFalse(configuration.contains("profiles.limit-mobs-per-chunk"));
        assertFalse(configuration.contains("profiles.HARDCODED-VILLAGER-LIMIT"));
        assertNotNull(configuration.getConfigurationSection("profiles.custom-profile"));
        assertEquals(7, configuration.getInt("profiles.custom-profile.group.count"));
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
                      reactive:
                        - hardcoded-hostile-mob-limit
                        - freeze-animal-farms
                  skyblock:
                    profiles:
                      proactive:
                        - LIMIT-VILLAGERS-PER-CHUNK
                        - custom-profile
                """);

        assertEquals(4, ProfileManager.purgeConfigFile(file));

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        assertEquals(List.of("soft-nerf-animal-farms"),
                configuration.getStringList("world-settings.default.profiles.proactive"));
        assertEquals(List.of("freeze-animal-farms"),
                configuration.getStringList("world-settings.default.profiles.reactive"));
        assertEquals(List.of("custom-profile"),
                configuration.getStringList("world-settings.skyblock.profiles.proactive"));
    }
}
