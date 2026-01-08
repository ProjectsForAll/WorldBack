package host.plas.worldback.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.worldback.WorldBack;
import host.plas.worldback.data.WorldSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;


public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", WorldBack.getInstance(), false);
    }

    @Override
    public void init() {
        getIgnoredWorlds();
    }

    public ConcurrentSkipListSet<String> getIgnoredWorlds() {
        reloadResource();

        return new ConcurrentSkipListSet<>(getOrSetDefault("ignored-worlds", new ArrayList<>()));
    }

    public Location getSpawnLocation(World world) {
        Location location = world.getSpawnLocation();

        String key = "world-spawns." + world.getName();
//        if (getResource().contains(key)) {
//            double x = getOrSetDefault(key + ".x", location.getX());
//            double y = getOrSetDefault(key + ".y", location.getY());
//            double z = getOrSetDefault(key + ".z", location.getZ());
//
//            float yaw = getOrSetDefault(key + ".yaw", location.getYaw());
//            float pitch = getOrSetDefault(key + ".pitch", location.getPitch());
//
//            return new Location(world, x, y, z, yaw, pitch);
//        } else {
//            return location;
//        }

        double x = getOrSetDefault(key + ".x", location.getX());
        double y = getOrSetDefault(key + ".y", location.getY());
        double z = getOrSetDefault(key + ".z", location.getZ());

        float yaw = getOrSetDefault(key + ".yaw", location.getYaw());
        float pitch = getOrSetDefault(key + ".pitch", location.getPitch());

        return new Location(world, x, y, z, yaw, pitch);
    }

    public Optional<WorldSet> getWorldSet(String name) {
        reloadResource();

        String baseKey = "worldsets." + name;
        if (!getResource().contains(baseKey)) {
            return Optional.empty();
        }

        WorldSet worldSet = new WorldSet(name);

        // Load worlds
        List<String> worldNames = getOrSetDefault(baseKey + ".worlds", new ArrayList<>());
        worldSet.getWorldNames().addAll(worldNames);

        // Load spawnpoint if exists
        if (getResource().contains(baseKey + ".spawnpoint")) {
            String spawnWorldName = getOrSetDefault(baseKey + ".spawnpoint.world", "");
            World spawnWorld = Bukkit.getWorld(spawnWorldName);
            if (spawnWorld != null) {
                double x = getOrSetDefault(baseKey + ".spawnpoint.x", 0.0);
                double y = getOrSetDefault(baseKey + ".spawnpoint.y", 64.0);
                double z = getOrSetDefault(baseKey + ".spawnpoint.z", 0.0);
                float yaw = getOrSetDefault(baseKey + ".spawnpoint.yaw", 0.0f);
                float pitch = getOrSetDefault(baseKey + ".spawnpoint.pitch", 0.0f);
                worldSet.setSpawnpoint(new Location(spawnWorld, x, y, z, yaw, pitch));
            }
        }

        return Optional.of(worldSet);
    }

    public void saveWorldSet(WorldSet worldSet) {
        reloadResource();

        String baseKey = "worldsets." + worldSet.getName();

        // Save worlds - use getOrSetDefault which will set if not exists
        getOrSetDefault(baseKey + ".worlds", new ArrayList<>(worldSet.getWorldNames()));
        // Update with actual values
        getResource().set(baseKey + ".worlds", new ArrayList<>(worldSet.getWorldNames()));

        // Save spawnpoint if exists
        if (worldSet.hasSpawnpoint()) {
            Location spawnpoint = worldSet.getSpawnpoint();
            getResource().set(baseKey + ".spawnpoint.world", spawnpoint.getWorld().getName());
            getResource().set(baseKey + ".spawnpoint.x", spawnpoint.getX());
            getResource().set(baseKey + ".spawnpoint.y", spawnpoint.getY());
            getResource().set(baseKey + ".spawnpoint.z", spawnpoint.getZ());
            getResource().set(baseKey + ".spawnpoint.yaw", spawnpoint.getYaw());
            getResource().set(baseKey + ".spawnpoint.pitch", spawnpoint.getPitch());
        } else {
            // Remove spawnpoint if it doesn't exist
            getResource().set(baseKey + ".spawnpoint", null);
        }

        try {
            java.io.File configFile = new java.io.File(WorldBack.getInstance().getDataFolder(), "config.yml");
            YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Set the worlds list
            yamlConfig.set(baseKey + ".worlds", new ArrayList<>(worldSet.getWorldNames()));
            
            // Set spawnpoint if exists
            if (worldSet.hasSpawnpoint()) {
                Location spawnpoint = worldSet.getSpawnpoint();
                yamlConfig.set(baseKey + ".spawnpoint.world", spawnpoint.getWorld().getName());
                yamlConfig.set(baseKey + ".spawnpoint.x", spawnpoint.getX());
                yamlConfig.set(baseKey + ".spawnpoint.y", spawnpoint.getY());
                yamlConfig.set(baseKey + ".spawnpoint.z", spawnpoint.getZ());
                yamlConfig.set(baseKey + ".spawnpoint.yaw", spawnpoint.getYaw());
                yamlConfig.set(baseKey + ".spawnpoint.pitch", spawnpoint.getPitch());
            } else {
                yamlConfig.set(baseKey + ".spawnpoint", null);
            }
            
            yamlConfig.save(configFile);
        } catch (Throwable e) {
            WorldBack.getInstance().logWarning("Failed to save WorldSet " + worldSet.getName(), e);
        }
    }

    public List<WorldSet> getAllWorldSets() {
        reloadResource();

        List<WorldSet> worldSets = new ArrayList<>();

        if (!getResource().contains("worldsets")) {
            return worldSets;
        }

        // Get all WorldSet names from config
        Object worldsetsObj = getResource().get("worldsets");
        if (worldsetsObj instanceof org.bukkit.configuration.ConfigurationSection) {
            org.bukkit.configuration.ConfigurationSection worldsetsSection = (org.bukkit.configuration.ConfigurationSection) worldsetsObj;
            for (String name : worldsetsSection.getKeys(false)) {
                getWorldSet(name).ifPresent(worldSets::add);
            }
        }

        return worldSets;
    }
}
