# Description
TheBridge is a Minecraft Java Server minigame plugin. Two teams (1v1 or 2v2) fight on bridges over the void. Build blocks toward the enemy, fight, and score by entering the enemy goal. First team to the score limit wins. If you have a question, please make an [issue](https://github.com/Kylan1940/TheBridge/issues/new).

TheBridge is an independent plugin. It is not affiliated with Hypixel or any other server network.

# Features
>- 1v1 and 2v2 (any team size up to `match.max-team-size`)
>- Multiple arenas, each saved in its own file
>- Arena states: `DISABLED`, `WAITING`, `STARTING`, `IN_GAME`, `ENDING`, `RESETTING`
>- Setup validation with clear errors (an arena can't be enabled while something is missing)
>- Goal detection without commands, duplicate-goal protection
>- Respawn delay, void death, kill credit for void kills
>- Configurable equipment, buildable blocks, break rules
>- Arena reset: only changed blocks are tracked and restored in small batches
>- Crash recovery for players (inventory, location, game mode ...) and for arena blocks
>- SQLite statistics (wins, losses, goals, kills, deaths, matches, win streak), saved on a background thread
>- Titles, action bar, sounds, optional console-command rewards
>- Permissions and tab completion

# Installation
1. Download the plugin.
2. Put it into the **plugins** folder
3. Restart the server
4. Create an arena (see [Arena setup](#arena-setup-example))

# Compatibility
- Java **21** is required.
- Built against **Paper API 1.21.8** with `api-version: 1.21`. The code uses only Bukkit/Paper API (no NMS), so other Paper-based servers (Purpur, Pufferfish ...) are expected to work. Spigot is **not** a declared target because the plugin uses Paper's Adventure API.
- **Folia is not supported.** TheBridge uses the normal Bukkit scheduler and does not declare `folia-supported`.
- Only 1.21.x was considered. Nothing has been tested on a server yet, so no version is "verified". Please report what works for you.

# Commands
Main command: `/thebridge` (aliases: `/tb`, `/bridge`)

| Command                                  | Description                                                   | Permission        |
|------------------------------------------|---------------------------------------------------------------|-------------------|
| `/thebridge help`                        | Show the commands                                             | none              |
| `/thebridge list`                        | List arenas with state and players                            | `thebridge.join`  |
| `/thebridge join <arena>`                | Join an arena                                                 | `thebridge.join`  |
| `/thebridge leave`                       | Leave your match                                              | `thebridge.leave` |
| `/thebridge stats [player]`              | Show statistics                                               | `thebridge.stats` |
| `/thebridge create <arena>`              | Create an arena in your current world                         | `thebridge.admin` |
| `/thebridge setlobby <arena>`            | Set the waiting room to your position                         | `thebridge.admin` |
| `/thebridge setspawn <arena> <red\|blue>` | Set a team spawn to your position                             | `thebridge.admin` |
| `/thebridge pos1` / `pos2`               | Select two corners (used by `setgoal` and `setbounds`)        | `thebridge.admin` |
| `/thebridge setgoal <arena> <red\|blue>`  | Set the goal that team **defends** = the selected box         | `thebridge.admin` |
| `/thebridge setbounds <arena>`           | Set the arena box = the selected box (its bottom is the void) | `thebridge.admin` |
| `/thebridge teamsize <arena> <n>`        | 1 = 1v1, 2 = 2v2 ...                                          | `thebridge.admin` |
| `/thebridge scorelimit <arena> <n>`      | Goals to win for this arena (0 = use config.yml)              | `thebridge.admin` |
| `/thebridge duration <arena> <seconds>`  | Match length for this arena (0 = use config.yml)              | `thebridge.admin` |
| `/thebridge enable <arena>`              | Validate and open the arena                                   | `thebridge.admin` |
| `/thebridge disable <arena>`             | Close the arena (safely cancels a running match)              | `thebridge.admin` |
| `/thebridge stop <arena>`                | Cancel the running match (no winner, no stats)                | `thebridge.admin` |
| `/thebridge info <arena>`                | Show the arena setup and any problems                         | `thebridge.admin` |
| `/thebridge delete <arena>`              | Delete a disabled arena                                       | `thebridge.admin` |
| `/thebridge reload`                      | Reload config.yml (not the arena files)                       | `thebridge.admin` |

Differences from the first draft: `pos1`/`pos2`, `teamsize`, `scorelimit`, `duration` and `stop` were added, because goals and bounds are boxes and need two corners. Setup commands only work while the arena is **disabled**, so spawns can't change under running players.

# Permissions
| Permission         | Default | Purpose                                                                                                                                                              |
|--------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `thebridge.admin`  | OP      | All setup/management commands. Also allows any command during a match.                                                                                               |
| `thebridge.join`   | true    | Join arenas and list them.                                                                                                                                           |
| `thebridge.leave`  | true    | Leave a match.                                                                                                                                                       |
| `thebridge.stats`  | true    | View statistics.                                                                                                                                                     |
| `thebridge.bypass` | OP      | Build/break inside an arena that is **empty and waiting** (for editing the map). It never works for players in a match or inside an arena with a match, on purpose. |

# Arena setup example
Build your map first (two islands, a void in between, a goal on each side). Then, standing in the map's world:

```
/thebridge create lava
/thebridge setlobby lava              (stand in the waiting room)
/thebridge setspawn lava red          (stand on the red spawn)
/thebridge setspawn lava blue
/thebridge pos1                       (stand in one corner of the RED goal)
/thebridge pos2                       (stand in the opposite corner)
/thebridge setgoal lava red           (red DEFENDS this goal, blue scores here)
/thebridge pos1                       (corners of the BLUE goal)
/thebridge pos2
/thebridge setgoal lava blue
/thebridge pos1                       (a box around the WHOLE arena, from the void level up to above the highest build height)
/thebridge pos2
/thebridge setbounds lava
/thebridge teamsize lava 1            (1v1; use 2 for 2v2)
/thebridge enable lava
```

If something is missing, `enable` tells you exactly what. Players then use `/thebridge join lava`. The match starts when the arena is full.

**Rules of thumb:** the bottom of the bounds is the void: falling below it is a death. Spawns must be inside the bounds and not inside a goal. The lobby can be anywhere.

# Testing alone (solo test mode)
No friend or second account? Set this in `config.yml` and run `/thebridge reload`:

```yaml
match:
  solo-test: true
```

Then join an arena alone: the countdown starts with one player. You are team RED and score by entering the **blue** goal. You can test the countdown, goals, void deaths, building rules, bridge reset and the end of the match. You can **not** test fighting, kills and forfeits (that needs an opponent).
Matches in solo test mode never write to the statistics and never give rewards. The setting is read when a player joins an empty arena, so switch it on or off while no match is waiting. **Turn it off again on a real server.**
To test with two players on your own PC you can also run a second client with an offline account on a local server (`online-mode=false`).

# Match flow
1. Players join; when the arena is full a countdown starts (`match.countdown`).
2. Teams are assigned, players are teleported and equipped, a short freeze (`match.round-countdown`) starts every round.
3. Entering the **enemy** goal scores one point. Players and (optionally) bridges are reset.
4. The match ends at `match.score-limit` goals, or when `match.duration` runs out (more goals wins, equal goals = draw).
5. Results are shown, players get their old inventory and position back, the arena resets and opens again.

Leaving or disconnecting mid-match counts as leaving: you get your own items back immediately, and if a team has nobody left the other team wins. Rejoining the **match** is not supported. Rejoining the **server** after a crash restores your items and position.

# Configuration
Everything is in `plugins/TheBridge/config.yml` (documented inside the file): countdown, duration, score limit, respawn delay, team size limit, allowed blocks, break rules, equipment (`{team}` becomes RED/BLUE), reset behaviour, statistics, rewards, effects and all messages. Invalid numbers are corrected with a warning in the console. Per-arena values live in `plugins/TheBridge/arenas/<name>.yml`.

When a new plugin version needs a newer `config-version`, your old file is renamed to `config-<version>.yml` and a fresh one is written (same as in my other plugins).

# Arena reset limitations
The reset restores the **type and block state** (rotation, waterlogged, ...) of every block a player placed or broke, a few hundred per tick. It does **not** restore:
- items inside containers (players can't open containers in a match),
- entities other than dropped items and arrows (those are removed),
- sand/gravel falling, redstone, TNT chains, changes by other plugins or commands. Don't put gravity blocks or redstone in `build.allowed-blocks`,
- fluids: buckets are blocked and liquid flow is cancelled inside arenas.

Crash safety: the list of changed blocks is saved to `plugins/TheBridge/journals/` every `reset.journal-interval` seconds. After a crash the arena is restored from it on the next start. Blocks changed in the last few seconds before a crash can be missed.

# Statistics
SQLite file `plugins/TheBridge/stats.db`. All database work runs on one background thread, so the server never waits for the disk. A database problem is logged and never stops a match. The storage code is behind the `Storage` interface so MySQL/MariaDB can be added later; only SQLite exists now.

# Building
Requirements: JDK 21 and Maven.

```
mvn clean package
```

The plugin jar is `target/TheBridge-<version>.jar`. Tests run automatically; `mvn clean package -DskipTests` skips them.

# Manual test checklist
Things that need a real server (not covered by the automated tests):
- [ ] Plugin enables without errors, `/thebridge help` works
- [ ] Setup of a 1v1 arena as above, `enable` accepts it
- [ ] Two players: countdown, teleport, equipment, frozen start
- [ ] Scoring in the enemy goal, no score in your own goal, no double score
- [ ] Death by player, by void; respawn delay; kill credit for a void kill
- [ ] Place and break blocks: only allowed blocks, only inside the bounds, goals protected
- [ ] End of match: results, inventory/position restored, bridges removed
- [ ] `/thebridge leave` and quitting in each state (waiting, countdown, running, ending)
- [ ] `/thebridge disable` during a running match; `/stop` and plugin disable in the middle of a match
- [ ] Kill the server process in a match, restart: arena restored, player gets items back on join
- [ ] 2v2 with a teammate leaving
- [ ] `match.solo-test: true`: one player can start a match and no stats are written
- [ ] `/thebridge stats` after a match

# Troubleshooting
| Problem                                   | What to check                                                                                                 |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `enable` lists problems                   | Fix each line; `/thebridge info <arena>` shows the same list                                                  |
| "World ... is not loaded"                 | The arena's world must be loaded before TheBridge enables (world plugins: load them first)                    |
| "Arena stays disabled" in the console     | Same list as above, logged at startup                                                                         |
| Players can't place blocks                | The block must be in `build.allowed-blocks`, inside the bounds, outside the goals and the round must be running |
| Goal doesn't count                        | You must enter the goal of the **other** team, and the goal box must be inside the bounds                     |
| Players die instantly                     | The bottom of `setbounds` is the void. Make sure it is below your islands                                     |
| Stats say "not available"                 | `stats.enabled: true`? Look for a `SQLite` error in the console                                               |
| Bridges not removed                       | Check the console for "Cannot restore a block" (world unloaded), see limitations above                        |

# Project structure
```
org.kylan1940.thebridge
  TheBridge              main class: creates everything, wires it together
  arena/                 Arena (data + validation), ArenaState, ArenaManager (save/load), Point, Region
  match/                 Match (the rules of ONE match), MatchManager (runs matches), MatchTeam
  player/                PlayerSnapshot (save/restore a player), EquipmentGiver (kits)
  reset/                 BlockTracker (what changed), ArenaResetter (puts it back), BlockKey
  stats/                 StatsManager (background thread), PlayerStats, PlayerResult, MatchRecord
  storage/               Storage (interface), SQLiteStorage, StorageException
  command/               BridgeCommand, BridgeTabCompleter
  listener/              BlockListener (building), PlayerListener (moving, damage, quitting)
  config/                GameSettings (checked numbers from config.yml)
  message/               MessageUtil (messages, titles, sounds)
  utils/                 ConfigUpdater, LocationUtil
```

# License
Apache License 2.0, see [LICENSE](LICENSE).