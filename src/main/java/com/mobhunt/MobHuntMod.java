package com.mobhunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.world.GameMode;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobHuntMod implements ModInitializer {

    public static final String MOD_ID = "mobhunt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 10 minutes = 12000 ticks. Change this to adjust the timer!
    public static final int KILL_TIMER_TICKS = 12000;

    // Ticks since last kill per player
    private static final Map<UUID, Integer> ticksSinceKill = new HashMap<>();
    // Whether the player has killed their first mob (to start the timer)
    private static final Map<UUID, Boolean> timerStarted = new HashMap<>();
    // Whether the game has been started (globally)
    private static boolean gameActive = false;
    // Game mode: "survival" or "hardcore"
    private static String gameModeSetting = "survival";

    @Override
    public void onInitialize() {
        LOGGER.info("MobHunt Mod loaded! Use /start survival or /start hardcore to begin.");

        // ── COMMAND REGISTRATION ─────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("start")
                            // /start survival
                            .then(CommandManager.argument("mode", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        builder.suggest("survival");
                                        builder.suggest("hardcore");
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String mode = StringArgumentType.getString(ctx, "mode");
                                        if (!mode.equals("survival") && !mode.equals("hardcore")) {
                                            ctx.getSource().sendError(Text.literal(
                                                            "Usage: /start survival  or  /start hardcore")
                                                    .formatted(Formatting.RED));
                                            return 0;
                                        }
                                        return startGame(ctx.getSource().getServer(), mode);
                                    })
                            )
                            // /start (no argument — defaults to survival)
                            .executes(ctx -> {
                                ctx.getSource().sendError(Text.literal(
                                                "Usage: /start survival  or  /start hardcore")
                                        .formatted(Formatting.RED));
                                return 0;
                            })
            );
        });

        // ── SERVER TICK: count up timers and kill slow players ───────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!gameActive) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();

                if (!timerStarted.getOrDefault(uuid, false)) continue;

                int ticks = ticksSinceKill.getOrDefault(uuid, 0) + 1;
                ticksSinceKill.put(uuid, ticks);

                int remaining = KILL_TIMER_TICKS - ticks;
                int remSec = remaining / 20;

                // Update action bar every second
                if (ticks % 20 == 0) {
                    sendActionBar(player, remSec);
                }

                // Warning messages
                if (remaining == 3000) {
                    player.sendMessage(Text.literal("⚠ MobHunt: 2:30 left — KILL SOMETHING!")
                            .formatted(Formatting.YELLOW), false);
                } else if (remaining == 1200) {
                    player.sendMessage(Text.literal("☠ MobHunt: 1 MINUTE LEFT! KILL NOW!")
                            .formatted(Formatting.RED), false);
                } else if (remaining == 200) {
                    player.sendMessage(Text.literal("💀 MobHunt: 10 SECONDS!")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                }

                // Time's up — kill the player
                if (remaining <= 0) {
                    player.kill();
                    player.sendMessage(Text.literal("☠ You failed to kill a mob in time! Game Over.")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD), false);

                    server.getPlayerManager().broadcast(
                            Text.literal("☠ " + player.getName().getString() +
                                            " was too slow! They didn't kill a mob in time.")
                                    .formatted(Formatting.DARK_RED),
                            false
                    );

                    ticksSinceKill.remove(uuid);
                    timerStarted.remove(uuid);
                }
            }
        });

        // ── MOB DEATH: check if a player landed the kill ─────────────────────
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!gameActive) return;
            if (entity instanceof PlayerEntity) return;
            if (!(entity instanceof HostileEntity || entity instanceof AnimalEntity)) return;

            if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
                UUID uuid = killer.getUuid();

                if (!timerStarted.getOrDefault(uuid, false)) {
                    timerStarted.put(uuid, true);
                    ticksSinceKill.put(uuid, 0);
                    killer.sendMessage(Text.literal(
                                    "✅ MobHunt timer started! Kill a mob every 10 minutes or you die!")
                            .formatted(Formatting.GREEN, Formatting.BOLD), false);
                } else {
                    ticksSinceKill.put(uuid, 0);
                    killer.sendMessage(Text.literal("✅ Timer reset! Next kill required in 10:00")
                            .formatted(Formatting.GREEN), false);
                }
            }
        });

        // ── PLAYER JOIN: put them in Adventure mode lobby ────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!gameActive) {
                // Lobby: adventure mode, can't interact with the world
                player.changeGameMode(GameMode.ADVENTURE);
                player.sendMessage(Text.literal(
                                "⚔ MobHunt Lobby — Waiting for the host to start the game.")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.sendMessage(Text.literal(
                                "The host can run: /start survival  or  /start hardcore")
                        .formatted(Formatting.GRAY), false);
            } else {
                // Late join during an active game — put them in the right mode
                applyGameMode(player);
                player.sendMessage(Text.literal(
                                "⚔ MobHunt is active! Kill a mob to start your 10-minute timer.")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
        });

        // ── PLAYER LEAVE: clean up data ───────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            ticksSinceKill.remove(uuid);
            timerStarted.remove(uuid);
        });
    }

    /**
     * Start the MobHunt game for all online players.
     */
    private int startGame(net.minecraft.server.MinecraftServer server, String mode) {
        if (gameActive) {
            server.getPlayerManager().broadcast(
                    Text.literal("⚠ MobHunt is already running!")
                            .formatted(Formatting.YELLOW),
                    false
            );
            return 0;
        }

        gameActive = true;
        gameModeSetting = mode;

        // Announce and switch all players to the chosen game mode
        server.getPlayerManager().broadcast(
                Text.literal("⚔ MobHunt has started in " + mode.toUpperCase() + " mode! " +
                                "Kill a mob to start your 10-minute timer!")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                false
        );

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyGameMode(player);
            player.sendMessage(Text.literal(
                            "✅ You are now in " + mode + " mode. Kill your first mob to start the timer!")
                    .formatted(Formatting.GREEN), false);
        }

        LOGGER.info("MobHunt started in {} mode.", mode);
        return 1;
    }

    /**
     * Apply the correct game mode to a player based on gameModeSetting.
     * Note: Fabric doesn't have a true "hardcore" GameMode — we use Survival
     * and rely on the server's hardcore flag, or you can set it per-world.
     * If you want the kill-on-death behaviour of hardcore, handle it in the
     * AFTER_DEATH event above (e.g. ban the player or set them to spectator).
     */
    private void applyGameMode(ServerPlayerEntity player) {
        if (gameModeSetting.equals("hardcore")) {
            player.changeGameMode(GameMode.SURVIVAL);
            // Optional: mark the player as "hardcore" — they get spectator on death
            // You could add a Set<UUID> hardcorePlayers and handle it in AFTER_DEATH.
        } else {
            player.changeGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Send a countdown to the player's action bar (above hotbar).
     */
    private void sendActionBar(ServerPlayerEntity player, int secondsLeft) {
        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;

        Formatting color;
        String icon;
        if (secondsLeft > 300) {
            color = Formatting.GREEN;
            icon = "⚔";
        } else if (secondsLeft > 60) {
            color = Formatting.YELLOW;
            icon = "⚠";
        } else {
            color = Formatting.RED;
            icon = "☠";
        }

        String timeStr = String.format("%s Kill timer: %d:%02d", icon, minutes, seconds);
        player.networkHandler.sendPacket(
                new OverlayMessageS2CPacket(Text.literal(timeStr).formatted(color))
        );
    }
}