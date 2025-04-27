package org.teamck.minecraftLibrarianDatabase;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> messages;
    private String chatLanguage;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
        loadLanguages();
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
        return getMessage("enchantments." + enchantId.toLowerCase(), language);
    }

    public String getLocalEnchantName(String enchantId) {
        return getEnchantName(enchantId, chatLanguage);
    }
} 