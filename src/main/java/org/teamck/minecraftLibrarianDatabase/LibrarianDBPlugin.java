package org.teamck.minecraftLibrarianDatabase;

import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public class LibrarianDBPlugin extends JavaPlugin {
    private Database database;
    private DescriptionManager descManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            database = new SQLiteDatabase(this);
            database.init();
        } catch (SQLException e) {
            getLogger().severe("데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        descManager = new DescriptionManager(this);
        getCommand("librariandb").setExecutor(new LibrarianDBCommand(database, descManager, this));
        getLogger().info("LibrarianDB 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (database instanceof SQLiteDatabase) {
            try {
                ((SQLiteDatabase) database).connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        getLogger().info("LibrarianDB 플러그인이 비활성화되었습니다.");
    }
}