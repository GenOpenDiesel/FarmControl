package com.froobworld.farmcontrol;

import com.froobworld.farmcontrol.command.FarmControlCommand;
import com.froobworld.farmcontrol.config.FcConfig;
import com.froobworld.farmcontrol.controller.*;
import com.froobworld.farmcontrol.controller.action.RemoveRandomMovementAction;
import com.froobworld.farmcontrol.debug.DeathWatchListener;
import com.froobworld.farmcontrol.debug.EntityRemoveListener;
import com.froobworld.farmcontrol.debug.MobRemovalLogger;
import com.froobworld.farmcontrol.message.MessageManager;
import com.froobworld.farmcontrol.metrics.FcMetrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class FarmControl extends JavaPlugin {

    private FcConfig fcConfig;
    private HookManager hookManager;
    private ActionManager actionManager;
    private TriggerManager triggerManager;
    private ProfileManager profileManager;
    private ExclusionManager exclusionManager;
    private FarmController farmController;
    private MessageManager messageManager;
    private MobRemovalLogger mobRemovalLogger;
    private DeathWatchListener deathWatchListener;
    private EntityRemoveListener entityRemoveListener;

    public void onEnable() {
        ProfileManager.purgeConfiguredLimitProfiles(this);
        this.fcConfig = new FcConfig(this);
        try {
            fcConfig.load();
        } catch (Exception e) {
            e.printStackTrace();
        }

        hookManager = new HookManager(this);
        hookManager.load();
        mobRemovalLogger = new MobRemovalLogger(this);
        mobRemovalLogger.reload();
        registerDeathWatch();
        actionManager = new ActionManager();
        actionManager.addDefaults(this);
        triggerManager = new TriggerManager();
        triggerManager.addDefaults(this);
        profileManager = new ProfileManager(this);

        try {
            profileManager.load();
        } catch (IOException e) {
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        exclusionManager = new ExclusionManager(this);
        messageManager = new MessageManager(this);

        try {
            messageManager.reload();
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        farmController = new FarmController(this);
        farmController.load();
        farmController.register();

        registerCommands();

        new FcMetrics(this, 9692);
        hookManager.getSchedulerHook().runRepeatingTask(() -> RemoveRandomMovementAction.cleanUp(this), 1200, 1200);
    }

    public void reload() throws Exception {
        ProfileManager.purgeConfiguredLimitProfiles(this);
        fcConfig.load();
        mobRemovalLogger.reload();
        hookManager.reload();
        farmController.unRegister();
        profileManager.reload();
        messageManager.reload();
        farmController.reload();
        farmController.register();
    }

    public void onDisable() {
        unregisterDeathWatch();
        if (farmController != null) {
            farmController.unRegister();
            farmController.unload();
        }

        RemoveRandomMovementAction.cleanUp(this);
        if (mobRemovalLogger != null) {
            mobRemovalLogger.shutdown();
        }
    }

    public FcConfig getFcConfig() {
        return fcConfig;
    }

    public ActionManager getActionManager() {
        return actionManager;
    }

    public TriggerManager getTriggerManager() {
        return triggerManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public FarmController getFarmController() {
        return farmController;
    }

    public ExclusionManager getExclusionManager() {
        return exclusionManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public MobRemovalLogger getMobRemovalLogger() {
        return mobRemovalLogger;
    }

    /**
     * Registers the death watch listeners, warning once if the server is too old for removal tracking.
     */
    private void registerDeathWatch() {
        deathWatchListener = new DeathWatchListener(this, mobRemovalLogger);
        deathWatchListener.register();

        if (EntityRemoveListener.isSupported()) {
            entityRemoveListener = new EntityRemoveListener(this, mobRemovalLogger, deathWatchListener);
            entityRemoveListener.register();
        } else if (mobRemovalLogger.isDeathWatchEnabled() && mobRemovalLogger.isLogRemovals()) {
            getLogger().warning("This server does not provide EntityRemoveEvent, so the death watch cannot log mobs that vanish without dying.");
        }
    }

    private void unregisterDeathWatch() {
        if (deathWatchListener != null) {
            deathWatchListener.unregister();
            deathWatchListener = null;
        }

        if (entityRemoveListener != null) {
            entityRemoveListener.unregister();
            entityRemoveListener = null;
        }
    }

    public void registerCommands() {
        FarmControlCommand farmControlCommand = new FarmControlCommand(this);
        getCommand("farmcontrol").setExecutor(farmControlCommand);
        getCommand("farmcontrol").setTabCompleter(farmControlCommand.getTabCompleter());
        getCommand("farmcontrol").setPermission("farmcontrol.command.farmcontrol");
        getCommand("farmcontrol").setPermissionMessage(FarmControlCommand.NO_PERMISSION_MESSAGE);
    }
}
