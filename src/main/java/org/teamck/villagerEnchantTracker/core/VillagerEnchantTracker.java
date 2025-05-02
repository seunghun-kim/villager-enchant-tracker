package org.teamck.villagerEnchantTracker.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.commands.FindVillagerCommand;
import org.teamck.villagerEnchantTracker.commands.VETRegionCommand;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.database.SQLiteDatabase;
import org.teamck.villagerEnchantTracker.manager.MessageManager;
import org.teamck.villagerEnchantTracker.commands.VETTradeCommand;
import org.teamck.villagerEnchantTracker.commands.VETEVTCommand;
import org.teamck.villagerEnchantTracker.commands.VETCommand;
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

            // Create command handlers
            VETTradeCommand librarianCommand = new VETTradeCommand(db, messageManager, this);
            VETRegionCommand regionCommand = new VETRegionCommand(db, messageManager, this);
            VETEVTCommand evtCommand = new VETEVTCommand(this, db);

            // Register main VET command
            VETCommand vetCommand = new VETCommand(this, librarianCommand, regionCommand, evtCommand);
            getCommand("vet").setExecutor(vetCommand);
            getCommand("vet").setTabCompleter(vetCommand);

            // Register findvillager command separately
            FindVillagerCommand findVillagerCommand = new FindVillagerCommand(messageManager, this, db);
            getCommand("findvillager").setExecutor(findVillagerCommand);
            getCommand("findvillager").setTabCompleter(findVillagerCommand);

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