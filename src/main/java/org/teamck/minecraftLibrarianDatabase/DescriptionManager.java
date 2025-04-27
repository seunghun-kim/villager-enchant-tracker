package org.teamck.minecraftLibrarianDatabase;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class DescriptionManager {
    private final Map<String, Map<String, Map<String, String>>> descriptions = new HashMap<>();
    private final JavaPlugin plugin;

    public DescriptionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "descriptions");
        if (!folder.exists()) folder.mkdirs();
        saveDefaultDescriptions();
        for (File file : folder.listFiles(f -> f.getName().endsWith(".json"))) {
            loadDescriptions(file);
        }
    }

    private void saveDefaultDescriptions() {
        File file = new File(plugin.getDataFolder(), "descriptions/ko.json");
        if (!file.exists()) {
            plugin.saveResource("descriptions/ko.json", false);
        }
    }

    private void loadDescriptions(File file) {
        try (Reader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
            Map<String, Map<String, String>> data = gson.fromJson(reader, type);
            descriptions.put(file.getName().replace(".json", ""), data);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load descriptions from " + file.getName());
        }
    }

    public String getLocalName(String enchantId, String lang) {
        Map<String, Map<String, String>> langData = descriptions.getOrDefault(lang, new HashMap<>());
        Map<String, String> enchantData = langData.getOrDefault(enchantId, new HashMap<>());
        return enchantData.getOrDefault("local_name", enchantId);
    }

    public String getDescription(String enchantId, String lang) {
        Map<String, Map<String, String>> langData = descriptions.getOrDefault(lang, new HashMap<>());
        Map<String, String> enchantData = langData.getOrDefault(enchantId, new HashMap<>());
        return enchantData.getOrDefault("description", "");
    }
}