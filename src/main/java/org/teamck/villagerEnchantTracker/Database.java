package org.teamck.villagerEnchantTracker;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

import java.util.List;

public interface Database {
    void init();
    void createTrade(String enchantId, int level, int price, Location location, String description);
    boolean addVillagerTrades(Villager villager, String description);
    List<Trade> searchTrades(String enchantId);
    List<Trade> listTrades();
    void deleteTrade(int id);
    boolean updateTradeDescription(int id, String description);
} 