package host.plas.worldback.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.drak.thebase.async.AsyncUtils;
import host.plas.bou.sql.DBOperator;
import host.plas.bou.sql.DatabaseType;
import host.plas.worldback.WorldBack;
import host.plas.worldback.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

public class MainOperator extends DBOperator {
    public MainOperator() {
        super(WorldBack.getDatabaseConfig().getConnectorSet(), WorldBack.getInstance());
    }

    @Override
    public void ensureTables() {
        String s1 = Statements.getStatement(Statements.StatementType.CREATE_TABLES, getConnectorSet());

        execute(s1, stmt -> {});
    }

    @Override
    public void ensureDatabase() {
        String s1 = Statements.getStatement(Statements.StatementType.CREATE_DATABASE, getConnectorSet());

        execute(s1, stmt -> {});
    }

    public void putPlayer(PlayerData playerData) {
        putPlayer(playerData, true);
    }

    public void putPlayer(PlayerData playerData, boolean async) {
        if (async) {
            putPlayerThreaded(playerData);
        } else {
            putPlayerThreaded(playerData).join();
        }
    }

    public CompletableFuture<Void> putPlayerThreaded(PlayerData playerData) {
        return AsyncUtils.executeAsync(() -> {
            ensureUsable();

            String s1 = Statements.getStatement(Statements.StatementType.PUSH_PLAYER_MAIN, getConnectorSet());

            execute(s1, stmt -> {
                try {
                    stmt.setString(1, playerData.getIdentifier());
                    stmt.setString(2, playerData.getName());
                    stmt.setString(3, serializeWorldLocs(playerData));

                    if (getType() == DatabaseType.MYSQL) {
                        stmt.setString(4, playerData.getName());
                        stmt.setString(5, serializeWorldLocs(playerData));
                    }
                } catch (Throwable e) {
                    WorldBack.getInstance().logWarning("Failed to set values for statement: " + s1, e);
                }
            });
        });
    }

    public static String serializeWorldLocs(PlayerData playerData) {
        ConcurrentSkipListMap<String, Location> locs = playerData.getWorldPlaces();

        JsonObject root = new JsonObject();

        // Serialize world locations
        JsonArray locationsArray = new JsonArray();
        locs.forEach((worldName, location) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("world", worldName);
            obj.addProperty("x", location.getX());
            obj.addProperty("y", location.getY());
            obj.addProperty("z", location.getZ());
            obj.addProperty("yaw", location.getYaw());
            obj.addProperty("pitch", location.getPitch());

            locationsArray.add(obj);
        });
        root.add("locations", locationsArray);

        // Serialize WorldSet data
        if (playerData.getCurrentWorldSet() != null) {
            root.addProperty("currentWorldSet", playerData.getCurrentWorldSet());
        }
        if (playerData.getLastEnvironment() != null) {
            root.addProperty("lastEnvironment", playerData.getLastEnvironment().name());
        }

        return root.toString();
    }

    public static ConcurrentSkipListMap<String, Location> deserializeWorldLocs(String data) {
        ConcurrentSkipListMap<String, Location> locs = new ConcurrentSkipListMap<>();

        try {
            JsonElement rootElement = new Gson().fromJson(data, JsonElement.class);
            
            // Check if it's the old format (JsonArray) or new format (JsonObject)
            JsonArray dataArray;
            if (rootElement.isJsonArray()) {
                // Old format - just an array of locations
                dataArray = rootElement.getAsJsonArray();
            } else if (rootElement.isJsonObject()) {
                // New format - object with locations array
                JsonObject root = rootElement.getAsJsonObject();
                if (root.has("locations")) {
                    dataArray = root.getAsJsonArray("locations");
                } else {
                    // Fallback to old format
                    dataArray = rootElement.getAsJsonArray();
                }
            } else {
                return locs;
            }

            for (JsonElement element : dataArray) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    String worldName = obj.get("world").getAsString();
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    double x = obj.get("x").getAsDouble();
                    double y = obj.get("y").getAsDouble();
                    double z = obj.get("z").getAsDouble();
                    float yaw = obj.get("yaw").getAsFloat();
                    float pitch = obj.get("pitch").getAsFloat();

                    Location location = new Location(world, x, y, z, yaw, pitch);
                    locs.put(worldName, location);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            return locs;
        }

        return locs;
    }

    public static void deserializePlayerData(PlayerData playerData, String data) {
        try {
            JsonElement rootElement = new Gson().fromJson(data, JsonElement.class);
            
            if (rootElement.isJsonObject()) {
                JsonObject root = rootElement.getAsJsonObject();
                
                // Deserialize WorldSet data
                if (root.has("currentWorldSet") && !root.get("currentWorldSet").isJsonNull()) {
                    playerData.setCurrentWorldSet(root.get("currentWorldSet").getAsString());
                }
                if (root.has("lastEnvironment") && !root.get("lastEnvironment").isJsonNull()) {
                    try {
                        Environment env = Environment.valueOf(root.get("lastEnvironment").getAsString());
                        playerData.setLastEnvironment(env);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public CompletableFuture<Optional<PlayerData>> pullPlayerThreaded(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            ensureUsable();

            String s1 = Statements.getStatement(Statements.StatementType.PULL_PLAYER_MAIN, getConnectorSet());

            AtomicReference<Optional<PlayerData>> ref = new AtomicReference<>(Optional.empty());

            executeQuery(s1, stmt -> {
                try {
                    stmt.setString(1, uuid);
                } catch (Throwable e) {
                    WorldBack.getInstance().logWarning("Failed to set values for statement: " + s1, e);
                }
            }, rs -> {
                try {
                    if (rs.next()) {
                        String name = rs.getString("Name");
                        String worldLocsData = rs.getString("WorldLocs");

                        ConcurrentSkipListMap<String, Location> worldLocs = deserializeWorldLocs(worldLocsData);

                        PlayerData playerData = new PlayerData(uuid, name);
                        playerData.getWorldPlaces().putAll(worldLocs);
                        deserializePlayerData(playerData, worldLocsData);
                        ref.set(Optional.of(playerData));
                    }
                } catch (Throwable e) {
                    WorldBack.getInstance().logWarning("Failed to get values from result set for statement: " + s1, e);
                }
            });

            return ref.get();
        });
    }
}
