package com.evailcodes.chaintogether.config;

import com.evailcodes.chaintogether.ChainTogether;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChainConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chaintogether-common.json");

    public static final ChainConfigSpec SPEC = new ChainConfigSpec();

    public static final ConfigValue<Double> CHAIN_LENGTH = ConfigValue.doubleValue(0.7, 0.5, 10.0);
    public static final ConfigValue<Boolean> UNBREAKABLE_CHAIN = ConfigValue.booleanValue(true);
    public static final ConfigValue<Boolean> PREVENT_TELEPORT = ConfigValue.booleanValue(true);
    public static final ConfigValue<Boolean> SHARED_RESPAWN = ConfigValue.booleanValue(true);
    public static final ConfigValue<Boolean> AUTO_RESPAWN = ConfigValue.booleanValue(false);
    public static final ConfigValue<Integer> CHAIN_TRANSPARENCY = ConfigValue.intValue(100, 10, 100);

    public static void load() {
        setDefaults();

        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            CHAIN_LENGTH.set(readDouble(root, "chainLength", CHAIN_LENGTH.get()));
            UNBREAKABLE_CHAIN.set(readBoolean(root, "unbreakableChain", UNBREAKABLE_CHAIN.get()));
            PREVENT_TELEPORT.set(readBoolean(root, "preventTeleport", PREVENT_TELEPORT.get()));
            SHARED_RESPAWN.set(readBoolean(root, "sharedRespawn", SHARED_RESPAWN.get()));
            AUTO_RESPAWN.set(readBoolean(root, "autoRespawn", AUTO_RESPAWN.get()));
            CHAIN_TRANSPARENCY.set(readInt(root, "chainTransparency", CHAIN_TRANSPARENCY.get()));
        } catch (Exception exception) {
            ChainTogether.LOGGER.warn("Failed to load ChainTogether config, using defaults", exception);
            setDefaults();
        }

        save();
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("chainLength", CHAIN_LENGTH.get());
        root.addProperty("unbreakableChain", UNBREAKABLE_CHAIN.get());
        root.addProperty("preventTeleport", PREVENT_TELEPORT.get());
        root.addProperty("sharedRespawn", SHARED_RESPAWN.get());
        root.addProperty("autoRespawn", AUTO_RESPAWN.get());
        root.addProperty("chainTransparency", CHAIN_TRANSPARENCY.get());

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            ChainTogether.LOGGER.error("Failed to save ChainTogether config", exception);
        }
    }

    public static float getTransparencyAsFloat() {
        return CHAIN_TRANSPARENCY.get() / 100.0f;
    }

    public static double getDefaultDistanceMultiplier() {
        return CHAIN_LENGTH.get();
    }

    private static void setDefaults() {
        CHAIN_LENGTH.reset();
        UNBREAKABLE_CHAIN.reset();
        PREVENT_TELEPORT.reset();
        SHARED_RESPAWN.reset();
        AUTO_RESPAWN.reset();
        CHAIN_TRANSPARENCY.reset();
    }

    private static double readDouble(JsonObject root, String key, double fallback) {
        return root.has(key) ? root.get(key).getAsDouble() : fallback;
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    public static final class ChainConfigSpec {
        public void save() {
            ChainConfig.save();
        }
    }

    public static final class ConfigValue<T> {
        private final T defaultValue;
        private final Double min;
        private final Double max;
        private T value;

        private ConfigValue(T defaultValue, Double min, Double max) {
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.value = defaultValue;
        }

        public static ConfigValue<Double> doubleValue(double defaultValue, double min, double max) {
            return new ConfigValue<>(defaultValue, min, max);
        }

        public static ConfigValue<Integer> intValue(int defaultValue, int min, int max) {
            return new ConfigValue<>(defaultValue, (double) min, (double) max);
        }

        public static ConfigValue<Boolean> booleanValue(boolean defaultValue) {
            return new ConfigValue<>(defaultValue, null, null);
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = clamp(value);
        }

        public void reset() {
            value = defaultValue;
        }

        @SuppressWarnings("unchecked")
        private T clamp(T candidate) {
            if (candidate instanceof Double doubleValue && min != null && max != null) {
                return (T) Double.valueOf(Math.max(min, Math.min(max, doubleValue)));
            }
            if (candidate instanceof Integer integerValue && min != null && max != null) {
                return (T) Integer.valueOf((int) Math.max(min, Math.min(max, integerValue)));
            }
            return candidate;
        }
    }
}
