package host.plas.worldback.events;

import gg.drak.thebase.events.BaseEventHandler;
import host.plas.bou.events.ListenerConglomerate;
import host.plas.worldback.WorldBack;
import org.bukkit.Bukkit;

public class AbstractConglomerate implements ListenerConglomerate {
    public AbstractConglomerate() {
        register();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, WorldBack.getInstance());
        BaseEventHandler.bake(this, WorldBack.getInstance());
        WorldBack.getInstance().logInfo("Registered listeners for: &c" + this.getClass().getSimpleName());
    }
}
