package org.teamck.villagerEnchantTracker.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.teamck.villagerEnchantTracker.manager.EnchantmentManager;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MessageManager {
    private static MessageManager instance;
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> messages;
    private static final String DEFAULT_VERSION = "0.1.0";

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
        File localizationDir = new File(plugin.getDataFolder(), "localization");
        if (!localizationDir.exists()) {
            localizationDir.mkdirs();
        }
        // 1. Find all resource language files (from plugin jar)
        String[] defaultLangs = new String[]{"en", "ko"};
        for (String lang : defaultLangs) {
            File langFile = new File(localizationDir, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
                    if (resourceStream != null) {
                        Files.copy(resourceStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create " + lang + ".yml: " + e.getMessage());
                }
            }
        }
        // 2. Scan all *.yml files in localizationDir (including user-added languages)
        File[] langFiles = localizationDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles == null) return;
        for (File langFile : langFiles) {
            String lang = langFile.getName().replaceFirst("\\.yml$", "");
            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(langFile);
            String userVersion = userConfig.getString("version", "");
            String pluginVersion = null;
            // Try to get plugin's resource version for this lang
            try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
                if (resourceStream != null) {
                    File tempFile = File.createTempFile("lang_resource_" + lang, ".yml");
                    try (FileOutputStream tempOut = new FileOutputStream(tempFile)) {
                        int b;
                        while ((b = resourceStream.read()) != -1) {
                            tempOut.write(b);
                        }
                    }
                    YamlConfiguration resourceConfig = YamlConfiguration.loadConfiguration(tempFile);
                    pluginVersion = resourceConfig.getString("version", "");
                    tempFile.delete();
                }
            } catch (IOException ignored) {}
            // 1. If plugin version is missing, just load user file
            if (pluginVersion == null || pluginVersion.isEmpty()) {
                messages.put(lang, userConfig);
                checkConfigVersion(userConfig, langFile.getName());
                continue;
            }
            // 2. If plugin version == user version: do nothing (just load)
            if (pluginVersion.equals(userVersion)) {
                messages.put(lang, userConfig);
                checkConfigVersion(userConfig, langFile.getName());
                continue;
            }
            // 3. If plugin version > user version: backup and overwrite
            if (compareVersion(pluginVersion, userVersion) > 0) {
                // backup
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File backupFile = new File(localizationDir, lang + "_backup_" + timestamp + ".yml");
                if (!langFile.renameTo(backupFile)) {
                    plugin.getLogger().warning("Failed to backup " + lang + ".yml before overwrite.");
                }
                // overwrite
                try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
                    if (resourceStream != null) {
                        Files.copy(resourceStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to overwrite " + lang + ".yml: " + e.getMessage());
                }
                // reload
                YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                messages.put(lang, config);
                checkConfigVersion(config, langFile.getName());
                continue;
            }
            // 4. Otherwise (user version > plugin version): just load user file
            messages.put(lang, userConfig);
            checkConfigVersion(userConfig, langFile.getName());
        }
    }

    // Returns 1 if v1 > v2, 0 if equal, -1 if v1 < v2 (semantic version, fallback to string compare)
    private int compareVersion(String v1, String v2) {
        String[] a1 = v1.split("[.-]");
        String[] a2 = v2.split("[.-]");
        int len = Math.max(a1.length, a2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseIntOrZero(a1[i]) : 0;
            int n2 = i < a2.length ? parseIntOrZero(a2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }
    private int parseIntOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private void checkConfigVersion(YamlConfiguration config, String fileName) {
        String fileVersion = config.getString("version", DEFAULT_VERSION);
        String pluginVersion = plugin.getDescription().getVersion();
        
        if (!fileVersion.equals(pluginVersion)) {
            plugin.getLogger().warning("Version mismatch in " + fileName + ": " +
                "File version is " + fileVersion + " but plugin version is " + pluginVersion);
        }
    }

    // Fallback-aware message retrieval
    private String getMessageInternal(String key, String language) {
        YamlConfiguration config = messages.get(language);
        if (config != null && config.contains(key)) {
            return config.getString(key);
        }
        YamlConfiguration enConfig = messages.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getString(key);
        }
        return key;
    }

    public String getMessage(String key, Player player) {
        String language = getBaseLanguageCode(player.getLocale());
        return getMessageInternal(key, language);
    }

    public String getMessage(String key, org.bukkit.command.CommandSender sender) {
        if (sender instanceof Player player) {
            return getMessage(key, player);
        }
        return getMessageInternal(key, "en");
    }

    public String getEnchantName(String enchantId, String language) {
        // Remove minecraft: prefix if present
        String key = enchantId;
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return getMessageInternal("enchantments." + key, language);
    }

    public String getEnchantName(String enchantId, Player player) {
        return getEnchantName(enchantId, getBaseLanguageCode(player.getLocale()));
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
        if (EnchantmentManager.getEnchant(enchantId) != null) {
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

    public TextComponent createClickableMessage(String message, Location loc, String command, Player player) {
        TextComponent textComponent = new TextComponent(message);
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                command + " " + loc.getX() + " " + loc.getY() + " " + loc.getZ()));
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(getMessage("click_to_show_particles", player))}));
        return textComponent;
    }
} 