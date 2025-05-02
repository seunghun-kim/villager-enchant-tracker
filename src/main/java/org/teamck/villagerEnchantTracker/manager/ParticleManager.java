package org.teamck.villagerEnchantTracker.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ParticleManager {
    private final JavaPlugin plugin;
    private final Map<Player, Set<BukkitTask>> activeTasks;
    private final Map<Player, Location> lineEndpoints;

    public ParticleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashMap<>();
        this.lineEndpoints = new HashMap<>();
    }

    public void cancelAllParticles(Player player) {
        Set<BukkitTask> tasks = activeTasks.get(player);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
            tasks.clear();
            activeTasks.remove(player);
        }
        lineEndpoints.remove(player);
    }

    private void removeTask(Player player, BukkitTask task) {
        Set<BukkitTask> tasks = activeTasks.get(player);
        if (tasks != null) {
            tasks.remove(task);
            if (tasks.isEmpty()) {
                activeTasks.remove(player);
            }
        }
    }

    public void spawnParticles(Location loc, Player player, boolean cancelExisting) {
        if (cancelExisting) {
            cancelAllParticles(player);
        }

        int duration = plugin.getConfig().getInt("particle-duration", 30);
        // Pillar effect
        if (plugin.getConfig().getBoolean("particle-effects.show-pillar", true)) {
            List<Map<?, ?>> particleConfigs = plugin.getConfig().getMapList("particles");
            int interval = plugin.getConfig().getInt("particle-interval", 1);
            BukkitTask pillarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                for (Map<?, ?> particleConfig : particleConfigs) {
                    String type = (String) particleConfig.get("type");
                    int height = (int) particleConfig.get("height");
                    int count = (int) particleConfig.get("count");
                    try {
                        Particle particle = Particle.valueOf(type);
                        for (int y = 0; y <= height; y++) {
                            spawnParticleWithRandomness(player, particle, loc.clone().add(0, y, 0), count);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Wrong Particle Type: " + type);
                    }
                }
            }, 0L, interval * 20L);
            activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pillarTask);
            scheduleCancel(player, pillarTask, duration, false);
        }

        // Line effect (draw from player to loc)
        if (plugin.getConfig().getBoolean("particle-effects.show-line", true)) {
            double lineUpdateInterval = plugin.getConfig().getDouble("particle-effects.line-update-interval", 0.1);
            int points = plugin.getConfig().getInt("particle-effects.line-points", 20);
            lineEndpoints.put(player, loc);
            BukkitTask lineTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Location currentStart = player.getLocation();
                Location currentEnd = lineEndpoints.get(player);
                if (currentEnd == null) return;
                for (int i = 0; i <= points; i++) {
                    double ratio = (double) i / points;
                    double x = currentStart.getX() + (currentEnd.getX() - currentStart.getX()) * ratio;
                    double y = currentStart.getY() + (currentEnd.getY() - currentStart.getY()) * ratio;
                    double z = currentStart.getZ() + (currentEnd.getZ() - currentStart.getZ()) * ratio;
                    player.spawnParticle(Particle.END_ROD, new Location(currentStart.getWorld(), x, y, z), 1, 0, 0, 0, 0);
                }
            }, 0L, (long)(lineUpdateInterval * 20L));
            activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(lineTask);
            scheduleCancel(player, lineTask, duration, true);
        }
    }

    // Helper to spawn a particle with random offset and speed
    private void spawnParticleWithRandomness(Player player, Particle particle, Location loc, int count) {
        double offsetX = (Math.random() - 0.5) * 0.5;
        double offsetY = (Math.random() - 0.5) * 0.5;
        double offsetZ = (Math.random() - 0.5) * 0.5;
        double speed = Math.random() * 0.1;
        player.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
    }

    // Helper to schedule task cancellation and cleanup
    private void scheduleCancel(Player player, BukkitTask task, int duration, boolean removeLineEndpoint) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            removeTask(player, task);
            if (removeLineEndpoint) {
                lineEndpoints.remove(player);
            }
        }, duration * 20L);
    }

    public void handleParticleCommand(Player player, String[] args) {
        if (args.length < 4) {
            return;
        }

        try {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            Location loc = new Location(player.getWorld(), x, y, z);
            spawnParticles(loc, player, true);
        } catch (NumberFormatException e) {
            // Ignore invalid coordinates
        }
    }
} 