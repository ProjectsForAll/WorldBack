package host.plas.worldback.data;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class WorldSet {
    private String name;
    private ConcurrentSkipListSet<String> worldNames;
    private Location spawnpoint; // nullable

    public WorldSet(String name) {
        this.name = name;
        this.worldNames = new ConcurrentSkipListSet<>();
        this.spawnpoint = null;
    }

    public boolean addWorld(String worldName) {
        return worldNames.add(worldName);
    }

    public boolean removeWorld(String worldName) {
        return worldNames.remove(worldName);
    }

    public boolean containsWorld(String worldName) {
        return worldNames.contains(worldName);
    }

    public boolean containsWorld(World world) {
        if (world == null) return false;
        return containsWorld(world.getName());
    }

    public void setSpawnpoint(Location spawnpoint) {
        this.spawnpoint = spawnpoint;
    }

    public boolean hasSpawnpoint() {
        return spawnpoint != null;
    }
}