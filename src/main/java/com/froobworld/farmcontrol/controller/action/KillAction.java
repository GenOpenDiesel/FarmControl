package com.froobworld.farmcontrol.controller.action;

import com.froobworld.farmcontrol.debug.MobRemovalLogger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

public class KillAction extends Action {

    private final MobRemovalLogger mobRemovalLogger;

    public KillAction(MobRemovalLogger mobRemovalLogger) {
        super("kill", Mob.class, true, false, false);
        this.mobRemovalLogger = mobRemovalLogger;
    }

    @Override
    public void doAction(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }

        mobRemovalLogger.logRemoval(mob, getName());
        mob.setHealth(0);
    }

    @Override
    public void undoAction(Entity entity) {
    }
}
