package com.mobhunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class MobHuntConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("mobhunt.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Settings ─────────────────────────────────────────────────────────────

    public enum KillMode {
        ALL,        // any LivingEntity except players
        HOSTILE,    // HostileEntity only
        PASSIVE     // PassiveEntity / AnimalEntity / AmbientEntity etc (not hostile)
    }

    public int defaultTimerMinutes = 10;
    public KillMode killMode = KillMode.ALL;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static MobHuntConfig instance;

    public static MobHuntConfig get() {
        if (instance == null) load();
        return instance;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(r, MobHuntConfig.class);
                // Null-guard in case new fields were added since last save
                if (instance.killMode == null) instance.killMode = KillMode.ALL;
                return;
            } catch (IOException e) {
                MobHuntMod.LOGGER.error("Failed to load MobHunt config, using defaults.", e);
            }
        }
        instance = new MobHuntConfig();
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            MobHuntMod.LOGGER.error("Failed to save MobHunt config.", e);
        }
    }
}