package host.plas.worldback;

import host.plas.bou.BetterPlugin;
import host.plas.worldback.commands.WorldBackCMD;
import host.plas.worldback.commands.WorldSetCMD;
import host.plas.worldback.config.DatabaseConfig;
import host.plas.worldback.config.MainConfig;
import host.plas.worldback.data.PlayerManager;
import host.plas.worldback.data.WorldSetManager;
import host.plas.worldback.database.MainOperator;
import host.plas.worldback.events.MainListener;
import host.plas.worldback.tickers.SaveTicker;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public final class WorldBack extends BetterPlugin {
    @Getter @Setter
    private static WorldBack instance;
    @Getter @Setter
    private static MainConfig mainConfig;
    @Getter @Setter
    private static DatabaseConfig databaseConfig;

    @Getter @Setter
    private static MainOperator database;

    @Getter @Setter
    private static MainListener mainListener;

    @Getter @Setter
    private static SaveTicker saveTicker;

    public WorldBack() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        // Plugin startup logic
        setInstance(this); // Set the instance of the plugin. // For use in other classes.

        setMainConfig(new MainConfig()); // Instantiate the main config and set it.
        setDatabaseConfig(new DatabaseConfig()); // Instantiate the database config and set it.

        setDatabase(new MainOperator()); // Instantiate the database operator and set it. // Uses the database config.

        setMainListener(new MainListener()); // Instantiate the main listener and set it.

        setSaveTicker(new SaveTicker());

        new WorldBackCMD();
        new WorldSetCMD();

        // Load WorldSets from config
        WorldSetManager.loadWorldSets();
    }

    @Override
    public void onBaseDisable() {
        // Plugin shutdown logic
        PlayerManager.getLoadedPlayers().forEach(playerData -> {
            // Save and unload all loaded player data.
            // Saves it in sync (hence the false) so it doesn't lose data.
            playerData.saveAndUnload(false);
        });
    }
}
