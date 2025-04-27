package org.teamck.minecraftLibrarianDatabase;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class ParticleManager {
    private final JavaPlugin plugin;

    public ParticleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnParticles(Location loc) {
        List<Map<?, ?>> particleConfigs = plugin.getConfig().getMapList("particles");
        int duration = plugin.getConfig().getInt("particle-duration", 30);
        int interval = plugin.getConfig().getInt("particle-interval", 1);

        for (int i = 0; i < duration; i++) {
            final int tick = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Map<?, ?> particleConfig : particleConfigs) {
                    String type = (String) particleConfig.get("type");
                    int height = (int) particleConfig.get("height");
                    int count = (int) particleConfig.get("count");

                    try {
                        Particle particle = Particle.valueOf(type);
                        for (int y = 0; y <= height; y++) {
                            Location particleLoc = loc.clone().add(0, y, 0);
                            // Create particles with random offset and speed
                            double offsetX = (Math.random() - 0.5) * 0.5;
                            double offsetY = (Math.random() - 0.5) * 0.5;
                            double offsetZ = (Math.random() - 0.5) * 0.5;
                            double speed = Math.random() * 0.1;
                            loc.getWorld().spawnParticle(particle, particleLoc, count, offsetX, offsetY, offsetZ, speed);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid particle type: " + type);
                    }
                }
            }, tick * (interval * 20L));
        }
    }
} 