package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.teamck.villagerEnchantTracker.core.VillagerEnchantTracker;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager.EnchantmentInfo;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.ui.EnchantmentTUI;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VETEVTCommand {
    private final VillagerEnchantTracker plugin;
    private final Database database;
    private final MessageManager messageManager;
    private final Logger logger;
    private final Map<UUID, EnchantmentTUI> activeTUIs = new HashMap<>();

    public VETEVTCommand(VillagerEnchantTracker plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.messageManager = MessageManager.getInstance();
        this.logger = plugin.getLogger();
    }

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        if (!player.hasPermission("villagerenchanttracker.use")) {
            player.sendMessage(messageManager.getMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        logger.info(String.format("Player %s executed EVT integration command: %s",
                player.getName(), String.join(" ", args)));

        if (args[0].equalsIgnoreCase("tui")) {
            return handleTUICommand(player, args);
        }

        return switch (args[0].toLowerCase()) {
            case "nearby" -> handleNearbyCommand(player, args);
            case "region" -> handleRegionCommand(player, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("villagerenchanttracker.use")) {
            return new ArrayList<>();
        }

        return switch (args.length) {
            case 1 -> getCompletions(args[0], Arrays.asList("nearby", "region", "tui"));
            case 2 -> getSecondArgumentCompletions(args[0], args[1]);
            case 3 -> args[0].toLowerCase().equals("tui") ? getCompletions(args[2], Arrays.asList("toggle", "next", "prev", "close", "feedback")) : new ArrayList<>();
            default -> new ArrayList<>();
        };
    }

    private List<String> getSecondArgumentCompletions(String command, String input) {
        return switch (command.toLowerCase()) {
            case "region" -> {
                List<String> completions = new ArrayList<>();
                if ("*".startsWith(input)) {
                    completions.add("*");
                }
                database.listRegions().stream()
                        .map(VillagerRegion::getName)
                        .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                        .forEach(completions::add);
                yield completions;
            }
            case "nearby" -> getCompletions(input, Arrays.asList("5", "10", "15", "20", "25", "30"));
            default -> new ArrayList<>();
        };
    }

    private List<String> getCompletions(String input, List<String> possibilities) {
        return possibilities.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private boolean handleNearbyCommand(Player player, String[] args) {
        logDebug("Player %s executing nearby command", player.getName());
            
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("nearby_usage", player));
            return true;
        }

        int radius = parseRadius(player, args[1]);
        if (radius == -1) return true;

        List<Villager> librarians = findNearbyLibrarians(player, radius);
        if (librarians.isEmpty()) {
            logDebug("No librarians found for player %s within %d blocks", player.getName(), radius);
            player.sendMessage(String.format(messageManager.getMessage("no_librarians_nearby", player), radius));
            return true;
        }

        EnchantmentData enchantData = collectEnchantmentData(librarians);
        showTUI(player, enchantData);
        
        return true;
    }

    private boolean handleRegionCommand(Player player, String[] args) {
        logDebug("Player %s executing region command", player.getName());
            
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("region_usage", player));
            return true;
        }

        String regionName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<VillagerRegion> regions = getSelectedRegions(player, regionName);
        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_regions", player));
            logWarning("No regions found in database");
            return true;
        }

        EnchantmentData enchantData = collectRegionEnchantmentData(regions, player);
        if (enchantData.totalLibrarians == 0) {
            logDebug("No librarians found in any selected region for player %s", player.getName());
            player.sendMessage(messageManager.getMessage("no_librarians_in_region", player));
            return true;
        }

        showTUI(player, enchantData);
        return true;
    }

    private boolean handleTUICommand(Player player, String[] args) {
        logDebug("Player %s executing TUI command", player.getName());
            
        if (args.length < 2) return false;
        
        EnchantmentTUI tui = activeTUIs.get(player.getUniqueId());
        if (tui == null) {
            logWarning("No active TUI found for player %s", player.getName());
            return false;
        }
        
        boolean result = tui.handleCommand(args[1], Arrays.copyOfRange(args, 2, args.length));
        if (result && args[1].equalsIgnoreCase("close")) {
            activeTUIs.remove(player.getUniqueId());
        }
        
        return result;
    }

    private void sendUsage(Player player) {
        player.sendMessage(messageManager.getMessage("evtintegration_header", player));
        player.sendMessage(messageManager.getMessage("evtintegration_nearby_usage", player));
        player.sendMessage(messageManager.getMessage("evtintegration_region_usage", player));
    }

    private int parseRadius(Player player, String radiusStr) {
        try {
            int radius = Integer.parseInt(radiusStr);
            logDebug("Searching with radius: %d", radius);
            return radius;
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getMessage("invalid_radius", player));
            logWarning("Invalid radius provided: %s", radiusStr);
            return -1;
        }
    }

    private List<Villager> findNearbyLibrarians(Player player, int radius) {
        return player.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof Villager villager && 
                        villager.getProfession() == Villager.Profession.LIBRARIAN)
                .map(entity -> (Villager) entity)
                .toList();
    }

    private List<VillagerRegion> getSelectedRegions(Player player, String regionName) {
        if (regionName.equalsIgnoreCase("all") || regionName.equals("*")) {
            logDebug("Searching in all regions");
            return database.listRegions();
        } else {
            VillagerRegion region = database.getRegionByName(regionName);
            if (region == null) {
                player.sendMessage(messageManager.getMessage("region_not_found", player));
                logWarning("Region not found: %s", regionName);
                return new ArrayList<>();
            }
            logDebug("Searching in region: %s", region.getName());
            return Collections.singletonList(region);
        }
    }

    private EnchantmentData collectEnchantmentData(List<Villager> librarians) {
        Set<EnchantmentInfo> existingTrades = new HashSet<>();
        for (Villager librarian : librarians) {
            Set<EnchantmentInfo> trades = EnchantmentManager.getVillagerEnchantments(librarian);
            existingTrades.addAll(trades);
            logDebug("Librarian at %s has enchantments: %s",
                    formatLocation(librarian.getLocation()), 
                    trades.stream()
                         .map(EnchantmentManager::formatEnchantmentInfo)
                         .collect(Collectors.joining(", ")));
        }
        
        Set<EnchantmentInfo> allEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        Set<EnchantmentInfo> newEnchants = EnchantmentManager.filterNewEnchants(allEnchants, existingTrades);
        
        logDebug("Total enchants: %d, Existing: %d, New: %d", 
            allEnchants.size(), existingTrades.size(), newEnchants.size());
        
        return new EnchantmentData(newEnchants, existingTrades, librarians.size());
    }

    private EnchantmentData collectRegionEnchantmentData(List<VillagerRegion> regions, Player player) {
        Set<EnchantmentInfo> existingTrades = new HashSet<>();
        int totalLibrarians = 0;

        for (VillagerRegion region : regions) {
            List<Villager> librarians = region.getLibrariansInRegion();
            totalLibrarians += librarians.size();
            logDebug("Region '%s': Found %d librarians", region.getName(), librarians.size());

            for (Villager librarian : librarians) {
                Set<EnchantmentInfo> trades = EnchantmentManager.getVillagerEnchantments(librarian);
                existingTrades.addAll(trades);
                logDebug("Librarian at %s in region '%s' has enchantments: %s",
                        formatLocation(librarian.getLocation()), 
                        region.getName(), 
                        trades.stream()
                             .map(EnchantmentManager::formatEnchantmentInfo)
                             .collect(Collectors.joining(", ")));
            }
        }

        Set<EnchantmentInfo> allEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        Set<EnchantmentInfo> newEnchants = EnchantmentManager.filterNewEnchants(allEnchants, existingTrades);
        
        logDebug("Total enchants: %d, Existing: %d, New: %d", 
            allEnchants.size(), existingTrades.size(), newEnchants.size());
        
        return new EnchantmentData(newEnchants, existingTrades, totalLibrarians);
    }

    private void showTUI(Player player, EnchantmentData enchantData) {
        EnchantmentTUI tui = new EnchantmentTUI(plugin, player, enchantData.newEnchants, enchantData.existingTrades);
        activeTUIs.put(player.getUniqueId(), tui);
        tui.render();
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private void logCommand(Player player, String label, String[] args) {
        logger.info(String.format("Player %s executed EVT integration command: /%s %s",
                player.getName(), label, String.join(" ", args)));
    }

    private void logDebug(String format, Object... args) {
        logger.info(String.format(format, args));
    }

    private void logWarning(String format, Object... args) {
        logger.warning(String.format(format, args));
    }

    private record EnchantmentData(Set<EnchantmentInfo> newEnchants, Set<EnchantmentInfo> existingTrades, int totalLibrarians) {}
} 
