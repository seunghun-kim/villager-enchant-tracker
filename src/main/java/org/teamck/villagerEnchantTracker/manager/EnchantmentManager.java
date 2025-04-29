package org.teamck.villagerEnchantTracker.manager;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.core.VillagerEnchantTracker;
import java.util.*;
import java.util.stream.Collectors;

public class EnchantmentManager {
    private static final VillagerEnchantTracker plugin = JavaPlugin.getPlugin(VillagerEnchantTracker.class);

    // 기본 Enchantment 관리 메서드
    public static Enchantment getEnchant(String enchantId) {
        return Enchantment.getByKey(NamespacedKey.minecraft(enchantId.replace("minecraft:", "")));
    }

    public static boolean isValidLevel(Enchantment enchant, int level) {
        return level > 0 && level <= enchant.getMaxLevel();
    }

    public static List<String> getAllEnchantIds() {
        return Arrays.stream(Enchantment.values())
                .map(enchant -> "minecraft:" + enchant.getKey().getKey())
                .collect(Collectors.toList());
    }

    // EVT 통합 관련 메서드
    public static Set<String> getAllMaxLevelEnchantments() {
        Set<String> maxLevelEnchantments = new HashSet<>();
        
        for (Enchantment enchantment : Enchantment.values()) {
            String name = "minecraft:" + enchantment.getKey().getKey();
            int maxLevel = enchantment.getMaxLevel();
            
            // Skip enchantments that can't be obtained from villagers
            if (enchantment.isCursed() || enchantment.isDiscoverable()) {
                continue;
            }
            
            maxLevelEnchantments.add(name + " " + maxLevel);
        }
        
        return maxLevelEnchantments;
    }
    
    public static Set<String> getVillagerEnchantments(Villager villager) {
        Set<String> enchantments = new HashSet<>();
        
        plugin.getLogger().info("Checking trades for villager at: " + villager.getLocation());
        plugin.getLogger().info("Number of recipes: " + villager.getRecipes().size());
        
        villager.getRecipes().forEach(recipe -> {
            ItemStack result = recipe.getResult();
            plugin.getLogger().info("Checking recipe result: " + result.getType());
            
            if (result.getType() == Material.ENCHANTED_BOOK) {
                plugin.getLogger().info("Found enchanted book");
                if (result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                    plugin.getLogger().info("Number of stored enchants: " + meta.getStoredEnchants().size());
                    meta.getStoredEnchants().forEach((enchantment, level) -> {
                        // Calculate the price from recipe ingredients
                        int price = recipe.getIngredients().stream()
                                .filter(i -> i.getType() == Material.EMERALD)
                                .mapToInt(ItemStack::getAmount)
                                .sum();
                        
                        String name = "minecraft:" + enchantment.getKey().getKey();
                        String fullEnchant = String.format("%s %d %d", name, level, price);
                        enchantments.add(fullEnchant);
                        plugin.getLogger().info("Added enchantment: " + fullEnchant + " with price: " + price);
                    });
                } else {
                    plugin.getLogger().warning("ItemMeta is not EnchantmentStorageMeta");
                }
            }
        });
        
        plugin.getLogger().info("Total enchantments found for villager: " + enchantments.size());
        return enchantments;
    }
} 