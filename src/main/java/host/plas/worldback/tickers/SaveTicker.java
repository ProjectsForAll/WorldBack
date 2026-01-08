package host.plas.worldback.tickers;

import host.plas.bou.scheduling.BaseRunnable;
import host.plas.worldback.data.PlayerData;
import host.plas.worldback.data.PlayerManager;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicBoolean;

public class SaveTicker extends BaseRunnable {
    public static AtomicBoolean isRunning = new AtomicBoolean(false);

    public SaveTicker() {
        super(0, 20 * 10); // Every 10 seconds

        isRunning.set(false);
    }

    @Override
    public void run() {
        if (isRunning.get()) return;

        isRunning.set(true);

        Bukkit.getOnlinePlayers().forEach(player -> {
            PlayerData data = PlayerManager.getOrCreatePlayer(player);
            data.putWorldLoc(player.getLocation());
            data.waitUntilFullyLoaded();
            data.save();
        });

        isRunning.set(false);
    }
}
