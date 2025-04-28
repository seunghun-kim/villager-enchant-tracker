package org.teamck.villagerEnchantTracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    enchant_id_string TEXT,
                    level INTEGER,
                    price INTEGER,
                    location TEXT,
                    description TEXT
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createTrade(String enchantId, int level, int price, Location location, String description) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO Trades (enchant_id_string, level, price, location, description) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, enchantId);
            stmt.setInt(2, level);
            stmt.setInt(3, price);
            stmt.setString(4, location.getX() + "," + location.getY() + "," + location.getZ());
            stmt.setString(5, description);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean addVillagerTrades(Villager villager, String description) {
        boolean hasEnchantTrades = false;
        for (MerchantRecipe recipe : villager.getRecipes()) {
            ItemStack result = recipe.getResult();
            if (result.getType() == Material.ENCHANTED_BOOK && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                hasEnchantTrades = true;
                meta.getStoredEnchants().forEach((enchant, level) -> {
                    int price = recipe.getIngredients().stream()
                            .filter(i -> i.getType() == Material.EMERALD)
                            .mapToInt(ItemStack::getAmount)
                            .sum();
                    createTrade("minecraft:" + enchant.getKey().getKey(), level, price, villager.getLocation(), description);
                });
            }
        }
        return hasEnchantTrades;
    }

    @Override
    public List<Trade> searchTrades(String enchantId) {
        List<Trade> trades = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Trades WHERE enchant_id_string = ?")) {
            stmt.setString(1, enchantId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String[] locParts = rs.getString("location").split(",");
                Location loc = new Location(Bukkit.getWorlds().get(0),
                        Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]));
                trades.add(new Trade(rs.getInt("id"), rs.getString("enchant_id_string"), rs.getInt("level"),
                        rs.getInt("price"), loc, rs.getString("description")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public List<Trade> listTrades() {
        List<Trade> trades = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Trades")) {
            while (rs.next()) {
                String[] locParts = rs.getString("location").split(",");
                Location loc = new Location(Bukkit.getWorlds().get(0),
                        Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]));
                trades.add(new Trade(rs.getInt("id"), rs.getString("enchant_id_string"), rs.getInt("level"),
                        rs.getInt("price"), loc, rs.getString("description")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    @Override
    public void deleteTrade(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM Trades WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void updateTradeDescription(int id, String description) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE Trades SET description = ? WHERE id = ?")) {
            stmt.setString(1, description);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
} 