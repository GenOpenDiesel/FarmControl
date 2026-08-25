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

    public static final int HARDCODED_MOB_LIMIT = 20;
    public static final String PASSIVE_MOB_LIMIT_PROFILE = "hardcoded-passive-mob-limit";
    public static final String HOSTILE_MOB_LIMIT_PROFILE = "hardcoded-hostile-mob-limit";
    private static final String LEGACY_MOB_LIMIT_PROFILE = "limit-mobs-per-chunk";
    private static final String LEGACY_VILLAGER_LIMIT_PROFILE = "limit-villagers-per-chunk";

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
        EntityCategory monsters = Objects.requireNonNull(EntityCategory.ofName("category:monster"));

        ActionProfile passiveMobLimit = new ActionProfile(
                new GroupDefinition(
                        Set.of(allMobs),
                        Set.of(monsters),
                        HARDCODED_MOB_LIMIT + 1,
                        0,
                        true,
                        false,
                        false
                ),
                Set.of(killAction)
        );
        ActionProfile hostileMobLimit = new ActionProfile(
                new GroupDefinition(
                        Set.of(monsters),
                        Collections.emptySet(),
                        HARDCODED_MOB_LIMIT + 1,
                        0,
                        true,
                        false,
                        false
                ),
                Set.of(killAction)
        );

        actionProfileMap.put(PASSIVE_MOB_LIMIT_PROFILE, passiveMobLimit);
        actionProfileMap.put(HOSTILE_MOB_LIMIT_PROFILE, hostileMobLimit);

        // Keep old installations safe: their config still references these profile names.
        // Aliasing them prevents the previous combined/villager limits from being loaded.
        actionProfileMap.put(LEGACY_MOB_LIMIT_PROFILE, passiveMobLimit);
        actionProfileMap.put(LEGACY_VILLAGER_LIMIT_PROFILE, hostileMobLimit);
    }
}
