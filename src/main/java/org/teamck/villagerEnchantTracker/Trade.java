package org.teamck.villagerEnchantTracker;

import org.bukkit.Location;

public class Trade {
    private final int id;
    private final String enchantId;
    private final int level;
    private final int price;
    private final Location location;
    private final String description;
    private final String regionName;

    public Trade(int id, String enchantId, int level, int price, Location location, String description) {
        this(id, enchantId, level, price, location, description, null);
    }

    public Trade(int id, String enchantId, int level, int price, Location location, String description, String regionName) {
        this.id = id;
        this.enchantId = enchantId;
        this.level = level;
        this.price = price;
        this.location = location;
        this.description = description;
        this.regionName = regionName;
    }

    public int getId() { return id; }
    public String getEnchantId() { return enchantId; }
    public int getLevel() { return level; }
    public int getPrice() { return price; }
    public Location getLocation() { return location; }
    public String getDescription() { return description; }
    public String getRegionName() { return regionName; }
} 