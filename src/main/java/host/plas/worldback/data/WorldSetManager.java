package host.plas.worldback.data;

import host.plas.worldback.WorldBack;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

public class WorldSetManager {
    @Getter
    private static ConcurrentSkipListSet<WorldSet> loadedWorldSets = new ConcurrentSkipListSet<>();

    public static WorldSet createWorldSet(String name) {
        WorldSet worldSet = new WorldSet(name);
        loadedWorldSets.add(worldSet);
        WorldBack.getMainConfig().saveWorldSet(worldSet);
        return worldSet;
    }

    public static Optional<WorldSet> getWorldSet(String name) {
        // First check loaded WorldSets
        Optional<WorldSet> loaded = loadedWorldSets.stream()
                .filter(ws -> ws.getName().equalsIgnoreCase(name))
                .findFirst();
        if (loaded.isPresent()) {
            return loaded;
        }

        // If not loaded, try loading from config
        return WorldBack.getMainConfig().getWorldSet(name).map(ws -> {
            loadedWorldSets.add(ws);
            return ws;
        });
    }

    public static Optional<WorldSet> getWorldSetByWorld(String worldName) {
        return getWorldSetByWorld(Bukkit.getWorld(worldName));
    }

    public static Optional<WorldSet> getWorldSetByWorld(World world) {
        if (world == null) return Optional.empty();

        // Check loaded WorldSets first
        Optional<WorldSet> found = loadedWorldSets.stream()
                .filter(ws -> ws.containsWorld(world))
                .findFirst();
        if (found.isPresent()) {
            return found;
        }

        // If not found, check all WorldSets from config
        List<WorldSet> allWorldSets = WorldBack.getMainConfig().getAllWorldSets();
        for (WorldSet worldSet : allWorldSets) {
            if (worldSet.containsWorld(world)) {
                loadedWorldSets.add(worldSet);
                return Optional.of(worldSet);
            }
        }

        return Optional.empty();
    }

    public static void saveWorldSet(WorldSet worldSet) {
        WorldBack.getMainConfig().saveWorldSet(worldSet);
    }

    public static void loadWorldSets() {
        List<WorldSet> worldSets = WorldBack.getMainConfig().getAllWorldSets();
        loadedWorldSets.clear();
        loadedWorldSets.addAll(worldSets);
    }

    public static boolean removeWorldSet(String name) {
        Optional<WorldSet> worldSet = getWorldSet(name);
        if (worldSet.isPresent()) {
            loadedWorldSets.remove(worldSet.get());
            // Remove from config by setting it to null or deleting the key
            try {
                java.io.File configFile = new java.io.File(WorldBack.getInstance().getDataFolder(), "config.yml");
                YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(configFile);
                yamlConfig.set("worldsets." + name, null);
                yamlConfig.save(configFile);
            } catch (Throwable e) {
                WorldBack.getInstance().logWarning("Failed to remove WorldSet " + name, e);
            }
            return true;
        }
        return false;
    }
}