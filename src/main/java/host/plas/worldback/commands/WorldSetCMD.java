package host.plas.worldback.commands;

import gg.drak.thebase.async.AsyncUtils;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
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

public class WorldSetCMD extends SimplifiedCommand {
    public WorldSetCMD() {
        super("worldset", WorldBack.getInstance());
    }

    @Override
    public boolean command(CommandContext ctx) {
        if (!ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldset <create|add|remove|join|spawnpoint> [args]");
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
            case "spawnpoint":
                return handleSpawnpoint(ctx);
            default:
                ctx.sendMessage("&cUnknown subcommand. Use: create, add, remove, join, spawnpoint");
                return false;
        }
    }

    private boolean handleCreate(CommandContext ctx) {
        if (!ctx.isArgUsable(2)) {
            ctx.sendMessage("&cUsage: /worldset create <name>");
            return false;
        }

        String name = ctx.getStringArg(1);
        
        if (WorldSetManager.getWorldSet(name).isPresent()) {
            ctx.sendMessage("&cWorldSet '" + name + "' already exists.");
            return false;
        }

        WorldSetManager.createWorldSet(name);
        ctx.sendMessage("&aWorldSet '" + name + "' created successfully.");
        return true;
    }

    private boolean handleAdd(CommandContext ctx) {
        if (!ctx.isArgUsable(3)) {
            ctx.sendMessage("&cUsage: /worldset add <name> <world>");
            return false;
        }

        String name = ctx.getStringArg(1);
        String worldName = ctx.getStringArg(2);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (!worldSetOpt.isPresent()) {
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
            ctx.sendMessage("&aAdded world '" + worldName + "' to WorldSet '" + name + "'.");
        } else {
            ctx.sendMessage("&cWorld '" + worldName + "' is already in WorldSet '" + name + "'.");
        }
        return true;
    }

    private boolean handleRemove(CommandContext ctx) {
        if (!ctx.isArgUsable(3)) {
            ctx.sendMessage("&cUsage: /worldset remove <name> <world>");
            return false;
        }

        String name = ctx.getStringArg(1);
        String worldName = ctx.getStringArg(2);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (!worldSetOpt.isPresent()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        WorldSet worldSet = worldSetOpt.get();
        if (worldSet.removeWorld(worldName)) {
            WorldSetManager.saveWorldSet(worldSet);
            ctx.sendMessage("&aRemoved world '" + worldName + "' from WorldSet '" + name + "'.");
        } else {
            ctx.sendMessage("&cWorld '" + worldName + "' is not in WorldSet '" + name + "'.");
        }
        return true;
    }

    private boolean handleJoin(CommandContext ctx) {
        if (!ctx.isArgUsable(2)) {
            ctx.sendMessage("&cUsage: /worldset join <name>");
            return false;
        }

        Player player = ctx.getPlayer().orElse(null);
        if (player == null) {
            ctx.sendMessage("&cThis command can only be used by players.");
            return false;
        }

        String name = ctx.getStringArg(1);

        Optional<WorldSet> worldSetOpt = WorldSetManager.getWorldSet(name);
        if (!worldSetOpt.isPresent()) {
            ctx.sendMessage("&cWorldSet '" + name + "' not found.");
            return false;
        }

        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();
            
            data.setCurrentWorldSet(name);
            data.save();
            
            Bukkit.getScheduler().runTask(WorldBack.getInstance(), () -> {
                ctx.sendMessage("&aJoined WorldSet '" + name + "'.");
            });
        });

        return true;
    }

    private boolean handleSpawnpoint(CommandContext ctx) {
        if (!ctx.isArgUsable(2)) {
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
        if (ctx.isArgUsable(3)) {
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
        ctx.sendMessage("&aSet spawnpoint for WorldSet '" + name + "' at " + 
                       spawnpoint.getWorld().getName() + " (" + 
                       (int)spawnpoint.getX() + ", " + 
                       (int)spawnpoint.getY() + ", " + 
                       (int)spawnpoint.getZ() + ").");
        return true;
    }
}