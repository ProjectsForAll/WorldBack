package host.plas.worldback.data;

import gg.drak.thebase.objects.Identifiable;
import host.plas.bou.scheduling.TaskManager;
import host.plas.worldback.WorldBack;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter @Setter
public class PlayerData implements Identifiable {
    private String identifier;

    private String name;
    private ConcurrentSkipListMap<String, Location> worldPlaces;

    private AtomicBoolean fullyLoaded;

    // Maps WorldSet name to the last world name the player was in for that WorldSet
    private ConcurrentSkipListMap<String, String> lastWorldPerWorldSet;

    public PlayerData(String identifier, String name) {
        this.identifier = identifier;
        this.name = name;
        this.worldPlaces = new ConcurrentSkipListMap<>();
        this.lastWorldPerWorldSet = new ConcurrentSkipListMap<>();
        this.fullyLoaded = new AtomicBoolean(false);
    }

    public PlayerData(Player player) {
        this(player.getUniqueId().toString(), player.getName());
    }

    public PlayerData(String uuid) {
        this(uuid, "");
    }

    public Optional<Player> asPlayer() {
        try {
            return Optional.ofNullable(Bukkit.getPlayer(UUID.fromString(identifier)));
        } catch (Throwable e) {
            WorldBack.getInstance().logWarning("Failed to get player from identifier: " + identifier, e);

            return Optional.empty();
        }
    }

    public Optional<OfflinePlayer> asOfflinePlayer() {
        try {
            return Optional.of(Bukkit.getOfflinePlayer(UUID.fromString(identifier)));
        } catch (Throwable e) {
            WorldBack.getInstance().logWarning("Failed to get offline player from identifier: " + identifier, e);

            return Optional.empty();
        }
    }

    public boolean isOnline() {
        return asPlayer().isPresent();
    }

    public void load() {
        PlayerManager.loadPlayer(this);
    }

    public void unload() {
        PlayerManager.unloadPlayer(this);
    }

    public void save() {
        PlayerManager.savePlayer(this);
    }

    public void save(boolean async) {
        PlayerManager.savePlayer(this, async);
    }

    public void augment(CompletableFuture<Optional<PlayerData>> future, boolean isGet) {
        fullyLoaded.set(false);

        future.whenComplete((data, error) -> {
            if (error != null) {
                WorldBack.getInstance().logWarning("Failed to augment player data", error);

                this.fullyLoaded.set(true);
                return;
            }

            if (data.isPresent()) {
                PlayerData newData = data.get();

                this.name = newData.getName();
                this.worldPlaces = newData.getWorldPlaces();
                this.lastWorldPerWorldSet = newData.getLastWorldPerWorldSet();
            } else {
                if (! isGet) {
                    this.save();
                }
            }

            this.fullyLoaded.set(true);
        });
    }

    public boolean isFullyLoaded() {
        return fullyLoaded.get();
    }

    public void saveAndUnload(boolean async) {
        save(async);
        unload();
    }

    public void saveAndUnload() {
        saveAndUnload(true);
    }

    public PlayerData waitUntilFullyLoaded() {
        while (! isFullyLoaded()) {
            Thread.onSpinWait();
        }
        return this;
    }

    public void putWorldLoc(Location location) {
        if (location == null || location.getWorld() == null) return;

        worldPlaces.put(location.getWorld().getName(), location);
    }

    public Location getWorldLoc(String worldName) {
        return worldPlaces.get(worldName);
    }

    public Location getWorldLoc(World world) {
        if (world == null) return null;

        return getWorldLoc(world.getName());
    }

    public void teleportWorldLoc(World world) {
        asPlayer().ifPresent(player -> {
//            World playerWorld = player.getWorld();
//            if (playerWorld == null) return;
            if (WorldBack.getMainConfig().getIgnoredWorlds().contains(world.getName())) {
                return;
            }

            Location loc = getWorldLoc(world);
            if (loc != null) {
                TaskManager.teleport(player, loc);
            } else {
                TaskManager.teleport(player, WorldBack.getMainConfig().getSpawnLocation(world));
            }
        });
    }

    /**
     * Sets the last world the player was in for a specific WorldSet
     */
    public void setLastWorldForWorldSet(String worldSetName, String worldName) {
        if (worldSetName != null && worldName != null) {
            lastWorldPerWorldSet.put(worldSetName, worldName);
        }
    }

    /**
     * Gets the last world the player was in for a specific WorldSet
     */
    public String getLastWorldForWorldSet(String worldSetName) {
        return lastWorldPerWorldSet.get(worldSetName);
    }
}
