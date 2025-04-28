package org.teamck.villagerEnchantTracker;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;

public class VillagerTradeSearcher {
    public static List<Trade> searchNearbyVillagerTrades(Player player, String enchantId, double radius) {
        List<Trade> trades = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager) {
                for (MerchantRecipe recipe : villager.getRecipes()) {
                    ItemStack result = recipe.getResult();
                    if (result.getType() == Material.ENCHANTED_BOOK && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                        meta.getStoredEnchants().forEach((enchant, level) -> {
                            if (("minecraft:" + enchant.getKey().getKey()).equals(enchantId)) {
                                int price = recipe.getIngredients().stream()
                                        .filter(i -> i.getType() == Material.EMERALD)
                                        .mapToInt(ItemStack::getAmount)
                                        .sum();
                                trades.add(new Trade(0, enchantId, level, price, villager.getLocation(), ""));
                            }
                        });
                    }
                }
            }
        }
        return trades;
    }
} 