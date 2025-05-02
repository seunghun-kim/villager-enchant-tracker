package org.teamck.villagerEnchantTracker.database;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;

import java.util.List;

public interface Database {
    void init();
    boolean addTrade(Trade trade);
    List<Trade> searchTrades(String enchantId);
    List<Trade> listTrades();
    void deleteTrade(int id);
    boolean updateTradeDescription(int id, String description);
    
    // Region management methods
    int createRegion(String name, Location min, Location max);
    boolean deleteRegion(int id);
    List<VillagerRegion> listRegions();
    VillagerRegion getRegion(int id);
    VillagerRegion getRegionByName(String name);
    List<Trade> getTradesInRegion(int regionId);
    List<Trade> getTradesByVillager(String villagerUuid);
    boolean updateRegionName(int id, String newName);
} 