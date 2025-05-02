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
        return Enchantment.getByKey(NamespacedKey.minecraft(normalizeEnchantmentId(enchantId).replace("minecraft:", "")));
    }

    public static boolean isValidLevel(Enchantment enchant, int level) {
        return level > 0 && level <= enchant.getMaxLevel();
    }

    public static List<String> getAllEnchantIds() {
        return Arrays.stream(Enchantment.values())
                .map(enchant -> normalizeEnchantmentId("minecraft:" + enchant.getKey().getKey()))
                .collect(Collectors.toList());
    }

    // EVT 통합 관련 메서드
    public static Set<EnchantmentInfo> getAllMaxLevelEnchantments() {
        Set<EnchantmentInfo> maxLevelEnchantments = new HashSet<>();
        
        for (Enchantment enchantment : Enchantment.values()) {
            String name = "minecraft:" + enchantment.getKey().getKey();
            int maxLevel = enchantment.getMaxLevel();
            if (enchantment.isTradeable()) {
                maxLevelEnchantments.add(new EnchantmentInfo(name, maxLevel, null));
            }
        }
        
        return maxLevelEnchantments;
    }
    
    public static Set<EnchantmentInfo> getVillagerEnchantments(Villager villager) {
        Set<EnchantmentInfo> enchantments = new HashSet<>();
        
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
                        
                        String name = normalizeEnchantmentId("minecraft:" + enchantment.getKey().getKey());
                        enchantments.add(new EnchantmentInfo(name, level, price));
                        plugin.getLogger().info("Added enchantment: " + name + " level " + level + " price " + price);
                    });
                } else {
                    plugin.getLogger().warning("ItemMeta is not EnchantmentStorageMeta");
                }
            }
        });
        
        plugin.getLogger().info("Total enchantments found for villager: " + enchantments.size());
        return enchantments;
    }

    public static EnchantmentInfo parseEnchantmentString(String enchantString) {
        String[] parts = enchantString.split(" ");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid enchantment string format: " + enchantString);
        }
        Integer price = parts.length == 3 ? Integer.parseInt(parts[2]) : null;
        return new EnchantmentInfo(parts[0], Integer.parseInt(parts[1]), price);
    }

    public static String formatEnchantmentInfo(EnchantmentInfo info) {
        return info.getPrice() != null ? 
            String.format("%s %d %d", info.getId(), info.getLevel(), info.getPrice()) :
            String.format("%s %d", info.getId(), info.getLevel());
    }

    public static class EnchantmentInfo {
        private final String id;
        private final int level;
        private final Integer price;  // nullable

        public EnchantmentInfo(String id, int level, Integer price) {
            this.id = EnchantmentManager.normalizeEnchantmentId(id);
            this.level = level;
            this.price = price;
        }

        public String getId() { return id; }
        public int getLevel() { return level; }
        public Integer getPrice() { return price; }

        /**
         * 두 인챈트의 기본적인 동등성을 비교합니다 (ID만 비교).
         * 새로운 인챈트 필터링에 사용됩니다.
         */
        public boolean hasSameEnchantment(EnchantmentInfo other) {
            return Objects.equals(id, other.id);
        }

        /**
         * 한 인챈트가 다른 인챈트를 대체할 수 있는지 확인합니다.
         * 같은 종류의 인챈트이고 레벨이 같거나 더 높으면 true를 반환합니다.
         */
        public boolean canReplace(EnchantmentInfo other) {
            return Objects.equals(id, other.id) && level >= other.level;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnchantmentInfo that = (EnchantmentInfo) o;
            // ID와 레벨만 비교하고 가격은 무시
            return level == that.level && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            // equals와 일관성을 위해 price는 제외
            return Objects.hash(id, level);
        }

        @Override
        public String toString() {
            return formatEnchantmentInfo(this);
        }
    }
    public static Set<EnchantmentInfo> filterNewEnchants(Set<EnchantmentInfo> allMaxEnchants, Set<EnchantmentInfo> existingEnchants) {
        Set<EnchantmentInfo> newEnchants = new HashSet<>();
        
        for (EnchantmentInfo maxEnchant : allMaxEnchants) {
            boolean shouldAdd = true;
            
            for (EnchantmentInfo existing : existingEnchants) {
                if (existing.canReplace(maxEnchant)) {
                    shouldAdd = false;
                    break;
                }
            }
            
            if (shouldAdd) {
                newEnchants.add(maxEnchant);
            }
        }
        
        return newEnchants;
    }

    /**
     * Normalize any enchantment id string to the format 'minecraft:xxx'.
     * Accepts 'enchantments.minecraft.fortune', 'minecraft.fortune', 'fortune', etc.
     * Handles multiple 'minecraft:' prefixes by stripping them all before adding one back.
     */
    public static String normalizeEnchantmentId(String id) {
        if (id == null) return null;
        String key = id.trim();
        if (key.startsWith("enchantments.")) {
            key = key.substring("enchantments.".length());
        }
        // Repeatedly strip 'minecraft:' prefix to handle cases like 'minecraft:minecraft:breach'
        while (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        key = key.toLowerCase();
        return "minecraft:" + key;
    }
} 