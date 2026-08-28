package com.froobworld.farmcontrol.command;

import com.froobworld.farmcontrol.FarmControl;
import com.froobworld.farmcontrol.debug.LossRecord;
import com.froobworld.farmcontrol.debug.WatchZone;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Summarises what has been dying inside the death watch zones: which mobs, from what, and by whose hand.
 */
public class DeathsCommand implements CommandExecutor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int DEFAULT_MINUTES = 60;
    private static final int LIST_LIMIT = 20;

    private final FarmControl farmControl;

    public DeathsCommand(FarmControl farmControl) {
        this.farmControl = farmControl;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!farmControl.getMobRemovalLogger().isDeathWatchEnabled()) {
            sender.sendMessage(ChatColor.RED + "The death watch is disabled. Enable death-watch.enabled in plugins/FarmControl/debug.yml and run /" + label + " reload.");
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("zones")) {
            sendZones(sender);
            return true;
        }

        String scope = args.length >= 2 && !args[1].equalsIgnoreCase("all") ? args[1] : null;
        int minutes = DEFAULT_MINUTES;
        boolean list = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("list")) {
                list = true;
                continue;
            }

            try {
                minutes = Math.max(1, Integer.parseInt(args[i]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Not a number of minutes: " + args[i]);
                return true;
            }
        }

        List<LossRecord> records = farmControl.getMobRemovalLogger().getRecords(scope, minutes);
        sender.sendMessage(ChatColor.YELLOW + "FarmControl death watch " + ChatColor.GRAY + "(last " + minutes + " min"
                + (scope == null ? ", all zones" : ", " + scope) + ")");

        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nothing died or vanished in the watched area in that window.");
            return true;
        }

        sender.sendMessage(ChatColor.WHITE + "Total losses: " + ChatColor.AQUA + records.size());

        Map<String, Integer> victims = new TreeMap<>();
        Map<String, Integer> causes = new TreeMap<>();
        Map<String, Integer> killers = new TreeMap<>();
        Map<String, Integer> pairs = new LinkedHashMap<>();
        for (LossRecord record : records) {
            victims.merge(record.getVictimType(), 1, Integer::sum);
            causes.merge(record.getCause(), 1, Integer::sum);
            if (record.getKillerType() != null) {
                killers.merge(record.getKillerType(), 1, Integer::sum);
            }
            pairs.merge(record.getVictimType() + " <- " + record.getCulprit(), 1, Integer::sum);
        }

        sendBreakdown(sender, "Killed mobs", victims);
        sendBreakdown(sender, "Causes", causes);
        if (killers.isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "Killer mobs: " + ChatColor.GRAY + "none - nothing was killed by another entity.");
        } else {
            sendBreakdown(sender, "Killer mobs", killers);
        }
        sendBreakdown(sender, "Victim <- culprit", pairs);

        if (list) {
            sender.sendMessage(ChatColor.WHITE + "Latest entries:");
            int from = Math.max(0, records.size() - LIST_LIMIT);
            for (LossRecord record : records.subList(from, records.size())) {
                sender.sendMessage(ChatColor.GRAY + " " + record.getTimestamp().format(TIME_FORMAT)
                        + ChatColor.WHITE + " " + record.getVictimType()
                        + ChatColor.GRAY + " by " + ChatColor.AQUA + record.getCulprit()
                        + ChatColor.GRAY + " (" + record.getCause() + ") "
                        + String.format(Locale.ROOT, "%s %.0f %.0f %.0f", record.getWorldName(), record.getX(), record.getY(), record.getZ()));
            }
        } else {
            sender.sendMessage(ChatColor.GRAY + "Add 'list' for the latest individual entries, or read plugins/FarmControl/logs/.");
        }

        return true;
    }

    private void sendBreakdown(CommandSender sender, String title, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());

        StringBuilder builder = new StringBuilder();
        builder.append(ChatColor.WHITE).append(title).append(": ");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : entries) {
            if (!first) {
                builder.append(ChatColor.GRAY).append(", ");
            }
            first = false;
            builder.append(ChatColor.AQUA).append(entry.getKey()).append(ChatColor.GRAY).append(" x").append(entry.getValue());
        }

        sender.sendMessage(builder.toString());
    }

    private void sendZones(CommandSender sender) {
        List<WatchZone> zones = farmControl.getMobRemovalLogger().getZones();
        if (zones.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No death watch zones are configured in debug.yml.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Death watch zones:");
        for (WatchZone zone : zones) {
            sender.sendMessage(ChatColor.GRAY + " " + zone);
        }
    }
}
