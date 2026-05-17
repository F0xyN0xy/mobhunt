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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.world.GameMode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobHuntMod implements ModInitializer {

    public static final String MOD_ID = "mobhunt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final int DEFAULT_KILL_TIMER_TICKS = 12000;

    private static int killTimerTicks = DEFAULT_KILL_TIMER_TICKS;
    private static final Map<UUID, Integer> ticksSinceKill = new HashMap<>();
    private static boolean gameActive = false;

    @Override
    public void onInitialize() {
        LOGGER.info("MobHunt Mod loaded! Use /start [minutes] to begin.");

        // ── COMMAND REGISTRATION ─────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("start")
                            .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, 120))
                                    .executes(ctx -> {
                                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                        return startGame(ctx.getSource().getServer(), minutes * 60 * 20);
                                    })
                            )
                            .executes(ctx -> startGame(ctx.getSource().getServer(), DEFAULT_KILL_TIMER_TICKS))
            );

            // /stophunt — resets everything so a new game can begin
            dispatcher.register(
                    CommandManager.literal("stophunt")
                            .executes(ctx -> stopGame(ctx.getSource().getServer()))
            );
        });

        // ── SERVER TICK ──────────────────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!gameActive) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();

                int ticks = ticksSinceKill.getOrDefault(uuid, 0) + 1;
                ticksSinceKill.put(uuid, ticks);

                int remaining = killTimerTicks - ticks;

                if (ticks % 20 == 0) {
                    sendActionBar(player, remaining / 20, killTimerTicks / 20);
                }

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

                if (remaining <= 0) {
                    player.kill();
                    player.sendMessage(Text.literal("☠ You failed to kill a mob in time! Game Over.")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                    server.getPlayerManager().broadcast(
                            Text.literal("☠ " + player.getName().getString() +
                                            " was too slow! They didn't kill a mob in time.")
                                    .formatted(Formatting.DARK_RED), false);
                    ticksSinceKill.remove(uuid);
                }
            }
        });

        // ── MOB DEATH ────────────────────────────────────────────────────────
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!gameActive) return;
            if (entity instanceof PlayerEntity) return;
            if (!(entity instanceof HostileEntity || entity instanceof AnimalEntity)) return;

            if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
                ticksSinceKill.put(killer.getUuid(), 0);
                int totalSec = killTimerTicks / 20;
                int min = totalSec / 60;
                int sec = totalSec % 60;
                killer.sendMessage(Text.literal(
                                String.format("✅ Timer reset! Next kill required in %d:%02d", min, sec))
                        .formatted(Formatting.GREEN), false);
            }
        });

        // ── PLAYER JOIN ──────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!gameActive) {
                player.changeGameMode(GameMode.ADVENTURE);
                player.sendMessage(Text.literal(
                                "⚔ MobHunt Lobby — Waiting for the host to start the game.")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.sendMessage(Text.literal(
                                "Host: /start [minutes]  (default: 10 minutes)")
                        .formatted(Formatting.GRAY), false);
            } else {
                player.changeGameMode(GameMode.SURVIVAL);
                if (!ticksSinceKill.containsKey(player.getUuid())) {
                    ticksSinceKill.put(player.getUuid(), 0);
                }
                int remaining = (killTimerTicks - ticksSinceKill.get(player.getUuid())) / 20;
                int min = remaining / 60;
                int sec = remaining % 60;
                player.sendMessage(Text.literal(
                                String.format("⚔ MobHunt is active! Time remaining: %d:%02d", min, sec))
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
        });

        // ── PLAYER LEAVE ─────────────────────────────────────────────────────
        // Timer is intentionally kept on disconnect so it survives rejoins.
        // It is only cleared on death, or when /stophunt is used.
    }

    private static int startGame(net.minecraft.server.MinecraftServer server, int timerTicks) {
        if (gameActive) {
            server.getPlayerManager().broadcast(
                    Text.literal("⚠ MobHunt is already running!").formatted(Formatting.YELLOW), false);
            return 0;
        }

        gameActive = true;
        killTimerTicks = timerTicks;

        int totalSec = timerTicks / 20;
        int min = totalSec / 60;
        int sec = totalSec % 60;

        server.getPlayerManager().broadcast(
                Text.literal(String.format("⚔ MobHunt started! You have %d:%02d to kill a mob — GO!", min, sec))
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.changeGameMode(GameMode.SURVIVAL);
            ticksSinceKill.put(player.getUuid(), 0);
        }

        LOGGER.info("MobHunt started with a {}:{} timer.", min, String.format("%02d", sec));
        return 1;
    }

    private static int stopGame(net.minecraft.server.MinecraftServer server) {
        if (!gameActive) {
            server.getPlayerManager().broadcast(
                    Text.literal("⚠ MobHunt is not running!").formatted(Formatting.YELLOW), false);
            return 0;
        }

        gameActive = false;
        killTimerTicks = DEFAULT_KILL_TIMER_TICKS;
        ticksSinceKill.clear();

        server.getPlayerManager().broadcast(
                Text.literal("🛑 MobHunt has been stopped. Use /start to begin a new game.")
                        .formatted(Formatting.RED, Formatting.BOLD), false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.changeGameMode(GameMode.ADVENTURE);
        }

        LOGGER.info("MobHunt stopped.");
        return 1;
    }

    private static void sendActionBar(ServerPlayerEntity player, int secondsLeft, int totalSec) {
        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;

        Formatting color;
        String icon;
        if (secondsLeft > totalSec / 2) {
            color = Formatting.GREEN;
            icon = "⚔";
        } else if (secondsLeft > totalSec / 10) {
            color = Formatting.YELLOW;
            icon = "⚠";
        } else {
            color = Formatting.RED;
            icon = "☠";
        }

        String timeStr = String.format("%s Kill timer: %d:%02d", icon, minutes, seconds);
        player.networkHandler.sendPacket(
            new GameMessageS2CPacket(Text.literal(timeStr).formatted(color), true)
        );
    }
}