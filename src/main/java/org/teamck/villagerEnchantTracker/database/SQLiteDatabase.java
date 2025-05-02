package org.teamck.villagerEnchantTracker.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLiteDatabase implements Database {
    final Connection connection;

    public SQLiteDatabase(JavaPlugin plugin) throws SQLException {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/enchants.db");
        init();
    }

    @Override
    public void init() {
        try (Statement stmt = connection.createStatement()) {
            // Create Regions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Regions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    min_x REAL NOT NULL,
                    min_y REAL NOT NULL,
                    min_z REAL NOT NULL,
                    max_x REAL NOT NULL,
                    max_y REAL NOT NULL,
                    max_z REAL NOT NULL,
                    world_name TEXT NOT NULL
                )
            """);

            // Create Trades table without region_id
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    villager_uuid TEXT NOT NULL,
                    enchant_id_string TEXT NOT NULL,
                    level INTEGER NOT NULL,
                    price INTEGER NOT NULL,
                    description TEXT,
                    UNIQUE(villager_uuid, enchant_id_string)
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean addTrade(Trade trade) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO Trades (villager_uuid, enchant_id_string, level, price, description) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, trade.getVillagerUuid());
            stmt.setString(2, EnchantmentManager.normalizeEnchantmentId(trade.getEnchantId()));
            stmt.setInt(3, trade.getLevel());
            stmt.setInt(4, trade.getPrice());
            stmt.setString(5, trade.getDescription());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Trade> searchTrades(String enchantId) {
        List<Trade> trades = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Trades WHERE enchant_id_string = ?")) {
            stmt.setString(1, EnchantmentManager.normalizeEnchantmentId(enchantId));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Trade trade = new Trade(rs.getInt("id"), rs.getString("villager_uuid"), rs.getString("enchant_id_string"),
                        rs.getInt("level"), rs.getInt("price"), rs.getString("description"), null);

                Villager villager = trade.getVillager();
                boolean added = false;
                if (villager != null) {
                    for (VillagerRegion region : listRegions()) {
                        if (region.getWorld().equals(villager.getWorld()) && region.contains(villager.getLocation())) {
                            trades.add(new Trade(trade.getId(), trade.getVillagerUuid(), trade.getEnchantId(), trade.getLevel(),
                                    trade.getPrice(), trade.getDescription(), region.getName()));
                            added = true;
                            break;
                        }
                    }
                }
                if (!added) {
                    trades.add(trade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public List<Trade> listTrades() {
        List<Trade> trades = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Trades")) {
            while (rs.next()) {
                Trade trade = new Trade(rs.getInt("id"), rs.getString("villager_uuid"),
                        EnchantmentManager.normalizeEnchantmentId(rs.getString("enchant_id_string")),
                        rs.getInt("level"), rs.getInt("price"), rs.getString("description"), null);

                Villager villager = trade.getVillager();
                boolean added = false;
                if (villager != null) {
                    for (VillagerRegion region : listRegions()) {
                        if (region.getWorld().equals(villager.getWorld()) && region.contains(villager.getLocation())) {
                            Trade regionTrade = new Trade(trade.getId(), trade.getVillagerUuid(), trade.getEnchantId(), trade.getLevel(),
                                    trade.getPrice(), trade.getDescription(), region.getName());
                            if (addedIds.add(regionTrade.getId())) {
                                trades.add(regionTrade);
                                added = true;
                            }
                            break;
                        }
                    }
                }
                if (!added && addedIds.add(trade.getId())) {
                    trades.add(trade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public void deleteTrade(int id) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM Trades WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean updateTradeDescription(int id, String description) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE Trades SET description = ? WHERE id = ?")) {
            stmt.setString(1, description);
            stmt.setInt(2, id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int createRegion(String name, Location min, Location max) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO Regions (name, min_x, min_y, min_z, max_x, max_y, max_z, world_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setDouble(2, min.getX());
            stmt.setDouble(3, min.getY());
            stmt.setDouble(4, min.getZ());
            stmt.setDouble(5, max.getX());
            stmt.setDouble(6, max.getY());
            stmt.setDouble(7, max.getZ());
            stmt.setString(8, min.getWorld().getName());
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public boolean deleteRegion(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM Regions WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public List<VillagerRegion> listRegions() {
        List<VillagerRegion> regions = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Regions")) {
            while (rs.next()) {
                Location min = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"));
                Location max = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"));
                regions.add(new VillagerRegion(rs.getInt("id"), rs.getString("name"), min, max));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return regions;
    }

    @Override
    public VillagerRegion getRegion(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Regions WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Location min = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"));
                Location max = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"));
                return new VillagerRegion(rs.getInt("id"), rs.getString("name"), min, max);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public VillagerRegion getRegionByName(String name) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Regions WHERE name = ?")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Location min = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"));
                Location max = new Location(Bukkit.getWorld(rs.getString("world_name")),
                        rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"));
                return new VillagerRegion(rs.getInt("id"), rs.getString("name"), min, max);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Trade> getTradesInRegion(int regionId) {
        List<Trade> trades = new ArrayList<>();
        VillagerRegion region = getRegion(regionId);
        if (region == null) return trades;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Trades")) {
            while (rs.next()) {
                Trade trade = new Trade(rs.getInt("id"), rs.getString("villager_uuid"),
                        EnchantmentManager.normalizeEnchantmentId(rs.getString("enchant_id_string")),
                        rs.getInt("level"), rs.getInt("price"), rs.getString("description"), null);
                
                Villager villager = trade.getVillager();
                if (villager != null && region.contains(villager.getLocation())) {
                    trades.add(new Trade(trade.getId(), trade.getVillagerUuid(), trade.getEnchantId(), trade.getLevel(),
                            trade.getPrice(), trade.getDescription(), region.getName()));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public List<Trade> getTradesByVillager(String villagerUuid) {
        List<Trade> trades = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Trades WHERE villager_uuid = ?")) {
            stmt.setString(1, villagerUuid);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Trade trade = new Trade(rs.getInt("id"), villagerUuid,
                        EnchantmentManager.normalizeEnchantmentId(rs.getString("enchant_id_string")),
                        rs.getInt("level"), rs.getInt("price"), rs.getString("description"), null);
                
                // Check if the trade is in any region
                Villager villager = trade.getVillager();
                if (villager != null) {
                    for (VillagerRegion region : listRegions()) {
                        // Only check regions in the same world
                        if (region.getWorld().equals(villager.getWorld()) && region.contains(villager.getLocation())) {
                            trades.add(new Trade(trade.getId(), trade.getVillagerUuid(), trade.getEnchantId(), trade.getLevel(),
                                    trade.getPrice(), trade.getDescription(), region.getName()));
                            break;
                        }
                    }
                    if (!trades.contains(trade)) {
                        trades.add(trade);
                    }
                } else {
                    trades.add(trade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public boolean updateRegionName(int id, String newName) {
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE Regions SET name = ? WHERE id = ?")) {
            stmt.setString(1, newName);
            stmt.setInt(2, id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
} 