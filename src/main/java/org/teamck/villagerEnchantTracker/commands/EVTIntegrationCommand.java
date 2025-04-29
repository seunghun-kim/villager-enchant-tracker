package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.teamck.villagerEnchantTracker.core.VillagerEnchantTracker;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.*;

public class EVTIntegrationCommand implements CommandExecutor, TabCompleter {
    private final VillagerEnchantTracker plugin;
    private final Database database;
    private final MessageManager messageManager;
    private List<String> pendingCommands = new ArrayList<>();

    public EVTIntegrationCommand(VillagerEnchantTracker plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.messageManager = MessageManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", "en"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("villagerenchanttracker.use")) {
            player.sendMessage(messageManager.getChatMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        plugin.getLogger().info(String.format("Player %s executed EVT integration command: /%s %s",
                player.getName(), label, String.join(" ", args)));

        switch (args[0].toLowerCase()) {
            case "nearby":
                return handleNearbyCommand(player, args);
            case "region":
                return handleRegionCommand(player, args);
            default:
                sendUsage(player);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            // First argument: subcommands
            String input = args[0].toLowerCase();
            List<String> subcommands = Arrays.asList("nearby", "region");
            
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(input)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            // Second argument: region name for region subcommand or radius for nearby
            String input = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("region")) {
                // Add "*" for all regions
                if ("*".startsWith(input)) {
                    completions.add("*");
                }
                
                // Add region names
                database.listRegions().stream()
                        .map(VillagerRegion::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("nearby")) {
                // Suggest some common radius values
                List<String> radii = Arrays.asList("5", "10", "15", "20", "25", "30");
                for (String radius : radii) {
                    if (radius.startsWith(input)) {
                        completions.add(radius);
                    }
                }
            }
        } else if (args.length == 3) {
            // Third argument: discount amount for both subcommands
            String input = args[2].toLowerCase();
            List<String> discounts = Arrays.asList("5", "10", "15", "20", "25", "30");
            for (String discount : discounts) {
                if (discount.startsWith(input)) {
                    completions.add(discount);
                }
            }
        }
        
        return completions;
    }

    private boolean handleNearbyCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("nearby_usage", player));
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_radius", player));
            return true;
        }

        int discountAmount;
        try {
            discountAmount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_discount", player));
            return true;
        }

        // Find nearby librarians
        List<Villager> librarians = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager && villager.getProfession() == Villager.Profession.LIBRARIAN) {
                librarians.add(villager);
            }
        }

        if (librarians.isEmpty()) {
            plugin.getLogger().info(String.format("No librarians found for player %s within %d blocks",
                    player.getName(), radius));
            player.sendMessage(messageManager.format("no_librarians_nearby", player, radius));
            return true;
        }

        plugin.getLogger().info(String.format("Found %d librarians for player %s",
                librarians.size(), player.getName()));

        // Get all existing trades from nearby librarians
        Set<String> existingTrades = new HashSet<>();
        for (Villager librarian : librarians) {
            Set<String> trades = EnchantmentManager.getVillagerEnchantments(librarian);
            existingTrades.addAll(trades);
            plugin.getLogger().info(String.format("Librarian at %s has enchantments: %s",
                    formatLocation(librarian.getLocation()), String.join(", ", trades)));
        }

        // Show new enchantments first
        Set<String> newEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        newEnchants.removeAll(existingTrades);
        
        if (!newEnchants.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("new_enchants_header", player));
            for (String enchant : newEnchants) {
                String[] parts = enchant.split(" ");
                String enchantId = parts[0];
                int level = Integer.parseInt(parts[1]);
                String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
                
                net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                    messageManager.format("new_enchant_line", player, localName, level)
                );
                
                // Add price options
                int[] prices = {64, 48, 32, 16, 1};
                for (int price : prices) {
                    line.addExtra(createPriceComponent(price, enchantId, level, player));
                    line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
                }
                
                player.spigot().sendMessage(line);
            }
        }

        // Then show existing enchantments
        if (!existingTrades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("existing_enchants_header", player));
            for (String enchant : existingTrades) {
                String[] parts = enchant.split(" ");
                String enchantId = parts[0];
                int level = Integer.parseInt(parts[1]);
                int originalPrice = Integer.parseInt(parts[2]);
                String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
                
                // Calculate discounted price
                int discountedPrice = originalPrice - discountAmount;
                if (discountedPrice < 1) discountedPrice = 1;
                
                net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                    messageManager.format("existing_enchant_line", player, localName, level, discountedPrice)
                );
                
                // Add price options starting from discounted price
                List<Integer> prices = new ArrayList<>();
                for (int price = discountedPrice; price >= 1; price -= 5) {
                    prices.add(price);
                }
                
                for (int price : prices) {
                    line.addExtra(createPriceComponent(price, enchantId, level, player));
                    line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
                }
                
                player.spigot().sendMessage(line);
            }
        }

        player.sendMessage(messageManager.getChatMessage("enchants_footer", player));
        
        int totalEnchants = existingTrades.size() + newEnchants.size();
        plugin.getLogger().info(String.format("Completed nearby search for player %s: %d enchantments found",
                player.getName(), totalEnchants));
        return true;
    }

    private boolean handleRegionCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("region_usage", player));
            return true;
        }

        int discountAmount;
        try {
            discountAmount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_discount", player));
            return true;
        }

        // Get selected regions
        List<VillagerRegion> regions = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all") || args[1].equals("*")) {
            regions.addAll(database.listRegions());
        } else {
            VillagerRegion region = database.getRegionByName(args[1]);
            if (region == null) {
                player.sendMessage(messageManager.getChatMessage("region_not_found", player));
                return true;
            }
            regions.add(region);
        }

        if (regions.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_regions", player));
            return true;
        }

        // Get all existing trades from librarians in regions
        Set<String> existingTrades = new HashSet<>();
        int totalLibrarians = 0;

        for (VillagerRegion region : regions) {
            List<Villager> librarians = region.getLibrariansInRegion();
            totalLibrarians += librarians.size();
            plugin.getLogger().info(String.format("Region '%s': Found %d librarians",
                    region.getName(), librarians.size()));

            for (Villager librarian : librarians) {
                Set<String> trades = EnchantmentManager.getVillagerEnchantments(librarian);
                existingTrades.addAll(trades);
                plugin.getLogger().info(String.format("Librarian at %s in region '%s' has enchantments: %s",
                        formatLocation(librarian.getLocation()), region.getName(), String.join(", ", trades)));
            }
        }

        if (totalLibrarians == 0) {
            plugin.getLogger().info(String.format("No librarians found in any selected region for player %s",
                    player.getName()));
            player.sendMessage(messageManager.getChatMessage("no_librarians_in_region", player));
            return true;
        }

        // Show new enchantments first
        Set<String> newEnchants = new HashSet<>(EnchantmentManager.getAllMaxLevelEnchantments());
        newEnchants.removeAll(existingTrades);
        
        if (!newEnchants.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("new_enchants_header", player));
            for (String enchant : newEnchants) {
                String[] parts = enchant.split(" ");
                String enchantId = parts[0];
                int level = Integer.parseInt(parts[1]);
                String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
                
                net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                    messageManager.format("new_enchant_line", player, localName, level)
                );
                
                // Add price options
                int[] prices = {64, 48, 32, 16, 1};
                for (int price : prices) {
                    line.addExtra(createPriceComponent(price, enchantId, level, player));
                    line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
                }
                
                player.spigot().sendMessage(line);
            }
        }

        // Then show existing enchantments
        if (!existingTrades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("existing_enchants_header", player));
            for (String enchant : existingTrades) {
                String[] parts = enchant.split(" ");
                String enchantId = parts[0];
                int level = Integer.parseInt(parts[1]);
                int originalPrice = Integer.parseInt(parts[2]);
                String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
                
                // Calculate discounted price
                int discountedPrice = originalPrice - discountAmount;
                if (discountedPrice < 1) discountedPrice = 1;
                
                net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                    messageManager.format("existing_enchant_line", player, localName, level, discountedPrice)
                );
                
                // Add price options starting from discounted price
                List<Integer> prices = new ArrayList<>();
                for (int price = discountedPrice; price >= 1; price -= 5) {
                    prices.add(price);
                }
                
                for (int price : prices) {
                    line.addExtra(createPriceComponent(price, enchantId, level, player));
                    line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
                }
                
                player.spigot().sendMessage(line);
            }
        }

        player.sendMessage(messageManager.getChatMessage("enchants_footer", player));
        
        int totalEnchants = existingTrades.size() + newEnchants.size();
        plugin.getLogger().info(String.format("Completed region search for player %s: %d enchantments found",
                player.getName(), totalEnchants));
        return true;
    }

    private net.md_5.bungee.api.chat.TextComponent createPriceComponent(int price, String enchantId, int level, Player player) {
        net.md_5.bungee.api.chat.TextComponent priceComponent = new net.md_5.bungee.api.chat.TextComponent(
            messageManager.format("price_component", player, price)
        );
        
        // Add hover text to show the command that will be executed
        String command = String.format("/evt search add %d %s %d", price, enchantId, level);
        priceComponent.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
            command
        ));
        priceComponent.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(messageManager.format("price_hover", player, command)).create()
        ));
        
        return priceComponent;
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private void sendUsage(Player player) {
        player.sendMessage(messageManager.getChatMessage("evtintegration_header", player));
        player.sendMessage(messageManager.getChatMessage("evtintegration_nearby_usage", player));
        player.sendMessage(messageManager.getChatMessage("evtintegration_region_usage", player));
    }
} 