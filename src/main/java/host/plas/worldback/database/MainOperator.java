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

        // Serialize last world per WorldSet
        JsonObject lastWorldsObj = new JsonObject();
        playerData.getLastWorldPerWorldSet().forEach(lastWorldsObj::addProperty);
        if (!lastWorldsObj.isEmpty()) {
            root.add("lastWorldPerWorldSet", lastWorldsObj);
        }

        return root.toString();
    }

    /**
     * Checks if the data is in old format (just JSON array)
     */
    public static boolean isOldFormat(String data) {
        try {
            JsonElement rootElement = new Gson().fromJson(data, JsonElement.class);
            return rootElement.isJsonArray();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Migrates old format data to new format
     */
    public static String migrateToNewFormat(String oldData) {
        try {
            JsonElement rootElement = new Gson().fromJson(oldData, JsonElement.class);
            
            JsonObject newRoot = new JsonObject();
            
            // Handle old format (just array)
            if (rootElement.isJsonArray()) {
                newRoot.add("locations", rootElement.getAsJsonArray());
                return newRoot.toString();
            }
            
            // Handle new format but missing locations wrapper
            if (rootElement.isJsonObject()) {
                JsonObject oldRoot = rootElement.getAsJsonObject();
                
                // If it already has locations, it's already new format
                if (oldRoot.has("locations")) {
                    return oldData; // Already new format
                }
                
                // If it has old currentWorldSet/lastEnvironment, migrate those
                // Copy all existing properties
                for (String key : oldRoot.keySet()) {
                    if (!key.equals("currentWorldSet") && !key.equals("lastEnvironment")) {
                        newRoot.add(key, oldRoot.get(key));
                    }
                }
                
                // If no locations array exists, try to create one from the root
                // This handles edge cases where data might be malformed
                return newRoot.toString();
            }
        } catch (Throwable e) {
            WorldBack.getInstance().logWarning("Failed to migrate old format data", e);
        }
        
        return oldData; // Return original if migration fails
    }

    public static ConcurrentSkipListMap<String, Location> deserializeWorldLocs(String data) {
        ConcurrentSkipListMap<String, Location> locs = new ConcurrentSkipListMap<>();

        try {
            JsonElement rootElement = new Gson().fromJson(data, JsonElement.class);
            
            // Check if it's the old format (JsonArray) or new format (JsonObject)
            JsonArray dataArray;
            boolean isOldFormat = rootElement.isJsonArray();
            
            if (isOldFormat) {
                // Old format - just an array of locations
                dataArray = rootElement.getAsJsonArray();
            } else if (rootElement.isJsonObject()) {
                // New format - object with locations array
                JsonObject root = rootElement.getAsJsonObject();
                if (root.has("locations")) {
                    dataArray = root.getAsJsonArray("locations");
                } else {
                    // No locations array found - return empty
                    return locs;
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
            
            // Handle old format (just array) - migrate it
            if (rootElement.isJsonArray()) {
                // Old format detected - try to migrate legacy data
                // For old format, we can't determine WorldSet info, so we'll leave it empty
                // The data will be migrated to new format on next save
                return;
            }
            
            if (rootElement.isJsonObject()) {
                JsonObject root = rootElement.getAsJsonObject();
                
                // Deserialize last world per WorldSet
                if (root.has("lastWorldPerWorldSet") && root.get("lastWorldPerWorldSet").isJsonObject()) {
                    JsonObject lastWorldsObj = root.getAsJsonObject("lastWorldPerWorldSet");
                    for (String worldSetName : lastWorldsObj.keySet()) {
                        if (!lastWorldsObj.get(worldSetName).isJsonNull()) {
                            String worldName = lastWorldsObj.get(worldSetName).getAsString();
                            playerData.getLastWorldPerWorldSet().put(worldSetName, worldName);
                        }
                    }
                }
                
                // Legacy support: migrate old currentWorldSet/lastEnvironment to new format
                if (root.has("currentWorldSet") && !root.get("currentWorldSet").isJsonNull()) {
                    String worldSetName = root.get("currentWorldSet").getAsString();
                    // Try to find a world in that WorldSet based on lastEnvironment
                    if (root.has("lastEnvironment") && !root.get("lastEnvironment").isJsonNull()) {
                        try {
                            Environment env = Environment.valueOf(root.get("lastEnvironment").getAsString());
                            // Find a world in the WorldSet with that environment
                            host.plas.worldback.data.WorldSetManager.getWorldSet(worldSetName).ifPresent(worldSet -> {
                                for (String worldName : worldSet.getWorldNames()) {
                                    World world = Bukkit.getWorld(worldName);
                                    if (world != null && world.getEnvironment() == env) {
                                        playerData.getLastWorldPerWorldSet().put(worldSetName, worldName);
                                        break;
                                    }
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    } else {
                        // If we have currentWorldSet but no lastEnvironment, try to find any world
                        // that the player has a location for in that WorldSet
                        host.plas.worldback.data.WorldSetManager.getWorldSet(worldSetName).ifPresent(worldSet -> {
                            // Find the first world in the WorldSet that the player has a location for
                            for (String worldName : worldSet.getWorldNames()) {
                                if (playerData.getWorldLoc(worldName) != null) {
                                    playerData.getLastWorldPerWorldSet().put(worldSetName, worldName);
                                    break;
                                }
                            }
                        });
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
            AtomicReference<Boolean> needsMigrationRef = new AtomicReference<>(false);

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

                        // Check if data is in old format and migrate if needed
                        boolean needsMigration = isOldFormat(worldLocsData);
                        needsMigrationRef.set(needsMigration);
                        
                        if (needsMigration) {
                            WorldBack.getInstance().logInfo("Migrating old format data for player: " + uuid);
                            // Migrate the data format
                            worldLocsData = migrateToNewFormat(worldLocsData);
                        }

                        ConcurrentSkipListMap<String, Location> worldLocs = deserializeWorldLocs(worldLocsData);

                        PlayerData playerData = new PlayerData(uuid, name);
                        playerData.getWorldPlaces().putAll(worldLocs);
                        // Initialize lastWorldPerWorldSet if null (shouldn't happen, but safety check)
                        if (playerData.getLastWorldPerWorldSet() == null) {
                            playerData.setLastWorldPerWorldSet(new java.util.concurrent.ConcurrentSkipListMap<>());
                        }
                        deserializePlayerData(playerData, worldLocsData);
                        
                        // If we migrated the data, save it back in new format
                        if (needsMigrationRef.get()) {
                            // Save the migrated data back to database asynchronously
                            putPlayer(playerData, true);
                        }
                        
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
