package org.teamck.villagerEnchantTracker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class MessageManager {
    private static MessageManager instance;
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> messages;
    private String chatLanguage;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
        instance = this;
        loadLanguages();
    }

    public static MessageManager getInstance() {
        return instance;
    }

    private void loadLanguages() {
        // Load language settings from config
        chatLanguage = plugin.getConfig().getString("language.chat", "en");

        // Create localization directory
        File localizationDir = new File(plugin.getDataFolder(), "localization");
        if (!localizationDir.exists()) {
            localizationDir.mkdirs();
        }

        // Load each language file
        for (String lang : new String[]{"en", "ko"}) {
            File langFile = new File(localizationDir, lang + ".yml");
            if (!langFile.exists()) {
                plugin.saveResource("localization/" + lang + ".yml", false);
            }
            messages.put(lang, YamlConfiguration.loadConfiguration(langFile));
        }
    }

    public String getMessage(String key, String language) {
        YamlConfiguration config = messages.get(language);
        if (config == null) {
            config = messages.get("en"); // Use English as default
        }
        return config.getString(key, key);
    }

    public String getChatMessage(String key) {
        return getMessage(key, chatLanguage);
    }

    public String format(String key, Object... args) {
        String message = getChatMessage(key);
        return String.format(message, args);
    }

    public String getEnchantName(String enchantId, String language) {
        // Remove minecraft: prefix if present
        String key = enchantId;
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return getMessage("enchantments." + key, language);
    }

    public String getLocalEnchantName(String enchantId) {
        return getEnchantName(enchantId, chatLanguage);
    }

    public String getBaseLanguageCode(String fullLocale) {
        return fullLocale.split("_")[0];
    }

    public String getEnchantIdFromLocalName(String localName, String language) {
        plugin.getLogger().info("Searching for enchantment ID for name: " + localName + " in language: " + language);
        
        // First try to find enchantment by localized name
        String baseLanguage = getBaseLanguageCode(language);
        YamlConfiguration config = messages.get(baseLanguage);
        if (config == null) {
            plugin.getLogger().info("Language " + baseLanguage + " not found, using English");
            config = messages.get("en"); // Use English as default
        }

        // Get all enchantment entries
        if (config.contains("enchantments")) {
            plugin.getLogger().info("Found enchantments section in config");
            for (String key : config.getConfigurationSection("enchantments").getKeys(false)) {
                String value = config.getString("enchantments." + key);
                plugin.getLogger().info("Checking key: " + key + " with value: " + value);
                if (value != null && value.equals(localName)) {
                    plugin.getLogger().info("Found matching enchantment: " + key);
                    return "minecraft:" + key;
                }
            }
        } else {
            plugin.getLogger().info("No enchantments section found in config");
        }
        
        // If not found by localized name, try as enchantment ID
        String enchantId = localName;
        // Remove enchantments. prefix if present
        if (enchantId.startsWith("enchantments.")) {
            enchantId = enchantId.substring("enchantments.".length());
        }
        if (!enchantId.startsWith("minecraft:")) {
            enchantId = "minecraft:" + enchantId;
        }
        if (EnchantManager.getEnchant(enchantId) != null) {
            plugin.getLogger().info("Found valid enchantment ID: " + enchantId);
            return enchantId;
        }
        
        plugin.getLogger().info("No matching enchantment found");
        return null;
    }

    public List<String> getEnchantNames(String language) {
        List<String> names = new ArrayList<>();
        String baseLanguage = getBaseLanguageCode(language);
        YamlConfiguration config = messages.get(baseLanguage);
        if (config == null) {
            config = messages.get("en");
        }

        if (config.contains("enchantments")) {
            for (String key : config.getConfigurationSection("enchantments").getKeys(false)) {
                String value = config.getString("enchantments." + key);
                if (value != null) {
                    names.add(value);
                }
            }
        }
        return names;
    }

    public TextComponent createClickableMessage(String message, Location loc, String command) {
        TextComponent textComponent = new TextComponent(message);
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                command + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(getChatMessage("click_to_show_particles"))}));
        return textComponent;
    }
} 