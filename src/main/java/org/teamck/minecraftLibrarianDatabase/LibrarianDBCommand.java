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
    private final DescriptionManager descManager;
    private final JavaPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "add-villager", "search", "nearby", "list", "delete", "update");

    public LibrarianDBCommand(Database db, DescriptionManager descManager, JavaPlugin plugin) {
        this.db = db;
        this.descManager = descManager;
        this.plugin = plugin;
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
                        plugin.getLogger().warning("잘못된 파티클 타입: " + type);
                    }
                }
            }, tick * (interval * 20L));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("librariandb.admin")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/librariandb [create|add-villager|search|nearby|list|delete|update]");
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
            default -> sender.sendMessage("잘못된 서브커맨드입니다.");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage("사용법: /librariandb create <enchant> <level> <price> <x> <y> <z> [description]");
            return;
        }

        String enchantId = args[1];
        Enchantment enchant = EnchantManager.getEnchant(enchantId);
        if (enchant == null) {
            player.sendMessage("잘못된 인챈트 ID입니다.");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (!EnchantManager.isValidLevel(enchant, level)) {
                player.sendMessage("레벨은 1에서 " + enchant.getMaxLevel() + " 사이여야 합니다.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("레벨은 숫자여야 합니다.");
            return;
        }

        int price;
        try {
            price = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("가격은 숫자여야 합니다.");
            return;
        }

        Location location;
        try {
            double x = args[4].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[4]);
            double y = args[5].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[5]);
            double z = args[6].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[6]);
            location = new Location(player.getWorld(), x, y, z);
        } catch (NumberFormatException e) {
            player.sendMessage("좌표는 숫자 또는 ~이어야 합니다.");
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 7, args.length));
        db.createTrade(enchantId, level, price, location, description);
        player.sendMessage("거래가 등록되었습니다.");
    }

    private void handleAddVillager(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("사용법: /librariandb add-villager [description]");
            return;
        }

        Entity target = player.getNearbyEntities(5, 5, 5).stream()
                .filter(e -> e instanceof Villager)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);

        if (!(target instanceof Villager villager)) {
            player.sendMessage("근처에 주민이 없습니다.");
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        db.addVillagerTrades(villager, description);
        player.sendMessage("주민의 인챈트 거래가 등록되었습니다.");
    }

    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("사용법: /librariandb search <enchant>");
            return;
        }

        String enchantId = args[1];
        List<Trade> trades = db.searchTrades(enchantId);
        if (trades.isEmpty()) {
            player.sendMessage("해당 인챈트의 거래가 없습니다.");
            return;
        }

        String lang = player.getLocale();
        for (Trade trade : trades) {
            String localName = descManager.getLocalName(enchantId, lang);
            Location loc = trade.getLocation();
            player.sendMessage(String.format("[%s %d] %d원에 거래 가능\n(%d, %d, %d) %s",
                    localName, trade.getLevel(), trade.getPrice(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    trade.getDescription()));
            
            spawnParticles(loc);
        }
    }

    private void handleNearby(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("사용법: /librariandb nearby <enchant> [radius]");
            return;
        }

        String enchantId = args[1];
        double radius = args.length > 2 ? Double.parseDouble(args[2]) : 50.0;
        List<Trade> trades = VillagerTradeSearcher.searchNearbyVillagerTrades(player, enchantId, radius);
        if (trades.isEmpty()) {
            player.sendMessage("근처 주민에게 해당 인챈트 거래가 없습니다.");
            return;
        }

        String lang = player.getLocale();
        for (Trade trade : trades) {
            String localName = descManager.getLocalName(enchantId, lang);
            Location loc = trade.getLocation();
            player.sendMessage(String.format("[%s %d] %d원에 거래 가능\n(%d, %d, %d)",
                    localName, trade.getLevel(), trade.getPrice(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            
            spawnParticles(loc);
        }
    }

    private void handleList(Player player, String[] args) {
        List<Trade> trades = db.listTrades();
        if (trades.isEmpty()) {
            player.sendMessage("등록된 거래가 없습니다.");
            return;
        }

        String lang = player.getLocale();
        player.sendMessage("=== 등록된 거래 목록 ===");
        for (Trade trade : trades) {
            String localName = descManager.getLocalName(trade.getEnchantId(), lang);
            Location loc = trade.getLocation();
            player.sendMessage(String.format("ID: %d, [%s %d] %d원, (%d, %d, %d) %s",
                    trade.getId(), localName, trade.getLevel(), trade.getPrice(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), trade.getDescription()));
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("사용법: /librariandb delete <id>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            db.deleteTrade(id);
            player.sendMessage("거래가 삭제되었습니다.");
        } catch (NumberFormatException e) {
            player.sendMessage("ID는 숫자여야 합니다.");
        }
    }

    private void handleUpdate(Player player, String[] args) {
        if (args.length < 7) {
            player.sendMessage("사용법: /librariandb update <id> <enchant> <level> <price> <x> <y> <z> [description]");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String enchantId = args[2];
            Enchantment enchant = EnchantManager.getEnchant(enchantId);
            if (enchant == null) {
                player.sendMessage("잘못된 인챈트 ID입니다.");
                return;
            }

            int level = Integer.parseInt(args[3]);
            if (!EnchantManager.isValidLevel(enchant, level)) {
                player.sendMessage("레벨은 1에서 " + enchant.getMaxLevel() + " 사이여야 합니다.");
                return;
            }

            int price = Integer.parseInt(args[4]);
            double x = args[5].equals("~") ? player.getLocation().getX() : Double.parseDouble(args[5]);
            double y = args[6].equals("~") ? player.getLocation().getY() : Double.parseDouble(args[6]);
            double z = args[7].equals("~") ? player.getLocation().getZ() : Double.parseDouble(args[7]);
            Location location = new Location(player.getWorld(), x, y, z);
            String description = String.join(" ", Arrays.copyOfRange(args, 8, args.length));

            db.updateTrade(id, enchantId, level, price, location, description);
            player.sendMessage("거래가 수정되었습니다.");
        } catch (NumberFormatException e) {
            player.sendMessage("숫자 형식이 잘못되었습니다.");
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