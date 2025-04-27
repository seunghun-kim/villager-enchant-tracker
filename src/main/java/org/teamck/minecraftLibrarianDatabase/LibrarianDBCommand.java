package org.teamck.minecraftLibrarianDatabase;

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
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "add-villager", "search", "nearby", "list", "delete", "update");

    public LibrarianDBCommand(Database db, MessageManager messageManager, JavaPlugin plugin) {
        this.db = db;
        this.messageManager = messageManager;
        this.plugin = plugin;
        this.particleManager = new ParticleManager(plugin);
    }

    private void spawnParticles(Location loc) {
        List<Map<?, ?>> particleConfigs = plugin.getConfig().getMapList("particles");
        int duration = plugin.getConfig().getInt("particle-duration", 30);
        int interval = plugin.getConfig().getInt("particle-interval", 1);

        for (int i = 0; i < duration; i++) {
            final int tick = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Map<?, ?> particleConfig : particleConfigs) {
                    String type = (String) particleConfig.get("type");
                    int height = (int) particleConfig.get("height");
                    int count = (int) particleConfig.get("count");

                    try {
                        Particle particle = Particle.valueOf(type);
                        for (int y = 0; y <= height; y++) {
                            Location particleLoc = loc.clone().add(0, y, 0);
                            double offsetX = (Math.random() - 0.5) * 0.5;
                            double offsetY = (Math.random() - 0.5) * 0.5;
                            double offsetZ = (Math.random() - 0.5) * 0.5;
                            double speed = Math.random() * 0.1;
                            loc.getWorld().spawnParticle(particle, particleLoc, count, offsetX, offsetY, offsetZ, speed);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Wrong Particle Type: " + type);
                    }
                }
            }, tick * (interval * 20L));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("librariandb.admin")) {
            sender.sendMessage(messageManager.getChatMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messageManager.getChatMessage("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "add-villager" -> handleAddVillager(player, args);
            case "search" -> handleSearch(player, args);
            case "nearby" -> handleNearby(player, args);
            case "list" -> handleList(player, args);
            case "delete" -> handleDelete(player, args);
            case "update" -> handleUpdate(player, args);
            default -> sender.sendMessage(messageManager.getChatMessage("invalid_subcommand"));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage(messageManager.getChatMessage("create_usage"));
            return;
        }

        String enchantId = args[1];
        Enchantment enchant = EnchantManager.getEnchant(enchantId);
        if (enchant == null) {
            player.sendMessage(messageManager.getChatMessage("invalid_enchant"));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (!EnchantManager.isValidLevel(enchant, level)) {
                player.sendMessage(messageManager.format("invalid_level", enchant.getMaxLevel()));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("level_must_be_number"));
            return;
        }

        int price;
        try {
            price = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("price_must_be_number"));
            return;
        }

        Location location;
        try {
            double x = args[4].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[4]);
            double y = args[5].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[5]);
            double z = args[6].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[6]);
            location = new Location(player.getWorld(), x, y, z);
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("coordinates_must_be_number"));
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 7, args.length));
        db.createTrade(enchantId, level, price, location, description);
        player.sendMessage(messageManager.getChatMessage("trade_created"));
    }

    private void handleAddVillager(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("add_villager_usage"));
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
        db.addVillagerTrades(villager, description);
        player.sendMessage(messageManager.getChatMessage("villager_trades_registered"));
    }

    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("search_usage"));
            return;
        }

        String enchantId = args[1];
        List<Trade> trades = db.searchTrades(enchantId);
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_trades_found"));
            return;
        }

        String lang = player.getLocale();
        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, lang);
            Location loc = trade.getLocation();
            player.sendMessage(messageManager.format("trade_info",
                    localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription()));
            
            particleManager.spawnParticles(loc);
        }
    }

    private void handleNearby(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getChatMessage("nearby_usage"));
            return;
        }

        String enchantId = args[1];
        double radius = args.length > 2 ? Double.parseDouble(args[2]) : 50.0;
        List<Trade> trades = VillagerTradeSearcher.searchNearbyVillagerTrades(player, enchantId, radius);
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_nearby_trades"));
            return;
        }

        String lang = player.getLocale();
        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(enchantId, lang);
            Location loc = trade.getLocation();
            player.sendMessage(messageManager.format("nearby_trade_info",
                    localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            
            particleManager.spawnParticles(loc);
        }
    }

    private void handleList(Player player, String[] args) {
        List<Trade> trades = db.listTrades();
        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getChatMessage("no_trades"));
            return;
        }

        String lang = player.getLocale();
        player.sendMessage(messageManager.getChatMessage("trade_list_header"));
        for (Trade trade : trades) {
            String localName = messageManager.getEnchantName(trade.getEnchantId(), lang);
            Location loc = trade.getLocation();
            player.sendMessage(messageManager.format("trade_list_entry",
                    trade.getId(), localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription()));
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

    private void handleUpdate(Player player, String[] args) {
        if (args.length < 7) {
            player.sendMessage(messageManager.getChatMessage("update_usage"));
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String enchantId = args[2];
            Enchantment enchant = EnchantManager.getEnchant(enchantId);
            if (enchant == null) {
                player.sendMessage(messageManager.getChatMessage("invalid_enchant"));
                return;
            }

            int level = Integer.parseInt(args[3]);
            if (!EnchantManager.isValidLevel(enchant, level)) {
                player.sendMessage(messageManager.format("invalid_level", enchant.getMaxLevel()));
                return;
            }

            int price = Integer.parseInt(args[4]);
            double x = args[5].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[5]);
            double y = args[6].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[6]);
            double z = args[7].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[7]);
            Location location = new Location(player.getWorld(), x, y, z);
            String description = String.join(" ", Arrays.copyOfRange(args, 8, args.length));

            db.updateTrade(id, enchantId, level, price, location, description);
            player.sendMessage(messageManager.getChatMessage("trade_updated"));
        } catch (NumberFormatException e) {
            player.sendMessage(messageManager.getChatMessage("invalid_number_format"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("librariandb.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        switch (args[0].toLowerCase()) {
            case "create", "update" -> {
                if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("update")) {
                        return db.listTrades().stream()
                                .map(trade -> String.valueOf(trade.getId()))
                                .filter(id -> id.startsWith(args[1]))
                                .collect(Collectors.toList());
                    }
                    return EnchantManager.getAllEnchantIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    String enchantId = args[1];
                    Enchantment enchant = EnchantManager.getEnchant(enchantId);
                    if (enchant != null) {
                        List<String> levels = new ArrayList<>();
                        for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                            levels.add(String.valueOf(i));
                        }
                        return levels.stream()
                                .filter(level -> level.startsWith(args[2]))
                                .collect(Collectors.toList());
                    }
                } else if (args.length >= 4 && args.length <= 6) {
                    return List.of("~");
                }
            }
            case "search" -> {
                if (args.length == 2) {
                    return EnchantManager.getAllEnchantIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
            case "nearby" -> {
                if (args.length == 2) {
                    return EnchantManager.getAllEnchantIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    return List.of("10", "20", "30", "40", "50");
                }
            }
            case "delete" -> {
                if (args.length == 2) {
                    return db.listTrades().stream()
                            .map(trade -> String.valueOf(trade.getId()))
                            .collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}