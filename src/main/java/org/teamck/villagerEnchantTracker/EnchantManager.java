package org.teamck.villagerEnchantTracker;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnchantManager {
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
} 