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

            // Check if player has a WorldSet
            if (data.getCurrentWorldSet() != null) {
                Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(data.getCurrentWorldSet());
                if (worldSetOpt.isPresent()) {
                    WorldSet worldSet = worldSetOpt.get();

                    // Check lastEnvironment and teleport accordingly
                    Environment lastEnv = data.getLastEnvironment();
                    
                    if (lastEnv == Environment.NETHER) {
                        // Find nether world in WorldSet
                        World netherWorld = null;
                        for (String worldName : worldSet.getWorldNames()) {
                            World w = Bukkit.getWorld(worldName);
                            if (w != null && w.getEnvironment() == Environment.NETHER) {
                                netherWorld = w;
                                break;
                            }
                        }
                        
                        if (netherWorld != null) {
                            Location loc = data.getWorldLocByEnvironment(worldSet, Environment.NETHER);
                            if (loc == null) {
                                loc = WorldBack.getMainConfig().getSpawnLocation(netherWorld);
                            }
                            final Location finalLoc = loc;
                            Bukkit.getScheduler().runTask(WorldBack.getInstance(), () -> {
                                TaskManager.teleport(player, finalLoc);
                            });
                        }
                    } else if (lastEnv == Environment.THE_END) {
                        // Find end world in WorldSet
                        World endWorld = null;
                        for (String worldName : worldSet.getWorldNames()) {
                            World w = Bukkit.getWorld(worldName);
                            if (w != null && w.getEnvironment() == Environment.THE_END) {
                                endWorld = w;
                                break;
                            }
                        }
                        
                        if (endWorld != null) {
                            Location loc = data.getWorldLocByEnvironment(worldSet, Environment.THE_END);
                            if (loc == null) {
                                loc = WorldBack.getMainConfig().getSpawnLocation(endWorld);
                            }
                            final Location finalLoc = loc;
                            Bukkit.getScheduler().runTask(WorldBack.getInstance(), () -> {
                                TaskManager.teleport(player, finalLoc);
                            });
                        }
                    }
                    // If NORMAL or null, use existing overworld logic (which happens in onWorldMove)
                }
            } else {
                // Check if player's current world belongs to a WorldSet
                World currentWorld = player.getWorld();
                if (currentWorld != null) {
                    Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSetByWorld(currentWorld);
                    if (worldSetOpt.isPresent()) {
                        // Player joined a world that belongs to a WorldSet, but they're not in that WorldSet
                        // Optionally auto-join them? For now, just use existing logic
                    }
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        PlayerData data = PlayerManager.getOrCreatePlayer(player);

        data.putWorldLoc(player.getLocation());

        data.saveAndUnload();
    }

    // Track if player is using a portal (set to true when portal event fires)
    private static final java.util.Set<Player> usingPortal = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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

        PlayerData data = PlayerManager.getOrCreatePlayer(player);

        data.putWorldLoc(from);

        if (fromWorld == toWorld) return;

        // Check if player is using a portal - if so, let portal handle the teleportation
        if (usingPortal.contains(player)) {
            usingPortal.remove(player);
            // Update lastEnvironment for WorldSet
            if (data.getCurrentWorldSet() != null) {
                Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(data.getCurrentWorldSet());
                if (worldSetOpt.isPresent() && worldSetOpt.get().containsWorld(toWorld)) {
                    data.setLastEnvironment(toWorld.getEnvironment());
                    data.save();
                }
            }
            // Don't teleport - portal already handled it
            return;
        }

        // Update lastEnvironment when changing worlds within a WorldSet
        if (data.getCurrentWorldSet() != null) {
            Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(data.getCurrentWorldSet());
            if (worldSetOpt.isPresent()) {
                WorldSet worldSet = worldSetOpt.get();
                if (worldSet.containsWorld(toWorld)) {
                    data.setLastEnvironment(toWorld.getEnvironment());
                }
            }
        }

        data.teleportWorldLoc(toWorld);

        data.save();
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        
        // Mark player as using portal so onWorldMove doesn't interfere
        usingPortal.add(player);
        
        // Let the portal event proceed normally - don't cancel it
        // The plugin will handle tracking the environment after the portal teleports
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();
            
            // Check if player has a WorldSet
            if (data.getCurrentWorldSet() != null) {
                Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(data.getCurrentWorldSet());
                if (worldSetOpt.isPresent()) {
                    WorldSet worldSet = worldSetOpt.get();
                    
                    // Check if WorldSet has a spawnpoint
                    if (worldSet.hasSpawnpoint()) {
                        Location spawnpoint = worldSet.getSpawnpoint();
                        final Location finalSpawnpoint = spawnpoint;
                        Bukkit.getScheduler().runTask(WorldBack.getInstance(), () -> {
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
                        Bukkit.getScheduler().runTask(WorldBack.getInstance(), () -> {
                            event.setRespawnLocation(finalSpawnLoc);
                        });
                    }
                }
            }
        });
    }
}
