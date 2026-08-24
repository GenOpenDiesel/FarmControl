package com.froobworld.farmcontrol.controller.action;

import com.froobworld.farmcontrol.debug.MobRemovalLogger;
import org.bukkit.entity.Entity;

public class RemoveAction extends Action {

    private final MobRemovalLogger mobRemovalLogger;

    public RemoveAction(MobRemovalLogger mobRemovalLogger) {
        super("remove", Entity.class, true, false, false);
        this.mobRemovalLogger = mobRemovalLogger;
    }

    @Override
    public void doAction(Entity entity) {
        mobRemovalLogger.logRemoval(entity, getName());
        entity.remove();
    }

    @Override
    public void undoAction(Entity entity) {
    }
}
