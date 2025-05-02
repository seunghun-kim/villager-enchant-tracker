package org.teamck.villagerEnchantTracker.commands;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VETRegionCommand {
    private final Database db;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "list", "delete", "edit");

    public VETRegionCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
    }

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(messageManager.getMessage("region_usage", player));
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean isWrite = sub.equals("create") || sub.equals("delete") || sub.equals("edit");
        if (isWrite) {
            if (!player.hasPermission("villagerenchanttracker.write")) {
                player.sendMessage(messageManager.getMessage("no_permission", player));
                return true;
            }
        } else {
            if (!player.hasPermission("villagerenchanttracker.use")) {
                player.sendMessage(messageManager.getMessage("no_permission", player));
                return true;
            }
        }

        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "list" -> handleList(player);
            case "delete" -> handleDelete(player, args);
            case "edit" -> handleEdit(player, args);
            default -> player.sendMessage(messageManager.getMessage("invalid_subcommand", player));
        }

        return true;
    }

    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("villagerenchanttracker.use")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length == 2) yield List.of("coords", "<name>");
                else if (args.length == 3 && args[1].toLowerCase().equals("coords")) yield List.of("<name>");
                else if (args.length >= 4 && args.length <= 9 && args[1].toLowerCase().equals("coords")) yield List.of("<x1> <y1> <z1> <x2> <y2> <z2>");
                else yield new ArrayList<>();
            }
            case "delete", "edit" -> args.length == 2 ? db.listRegions().stream()
                    .map(r -> String.valueOf(r.getId()))
                    .collect(Collectors.toList()) :
                    args.length == 3 && args[0].equalsIgnoreCase("edit") ? List.of("<newName>") :
                    new ArrayList<>();
            default -> new ArrayList<>();
        };
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("region_create_usage", player));
            return;
        }

        // Check if using WorldEdit selection or coordinates
        String subOption = args[1].toLowerCase();
        if (subOption.equals("coords")) {
            if (args.length < 9) {
                player.sendMessage(messageManager.getMessage("region_create_coords_usage", player));
                return;
            }
            // 이름은 공백을 포함할 수 있으므로, args[2]부터 args[length-6]까지를 이름으로 처리
            int nameEndIndex = args.length - 6;
            String name = String.join(" ", Arrays.copyOfRange(args, 2, nameEndIndex));
            try {
                int x1 = Integer.parseInt(args[nameEndIndex]);
                int y1 = Integer.parseInt(args[nameEndIndex + 1]);
                int z1 = Integer.parseInt(args[nameEndIndex + 2]);
                int x2 = Integer.parseInt(args[nameEndIndex + 3]);
                int y2 = Integer.parseInt(args[nameEndIndex + 4]);
                int z2 = Integer.parseInt(args[nameEndIndex + 5]);

                Location minLoc = new Location(player.getWorld(),
                        Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                Location maxLoc = new Location(player.getWorld(),
                        Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

                int regionId = db.createRegion(name, minLoc, maxLoc);
                if (regionId != -1) {
                    player.sendMessage(String.format(messageManager.getMessage("region_created", player), name));
                } else {
                    player.sendMessage(messageManager.getMessage("region_creation_failed", player));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(messageManager.getMessage("invalid_coordinates", player));
            }
            return;
        }

        // Get the name of the region. Region name can contain spaces.
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

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
                        player.sendMessage(String.format(messageManager.getMessage("region_created", player), name));
                    } else {
                        player.sendMessage(messageManager.getMessage("region_creation_failed", player));
                    }
                    return;
                }
            } catch (IncompleteRegionException e) {
                player.sendMessage(messageManager.getMessage("incomplete_selection", player));
                return;
            }
        }

        // If WorldEdit is not available or no selection, inform the player
        player.sendMessage(messageManager.getMessage("region_create_coords_usage", player));
    }

    private void handleList(Player player) {
        List<VillagerRegion> regions = db.listRegions();
        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_regions", player));
            return;
        }

        player.sendMessage(messageManager.getMessage("region_list_header", player));
        for (VillagerRegion region : regions) {
            String message = String.format(messageManager.getMessage("region_list_entry", player),
                    region.getId(), region.getName(),
                    region.getMin().getBlockX(), region.getMin().getBlockY(), region.getMin().getBlockZ(),
                    region.getMax().getBlockX(), region.getMax().getBlockY(), region.getMax().getBlockZ());
            player.sendMessage(message);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("region_delete_usage", player));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            boolean success = db.deleteRegion(id);
            if (success) {
                player.sendMessage(messageManager.getMessage("region_deleted", player));
            } else {
                player.sendMessage(messageManager.getMessage("region_not_found", player));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getMessage("id_must_be_number", player));
        }
    }

    private void handleEdit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("region_edit_usage", player));
            return;
        }
        
        int id = Integer.parseInt(args[1]);
        VillagerRegion region = db.getRegion(id);
        if (region == null) {
            player.sendMessage(messageManager.getMessage("region_not_found", player));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(messageManager.getMessage("region_edit_usage", player));
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        db.updateRegionName(id, newName);
        player.sendMessage(String.format(messageManager.getMessage("region_name_updated", player), newName));
    }
} 