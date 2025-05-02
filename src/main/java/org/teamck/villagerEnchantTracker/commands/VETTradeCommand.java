package org.teamck.villagerEnchantTracker.commands;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.manager.ParticleManager;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public class VETTradeCommand {
    private final Database db;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private final ParticleManager particleManager;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "search", "list", "delete", "edit-description", "confirm", "register", "description");

    public VETTradeCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
    }

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }
        
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(messageManager.getMessage("usage", player));
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean isWrite = sub.equals("create") || sub.equals("delete") || sub.equals("edit-description") || sub.equals("confirm") || sub.equals("register") || sub.equals("description");
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
            case "confirm" -> handleConfirm(player, args);
            case "register" -> handleRegister(player, args, "");
            case "description" -> handleRegister(player, args, String.join(" ", Arrays.copyOfRange(args, 5, args.length)));
            case "search" -> handleSearch(player, args);
            case "list" -> handleList(player, args);
            case "delete" -> handleDelete(player, args);
            case "edit-description" -> handleEditDescription(player, args);
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

        String baseLanguage = messageManager.getBaseLanguageCode(((Player) sender).getLocale());
        return switch (args[0].toLowerCase()) {
            case "search" -> args.length == 2 ? messageManager.getEnchantNames(baseLanguage).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList()) : new ArrayList<>();
            case "create" -> args.length >= 2 ? List.of("<description>") : new ArrayList<>();
            case "edit-description" -> {
                if (args.length == 2) yield List.of("<id>");
                else if (args.length >= 3) yield List.of("<description>");
                else yield new ArrayList<>();
            }
            case "delete" -> args.length == 2 ? List.of("<id>") : new ArrayList<>();
            case "confirm", "register", "description" -> new ArrayList<>(); // These are internal commands, no tab completion needed
            default -> new ArrayList<>();
        };
    }

    private void handleCreate(Player player, String[] args) {
        // Find the closest villager
        Entity target = player.getNearbyEntities(5, 5, 5).stream()
                .filter(e -> e instanceof Villager)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);

        if (!(target instanceof Villager villager)) {
            player.sendMessage(messageManager.getMessage("no_villager_nearby", player));
            return;
        }

        boolean found = false;
        for (MerchantRecipe recipe : villager.getRecipes()) {
            ItemStack result = recipe.getResult();
            if (result.getType() == Material.ENCHANTED_BOOK && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
                    String enchantId = EnchantmentManager.normalizeEnchantmentId("minecraft:" + entry.getKey().getKey());
                    int level = entry.getValue();
                    int price = recipe.getIngredients().stream()
                        .filter(i -> i.getType() == Material.EMERALD)
                        .mapToInt(ItemStack::getAmount)
                        .sum();
                    String localName = messageManager.getEnchantName(enchantId, player);
                    String display = String.format(messageManager.getMessage("trade_clickable_enchant", player), localName, level, price);
                    TextComponent comp = new TextComponent(display);
                    comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        String.format("/vet trade confirm %s %s %d %d", villager.getUniqueId(), enchantId, level, price)));
                    player.spigot().sendMessage(comp);
                    found = true;
                }
            }
        }
        if (!found) {
            player.sendMessage(messageManager.getMessage("no_enchant_trades", player));
        }
    }

    private void handleConfirm(Player player, String[] args) {
        if (args.length < 5) return;
        String villagerUUID = args[1];
        String enchantId = EnchantmentManager.normalizeEnchantmentId(args[2]);
        int level = Integer.parseInt(args[3]);
        int price = Integer.parseInt(args[4]);
        String localName = messageManager.getEnchantName(enchantId, player);
        String askMsg = String.format(messageManager.getMessage("trade_ask_description", player), localName, level, price);
        TextComponent yes = new TextComponent(messageManager.getMessage("trade_yes", player));
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
            String.format("/vet trade description %s %s %d %d ", villagerUUID, enchantId, level, price)));
        TextComponent no = new TextComponent(messageManager.getMessage("trade_no", player));
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
            String.format("/vet trade register %s %s %d %d", villagerUUID, enchantId, level, price)));
        TextComponent msg = new TextComponent(askMsg + " ");
        msg.addExtra(yes);
        msg.addExtra(" ");
        msg.addExtra(no);
        player.spigot().sendMessage(msg);
    }

    private void handleRegister(Player player, String[] args, String description) {
        if (args.length < 5) return;
        String villagerUUID = args[1];
        String enchantId = EnchantmentManager.normalizeEnchantmentId(args[2]);
        int level = Integer.parseInt(args[3]);
        int price = Integer.parseInt(args[4]);
        // 디버깅 메시지 추가
        plugin.getLogger().info("Registering trade with description: " + description);
        // regionName 등은 필요시 추가
        Trade trade = new Trade(villagerUUID, enchantId, level, price, description);
        db.addTrade(trade); // 실제 등록 로직에 맞게 구현
        player.sendMessage(messageManager.getMessage("villager_trades_registered", player));
    }

    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("search_usage", player));
            return;
        }

        String searchTerm = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));
        enchantId = EnchantmentManager.normalizeEnchantmentId(enchantId);
        
        if (enchantId == null) {
            player.sendMessage(messageManager.getMessage("invalid_enchant", player));
            return;
        }

        // Cancel all existing particles before showing new results
        particleManager.cancelAllParticles(player);

        List<Trade> trades = db.searchTrades(enchantId);
        plugin.getLogger().info("검색된 trade 개수: " + trades.size());
        if (trades.isEmpty()) {
            // log
            plugin.getLogger().info("No found trades for enchantId: " + enchantId);
            player.sendMessage(messageManager.getMessage("no_found_trades", player));
            return;
        }

        for (Trade trade : trades) {
            plugin.getLogger().info("Trade ID: " + trade.getId());
            String localName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));
            Villager villager = trade.getVillager();
            if (villager == null) {
                continue;
            }
            Location loc = villager.getLocation();
            String message = String.format(messageManager.getMessage("found_trade_info", player),
                    trade.getId(),
                    localName, trade.getLevel(),
                    trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription());
            
            // Make the message clickable
            TextComponent textComponent = messageManager.createClickableMessage(message, loc, "/vet particle", player);
            player.spigot().sendMessage(textComponent);
            
            // Spawn particles immediately for search results
            particleManager.spawnParticles(loc, player, false);
        }
    }

    private void handleList(Player player, String[] args) {
        List<Trade> trades = db.listTrades();
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_trades", player));
            return;
        }

        player.sendMessage(messageManager.getMessage("trade_list_header", player));
        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(trade.getEnchantId(), player);
            Villager villager = trade.getVillager();
            if (villager == null) {
                continue;
            }
            Location loc = villager.getLocation();
            String message = String.format(messageManager.getMessage("trade_list_entry", player),
                    trade.getId(),
                    localName, trade.getLevel(),
                    trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription());
            
            // Make the message clickable
            TextComponent textComponent = messageManager.createClickableMessage(message, loc, "/vet particle", player);
            player.spigot().sendMessage(textComponent);
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("delete_usage", player));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            db.deleteTrade(id);
            player.sendMessage(messageManager.getMessage("trade_deleted", player));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getMessage("id_must_be_number", player));
        }
    }

    private void handleEditDescription(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(messageManager.getMessage("edit_description_usage", player));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            boolean success = db.updateTradeDescription(id, description);
            if (success) {
                player.sendMessage(messageManager.getMessage("description_updated", player));
            } else {
                player.sendMessage(messageManager.getMessage("trade_not_found", player));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getMessage("id_must_be_number", player));
        }
    }
} 