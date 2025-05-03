package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.manager.ParticleManager;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FindVillagerCommand implements CommandExecutor, TabCompleter {
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private final ParticleManager particleManager;
    private final Database db;
    private static final double DEFAULT_RADIUS = 50.0;

    public FindVillagerCommand(MessageManager messageManager, JavaPlugin plugin, Database db) {
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
        this.db = db;
    }

    public List<Trade> searchNearbyVillagerTrades(Player player, String enchantId, double radius) {
        final String normalizedEnchantId = EnchantmentManager.normalizeEnchantmentId(enchantId);
        List<Trade> trades = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager) {
                for (MerchantRecipe recipe : villager.getRecipes()) {
                    ItemStack result = recipe.getResult();
                    if (result.getType() == Material.ENCHANTED_BOOK && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                        meta.getStoredEnchants().forEach((enchant, level) -> {
                            if (EnchantmentManager.normalizeEnchantmentId("minecraft:" + enchant.getKey().getKey()).equals(normalizedEnchantId)) {
                                int price = recipe.getIngredients().stream()
                                        .filter(i -> i.getType() == Material.EMERALD)
                                        .mapToInt(ItemStack::getAmount)
                                        .sum();
                                
                                // Check if the villager is in any region
                                String regionName = null;
                                for (VillagerRegion region : db.listRegions()) {
                                    if (region.contains(villager.getLocation())) {
                                        regionName = region.getName();
                                        break;
                                    }
                                }
                                trades.add(new Trade(villager.getUniqueId().toString(), normalizedEnchantId, level, price, "", regionName));
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
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        if (!player.hasPermission("villagerenchanttracker.use")) {
            player.sendMessage(messageManager.getMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messageManager.getMessage("findvillager_usage", player));
            return true;
        }

        String searchTerm = String.join(" ", args);
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));
        enchantId = EnchantmentManager.normalizeEnchantmentId(enchantId);

        if (enchantId == null) {
            player.sendMessage(messageManager.getMessage("invalid_enchant", player));
            return true;
        }

        // Search both database and nearby villagers
        List<Trade> trades = new ArrayList<>();
        List<Trade> dbTrades = db.searchTrades(enchantId);
        List<Trade> nearbyTrades = searchNearbyVillagerTrades(player, enchantId, DEFAULT_RADIUS);
        
        // Add database trades first
        trades.addAll(dbTrades);
        
        // Add nearby trades only if they are not already in the database trades
        for (Trade nearbyTrade : nearbyTrades) {
            boolean isDuplicate = false;
            for (Trade dbTrade : dbTrades) {
                if (dbTrade.getVillagerUuid().equals(nearbyTrade.getVillagerUuid()) && 
                    dbTrade.getEnchantId().equals(nearbyTrade.getEnchantId()) && 
                    dbTrade.getLevel() == nearbyTrade.getLevel()) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                trades.add(nearbyTrade);
            }
        }

        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_found_trades", player));
            return true;
        }

        // Cancel all existing particles before showing new results
        particleManager.cancelAllParticles(player);
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Location loc = trade.getLocation();
            if (loc == null) {
                player.sendMessage(String.format(messageManager.getMessage("found_trade_info_no_location", player), i + 1, localName, trade.getLevel(), trade.getPrice()));
                continue;
            }
            String message = String.format(messageManager.getMessage("found_trade_info", player),
                    i + 1, // 거래 번호 (1부터 시작)
                    localName, trade.getLevel(),
                    trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getRegionName() != null ? trade.getRegionName() : "");
            
            // Create clickable message using villager_uuid
            TextComponent textComponent = new TextComponent(message);
            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vet particle " + trade.getVillagerUuid()));
            player.spigot().sendMessage(textComponent);
            
            // Spawn particles immediately for search results. If there are multiple results, they will be spawned simultaneously.
            particleManager.spawnParticles(loc, player, false);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("villagerenchanttracker.use")) {
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