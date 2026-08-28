package com.froobworld.farmcontrol.debug;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * A single "an entity stopped existing" event, with as much detail about the culprit as we can gather.
 */
public class LossRecord {

    public enum Kind {
        DEATH,          // the entity died
        REMOVAL,        // the entity vanished without dying (despawn, plugin, transformation, ...)
        FARM_CONTROL    // FarmControl itself culled it
    }

    private final LocalDateTime timestamp;
    private final Kind kind;
    private final String zone;
    private final String victimType;
    private final String victimUuid;
    private final String victimName;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final String cause;
    private final double damage;
    private final String killerType;
    private final String killerName;
    private final String killerUuid;
    private final Double killerDistance;
    private final String weapon;
    private final String extra;

    private LossRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.kind = builder.kind;
        this.zone = builder.zone;
        this.victimType = builder.victimType;
        this.victimUuid = builder.victimUuid;
        this.victimName = builder.victimName;
        this.worldName = builder.worldName;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.cause = builder.cause;
        this.damage = builder.damage;
        this.killerType = builder.killerType;
        this.killerName = builder.killerName;
        this.killerUuid = builder.killerUuid;
        this.killerDistance = builder.killerDistance;
        this.weapon = builder.weapon;
        this.extra = builder.extra;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toLogLine(java.time.format.DateTimeFormatter formatter) {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(timestamp.format(formatter)).append("] ");
        builder.append("kind=").append(kind);
        builder.append(" zone=").append(zone == null ? "-" : zone);
        builder.append(" victim=").append(victimType);
        builder.append(" uuid=").append(victimUuid);
        builder.append(" name=").append(victimName == null ? "-" : victimName);
        builder.append(String.format(Locale.ROOT, " world=%s x=%.2f y=%.2f z=%.2f", worldName, x, y, z));
        builder.append(" cause=").append(cause == null ? "UNKNOWN" : cause);
        if (damage > 0) {
            builder.append(String.format(Locale.ROOT, " damage=%.2f", damage));
        }

        if (killerType != null) {
            builder.append(" killer=").append(killerType);
            if (killerName != null) {
                builder.append(" killerName=").append(killerName);
            }

            if (killerUuid != null) {
                builder.append(" killerUuid=").append(killerUuid);
            }

            if (killerDistance != null) {
                builder.append(String.format(Locale.ROOT, " killerDistance=%.2f", killerDistance));
            }
        }

        if (weapon != null) {
            builder.append(" weapon=").append(weapon);
        }

        if (extra != null) {
            builder.append(" extra=").append(extra);
        }

        return builder.toString();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Kind getKind() {
        return kind;
    }

    public String getZone() {
        return zone;
    }

    public String getVictimType() {
        return victimType;
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

    public String getCause() {
        return cause == null ? "UNKNOWN" : cause;
    }

    public String getKillerType() {
        return killerType;
    }

    public String getKillerName() {
        return killerName;
    }

    /**
     * A short, human readable answer to "what got it?".
     */
    public String getCulprit() {
        if (killerType != null) {
            return killerType;
        }

        if (kind == Kind.FARM_CONTROL) {
            return "FarmControl";
        }

        return getCause();
    }

    public static class Builder {
        private LocalDateTime timestamp = LocalDateTime.now();
        private Kind kind = Kind.DEATH;
        private String zone;
        private String victimType = "UNKNOWN";
        private String victimUuid = "-";
        private String victimName;
        private String worldName = "unknown";
        private double x;
        private double y;
        private double z;
        private String cause;
        private double damage;
        private String killerType;
        private String killerName;
        private String killerUuid;
        private Double killerDistance;
        private String weapon;
        private String extra;

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder zone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder victimType(String victimType) {
            this.victimType = victimType;
            return this;
        }

        public Builder victimUuid(String victimUuid) {
            this.victimUuid = victimUuid;
            return this;
        }

        public Builder victimName(String victimName) {
            this.victimName = victimName;
            return this;
        }

        public Builder world(String worldName) {
            this.worldName = worldName;
            return this;
        }

        public Builder position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder cause(String cause) {
            this.cause = cause;
            return this;
        }

        public Builder damage(double damage) {
            this.damage = damage;
            return this;
        }

        public Builder killerType(String killerType) {
            this.killerType = killerType;
            return this;
        }

        public Builder killerName(String killerName) {
            this.killerName = killerName;
            return this;
        }

        public Builder killerUuid(String killerUuid) {
            this.killerUuid = killerUuid;
            return this;
        }

        public Builder killerDistance(Double killerDistance) {
            this.killerDistance = killerDistance;
            return this;
        }

        public Builder weapon(String weapon) {
            this.weapon = weapon;
            return this;
        }

        public Builder extra(String extra) {
            this.extra = extra;
            return this;
        }

        public LossRecord build() {
            return new LossRecord(this);
        }
    }
}
