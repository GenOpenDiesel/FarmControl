package com.froobworld.farmcontrol.debug;

import com.froobworld.farmcontrol.FarmControl;
import org.bukkit.Location;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MobRemovalLogger {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ENTRY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final FarmControl farmControl;
    private final File logDirectory;
    private final ExecutorService writerExecutor;
    private volatile boolean enabled;

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
    }

    public void logRemoval(Entity entity, String action) {
        if (!enabled || !(entity instanceof Mob)) {
            return;
        }

        LocalDateTime timestamp = LocalDateTime.now();
        Location location = entity.getLocation();
        String worldName = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        String customName = entity.getCustomName() == null ? "-" : sanitise(entity.getCustomName());
        String entry = String.format(
                Locale.ROOT,
                "[%s] action=%s type=%s uuid=%s world=%s x=%.2f y=%.2f z=%.2f name=%s%n",
                timestamp.format(ENTRY_DATE_FORMAT),
                sanitise(action),
                entity.getType(),
                entity.getUniqueId(),
                sanitise(worldName),
                location.getX(),
                location.getY(),
                location.getZ(),
                customName
        );
        File logFile = new File(logDirectory, timestamp.toLocalDate().format(FILE_DATE_FORMAT) + ".log");

        writerExecutor.execute(() -> append(logFile, entry));
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
