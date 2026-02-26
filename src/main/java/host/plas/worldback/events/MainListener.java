package host.plas.worldback.events;

import gg.drak.thebase.async.AsyncUtils;
import host.plas.bou.scheduling.TaskManager;
import host.plas.worldback.WorldBack;
import host.plas.worldback.data.PlayerData;
import host.plas.worldback.data.PlayerManager;
import host.plas.worldback.data.WorldSet;
import host.plas.worldback.data.WorldSetManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;

public class MainListener extends AbstractConglomerate {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();

            // Check if player's current world belongs to a WorldSet
            World currentWorld = player.getWorld();
            if (currentWorld != null) {
                Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(currentWorld);
                if (worldSetOpt.isPresent()) {
                    WorldSet worldSet = worldSetOpt.get();
                    
                    // Check if player has a last world for this WorldSet
                    String lastWorldName = data.getLastWorldForWorldSet(worldSet.getName());
                    
                    // Determine which world to teleport to
                    World targetWorld = null;
                    Location targetLoc = null;
                    
                    if (lastWorldName != null) {
                        World lastWorld = Bukkit.getWorld(lastWorldName);
                        if (lastWorld != null && worldSet.containsWorld(lastWorld)) {
                            targetLoc = data.getWorldLoc(lastWorld);
                            if (targetLoc != null) {
                                targetWorld = lastWorld;
                            }
                        }
                    }
                    
                    // If no location in last world, check current world for saved location
                    if (targetLoc == null) {
                        targetLoc = data.getWorldLoc(currentWorld);
                        if (targetLoc != null) {
                            targetWorld = currentWorld;
                        }
                    }
                    
                    // If we found a location, teleport there (unless they're already very close)
                    if (targetLoc != null && targetWorld != null) {
                        final Location finalLoc = targetLoc;
                        final World finalWorld = targetWorld;
                        TaskManager.runTask(() -> {
                            // Only teleport if player is not already very close to the saved location
                            Location currentLoc = player.getLocation();
                            if (currentLoc.getWorld() == finalWorld && 
                                currentLoc.distance(finalLoc) > 5.0) { // More than 5 blocks away
                                TaskManager.teleport(player, finalLoc);
                            }
                        });
                        // Update tracking
                        data.setLastWorldForWorldSet(worldSet.getName(), targetWorld.getName());
                        data.save();
                        return;
                    }
                    
                    // Track that player is in this world for this WorldSet
                    data.setLastWorldForWorldSet(worldSet.getName(), currentWorld.getName());
                    data.save();
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        PlayerData data = PlayerManager.getOrCreatePlayer(player);

        Location location = player.getLocation();
        data.putWorldLoc(location);
        
        // Track last world per WorldSet when player quits
        if (location.getWorld() != null) {
            Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(location.getWorld());
            if (worldSetOpt.isPresent()) {
                WorldSet worldSet = worldSetOpt.get();
                data.setLastWorldForWorldSet(worldSet.getName(), location.getWorld().getName());
            }
        }

        data.saveAndUnload();
    }

    // Track players who just used a portal to delay location saving
    private static final java.util.Set<Player> justUsedPortal = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        // Mark player as using portal - we'll save location after teleportation completes
        justUsedPortal.add(player);
        
        // Schedule multiple tasks to save location after player moves away from portal
        // First save after a short delay (portal teleportation completes)
        TaskManager.runTaskLater(() -> {
            if (player.isOnline() && justUsedPortal.contains(player)) {
                Location loc = player.getLocation();
                if (loc != null && loc.getWorld() != null) {
                    // Check if player has moved away from portal (more than 3 blocks from spawn-like location)
                    // This is a simple check - if they're still at a portal, we'll save again later
                    PlayerData data = PlayerManager.getOrCreatePlayer(player);
                    
                    // Track last world per WorldSet
                    Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(loc.getWorld());
                    if (worldSetOpt.isPresent()) {
                        WorldSet worldSet = worldSetOpt.get();
                        data.setLastWorldForWorldSet(worldSet.getName(), loc.getWorld().getName());
                    }
                }
            }
        }, 5L);
        
        // Save location again after player has had time to move (20 ticks = 1 second)
        TaskManager.runTaskLater(() -> {
            if (player.isOnline() && justUsedPortal.contains(player)) {
                Location loc = player.getLocation();
                if (loc != null && loc.getWorld() != null) {
                    PlayerData data = PlayerManager.getOrCreatePlayer(player);
                    // Save the location AFTER player has had time to move away from portal
                    data.putWorldLoc(loc);
                    
                    // Track last world per WorldSet
                    Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(loc.getWorld());
                    if (worldSetOpt.isPresent()) {
                        WorldSet worldSet = worldSetOpt.get();
                        data.setLastWorldForWorldSet(worldSet.getName(), loc.getWorld().getName());
                    }
                    
                    data.save();
                }
            }
            justUsedPortal.remove(player);
        }, 20L); // 1 second delay to give player time to move away from portal
    }

    @EventHandler
    public void onWorldMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from == null) return;
        if (to == null) return;

        World fromWorld = from.getWorld();
        World toWorld = to.getWorld();

        if (fromWorld == null) return;
        if (toWorld == null) return;

        // Skip if player just used a portal - portal handler will save location
        if (justUsedPortal.contains(player)) {
            return;
        }

        PlayerData data = PlayerManager.getOrCreatePlayer(player);

        data.putWorldLoc(from);

        if (fromWorld == toWorld) return;

        // Track last world per WorldSet when player changes worlds
        Optional<WorldSet> toWorldSetOpt = WorldSetManager.getWorldSetByWorld(toWorld);
        if (toWorldSetOpt.isPresent()) {
            WorldSet worldSet = toWorldSetOpt.get();
            data.setLastWorldForWorldSet(worldSet.getName(), toWorld.getName());
        }

        data.teleportWorldLoc(toWorld);

        data.save();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();
            
            // Check if player's death world belongs to a WorldSet
            World deathWorld = player.getWorld();
            if (deathWorld != null) {
                Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(deathWorld);
                if (worldSetOpt.isPresent()) {
                    WorldSet worldSet = worldSetOpt.get();
                    
                    // Check if WorldSet has a spawnpoint
                    if (worldSet.hasSpawnpoint()) {
                        Location spawnpoint = worldSet.getSpawnpoint();
                        final Location finalSpawnpoint = spawnpoint;
                        TaskManager.runTask(() -> {
                            event.setRespawnLocation(finalSpawnpoint);
                        });
                        return;
                    }
                    
                    // No spawnpoint set - find first OVERWORLD world in WorldSet
                    World overworldWorld = null;
                    for (String worldName : worldSet.getWorldNames()) {
                        World w = Bukkit.getWorld(worldName);
                        if (w != null && w.getEnvironment() == Environment.NORMAL) {
                            overworldWorld = w;
                            break;
                        }
                    }
                    
                    if (overworldWorld != null) {
                        Location spawnLoc = WorldBack.getMainConfig().getSpawnLocation(overworldWorld);
                        final Location finalSpawnLoc = spawnLoc;
                        TaskManager.runTask(() -> {
                            event.setRespawnLocation(finalSpawnLoc);
                        });
                    }
                }
            }
        });
    }
}
