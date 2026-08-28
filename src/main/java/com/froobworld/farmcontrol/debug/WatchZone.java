package com.froobworld.farmcontrol.debug;

import org.bukkit.Location;

/**
 * A cylindrical area that the death watch keeps an eye on.
 */
public class WatchZone {

    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;
    private final double verticalRadius;

    public WatchZone(String name, String worldName, double x, double y, double z, double radius, double verticalRadius) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.verticalRadius = verticalRadius;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }

        if (Math.abs(location.getY() - y) > verticalRadius) {
            return false;
        }

        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz <= radius * radius;
    }

    public double distanceTo(Location location) {
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getRadius() {
        return radius;
    }

    public double getVerticalRadius() {
        return verticalRadius;
    }

    @Override
    public String toString() {
        return name + " (" + worldName + " " + (int) x + "," + (int) y + "," + (int) z + " r=" + (int) radius + " ry=" + (int) verticalRadius + ")";
    }
}
