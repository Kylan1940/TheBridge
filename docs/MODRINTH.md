# TheBridge

**A classic Bridge minigame for your server: fight across bridges over the void and score in the enemy goal.**

TheBridge lets you build as many Bridge arenas as you like, 1v1 or 2v2. It is built for server owners: everything is set up with in-game commands and `config.yml`, no coding needed.

## Features
- 1v1 and 2v2 arenas, as many arenas as you want
- Setup commands with clear error messages, an arena cannot be enabled while something is missing
- Automatic goal detection, respawn delay, void deaths, kill credit
- Your own starting equipment and building blocks in `config.yml`
- Arenas reset automatically after every match (only the changed blocks, in small batches, so no lag)
- Players get their inventory, game mode, health and position back after every match, even after a server crash
- SQLite statistics: wins, losses, goals, kills, deaths, win streak
- Titles, action bar, sounds, optional console-command rewards
- Every message is editable

## Requirements
- Java 21
- Paper 1.21+ (or a Paper fork). Folia is not supported.

## Quick start
1. Put the jar in `plugins/` and restart.
2. `/thebridge create <arena>`, set lobby, spawns, goals and bounds (`/thebridge help`).
3. `/thebridge enable <arena>`, then players use `/thebridge join <arena>`.

## Permissions
`thebridge.join`, `thebridge.leave`, `thebridge.stats` (everyone by default), `thebridge.admin` and `thebridge.bypass` (OP).

## Notes
TheBridge is an independent plugin and is not affiliated with Hypixel. Found a bug? Please open an issue on GitHub: https://github.com/Kylan1940/TheBridge/issues
