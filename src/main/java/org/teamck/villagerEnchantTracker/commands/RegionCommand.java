package org.teamck.villagerEnchantTracker.commands;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RegionCommand implements CommandExecutor, TabCompleter {
    private final Database db;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "list", "delete", "scan");

    public RegionCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", "en"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("region.admin")) {
            player.sendMessage(messageManager.getChatMessage("no_permission", player));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(messageManager.getChatMessage("region_usage", player));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "list" -> handleList(player);
            case "delete" -> handleDelete(player, args);
            case "scan" -> handleScan(player, args);
            default -> player.sendMessage(messageManager.getChatMessage("invalid_subcommand", player));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_create_usage", player));
            return;
        }

        String name = args[1];

        // Try to get WorldEdit selection first
        WorldEditPlugin worldEdit = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit != null) {
            try {
                Region selection = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
                if (selection != null) {
                    BlockVector3 min = selection.getMinimumPoint();
                    BlockVector3 max = selection.getMaximumPoint();

                    Location minLoc = new Location(player.getWorld(), min.x(), min.y(), min.z());
                    Location maxLoc = new Location(player.getWorld(), max.x(), max.y(), max.z());

                    int regionId = db.createRegion(name, minLoc, maxLoc);
                    if (regionId != -1) {
                        player.sendMessage(messageManager.format("region_created", player, name));
                    } else {
                        player.sendMessage(messageManager.getChatMessage("region_creation_failed", player));
                    }
                    return;
                }
            } catch (IncompleteRegionException e) {
                player.sendMessage(messageManager.getChatMessage("incomplete_selection", player));
                return;
            }
        }

        // If WorldEdit is not available or no selection, use coordinate-based creation
        if (args.length < 8) {
            player.sendMessage(messageManager.getChatMessage("region_create_coords_usage", player));
            return;
        }

        try {
            int x1 = Integer.parseInt(args[2]);
            int y1 = Integer.parseInt(args[3]);
            int z1 = Integer.parseInt(args[4]);
            int x2 = Integer.parseInt(args[5]);
            int y2 = Integer.parseInt(args[6]);
            int z2 = Integer.parseInt(args[7]);

            Location minLoc = new Location(player.getWorld(),
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
            Location maxLoc = new Location(player.getWorld(),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

            int regionId = db.createRegion(name, minLoc, maxLoc);
            if (regionId != -1) {
                player.sendMessage(messageManager.format("region_created", player, name));
            } else {
                player.sendMessage(messageManager.getChatMessage("region_creation_failed", player));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_coordinates", player));
        }
    }

    private void handleList(Player player) {
        List<VillagerRegion> regions = db.listRegions();
        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_regions", player));
            return;
        }

        player.sendMessage(messageManager.getChatMessage("region_list_header", player));
        for (VillagerRegion region : regions) {
            String message = messageManager.format("region_list_entry", player,
                    region.getId(), region.getName(),
                    region.getMin().getBlockX(), region.getMin().getBlockY(), region.getMin().getBlockZ(),
                    region.getMax().getBlockX(), region.getMax().getBlockY(), region.getMax().getBlockZ());
            player.sendMessage(message);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_delete_usage", player));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            boolean success = db.deleteRegion(id);
            if (success) {
                player.sendMessage(messageManager.getChatMessage("region_deleted", player));
            } else {
                player.sendMessage(messageManager.getChatMessage("region_not_found", player));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("id_must_be_number", player));
        }
    }

    private void handleScan(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_scan_usage", player));
            return;
        }

        String regionName = args[1];
        VillagerRegion region = db.getRegionByName(regionName);
        if (region == null) {
            player.sendMessage(messageManager.getChatMessage("region_not_found", player));
            return;
        }

        int count = scanVillagersInRegion(region);
        player.sendMessage(messageManager.format("villagers_registered", player, count));
    }

    private int scanVillagersInRegion(VillagerRegion region) {
        int count = 0;
        Location min = region.getMin();
        Location max = region.getMax();
        
        // Get all entities in the region's world
        for (Entity entity : min.getWorld().getEntities()) {
            if (entity instanceof Villager) {
                Location loc = entity.getLocation();
                // Check if the entity is within the region bounds
                if (loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                    loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                    loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ()) {
                    // Process the villager's trades
                    if (db.addVillagerTrades((Villager) entity, "Found in region: " + region.getName())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
} 