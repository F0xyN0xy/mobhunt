# ☠ MobHunt Mod (Fabric) — Kill or Die!

A Minecraft **Fabric** mod that forces every player to kill a mob on a repeating countdown timer — or die instantly. Supports **1.21–1.21.x**, customizable timers, and multiplayer out of the box.

---

## ⚔ How It Works

1. Players join → placed in **Adventure mode** (lobby) until the host starts the game
2. Host runs `/start` → all players switch to **Survival**, timers begin immediately
3. A **live countdown** ticks on every player's action bar (above the hotbar)
4. Kill any hostile or passive mob → timer resets to full
5. **Run out of time → instant death**, server announces it to everyone
6. Host can end the game at any time with `/stophunt`

### Warning Messages

| Time Remaining | Color | Message |
|---|---|---|
| 2:30 | 🟡 Yellow | Warning in chat |
| 1:00 | 🔴 Red | Urgent warning |
| 0:10 | ☠ Dark Red Bold | Final countdown |
| 0:00 | 💀 | Instant death, server broadcast |

### Action Bar Colours

The kill timer colour scales with how much time is left relative to the total:

| Time Remaining | Colour |
|---|---|
| > 50% of total | 🟢 Green |
| 10–50% of total | 🟡 Yellow |
| < 10% of total | 🔴 Red |

---

## 🎮 Commands

| Command | Description |
|---|---|
| `/start` | Start the game with the default 10-minute timer |
| `/start <minutes>` | Start with a custom timer (1–120 minutes) |
| `/stophunt` | Stop the game, reset all timers, return players to lobby |

**Examples:**
```
/start          → 10:00 timer (default)
/start 5        → 5:00 timer
/start 30       → 30:00 timer
```

> **Note:** These commands have no permission restriction by default — any player can run them. If you want to restrict them to ops only, wrap the executor in a `.requires(src -> src.hasPermissionLevel(2))` check in `MobHuntMod.java`.

---

## 🛠 Installation

### Requirements

- **Minecraft Java Edition 1.21–1.21.x**
- **Fabric Loader 0.16.0+** → [Install here](https://fabricmc.net/use/installer/)
- **Fabric API** (matching your MC version) → [Download here](https://modrinth.com/mod/fabric-api)
- **Mod Menu** *(optional, for mod list UI)* → [Download here](https://modrinth.com/mod/modmenu)

### Mod Menu Version Reference

Mod Menu releases are version-specific. Use the right one for your Minecraft version:

| Minecraft | Mod Menu |
|---|---|
| 1.21 / 1.21.1 | 11.x (e.g. `11.0.4`) |
| 1.21.3 | 12.x (e.g. `12.0.1`) |
| 1.21.4 | 13.x (e.g. `13.0.4`) |
| 1.21.5 | 14.x (e.g. `14.0.2`) |

MobHunt compiles against `11.0.4` (the lowest common API) and marks Mod Menu as optional, so it works correctly regardless of which version you install — or without it entirely.

### Steps

1. Install Fabric Loader 0.16.0+ for your MC version
2. Download Fabric API and place it in your `mods/` folder
3. Place `mobhunt-<version>.jar` in your `mods/` folder
4. *(Optional)* Add the correct Mod Menu jar for your MC version
5. Launch Minecraft with the Fabric profile

---

## 🔨 Building from Source

### Prerequisites

- **JDK 21** → [Download](https://adoptium.net/)
- **Git**

### Steps

```bash
git clone https://github.com/F0xyN0xy/mobhunt
cd mobhunt
./gradlew build
```

Output: `build/libs/mobhunt-<version>.jar`

On Windows use `gradlew.bat build` instead.

---

## ⚙ Customizing the Timer (default)

The default timer when `/start` is run with no argument is defined at the top of `MobHuntMod.java`:

```java
public static final int DEFAULT_KILL_TIMER_TICKS = 12000; // 10 minutes
```

Common values:

| Duration | Ticks |
|---|---|
| 3 minutes | 3600 |
| 5 minutes | 6000 |
| 10 minutes | 12000 |
| 15 minutes | 18000 |
| 20 minutes | 24000 |

At runtime, any timer between 1 and 120 minutes can be set via `/start <minutes>` without touching the code.

---

## 📁 Project Structure

```
mobhunt/
├── build.gradle              ← Dependencies and build config
├── gradle.properties         ← Versions (MC, Fabric, Mod Menu, mod)
├── settings.gradle           ← Gradle root project name and plugin repos
└── src/main/
    ├── java/com/mobhunt/
    │   └── MobHuntMod.java   ← All mod logic lives here (single file)
    └── resources/
        ├── assets/mobhunt/
        │   └── icon.png      ← Mod icon shown in Mod Menu
        └── fabric.mod.json   ← Mod metadata, dependencies, entrypoints
```

---

## 🧱 Architecture Notes (for contributors)

All game state is held in `static` fields on `MobHuntMod`:

```java
private static boolean gameActive         // whether a game is running
private static int killTimerTicks         // current timer length in ticks
private static Map<UUID, Integer> ticksSinceKill  // per-player tick counter
```

**Why `static`?** Fabric calls `onInitialize()` once per JVM session. Static fields survive across world loads within the same game launch, which is what keeps the timer running correctly when a player leaves and rejoins mid-game.

**Why no `SERVER_STOPPING` reset?** Resetting state on server stop caused the game to forget it had started whenever a singleplayer world was exited (the integrated server stops on world exit). State is intentionally only reset by `/stophunt`. If you need persistence across full game restarts, the right approach is to serialise `gameActive`, `killTimerTicks`, and `ticksSinceKill` to a file in the world's save directory using `ServerLifecycleEvents.SERVER_STARTED` / `SERVER_STOPPING`.

### Key Events Used

| Event | Purpose |
|---|---|
| `CommandRegistrationCallback` | Register `/start` and `/stophunt` |
| `ServerTickEvents.END_SERVER_TICK` | Advance per-player timers every tick |
| `ServerLivingEntityEvents.AFTER_DEATH` | Detect mob kills and reset the killer's timer |
| `ServerPlayConnectionEvents.JOIN` | Put joining players in the right mode; restore their timer |

### Adding New Features

**New commands:** register additional `CommandManager.literal(...)` blocks inside the existing `CommandRegistrationCallback` lambda.

**Persistence across restarts:** hook `ServerLifecycleEvents.SERVER_STARTED` to read a JSON file from the world save directory, and `SERVER_STOPPING` to write it. Use Gson (already on the classpath via Minecraft) for serialisation.

**Per-player scores / kill counts:** add a `Map<UUID, Integer> killCount` field alongside `ticksSinceKill` and increment it in the `AFTER_DEATH` handler.

**Spectator on death (true hardcore feel):** in the `remaining <= 0` block, replace `player.kill()` with `player.changeGameMode(GameMode.SPECTATOR)` and add the UUID to a `Set<UUID> eliminated` so the tick loop skips them.

---

## 🎮 Gameplay Tips

- **Passive mobs count** — cows, chickens, pigs, sheep all reset the timer if you're desperate
- Works in **multiplayer** — every player has their own independent timer
- Pair with a **Hardcore world** for permanent death on elimination
- The timer survives leaving and rejoining the same session — you can't escape it by logging out

---

## 📜 License

MIT — free to use, modify, and share.