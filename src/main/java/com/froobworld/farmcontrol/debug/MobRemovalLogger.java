package com.froobworld.farmcontrol.debug;

import com.froobworld.farmcontrol.FarmControl;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Writes a forensic log of everything that dies or disappears inside the configured watch zones, and keeps
 * the most recent records in memory so that /fc deaths can summarise them.
 */
public class MobRemovalLogger {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long CULL_MEMORY_MILLIS = 5_000L;
    private static final int CULL_MEMORY_MAX_ENTRIES = 4096;
    private static final DateTimeFormatter ENTRY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final FarmControl farmControl;
    private final File logDirectory;
    private final ExecutorService writerExecutor;
    private final Deque<LossRecord> recentRecords = new ArrayDeque<>();
    private final Map<UUID, Long> recentCulls = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile boolean deathWatchEnabled;
    private volatile boolean logToFile;
    private volatile boolean logDeaths;
    private volatile boolean logRemovals;
    private volatile boolean logOutsideZones;
    private volatile int memoryRecords;
    private volatile List<WatchZone> zones = Collections.emptyList();
    private volatile Set<String> entityTypeFilter = Collections.emptySet();
    private volatile Set<String> ignoredRemovalCauses = Collections.emptySet();

    public MobRemovalLogger(FarmControl farmControl) {
        this.farmControl = farmControl;
        this.logDirectory = new File(farmControl.getDataFolder(), "logs");
        this.writerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FarmControl-MobRemovalLogger");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void reload() {
        if (!farmControl.getDataFolder().exists() && !farmControl.getDataFolder().mkdirs()) {
            farmControl.getLogger().warning("Could not create the plugin data directory for debug.yml.");
        }

        File debugFile = new File(farmControl.getDataFolder(), "debug.yml");
        if (!debugFile.exists()) {
            farmControl.saveResource("debug.yml", false);
        }

        YamlConfiguration debugConfig = YamlConfiguration.loadConfiguration(debugFile);
        enabled = debugConfig.getBoolean("mob-removal-logging", false);

        deathWatchEnabled = debugConfig.getBoolean("death-watch.enabled", false);
        logToFile = debugConfig.getBoolean("death-watch.log-to-file", true);
        logDeaths = debugConfig.getBoolean("death-watch.log-deaths", true);
        logRemovals = debugConfig.getBoolean("death-watch.log-removals", true);
        logOutsideZones = debugConfig.getBoolean("death-watch.log-outside-zones", false);
        memoryRecords = Math.max(0, debugConfig.getInt("death-watch.memory-records", 5000));

        Set<String> types = new HashSet<>();
        for (String type : debugConfig.getStringList("death-watch.entity-types")) {
            types.add(type.toUpperCase(Locale.ROOT));
        }
        entityTypeFilter = types;

        Set<String> ignored = new HashSet<>();
        for (String cause : debugConfig.getStringList("death-watch.ignored-removal-causes")) {
            ignored.add(cause.toUpperCase(Locale.ROOT));
        }
        ignoredRemovalCauses = ignored;

        List<WatchZone> loadedZones = new ArrayList<>();
        List<?> zoneList = debugConfig.getList("death-watch.zones", Collections.emptyList());
        int index = 0;
        for (Object rawZone : zoneList) {
            index++;
            ConfigurationSection section = asSection(rawZone);
            if (section == null) {
                farmControl.getLogger().warning("Skipping malformed death-watch zone #" + index + " in debug.yml.");
                continue;
            }

            String world = section.getString("world");
            if (world == null) {
                farmControl.getLogger().warning("Skipping death-watch zone #" + index + " in debug.yml: no world set.");
                continue;
            }

            double radius = section.getDouble("radius", 48);
            loadedZones.add(new WatchZone(
                    section.getString("name", "zone-" + index),
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    radius,
                    section.getDouble("vertical-radius", radius)
            ));
        }
        zones = loadedZones;

        synchronized (recentRecords) {
            trimRecords();
        }
    }

    private ConfigurationSection asSection(Object rawZone) {
        if (rawZone instanceof ConfigurationSection section) {
            return section;
        }

        if (rawZone instanceof Map<?, ?> map) {
            YamlConfiguration configuration = new YamlConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                configuration.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            return configuration;
        }

        return null;
    }

    public boolean isDeathWatchEnabled() {
        return deathWatchEnabled;
    }

    public boolean isLogDeaths() {
        return logDeaths;
    }

    public boolean isLogRemovals() {
        return logRemovals;
    }

    public List<WatchZone> getZones() {
        return zones;
    }

    public boolean isIgnoredRemovalCause(String cause) {
        return cause != null && ignoredRemovalCauses.contains(cause.toUpperCase(Locale.ROOT));
    }

    /**
     * @return the zone containing the location, or null if none does.
     */
    public WatchZone zoneOf(Location location) {
        for (WatchZone zone : zones) {
            if (zone.contains(location)) {
                return zone;
            }
        }

        return null;
    }

    public boolean isWatched(Location location) {
        return logOutsideZones || zoneOf(location) != null;
    }

    public boolean isWatchedType(Entity entity) {
        return entityTypeFilter.isEmpty() || entityTypeFilter.contains(entity.getType().name());
    }

    /**
     * @return true if FarmControl culled this entity within the last few seconds, in which case the death and
     *         removal listeners should not log it a second time.
     */
    public boolean wasJustCulled(UUID uuid) {
        Long time = recentCulls.get(uuid);
        return time != null && System.currentTimeMillis() - time <= CULL_MEMORY_MILLIS;
    }

    private void rememberCull(UUID uuid) {
        long now = System.currentTimeMillis();
        recentCulls.put(uuid, now);
        if (recentCulls.size() > CULL_MEMORY_MAX_ENTRIES) {
            recentCulls.values().removeIf(time -> now - time > CULL_MEMORY_MILLIS);
        }
    }

    /**
     * Logs a mob culled by FarmControl itself.
     */
    public void logRemoval(Entity entity, String action) {
        if (!(entity instanceof Mob)) {
            return;
        }

        rememberCull(entity.getUniqueId());
        if (!enabled && !deathWatchEnabled) {
            return;
        }

        Location location = entity.getLocation();
        WatchZone zone = zoneOf(location);
        if (!enabled && zone == null) {
            return; // Death watch only cares about its zones.
        }

        LossRecord record = LossRecord.builder()
                .kind(LossRecord.Kind.FARM_CONTROL)
                .zone(zone == null ? null : zone.getName())
                .victimType(entity.getType().name())
                .victimUuid(entity.getUniqueId().toString())
                .victimName(entity.getCustomName())
                .world(location.getWorld() == null ? "unknown" : location.getWorld().getName())
                .position(location.getX(), location.getY(), location.getZ())
                .cause("FARMCONTROL_" + action.toUpperCase(Locale.ROOT))
                .build();

        record(record);
    }

    /**
     * Records a loss: keeps it in memory for /fc deaths and appends it to today's log file.
     */
    public void record(LossRecord record) {
        if (memoryRecords > 0) {
            synchronized (recentRecords) {
                recentRecords.addLast(record);
                trimRecords();
            }
        }

        if (!logToFile) {
            return;
        }

        File logFile = new File(logDirectory, record.getTimestamp().toLocalDate().format(FILE_DATE_FORMAT) + ".log");
        String entry = sanitise(record.toLogLine(ENTRY_DATE_FORMAT)) + System.lineSeparator();
        writerExecutor.execute(() -> append(logFile, entry));
    }

    private void trimRecords() {
        while (recentRecords.size() > memoryRecords) {
            recentRecords.removeFirst();
        }
    }

    /**
     * @return records from the last given number of minutes, optionally restricted to a zone or world name.
     */
    public List<LossRecord> getRecords(String zoneOrWorld, int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        List<LossRecord> snapshot;
        synchronized (recentRecords) {
            snapshot = new ArrayList<>(recentRecords);
        }

        List<LossRecord> result = new ArrayList<>();
        for (LossRecord record : snapshot) {
            if (record.getTimestamp().isBefore(cutoff)) {
                continue;
            }

            if (zoneOrWorld != null
                    && !zoneOrWorld.equalsIgnoreCase(record.getZone())
                    && !zoneOrWorld.equalsIgnoreCase(record.getWorldName())) {
                continue;
            }

            result.add(record);
        }

        return result;
    }

    private void append(File logFile, String entry) {
        try {
            Files.createDirectories(logDirectory.toPath());
            Files.writeString(
                    logFile.toPath(),
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            farmControl.getLogger().warning("Could not write mob removal log to " + logFile.getName() + ": " + e.getMessage());
        }
    }

    private String sanitise(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    public void shutdown() {
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                farmControl.getLogger().warning("Timed out while flushing mob removal logs.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
