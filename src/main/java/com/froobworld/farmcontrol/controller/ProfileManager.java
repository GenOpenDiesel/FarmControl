package com.froobworld.farmcontrol.controller;

import com.froobworld.farmcontrol.FarmControl;
import com.froobworld.farmcontrol.controller.action.Action;
import com.froobworld.farmcontrol.group.GroupDefinition;
import com.froobworld.farmcontrol.utils.EntityCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class ProfileManager {

    public static final int PASSIVE_MOB_LIMIT = 20;
    public static final int HOSTILE_MOB_LIMIT = 20;
    public static final int VILLAGER_LIMIT = 15;
    public static final String PASSIVE_MOB_LIMIT_PROFILE = "hardcoded-passive-mob-limit";
    public static final String HOSTILE_MOB_LIMIT_PROFILE = "hardcoded-hostile-mob-limit";
    public static final String VILLAGER_LIMIT_PROFILE = "hardcoded-villager-limit";
    private static final String LEGACY_MOB_LIMIT_PROFILE = "limit-mobs-per-chunk";
    private static final String LEGACY_VILLAGER_LIMIT_PROFILE = "limit-villagers-per-chunk";
    private static final String TRIM_ANIMAL_FARMS_PROFILE = "trim-animal-farms";
    private static final String TRIM_SPARSE_ANIMAL_FARMS_PROFILE = "trim-sparse-animal-farms";
    private static final String TRIM_VILLAGER_CHUNKS_PROFILE = "trim-villager-chunks";
    private static final Set<String> RESERVED_LIMIT_PROFILES = Set.of(
            PASSIVE_MOB_LIMIT_PROFILE,
            HOSTILE_MOB_LIMIT_PROFILE,
            VILLAGER_LIMIT_PROFILE,
            LEGACY_MOB_LIMIT_PROFILE,
            LEGACY_VILLAGER_LIMIT_PROFILE,
            TRIM_ANIMAL_FARMS_PROFILE,
            TRIM_SPARSE_ANIMAL_FARMS_PROFILE,
            TRIM_VILLAGER_CHUNKS_PROFILE
    );

    private final FarmControl farmControl;
    private final Map<String, ActionProfile> actionProfileMap = new HashMap<>();

    public ProfileManager(FarmControl farmControl) {
        this.farmControl = farmControl;
    }

    public ActionProfile getActionProfile(String profileName) {
        return actionProfileMap.get(profileName);
    }

    public void load() throws IOException {
        File file = new File(farmControl.getDataFolder(), "profiles.yml");
        if (!file.exists()) {
            saveDefaultProfiles(file);
        }

        ConfigurationSection profilesSection = YamlConfiguration.loadConfiguration(file).getConfigurationSection("profiles");
        if (profilesSection == null) {
            farmControl.getLogger().warning("The file 'profiles.yml' has no 'profiles' section - it is empty or malformed.");
            File brokenFile = new File(farmControl.getDataFolder(), "profiles.yml.broken");
            Files.deleteIfExists(brokenFile.toPath());
            Files.move(file.toPath(), brokenFile.toPath());
            farmControl.getLogger().warning("The old file has been kept as 'profiles.yml.broken' and the defaults have been regenerated.");
            saveDefaultProfiles(file);
            profilesSection = YamlConfiguration.loadConfiguration(file).getConfigurationSection("profiles");
            if (profilesSection == null) {
                farmControl.getLogger().severe("Unable to load any profiles - FarmControl will not act on any entities.");
                return;
            }
        }
        for (String name : profilesSection.getKeys(false)) {
            // Mob limits are owned by code. Never interpret a configured profile
            // with a reserved current or legacy name, even if disk cleanup failed.
            if (isHardcodedLimitProfile(name)) {
                continue;
            }

            try {
                ConfigurationSection profileSection = Objects.requireNonNull(profilesSection.getConfigurationSection(name));
                GroupDefinition groupDefinition = GroupDefinition.fromConfigurationSection(farmControl, name, Objects.requireNonNull(profileSection.getConfigurationSection("group")));
                Set<Action> actions = new HashSet<>();
                for (String actionName : profileSection.getStringList("actions")) {
                    Action action = farmControl.getActionManager().getAction(actionName.toLowerCase());
                    if (action == null) {
                        farmControl.getLogger().warning("Unknown action for profile '" + name + "': '" + actionName.toLowerCase() + "'");
                        continue;
                    }

                    Set<String> incompatibleCategories = new HashSet<>();
                    for (EntityCategory memberCategory : groupDefinition.getMemberCategories()) {
                        if (!memberCategory.isCompatibleWith(action)) {
                            incompatibleCategories.add(memberCategory.getName());
                        }
                    }

                    if (!incompatibleCategories.isEmpty()) {
                        String incompatibleCategoriesString = incompatibleCategories.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(", "));
                        farmControl.getLogger().warning("Note: action '" + actionName + "' in profile '" + name + "' is incompatible with the following entity types: " + incompatibleCategoriesString);
                    }

                    actions.add(action);
                }

                actionProfileMap.put(name, new ActionProfile(groupDefinition, actions));
            } catch (Exception ex) {
                ex.printStackTrace();
                farmControl.getLogger().warning("Unable to load the profile '" + name + "'. Incorrect syntax?");
            }
        }

        addHardcodedMobLimitProfiles();
    }

    public void reload() throws IOException {
        actionProfileMap.clear();
        load();
    }

    public static boolean isHardcodedLimitProfile(String profileName) {
        return profileName != null && RESERVED_LIMIT_PROFILES.contains(profileName.toLowerCase(Locale.ROOT));
    }

    /**
     * Removes all configurable entity-removal profiles and obsolete assignments.
     * The safe profiles file is regenerated later by {@link #load()}.
     */
    public static void purgeConfiguredLimitProfiles(FarmControl farmControl) {
        int removedEntries = 0;

        try {
            if (deleteProfilesFile(new File(farmControl.getDataFolder(), "profiles.yml"))) {
                removedEntries++;
            }
        } catch (Exception exception) {
            farmControl.getLogger().warning("Could not delete profiles.yml: "
                    + exception.getMessage());
        }

        try {
            removedEntries += purgeConfigFile(new File(farmControl.getDataFolder(), "config.yml"));
        } catch (Exception exception) {
            farmControl.getLogger().warning("Could not remove mob-limit profile assignments from config.yml: "
                    + exception.getMessage());
        }

        if (removedEntries > 0) {
            farmControl.getLogger().info("Removed " + removedEntries
                    + " configurable profile files or assignments; entity-removal limits are enforced only by code.");
        }
    }

    static boolean deleteProfilesFile(File file) throws IOException {
        return Files.deleteIfExists(file.toPath());
    }

    static int purgeConfigFile(File file) throws Exception {
        if (!file.isFile()) {
            return 0;
        }

        YamlConfiguration configuration = loadYaml(file);
        ConfigurationSection worldSettings = configuration.getConfigurationSection("world-settings");
        if (worldSettings == null) {
            return 0;
        }

        int removed = 0;
        for (String worldName : worldSettings.getKeys(false)) {
            for (String mode : List.of("proactive", "reactive")) {
                String path = worldName + ".profiles." + mode;
                List<String> configuredProfiles = new ArrayList<>(worldSettings.getStringList(path));
                int previousSize = configuredProfiles.size();
                configuredProfiles.removeIf(ProfileManager::isHardcodedLimitProfile);
                int removedFromList = previousSize - configuredProfiles.size();
                if (removedFromList > 0) {
                    worldSettings.set(path, configuredProfiles);
                    removed += removedFromList;
                }
            }
        }

        if (removed > 0) {
            configuration.save(file);
        }
        return removed;
    }

    private static YamlConfiguration loadYaml(File file) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(file);
        return configuration;
    }

    private void saveDefaultProfiles(File file) throws IOException {
        file.getParentFile().mkdirs();
        try (InputStream inputStream = farmControl.getResource("resources/profiles.yml")) {
            if (inputStream == null) {
                throw new IOException("The bundled resource 'resources/profiles.yml' is missing from the jar.");
            }
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void addHardcodedMobLimitProfiles() {
        Action killAction = Objects.requireNonNull(farmControl.getActionManager().getAction("kill"));
        EntityCategory allMobs = Objects.requireNonNull(EntityCategory.ofName("category:mob"));
        EntityCategory hostileMobs = Objects.requireNonNull(EntityCategory.ofName("category:enemy"));
        EntityCategory villagers = Objects.requireNonNull(EntityCategory.ofName("villager"));

        ActionProfile passiveMobLimit = new ActionProfile(
                new GroupDefinition(
                        Set.of(allMobs),
                        Set.of(hostileMobs, villagers),
                        PASSIVE_MOB_LIMIT + 1,
                        0,
                        true,
                        false,
                        false
                ),
                Set.of(killAction)
        );
        ActionProfile hostileMobLimit = new ActionProfile(
                new GroupDefinition(
                        Set.of(hostileMobs),
                        Collections.emptySet(),
                        HOSTILE_MOB_LIMIT + 1,
                        0,
                        true,
                        false,
                        false
                ),
                Set.of(killAction)
        );
        ActionProfile villagerLimit = new ActionProfile(
                new GroupDefinition(
                        Set.of(villagers),
                        Collections.emptySet(),
                        VILLAGER_LIMIT + 1,
                        0,
                        true,
                        false,
                        false
                ),
                Set.of(killAction)
        );

        actionProfileMap.put(PASSIVE_MOB_LIMIT_PROFILE, passiveMobLimit);
        actionProfileMap.put(HOSTILE_MOB_LIMIT_PROFILE, hostileMobLimit);
        actionProfileMap.put(VILLAGER_LIMIT_PROFILE, villagerLimit);
    }
}
