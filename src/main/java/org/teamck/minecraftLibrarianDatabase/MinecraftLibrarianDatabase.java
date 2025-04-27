package org.teamck.minecraftLibrarianDatabase;

import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public final class MinecraftLibrarianDatabase extends JavaPlugin {
    private Database db;
    private DescriptionManager descManager;

    @Override
    public void onEnable() {
        try {
            // Initialize database and description manager
            this.db = new SQLiteDatabase(this);
            this.descManager = new DescriptionManager(this);

            // Register command
            LibrarianDBCommand command = new LibrarianDBCommand(db, descManager);
            getCommand("librariandb").setExecutor(command);
            getCommand("librariandb").setTabCompleter(command);
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
