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
}
