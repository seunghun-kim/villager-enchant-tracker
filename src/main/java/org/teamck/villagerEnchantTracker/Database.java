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
    
    // Region management methods
    int createRegion(String name, Location min, Location max);
    void deleteRegion(int id);
    List<VillagerRegion> listRegions();
    VillagerRegion getRegion(int id);
    VillagerRegion getRegionByName(String name);
    List<Trade> getTradesInRegion(int regionId);
} 