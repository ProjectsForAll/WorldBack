package host.plas.worldback.commands;

import gg.drak.thebase.async.AsyncUtils;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import host.plas.bou.scheduling.TaskManager;
import host.plas.worldback.WorldBack;
import host.plas.worldback.data.PlayerData;
import host.plas.worldback.data.PlayerManager;
import host.plas.worldback.data.WorldSet;
import host.plas.worldback.data.WorldSetManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

public class WorldSetCMD extends SimplifiedCommand {
    public WorldSetCMD() {
        super("worldset", WorldBack.getInstance());
    }

    @Override
    public boolean command(CommandContext ctx) {
        if (!ctx.isArgUsable(0)) {
            ctx.sendMessage("&cUsage: /worldset <create|add|remove|join|teleport|spawnpoint> [args]");
            return false;
        }

        String subCommand = ctx.getStringArg(0).toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(ctx);
            case "add":
                return handleAdd(ctx);
            case "remove":
                return handleRemove(ctx);
            case "join":
                return handleJoin(ctx);
            case "tp":
            case "teleport":
                return handleTeleport(ctx);
            case "spawnpoint":
                return handleSpawnpoint(ctx);
            default:
                ctx.sendMessage("&cUnknown subcommand. Use: create, add, remove, join, spawnpoint");
                return false;
        }
    }

    private boolean handleCreate(CommandContext ctx) {
        if (! ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldset create <name>");
            return false;
        }

        String name = ctx.getStringArg(1);
        
        if (WorldSetManager.getWorldSet(name).isPresent()) {
            ctx.sendMessage("&cWorldSet '" + name + "' already exists.");
            return false;
        }

        WorldSetManager.createWorldSet(name);
        ctx.sendMessage("&cWorldSet &7'&d" + name + "&7' &ecreated successfully&7.");
        return true;
    }

    private boolean handleAdd(CommandContext ctx) {
        if (!ctx.isArgUsable(2)) {
            ctx.sendMessage("&cUsage: /worldset add <name> <world>");
            return false;
        }

        String name = ctx.getStringArg(1);
        String worldName = ctx.getStringArg(2);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (worldSetOpt.isEmpty()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ctx.sendMessage("&cWorld '" + worldName + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        boolean added = worldSet.addWorld(worldName);
        WorldSetManager.saveWorldSet(worldSet);
        if (added) {
            ctx.sendMessage("&eAdded world &7'&d" + worldName + "&7' &eto &cWorldSet &7'&d" + name + "&7'.");
        } else {
            ctx.sendMessage("&cWorld '" + worldName + "' is already in WorldSet '" + name + "'.");
        }
        return true;
    }

    private boolean handleRemove(CommandContext ctx) {
        if (!ctx.isArgUsable(2)) {
            ctx.sendMessage("&cUsage: /worldset remove <name> <world>");
            return false;
        }

        String name = ctx.getStringArg(1);
        String worldName = ctx.getStringArg(2);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (worldSetOpt.isEmpty()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        if (worldSet.removeWorld(worldName)) {
            WorldSetManager.saveWorldSet(worldSet);
            ctx.sendMessage("&eRemoved world &7'&d" + worldName + "&7' &efrom &cWorldSet &7'&d" + name + "&7'.");
        } else {
            ctx.sendMessage("&cWorld '" + worldName + "' is not in WorldSet '" + name + "'.");
        }
        return true;
    }

    private boolean handleJoin(CommandContext ctx) {
        if (!ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldset join <name> [player]");
            return false;
        }

        String name = ctx.getStringArg(1);
        
        // Get target player - either from argument or command sender
        Player targetPlayer = null;
        if (ctx.isArgUsable(2)) {
            // Player argument provided
            targetPlayer = ctx.getPlayerArg(2).orElse(null);
            if (targetPlayer == null) {
                ctx.sendMessage("&cPlayer not found.");
                return false;
            }
        } else {
            // No player argument - use command sender
            targetPlayer = ctx.getPlayer().orElse(null);
            if (targetPlayer == null) {
                ctx.sendMessage("&cThis command can only be used by players or you must specify a player.");
                return false;
            }
        }
        
        final Player player = targetPlayer;

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (worldSetOpt.isEmpty()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        
        // Teleport to last location in any world in this WorldSet
        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();
            
            Location targetLoc = null;
            
            // First, try to find location in the last world for this WorldSet
            String lastWorldName = data.getLastWorldForWorldSet(name);
            if (lastWorldName != null) {
                World lastWorld = Bukkit.getWorld(lastWorldName);
                if (lastWorld != null && worldSet.containsWorld(lastWorld)) {
                    targetLoc = data.getWorldLoc(lastWorld);
                }
            }
            
            // If no location found in last world, check all worlds in WorldSet for any saved location
            if (targetLoc == null) {
                for (String worldName : worldSet.getWorldNames()) {
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        Location loc = data.getWorldLoc(w);
                        if (loc != null) {
                            targetLoc = loc;
                            // Update last world to this one since we're using it
                            data.setLastWorldForWorldSet(name, worldName);
                            break;
                        }
                    }
                }
            }
            
            // If still no location found, try spawnpoint
            if (targetLoc == null && worldSet.hasSpawnpoint()) {
                targetLoc = worldSet.getSpawnpoint();
            }
            
            // If still no location, use first world's spawn
            if (targetLoc == null) {
                for (String worldName : worldSet.getWorldNames()) {
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        targetLoc = WorldBack.getMainConfig().getSpawnLocation(w);
                        break;
                    }
                }
            }
            
            if (targetLoc != null) {
                final Location finalLoc = targetLoc;
                final boolean isOtherPlayer = ctx.isArgUsable(2);
                TaskManager.runTask(() -> {
                    TaskManager.teleport(player, finalLoc);
                    if (isOtherPlayer) {
                        ctx.sendMessage("&eTeleported &a" + player.getName() + " &eto &cWorldSet &7'&d" + name + "&7'.");
                    } else {
                        ctx.sendMessage("&eTeleported to &cWorldSet &7'&d" + name + "&7'.");
                    }
                });
                data.save(); // Save if we updated lastWorldPerWorldSet
            } else {
                ctx.sendMessage("&cCould not find a valid location in WorldSet '" + name + "'.");
            }
        });

        return true;
    }

    private boolean handleSpawnpoint(CommandContext ctx) {
        if (!ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldset spawnpoint <name> [location]");
            return false;
        }

        String name = ctx.getStringArg(1);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (!worldSetOpt.isPresent()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        
        Location spawnpoint;
        if (ctx.isArgUsable(2)) {
            // Try to parse location from arguments
            Player player = ctx.getPlayer().orElse(null);
            if (player == null) {
                ctx.sendMessage("&cThis command requires a location or must be run by a player.");
                return false;
            }
            spawnpoint = player.getLocation();
        } else {
            Player player = ctx.getPlayer().orElse(null);
            if (player == null) {
                ctx.sendMessage("&cThis command must be run by a player to use current location.");
                return false;
            }
            spawnpoint = player.getLocation();
        }

        worldSet.setSpawnpoint(spawnpoint);
        WorldSetManager.saveWorldSet(worldSet);
        ctx.sendMessage("&eSet spawnpoint for &cWorldSet &7'&d" + name + "&7' &eat &a" +
                       spawnpoint.getWorld().getName() + " &7(&b" +
                       (int)spawnpoint.getX() + "&7, &b" +
                       (int)spawnpoint.getY() + "&7, &b" +
                       (int)spawnpoint.getZ() + "&7).");
        return true;
    }

    private boolean handleTeleport(CommandContext ctx) {
        if (!ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldset teleport <name> [player]");
            return false;
        }

        String name = ctx.getStringArg(1);
        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (worldSetOpt.isEmpty()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        
        // Get target player - either from argument or command sender
        Player targetPlayer = null;
        if (ctx.isArgUsable(2)) {
            // Player argument provided
            targetPlayer = ctx.getPlayerArg(2).orElse(null);
            if (targetPlayer == null) {
                ctx.sendMessage("&cPlayer not found.");
                return false;
            }
        } else {
            // No player argument - use command sender
            targetPlayer = ctx.getPlayer().orElse(null);
            if (targetPlayer == null) {
                ctx.sendMessage("&cThis command can only be used by players or you must specify a player.");
                return false;
            }
        }
        
        final Player player = targetPlayer;

        // Teleport to last location in any world in this WorldSet
        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();
            
            Location targetLoc = null;
            String targetWorldName = null;
            
            // Helper method to check if location is likely a portal location
            java.util.function.Predicate<Location> isLikelyPortalLocation = (loc) -> {
                if (loc == null || loc.getWorld() == null) return false;
                // Check for portal blocks nearby (nether portal or end portal)
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            org.bukkit.block.Block block = loc.getWorld().getBlockAt(x + dx, y + dy, z + dz);
                            org.bukkit.Material type = block.getType();
                            if (type == org.bukkit.Material.NETHER_PORTAL || 
                                type == org.bukkit.Material.END_PORTAL ||
                                type == org.bukkit.Material.END_GATEWAY) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            };
            
            // First, try to find location in the last world for this WorldSet
            String lastWorldName = data.getLastWorldForWorldSet(name);
            if (lastWorldName != null) {
                World lastWorld = Bukkit.getWorld(lastWorldName);
                if (lastWorld != null && worldSet.containsWorld(lastWorld)) {
                    Location loc = data.getWorldLoc(lastWorld);
                    // Only use if it doesn't look like a portal location
                    if (loc != null && !isLikelyPortalLocation.test(loc)) {
                        targetLoc = loc;
                        targetWorldName = lastWorldName;
                    }
                }
            }
            
            // If no location found in last world (or it was a portal location), 
            // check all worlds in WorldSet for any saved location
            if (targetLoc == null) {
                for (String worldName : worldSet.getWorldNames()) {
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        Location loc = data.getWorldLoc(w);
                        // Prefer locations that don't look like portal locations
                        if (loc != null) {
                            if (!isLikelyPortalLocation.test(loc)) {
                                targetLoc = loc;
                                targetWorldName = worldName;
                                break; // Found a good non-portal location
                            } else if (targetLoc == null) {
                                // Keep portal location as fallback if no other location found
                                targetLoc = loc;
                                targetWorldName = worldName;
                            }
                        }
                    }
                }
            }
            
            // If we found a location but it's a portal location, try to find a non-portal one
            if (targetLoc != null && isLikelyPortalLocation.test(targetLoc)) {
                // Try to find a non-portal location in any world
                for (String worldName : worldSet.getWorldNames()) {
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        Location loc = data.getWorldLoc(w);
                        if (loc != null && !isLikelyPortalLocation.test(loc)) {
                            targetLoc = loc;
                            targetWorldName = worldName;
                            break;
                        }
                    }
                }
            }
            
            // If still no location found, try spawnpoint
            if (targetLoc == null && worldSet.hasSpawnpoint()) {
                targetLoc = worldSet.getSpawnpoint();
            }
            
            // If still no location, use first world's spawn
            if (targetLoc == null) {
                for (String worldName : worldSet.getWorldNames()) {
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        targetLoc = WorldBack.getMainConfig().getSpawnLocation(w);
                        targetWorldName = worldName;
                        break;
                    }
                }
            }
            
            if (targetLoc != null) {
                final Location finalLoc = targetLoc;
                final boolean isOtherPlayer = ctx.isArgUsable(2);
                if (targetWorldName != null) {
                    data.setLastWorldForWorldSet(name, targetWorldName);
                }
                TaskManager.runTask(() -> {
                    TaskManager.teleport(player, finalLoc);
                    if (isOtherPlayer) {
                        ctx.sendMessage("&eTeleported &a" + player.getName() + " &eto &cWorldSet &7'&d" + name + "&7'.");
                    } else {
                        ctx.sendMessage("&eTeleported to &cWorldSet &7'&d" + name + "&7'.");
                    }
                });
                data.save(); // Save if we updated lastWorldPerWorldSet
            } else {
                ctx.sendMessage("&cCould not find a valid location in WorldSet '" + name + "'.");
            }
        });

        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext ctx) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();

        if (ctx.getArgCount() <= 1) {
            completions.add("create");
            completions.add("add");
            completions.add("remove");
            completions.add("join");
            completions.add("tp");
            completions.add("teleport");
            completions.add("spawnpoint");
        } else if (ctx.getArgCount() == 2) {
            String subCommand = ctx.getStringArg(0).toLowerCase();
            if (subCommand.equals("add") || subCommand.equals("remove") || subCommand.equals("spawnpoint") || 
                subCommand.equals("join") || subCommand.equals("tp") || subCommand.equals("teleport")) {
                completions.addAll(WorldSetManager.getAllWorldSetNames());
            }
        } else if (ctx.getArgCount() == 3) {
            String subCommand = ctx.getStringArg(0).toLowerCase();
            if (subCommand.equals("add") || subCommand.equals("remove")) {
                String partialWorld = ctx.getStringArg(2).toLowerCase();
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(partialWorld)) {
                        completions.add(world.getName());
                    }
                }
            } else if (subCommand.equals("join") || subCommand.equals("tp") || subCommand.equals("teleport")) {
                // Tab complete player names for join/teleport commands
                String partialPlayer = ctx.getStringArg(2).toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partialPlayer)) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}