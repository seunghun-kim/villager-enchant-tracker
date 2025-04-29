package org.teamck.villagerEnchantTracker;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.IncompleteRegionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RegionCommand implements CommandExecutor, TabCompleter {
    private final Database db;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "list", "delete");

    public RegionCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("villagerenchanttracker.admin")) {
            sender.sendMessage(messageManager.getChatMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messageManager.getChatMessage("region_usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "list" -> handleList(player);
            case "delete" -> handleDelete(player, args);
            default -> sender.sendMessage(messageManager.getChatMessage("invalid_subcommand"));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_create_usage"));
            return;
        }

        // Try WorldEdit if available
        WorldEditPlugin worldEdit = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit != null) {
            try {
                Region selection = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
                if (selection != null) {
                    String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    BlockVector3 min = selection.getMinimumPoint();
                    BlockVector3 max = selection.getMaximumPoint();

                    org.bukkit.Location minLoc = new org.bukkit.Location(player.getWorld(), min.x(), min.y(), min.z());
                    org.bukkit.Location maxLoc = new org.bukkit.Location(player.getWorld(), max.x(), max.y(), max.z());

                    int regionId = db.createRegion(name, minLoc, maxLoc);
                    if (regionId != -1) {
                        player.sendMessage(messageManager.format("region_created", name));
                    } else {
                        player.sendMessage(messageManager.getChatMessage("region_creation_failed"));
                    }
                    return;
                }
            } catch (IncompleteRegionException e) {
                player.sendMessage(messageManager.getChatMessage("incomplete_selection"));
                return;
            }
        }

        // If WorldEdit is not available or no selection, use coordinate-based creation
        if (args.length < 8) {
            player.sendMessage(messageManager.getChatMessage("region_create_coords_usage"));
            return;
        }

        try {
            String name = args[1];
            int x1 = Integer.parseInt(args[2]);
            int y1 = Integer.parseInt(args[3]);
            int z1 = Integer.parseInt(args[4]);
            int x2 = Integer.parseInt(args[5]);
            int y2 = Integer.parseInt(args[6]);
            int z2 = Integer.parseInt(args[7]);

            org.bukkit.Location minLoc = new org.bukkit.Location(player.getWorld(), 
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
            org.bukkit.Location maxLoc = new org.bukkit.Location(player.getWorld(), 
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

            int regionId = db.createRegion(name, minLoc, maxLoc);
            if (regionId != -1) {
                player.sendMessage(messageManager.format("region_created", name));
            } else {
                player.sendMessage(messageManager.getChatMessage("region_creation_failed"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_coordinates"));
        }
    }

    private void handleList(Player player) {
        List<VillagerRegion> regions = db.listRegions();
        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_regions"));
            return;
        }

        player.sendMessage(messageManager.getChatMessage("region_list_header"));
        for (VillagerRegion region : regions) {
            String message = messageManager.format("region_list_entry",
                    region.getId(), region.getName(),
                    region.getMin().getBlockX(), region.getMin().getBlockY(), region.getMin().getBlockZ(),
                    region.getMax().getBlockX(), region.getMax().getBlockY(), region.getMax().getBlockZ());
            player.sendMessage(message);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_delete_usage"));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            db.deleteRegion(id);
            player.sendMessage(messageManager.getChatMessage("region_deleted"));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("id_must_be_number"));
        }
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