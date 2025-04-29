package org.teamck.villagerEnchantTracker;

import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.commands.EVTIntegrationCommand;
import java.sql.SQLException;

public final class VillagerEnchantTracker extends JavaPlugin {
    private Database db;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        try {
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            // Initialize database and message manager
            this.db = new SQLiteDatabase(this);
            this.messageManager = new MessageManager(this);

            // Register commands
            LibrarianDBCommand librarianCommand = new LibrarianDBCommand(db, messageManager, this);
            getCommand("librariandb").setExecutor(librarianCommand);
            getCommand("librariandb").setTabCompleter(librarianCommand);

            FindVillagerCommand findVillagerCommand = new FindVillagerCommand(messageManager, this, db);
            getCommand("findvillager").setExecutor(findVillagerCommand);
            getCommand("findvillager").setTabCompleter(findVillagerCommand);

            RegionCommand regionCommand = new RegionCommand(db, messageManager, this);
            getCommand("villagerregion").setExecutor(regionCommand);
            getCommand("villagerregion").setTabCompleter(regionCommand);

            // Register EVT integration command
            EVTIntegrationCommand evtCommand = new EVTIntegrationCommand(this, db);
            getCommand("evtintegration").setExecutor(evtCommand);
            getCommand("evtintegration").setTabCompleter(evtCommand);
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
} 