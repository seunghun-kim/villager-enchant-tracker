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
                                String regionName = null;
                                for (VillagerRegion region : db.listRegions()) {
                                    if (region.contains(villager.getLocation())) {
                                        regionName = region.getName();
                                        break;
                                    }
                                }
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", "en"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("findvillager.use")) {
            player.sendMessage(messageManager.getChatMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messageManager.getChatMessage("findvillager_usage", player));
            return true;
        }

        String searchTerm = String.join(" ", args);
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));

        if (enchantId == null) {
            player.sendMessage(messageManager.getChatMessage("invalid_enchant", player));
            return true;
        }

        // Cancel all existing particles before showing new results
        particleManager.cancelAllParticles(player);

        // Search both database and nearby villagers
        List<Trade> trades = new ArrayList<>();
        trades.addAll(db.searchTrades(enchantId));
        trades.addAll(searchNearbyVillagerTrades(player, enchantId, DEFAULT_RADIUS));

        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_found_trades", player));
            return true;
        }

        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Location loc = trade.getLocation();
            String message = messageManager.format("found_trade_info", player,
                    localName, trade.getLevel(),
                    trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getRegionName() != null ? trade.getRegionName() : "");
            
            // Create clickable message
            TextComponent textComponent = new TextComponent(message);
            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/findvillager particle " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
            player.spigot().sendMessage(textComponent);
            
            // Spawn particles immediately for search results
            particleManager.spawnParticles(loc, player, false);
        }

        return true;
    }

    private void handleParticle(Player player, String[] args) {
        particleManager.handleParticleCommand(player, args);
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