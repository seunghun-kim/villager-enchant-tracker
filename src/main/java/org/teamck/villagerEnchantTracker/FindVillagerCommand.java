package org.teamck.villagerEnchantTracker;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FindVillagerCommand implements CommandExecutor, TabCompleter {
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private final ParticleManager particleManager;

    public FindVillagerCommand(MessageManager messageManager, JavaPlugin plugin) {
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
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
        List<Trade> trades = VillagerTradeSearcher.searchNearbyVillagerTrades(player, enchantId, radius);
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_found_trades"));
            return true;
        }

        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Location loc = trade.getLocation();
            String message = messageManager.format("found_trade_info",
                    localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            
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