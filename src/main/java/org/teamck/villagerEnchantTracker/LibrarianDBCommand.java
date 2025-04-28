package org.teamck.villagerEnchantTracker;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LibrarianDBCommand implements CommandExecutor, TabCompleter {
    private final Database db;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private final ParticleManager particleManager;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "search", "list", "delete", "edit-description", "particle");

    public LibrarianDBCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
    }

    private String resolveEnchantId(String searchTerm, String language) {
        // First try to find enchantment by localized name
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, language);
        
        // If not found by localized name, try as enchantment ID
        if (enchantId == null) {
            enchantId = searchTerm;
            // Remove enchantments. prefix if present
            if (enchantId.startsWith("enchantments.")) {
                enchantId = enchantId.substring("enchantments.".length());
            }
            if (!enchantId.startsWith("minecraft:")) {
                enchantId = "minecraft:" + enchantId;
            }
            if (EnchantManager.getEnchant(enchantId) == null) {
                return null;
            }
        }
        
        return enchantId;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("villagerenchanttracker.admin")) {
            sender.sendMessage(messageManager.getChatMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messageManager.getChatMessage("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "search" -> handleSearch(player, args);
            case "list" -> handleList(player, args);
            case "delete" -> handleDelete(player, args);
            case "edit-description" -> handleEditDescription(player, args);
            case "particle" -> handleParticle(player, args);
            default -> sender.sendMessage(messageManager.getChatMessage("invalid_subcommand"));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("create_usage"));
            return;
        }

        Entity target = player.getNearbyEntities(5, 5, 5).stream()
                .filter(e -> e instanceof Villager)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);

        if (!(target instanceof Villager villager)) {
            player.sendMessage(messageManager.getChatMessage("no_villager_nearby"));
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean tradesAdded = db.addVillagerTrades(villager, description);
        if (tradesAdded) {
            player.sendMessage(messageManager.getChatMessage("villager_trades_registered"));
        } else {
            player.sendMessage(messageManager.getChatMessage("no_enchant_trades"));
        }
    }

    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("search_usage"));
            return;
        }

        String searchTerm = String.join(" ", args);
        String enchantId = resolveEnchantId(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));
        
        if (enchantId == null) {
            player.sendMessage(messageManager.getChatMessage("invalid_enchant"));
            return;
        }

        // Cancel all existing particles before showing new results
        particleManager.cancelAllParticles(player);

        double radius = 50.0; // Default radius
        List<Trade> trades = VillagerTradeSearcher.searchNearbyVillagerTrades(player, enchantId, radius);
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_found_trades"));
            return;
        }

        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Location loc = trade.getLocation();
            String message = messageManager.format("found_trade_info",
                    localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            
            // Make the message clickable
            TextComponent textComponent = messageManager.createClickableMessage(message, loc, "/librariandb particle");
            player.spigot().sendMessage(textComponent);
            
            // Spawn particles immediately for search results
            particleManager.spawnParticles(loc, player, false);
        }
    }

    private void handleList(Player player, String[] args) {
        List<Trade> trades = db.listTrades();
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_trades"));
            return;
        }

        String baseLanguage = messageManager.getBaseLanguageCode(player.getLocale());
        player.sendMessage(messageManager.getChatMessage("trade_list_header"));
        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(trade.getEnchantId(), baseLanguage);
            Location loc = trade.getLocation();
            String message = messageManager.format("trade_list_entry",
                    trade.getId(), localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription());
            
            // Make the message clickable
            TextComponent textComponent = messageManager.createClickableMessage(message, loc, "/librariandb particle");
            player.spigot().sendMessage(textComponent);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("delete_usage"));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            db.deleteTrade(id);
            player.sendMessage(messageManager.getChatMessage("trade_deleted"));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("id_must_be_number"));
        }
    }

    private void handleEditDescription(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(messageManager.getChatMessage("edit_description_usage"));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            db.updateTradeDescription(id, description);
            player.sendMessage(messageManager.getChatMessage("description_updated"));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("id_must_be_number"));
        }
    }

    private void handleParticle(Player player, String[] args) {
        particleManager.handleParticleCommand(player, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> !s.equals("particle") && s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
} 