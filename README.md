# ☠ MobHunt Mod (Fabric) — Kill or Die!

A Minecraft **Fabric** mod for **Minecraft 1.21** that forces you to kill a mob every **10 minutes** or you die instantly. Perfect for Hardcore worlds!

---

## ⚔ How It Works

1. Join a world — you get a welcome message
2. **Kill your first mob** → 10-minute timer starts
3. A **countdown shows on your action bar** (above hotbar) every second
4. Kill any mob (hostile OR passive) to reset the clock
5. **Fail to kill in time → instant death!**

### ⚠ Warning Messages
| Time Remaining | Color | Message |
|---|---|---|
| 2:30 | 🟡 Yellow | Warning in chat |
| 1:00 | 🔴 Red | Urgent warning |
| 0:10 | ☠ Dark Red Bold | Final countdown |
| 0:00 | 💀 | You die, server notified |

---

## 🛠 Installation (No build needed!)

### Requirements
- **Minecraft Java Edition 1.21**
- **Fabric Loader 0.15+** → [Install here](https://fabricmc.net/use/installer/)
- **Fabric API 0.92+** → [Download here](https://modrinth.com/mod/fabric-api/versions?g=1.20.1)

### Steps
1. Install Fabric Loader for 1.21
2. Download Fabric API and put it in your `mods/` folder
3. Put `mobhunt-1.0.0.jar` in your `.minecraft/mods/` folder
4. Launch Minecraft with the Fabric profile
5. Create a **Hardcore world** for the ultimate experience!

---

## 🔨 Building from Source

### Prerequisites
- **JDK 17** or **JDK 21** → [Download](https://adoptium.net/)
- **Git**

### Steps
```bash
git clone <your-repo>
cd mobhunt-fabric
./gradlew build
```

Output: `build/libs/mobhunt-1.0.0.jar`

---

## ⚙ Customizing the Timer

Open `src/main/java/com/mobhunt/MobHuntMod.java` and change:

```java
public static final int KILL_TIMER_TICKS = 12000; // 10 minutes
```

| Duration | Ticks |
|---|---|
| 5 minutes | 6000 |
| 10 minutes | 12000 |
| 15 minutes | 18000 |
| 20 minutes | 24000 |

---

## 📁 File Structure

```
mobhunt-fabric/
├── build.gradle
├── gradle.properties
├── settings.gradle
└── src/main/
    ├── java/com/mobhunt/
    │   └── MobHuntMod.java       ← All mod logic (one file!)
    └── resources/
        └── fabric.mod.json       ← Mod metadata
```

---

## 🎮 Tips

- **Passive mobs count!** Cows, chickens, pigs — if you're desperate, kill one
- Keep a sword in your hotbar at ALL times
- Works in **multiplayer** — everyone has their own independent timer
- **Hardcore mode** is the intended experience!

---

## 📜 License
MIT — free to use and share!
