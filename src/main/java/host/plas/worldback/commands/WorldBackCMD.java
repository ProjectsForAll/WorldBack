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
}
