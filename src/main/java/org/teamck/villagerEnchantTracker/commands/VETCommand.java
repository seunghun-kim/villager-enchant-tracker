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

        // Handle /vet particle <x> <y> <z>
        if (subCommand.equals("particle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }
            if (args.length < 4) {
                player.sendMessage("§cUsage: /vet particle <x> <y> <z>");
                return true;
            }
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                Location loc = new Location(player.getWorld(), x, y, z);
                particleManager.spawnParticles(loc, player, true);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cCoordinates must be numbers.");
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