package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.manager.ParticleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FindVillagerCommand implements CommandExecutor, TabCompleter {
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private final ParticleManager particleManager;
    private final Database db;

    public FindVillagerCommand(MessageManager messageManager, JavaPlugin plugin, Database db) {
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
        this.db = db;
    }

    public List<Trade> searchNearbyVillagerTrades(Player player, String enchantId, double radius) {
        List<Trade> trades = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager) {
                for (MerchantRecipe recipe : villager.getRecipes()) {
                    ItemStack result = recipe.getResult();
                    if (result.getType() == Material.ENCHANTED_BOOK && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                        meta.getStoredEnchants().forEach((enchant, level) -> {
                            if (("minecraft:" + enchant.getKey().getKey()).equals(enchantId)) {
                                int price = recipe.getIngredients().stream()
                                        .filter(i -> i.getType() == Material.EMERALD)
                                        .mapToInt(ItemStack::getAmount)
                                        .sum();
                                
                                // Check if the villager is in any region
                                plugin.getLogger().info("Checking if villager is in any region");
                                String regionName = null;
                                for (VillagerRegion region : db.listRegions()) {
                                    plugin.getLogger().info("Region: " + region.getName() + " " + region.getMin() + " " + region.getMax());
                                    plugin.getLogger().info("Villager Location: " + villager.getLocation());
                                    if (region.contains(villager.getLocation())) {
                                        plugin.getLogger().info("Region found: " + region.getName());
                                        regionName = region.getName();
                                        break;
                                    }
                                }
                                plugin.getLogger().info("RegionName: " + regionName);
                                trades.add(new Trade(0, enchantId, level, price, villager.getLocation(), "", regionName));
                            }
                        });
                    }
                }
            }
        }
        return trades;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getChatMessage("player_only"));
            return true;
        }

        if (!sender.hasPermission("villagerenchanttracker.find")) {
            sender.sendMessage(messageManager.getChatMessage("no_permission"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messageManager.getChatMessage("findvillager_usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("particle")) {
            particleManager.handleParticleCommand(player, args);
            return true;
        }

        String searchTerm = String.join(" ", args);
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));
        
        if (enchantId == null) {
            player.sendMessage(messageManager.getChatMessage("invalid_enchant"));
            return true;
        }

        // Cancel all existing particles before showing new results
        particleManager.cancelAllParticles(player);

        double radius = 50.0; // Default radius
        List<Trade> trades = searchNearbyVillagerTrades(player, enchantId, radius);
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_found_trades"));
            return true;
        }

        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Location loc = trade.getLocation();
            String message = messageManager.format("found_trade_info",
                    localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getRegionName());
            
            // Make the message clickable
            TextComponent textComponent = messageManager.createClickableMessage(message, loc, "/findvillager particle");
            player.spigot().sendMessage(textComponent);
            
            // Spawn particles immediately for search results
            particleManager.spawnParticles(loc, player, false);
            
            // Draw a line between player and villager
            particleManager.drawLine(player.getLocation(), loc, player, false);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("villagerenchanttracker.find")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return messageManager.getEnchantNames(messageManager.getBaseLanguageCode(player.getLocale())).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
} 