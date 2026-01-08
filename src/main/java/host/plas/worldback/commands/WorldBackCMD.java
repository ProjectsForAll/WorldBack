package host.plas.worldback.commands;

import gg.drak.thebase.async.AsyncUtils;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import host.plas.worldback.WorldBack;
import host.plas.worldback.data.PlayerData;
import host.plas.worldback.data.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentSkipListSet;

public class WorldBackCMD extends SimplifiedCommand {
    public WorldBackCMD() {
        super("worldback", WorldBack.getInstance());
    }

    @Override
    public boolean command(CommandContext ctx) {
        if (! ctx.isArgUsable(1)) {
            ctx.sendMessage("&cUsage: /worldback <world> <player>");
            return false;
        }

        String worldName = ctx.getStringArg(0);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ctx.sendMessage("&cWorld not found.");
            return false;
        }

        Player player = ctx.getPlayerArg(1).orElse(null);
        if (player == null) {
            ctx.sendMessage("&cPlayer not found.");
            return false;
        }

        AsyncUtils.executeAsync(() -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.waitUntilFullyLoaded();

            data.teleportWorldLoc(world);
        });

        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext ctx) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();

        if (ctx.getArgCount() <= 1) {
            String partialWorld = ctx.getStringArg(0).toLowerCase();
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase().startsWith(partialWorld)) {
                    completions.add(world.getName());
                }
            }
        } else if (ctx.getArgCount() == 2) {
            String partialPlayer = ctx.getStringArg(1).toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialPlayer)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
