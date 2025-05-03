package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.manager.ParticleManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

public class VETCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final VETTradeCommand tradeCommand;
    private final VETRegionCommand regionCommand;
    private final VETEVTCommand evtCommand;
    private final ParticleManager particleManager;

    public VETCommand(JavaPlugin plugin, VETTradeCommand tradeCommand, VETRegionCommand regionCommand, VETEVTCommand evtCommand) {
        this.plugin = plugin;
        this.tradeCommand = tradeCommand;
        this.regionCommand = regionCommand;
        this.evtCommand = evtCommand;
        this.particleManager = new ParticleManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6=== VillagerEnchantTracker Commands ===");
            sender.sendMessage("§e/vet trade [create|search|list|delete|edit-description]");
            sender.sendMessage("§e/vet region [create|list|delete|edit]");
            sender.sendMessage("§e/vet evt [nearby|region]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        // Handle /vet particle <x> <y> <z> or /vet particle <villager_uuid>
        if (subCommand.equals("particle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("§cUsage: /vet particle <x> <y> <z> or /vet particle <villager_uuid>");
                return true;
            }
            try {
                if (args.length == 4) {
                    double x = Double.parseDouble(args[1]);
                    double y = Double.parseDouble(args[2]);
                    double z = Double.parseDouble(args[3]);
                    Location loc = new Location(player.getWorld(), x, y, z);
                    particleManager.spawnParticles(loc, player, true);
                } else if (args.length == 2) {
                    UUID villagerUuid = UUID.fromString(args[1]);
                    Entity entity = Bukkit.getEntity(villagerUuid);
                    if (entity instanceof Villager villager) {
                        Location loc = villager.getLocation();
                        particleManager.spawnParticles(loc, player, true);
                    } else {
                        player.sendMessage("§cVillager not found with UUID: " + args[1]);
                    }
                } else {
                    player.sendMessage("§cUsage: /vet particle <x> <y> <z> or /vet particle <villager_uuid>");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cCoordinates must be numbers.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid UUID format.");
            }
            return true;
        }

        return switch (subCommand) {
            case "trade" -> tradeCommand.executeCommand(sender, subArgs);
            case "region" -> regionCommand.executeCommand(sender, subArgs);
            case "evt" -> evtCommand.executeCommand(sender, subArgs);
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use /vet for help.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subCommands = Arrays.asList("trade", "region", "evt");
            for (String sub : subCommands) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            return switch (subCommand) {
                case "trade" -> tradeCommand.getTabCompletions(sender, subArgs);
                case "region" -> regionCommand.getTabCompletions(sender, subArgs);
                case "evt" -> evtCommand.getTabCompletions(sender, subArgs);
                default -> new ArrayList<>();
            };
        }

        return completions;
    }
} 