package org.kylan1940.thebridge.match;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.kylan1940.thebridge.TheBridge;
import org.kylan1940.thebridge.arena.Arena;
import org.kylan1940.thebridge.arena.ArenaState;
import org.kylan1940.thebridge.arena.Point;
import org.kylan1940.thebridge.arena.Region;
import org.kylan1940.thebridge.message.MessageUtil;
import org.kylan1940.thebridge.player.EquipmentGiver;
import org.kylan1940.thebridge.player.PlayerSnapshot;
import org.kylan1940.thebridge.reset.ArenaResetter;
import org.kylan1940.thebridge.stats.StatsManager;
import org.kylan1940.thebridge.utils.LocationUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runs the matches. This is the class that turns the decisions of Match (the rules)
 * into things the players see: teleports, messages, equipment, countdowns, respawns.
 *
 * Match life cycle (the arena state is shown in capitals):
 *
 *   join()                 WAITING     players wait in the lobby
 *   arena is full          STARTING    countdown, a timer runs once per second
 *   countdown reaches 0    IN_GAME     teams are assigned, round 1 starts (short freeze first)
 *   goal / death           IN_GAME     handleGoal() / handleDeath()
 *   score limit or time    ENDING      endMatch(): results are shown for a few seconds
 *   players sent back      RESETTING   changed blocks are restored in small batches
 *   restore finished       WAITING     the arena can be used again
 *
 * Everything here runs on the main server thread. Only the database (StatsManager) uses another thread.
 */
public class MatchManager {

    /** Result of join(), so the command can show the right message. */
    public enum JoinResult {
        JOINED,
        ALREADY_IN_MATCH,
        UNAVAILABLE,
        FULL
    }

    /** Remembers who hit a player last, so a fall into the void can still count as a kill. */
    private record LastHit(UUID attacker, long timeMillis) {
    }

    private final TheBridge plugin;
    private final ArenaResetter resetter;
    private final EquipmentGiver equipmentGiver;
    private final StatsManager statsManager;
    private final File snapshotFolder;

    /** One match per arena (key = lower case arena name). Exists from the first join until the players are sent back. */
    private final Map<String, Match> matches = new HashMap<>();
    /** Quick lookup: which match is this player in? */
    private final Map<UUID, Match> playerMatches = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();

    /** The once-per-second timer of each match (countdown and clock). */
    private final Map<String, BukkitTask> timers = new HashMap<>();
    /** The delayed task that ends the ENDING phase. */
    private final Map<String, BukkitTask> endingTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, LastHit> lastHits = new HashMap<>();

    /** Arenas whose blocks are being restored between two rounds. The round countdown waits for that. */
    private final Set<String> pausedRounds = new HashSet<>();

    public MatchManager(TheBridge plugin, ArenaResetter resetter, EquipmentGiver equipmentGiver, StatsManager statsManager) {
        this.plugin = plugin;
        this.resetter = resetter;
        this.equipmentGiver = equipmentGiver;
        this.statsManager = statsManager;
        this.snapshotFolder = new File(plugin.getDataFolder(), "snapshots");
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public Match getMatch(UUID player) {
        return playerMatches.get(player);
    }

    public Match getMatch(Arena arena) {
        return matches.get(key(arena));
    }

    public boolean isInMatch(UUID player) {
        return playerMatches.containsKey(player);
    }

    private String key(Arena arena) {
        return arena.getName().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Joining
    // ------------------------------------------------------------------

    public JoinResult join(Player player, Arena arena) {
        UUID uuid = player.getUniqueId();

        if (playerMatches.containsKey(uuid)) {
            return JoinResult.ALREADY_IN_MATCH;
        }
        if (!arena.getState().isJoinable()) {
            return JoinResult.UNAVAILABLE;
        }

        Location lobby = LocationUtil.toLocation(arena.getLobby());
        if (lobby == null) {
            // The lobby world is not loaded (for example it was unloaded after the arena was enabled).
            return JoinResult.UNAVAILABLE;
        }

        Match match = matches.get(key(arena));
        if (match == null) {
            match = new Match(arena, getScoreLimit(arena), getDuration(arena));
            match.setSoloTest(plugin.getSettings().soloTest);
            matches.put(key(arena), match);
        }
        if (!match.addPlayer(uuid, player.getName())) {
            return JoinResult.FULL;
        }
        playerMatches.put(uuid, match);

        // Save the player's normal state BEFORE we change anything.
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshots.put(uuid, snapshot);
        try {
            snapshot.saveTo(snapshotFile(uuid));
        } catch (IOException exception) {
            // The in-memory copy still works. Only crash recovery is lost for this player.
            plugin.getLogger().log(Level.WARNING, "Could not write the snapshot file of " + player.getName(), exception);
        }

        equipmentGiver.prepareForLobby(player);
        player.teleport(lobby);

        MessageUtil.sendToAll(getOnlinePlayers(match), "messages.player-joined",
                "%player%", player.getName(),
                "%players%", String.valueOf(match.getPlayerCount()),
                "%max%", String.valueOf(arena.getMaxPlayers()));

        debug(player.getName() + " joined arena " + arena.getName());

        if (match.isReadyToStart() && arena.getState() == ArenaState.WAITING) {
            startCountdown(match);
        }
        return JoinResult.JOINED;
    }

    private void startCountdown(Match match) {
        Arena arena = match.getArena();
        arena.setState(ArenaState.STARTING);
        match.setCountdown(plugin.getSettings().countdown);
        startTimer(match);
        MessageUtil.sendToAll(getOnlinePlayers(match), "messages.countdown-started",
                "%seconds%", String.valueOf(plugin.getSettings().countdown));
    }

    // ------------------------------------------------------------------
    // Leaving
    // ------------------------------------------------------------------

    /**
     * Removes a player from their match and gives them their normal state back.
     * Used for /thebridge leave, for disconnects and for kicks.
     *
     * @return false if the player was not in a match
     */
    public boolean leave(Player player) {
        UUID uuid = player.getUniqueId();
        Match match = playerMatches.remove(uuid);
        if (match == null) {
            return false;
        }
        Arena arena = match.getArena();

        cancelRespawn(uuid);
        lastHits.remove(uuid);
        match.removePlayer(uuid);
        restorePlayer(player);

        MessageUtil.send(player, "messages.left-match", "%arena%", arena.getName());
        MessageUtil.sendToAll(getOnlinePlayers(match), "messages.player-left",
                "%player%", player.getName(),
                "%players%", String.valueOf(match.getPlayerCount()),
                "%max%", String.valueOf(arena.getMaxPlayers()));

        debug(player.getName() + " left arena " + arena.getName());

        switch (arena.getState()) {
            case STARTING -> {
                // Someone left during the countdown: the arena is no longer full, so cancel it.
                if (!match.isReadyToStart()) {
                    stopTimer(match);
                    arena.setState(ArenaState.WAITING);
                    MessageUtil.sendToAll(getOnlinePlayers(match), "messages.countdown-cancelled");
                }
            }
            case IN_GAME -> {
                if (!match.isFinished()) {
                    MatchTeam forfeitWinner = match.getForfeitWinner();
                    if (forfeitWinner != null) {
                        MessageUtil.sendToAll(getOnlinePlayers(match), "messages.forfeit",
                                "%team%", forfeitWinner.getColoredName());
                        endMatch(match, forfeitWinner, false);
                    } else if (match.isEmpty()) {
                        // Everybody left. Nobody to win, so it is cancelled (no stats).
                        endMatch(match, null, true);
                    }
                }
            }
            default -> {
                // WAITING, ENDING: nothing special to do.
            }
        }

        // An empty waiting room needs no match object.
        if (arena.getState() == ArenaState.WAITING && match.isEmpty()) {
            matches.remove(key(arena));
        }
        return true;
    }

    /** Sends every player out of a match that has not started yet (used when an arena is disabled or deleted). */
    public void kickAll(Arena arena) {
        Match match = matches.get(key(arena));
        if (match == null) {
            return;
        }
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leave(player);
            } else {
                // Offline players are cleaned up by the quit listener, but make sure no stale entry stays.
                playerMatches.remove(uuid);
                match.removePlayer(uuid);
            }
        }
    }

    /**
     * Admin command: cancels the match of an arena. A running match ends WITHOUT a winner and
     * without stats, players are sent back and the blocks are restored.
     *
     * @return false if the arena had nothing to stop
     */
    public boolean stop(Arena arena) {
        Match match = matches.get(key(arena));
        if (match == null) {
            return false;
        }
        if (arena.getState() == ArenaState.IN_GAME) {
            MessageUtil.sendToAll(getOnlinePlayers(match), "messages.match-cancelled");
            endMatch(match, null, true);
            return true;
        }
        if (arena.getState().isJoinable()) {
            kickAll(arena);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Timer (runs once per second while a match is STARTING or IN_GAME)
    // ------------------------------------------------------------------

    private void startTimer(Match match) {
        stopTimer(match);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L);
        timers.put(key(match.getArena()), task);
    }

    private void stopTimer(Match match) {
        BukkitTask task = timers.remove(key(match.getArena()));
        if (task != null) {
            task.cancel();
        }
    }

    private void tick(Match match) {
        switch (match.getArena().getState()) {
            case STARTING -> tickStarting(match);
            case IN_GAME -> tickInGame(match);
            default -> stopTimer(match);
        }
    }

    private void tickStarting(Match match) {
        int remaining = match.tickCountdown();

        if (remaining <= 0) {
            beginMatch(match);
            return;
        }

        List<Player> players = getOnlinePlayers(match);
        // Chat message every 10 seconds and every second in the last 5.
        if (remaining <= 5 || remaining % 10 == 0) {
            MessageUtil.sendToAll(players, "messages.countdown", "%seconds%", String.valueOf(remaining));
            for (Player player : players) {
                MessageUtil.playSound(player, "countdown");
            }
        }
    }

    private void tickInGame(Match match) {
        // Between two rounds: wait until the arena is restored, then count down to the next round.
        if (match.isFrozen()) {
            if (pausedRounds.contains(key(match.getArena()))) {
                return;
            }
            int remaining = match.tickCountdown();
            List<Player> players = getOnlinePlayers(match);

            if (remaining <= 0) {
                match.startRound();
                for (Player player : players) {
                    MessageUtil.title(player, "messages.round-start-title", "messages.round-start-subtitle");
                    MessageUtil.playSound(player, "round-start");
                }
            } else {
                for (Player player : players) {
                    MessageUtil.title(player, "messages.round-countdown-title", "messages.round-countdown-subtitle",
                            "%seconds%", String.valueOf(remaining));
                    MessageUtil.playSound(player, "countdown");
                }
            }
            return;
        }

        boolean timeUp = match.tickClock();

        for (Player player : getOnlinePlayers(match)) {
            MessageUtil.actionBar(player, "messages.actionbar",
                    "%red%", String.valueOf(match.getScore(MatchTeam.RED)),
                    "%blue%", String.valueOf(match.getScore(MatchTeam.BLUE)),
                    "%limit%", String.valueOf(match.getScoreLimit()),
                    "%time%", formatTime(match.getSecondsLeft()));
        }

        if (timeUp) {
            MessageUtil.sendToAll(getOnlinePlayers(match), "messages.time-up");
            endMatch(match, match.decideTimeoutWinner(), false);
        }
    }

    // ------------------------------------------------------------------
    // Start of the match and of every round
    // ------------------------------------------------------------------

    private void beginMatch(Match match) {
        Arena arena = match.getArena();

        // A player may have gone offline in the very last tick. If so, go back to waiting.
        if (getOnlinePlayers(match).size() < match.getPlayersNeeded()) {
            stopTimer(match);
            arena.setState(ArenaState.WAITING);
            return;
        }

        match.assignTeams();
        arena.setState(ArenaState.IN_GAME);

        for (Player player : getOnlinePlayers(match)) {
            MatchTeam team = match.getTeam(player.getUniqueId());
            MessageUtil.send(player, "messages.match-started",
                    "%team%", team.getColoredName(),
                    "%limit%", String.valueOf(match.getScoreLimit()));
            MessageUtil.playSound(player, "start");
        }
        debug("Match started in arena " + arena.getName());

        resetPlayersForRound(match);
        beginRoundCountdown(match);
    }

    /** Sends everybody to their spawn with fresh equipment. */
    private void resetPlayersForRound(Match match) {
        for (Player player : getOnlinePlayers(match)) {
            UUID uuid = player.getUniqueId();
            cancelRespawn(uuid);
            match.setRespawning(uuid, false);
            lastHits.remove(uuid);
            spawnPlayer(match, player);
        }
    }

    /** Teleports one player to the spawn of their team and gives them their equipment. */
    private void spawnPlayer(Match match, Player player) {
        MatchTeam team = match.getTeam(player.getUniqueId());
        Location spawn = LocationUtil.toLocation(match.getArena().getSpawn(team));

        equipmentGiver.giveRoundEquipment(player, team);
        if (spawn != null) {
            player.teleport(spawn);
        } else {
            plugin.getLogger().warning("Spawn of arena " + match.getArena().getName() + " is in an unloaded world.");
        }
    }

    /** Freezes the round for a few seconds. The timer then counts down and calls startRound(). */
    private void beginRoundCountdown(Match match) {
        match.setCountdown(plugin.getSettings().roundCountdown);
        if (plugin.getSettings().roundCountdown == 0) {
            match.startRound();
        }
        startTimer(match);
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    /** Called by the move listener when a player entered the goal they attack. */
    public void handleGoal(Player scorer) {
        Match match = playerMatches.get(scorer.getUniqueId());
        if (match == null) {
            return;
        }

        // The rules decide if this goal counts. A repeated or simultaneous event comes back as IGNORED.
        Match.GoalResult result = match.registerGoal(scorer.getUniqueId());
        if (result == Match.GoalResult.IGNORED) {
            return;
        }

        MatchTeam team = match.getTeam(scorer.getUniqueId());
        List<Player> players = getOnlinePlayers(match);

        MessageUtil.sendToAll(players, "messages.goal-scored",
                "%player%", scorer.getName(),
                "%team%", team.getColoredName(),
                "%red%", String.valueOf(match.getScore(MatchTeam.RED)),
                "%blue%", String.valueOf(match.getScore(MatchTeam.BLUE)));
        for (Player player : players) {
            MessageUtil.title(player, "messages.goal-title", "messages.goal-subtitle",
                    "%player%", scorer.getName(),
                    "%team%", team.getColoredName());
            MessageUtil.playSound(player, "goal");
        }
        debug(scorer.getName() + " scored for " + team + " in arena " + match.getArena().getName());

        if (result == Match.GoalResult.MATCH_WON) {
            endMatch(match, team, false);
            return;
        }

        // Next round: everybody back to their spawn, optionally restore the bridges, then count down.
        resetPlayersForRound(match);

        if (plugin.getSettings().resetAfterScore) {
            String arenaKey = key(match.getArena());
            pausedRounds.add(arenaKey);
            resetter.restore(match.getArena(), () -> {
                pausedRounds.remove(arenaKey);
                if (!match.isFinished() && match.getArena().getState() == ArenaState.IN_GAME) {
                    beginRoundCountdown(match);
                }
            });
        } else {
            beginRoundCountdown(match);
        }
    }

    // ------------------------------------------------------------------
    // Movement (goal detection, void, arena limits)
    // ------------------------------------------------------------------

    /**
     * Called for every move of a player who is in a match.
     *
     * @return true when the move must be cancelled (frozen round, or walking out of the arena)
     */
    public boolean handleMovement(Player player, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        Match match = playerMatches.get(uuid);
        if (match == null) {
            return false;
        }
        Arena arena = match.getArena();

        // Frozen (countdown): looking around and jumping is fine, walking is not.
        if (match.isFrozen()) {
            return from.getX() != to.getX() || from.getZ() != to.getZ();
        }
        if (arena.getState() != ArenaState.IN_GAME || match.isFinished() || match.isRespawning(uuid)) {
            return false;
        }

        MatchTeam team = match.getTeam(uuid);
        Region bounds = arena.getBounds();

        // Falling below the bottom of the arena is death (the "void").
        // The death is handled one tick later: teleporting, changing the game mode and so on
        // inside a move event is fragile. handleDeath() ignores a player who is already dead,
        // so a second move event before the task runs does no harm.
        if (to.getY() < bounds.minY()) {
            Player killer = findKiller(uuid, match);
            Bukkit.getScheduler().runTask(plugin, () -> handleDeath(player, killer));
            return false;
        }
        // Leaving the arena sideways or upwards: push the player back.
        if (!bounds.contains(to.getX(), to.getY(), to.getZ())) {
            return true;
        }

        // Goal detection only when the player moved to another block (cheap, and robust against repeated events).
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return false;
        }
        Region attackedGoal = arena.getGoal(team.opposite()).expand(plugin.getSettings().goalMargin);
        if (attackedGoal.contains(to.getX(), to.getY(), to.getZ())) {
            // Also one tick later (see the void check above). Match.registerGoal() throws away duplicates.
            Bukkit.getScheduler().runTask(plugin, () -> handleGoal(player));
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Damage, death and respawn
    // ------------------------------------------------------------------

    public void recordHit(Player victim, Player attacker) {
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    /** The player that recently hit the victim (inside the kill credit time), or null. */
    private Player findKiller(UUID victim, Match match) {
        LastHit hit = lastHits.get(victim);
        if (hit == null) {
            return null;
        }
        long maxAge = plugin.getSettings().killCreditSeconds * 1000L;
        if (System.currentTimeMillis() - hit.timeMillis() > maxAge) {
            return null;
        }
        Player attacker = Bukkit.getPlayer(hit.attacker());
        // Only count the kill if the attacker is still in the same match and on the other team.
        if (attacker == null || playerMatches.get(attacker.getUniqueId()) != match) {
            return null;
        }
        if (match.getTeam(attacker.getUniqueId()) == match.getTeam(victim)) {
            return null;
        }
        return attacker;
    }

    /** Public version for the damage listener (it needs the killer when it turns lethal damage into a death). */
    public Player findKiller(Player victim) {
        Match match = playerMatches.get(victim.getUniqueId());
        return match == null ? null : findKiller(victim.getUniqueId(), match);
    }

    /**
     * A player died (lethal damage, the void or /kill).
     * We never show the vanilla death screen: the player waits as a spectator at their spawn,
     * then respawns with fresh equipment. Nothing is dropped.
     */
    public void handleDeath(Player victim, Player killer) {
        UUID uuid = victim.getUniqueId();
        Match match = playerMatches.get(uuid);
        if (match == null || match.isFinished() || match.isRespawning(uuid)
                || match.getArena().getState() != ArenaState.IN_GAME) {
            return;
        }

        match.addDeath(uuid);
        match.setRespawning(uuid, true);
        lastHits.remove(uuid);

        List<Player> players = getOnlinePlayers(match);
        if (killer != null) {
            match.addKill(killer.getUniqueId());
            MessageUtil.sendToAll(players, "messages.death-by-player",
                    "%player%", victim.getName(), "%killer%", killer.getName());
            MessageUtil.playSound(killer, "kill");
        } else {
            MessageUtil.sendToAll(players, "messages.death",
                    "%player%", victim.getName());
        }

        // Wait as a spectator at the own spawn (healed, no fire, nothing in the inventory).
        equipmentGiver.prepareForLobby(victim);
        victim.setGameMode(GameMode.SPECTATOR);
        Location spawn = LocationUtil.toLocation(match.getArena().getSpawn(match.getTeam(uuid)));
        if (spawn != null) {
            victim.teleport(spawn);
        }
        MessageUtil.playSound(victim, "death");

        int delay = plugin.getSettings().respawnDelay;
        MessageUtil.title(victim, "messages.respawn-title", "messages.respawn-subtitle", "%seconds%", String.valueOf(delay));

        if (delay == 0) {
            respawn(match, uuid);
        } else {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> respawn(match, uuid), delay * 20L);
            respawnTasks.put(uuid, task);
        }
    }

    private void respawn(Match match, UUID uuid) {
        respawnTasks.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);

        // The match could have ended, or the player left, while they were waiting.
        if (player == null || playerMatches.get(uuid) != match || match.isFinished() || !match.isRespawning(uuid)) {
            return;
        }
        match.setRespawning(uuid, false);
        spawnPlayer(match, player);
        MessageUtil.playSound(player, "respawn");
    }

    private void cancelRespawn(UUID uuid) {
        BukkitTask task = respawnTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Where a player must respawn if the vanilla death screen could not be avoided (for example /kill):
     * the lobby before the game, the team spawn during the game. Returns null if the player is not in a match.
     */
    public Location getRespawnLocation(Player player) {
        Match match = playerMatches.get(player.getUniqueId());
        if (match == null) {
            return null;
        }
        Arena arena = match.getArena();
        MatchTeam team = match.getTeam(player.getUniqueId());
        Point point = (arena.getState() == ArenaState.IN_GAME && team != null) ? arena.getSpawn(team) : arena.getLobby();
        return LocationUtil.toLocation(point);
    }

    // ------------------------------------------------------------------
    // End of the match
    // ------------------------------------------------------------------

    /**
     * Ends a running match.
     *
     * @param winner    the winning team, or null for a draw
     * @param cancelled true when an admin stopped it or everybody left: no stats, no rewards, no results screen
     */
    private void endMatch(Match match, MatchTeam winner, boolean cancelled) {
        // Guard: two things can end a match in the same tick (for example a goal and the timer).
        if (match.isFinished()) {
            return;
        }
        Arena arena = match.getArena();
        String arenaKey = key(arena);

        match.finish(winner);
        arena.setState(ArenaState.ENDING);
        stopTimer(match);
        pausedRounds.remove(arenaKey);

        for (UUID uuid : match.getPlayers()) {
            cancelRespawn(uuid);
            Player player = Bukkit.getPlayer(uuid);
            // Players who were waiting to respawn are back to normal for the results screen.
            if (player != null && match.isRespawning(uuid)) {
                match.setRespawning(uuid, false);
                equipmentGiver.prepareForLobby(player);
            }
        }

        if (!cancelled) {
            announceResult(match, winner);
            // A solo test match is only for trying things out: no statistics, no rewards.
            if (!match.isSoloTest()) {
                statsManager.recordMatch(match, winner);
            }
            if (plugin.getSettings().rewardsEnabled && !match.isSoloTest()) {
                giveRewards(match, winner);
            }
        }
        debug("Match ended in arena " + arena.getName() + " (winner: " + winner + ", cancelled: " + cancelled + ")");

        long delayTicks = cancelled ? 1L : plugin.getSettings().endingDelay * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> finishEnding(match), delayTicks);
        endingTasks.put(arenaKey, task);
    }

    private void announceResult(Match match, MatchTeam winner) {
        String winnerText = winner == null ? MessageUtil.get("messages.draw-text") : winner.getColoredName();

        for (Player player : getOnlinePlayers(match)) {
            MessageUtil.send(player, "messages.match-end",
                    "%winner%", winnerText,
                    "%red%", String.valueOf(match.getScore(MatchTeam.RED)),
                    "%blue%", String.valueOf(match.getScore(MatchTeam.BLUE)),
                    "%goals%", String.valueOf(match.getGoals(player.getUniqueId())),
                    "%kills%", String.valueOf(match.getKills(player.getUniqueId())),
                    "%deaths%", String.valueOf(match.getDeaths(player.getUniqueId())));

            MatchTeam team = match.getTeam(player.getUniqueId());
            if (winner == null) {
                MessageUtil.title(player, "messages.draw-title", "messages.draw-subtitle");
            } else if (team == winner) {
                MessageUtil.title(player, "messages.win-title", "messages.win-subtitle");
                MessageUtil.playSound(player, "win");
            } else {
                MessageUtil.title(player, "messages.lose-title", "messages.lose-subtitle");
                MessageUtil.playSound(player, "lose");
            }
        }
    }

    /** Runs the console commands from config.yml (rewards.winner-commands / loser-commands). */
    private void giveRewards(Match match, MatchTeam winner) {
        if (winner == null) {
            return;
        }
        for (Player player : getOnlinePlayers(match)) {
            List<String> commands = match.getTeam(player.getUniqueId()) == winner
                    ? plugin.getSettings().winnerCommands
                    : plugin.getSettings().loserCommands;

            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
        }
    }

    /** After the results screen: send everybody back, then restore the arena. */
    private void finishEnding(Match match) {
        Arena arena = match.getArena();
        String arenaKey = key(arena);
        endingTasks.remove(arenaKey);

        for (UUID uuid : match.getPlayers()) {
            playerMatches.remove(uuid);
            lastHits.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restorePlayer(player);
            }
        }
        matches.remove(arenaKey);
        arena.setState(ArenaState.RESETTING);

        resetter.restore(arena, () -> {
            // Back to WAITING, unless an admin switched the arena off in the meantime.
            arena.setState(arena.isEnabled() ? ArenaState.WAITING : ArenaState.DISABLED);
            debug("Arena " + arena.getName() + " is ready again.");
        });
    }

    // ------------------------------------------------------------------
    // Player state
    // ------------------------------------------------------------------

    /** Puts the player back the way they were before they joined, and removes the snapshot file. */
    private void restorePlayer(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        snapshot.restore(player);
        deleteSnapshotFile(player.getUniqueId());
    }

    /**
     * Called when a player joins the SERVER. A snapshot file for a player who is not in a match
     * means the server crashed while they were playing, so they get their things back now.
     */
    public void recoverAfterCrash(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerMatches.containsKey(uuid)) {
            return;
        }
        File file = snapshotFile(uuid);
        PlayerSnapshot snapshot = PlayerSnapshot.loadFrom(file);
        if (snapshot == null) {
            if (file.exists()) {
                plugin.getLogger().warning("Snapshot file of " + player.getName() + " is unreadable and was left in place: " + file.getName());
            }
            return;
        }
        snapshot.restore(player);
        deleteSnapshotFile(uuid);
        MessageUtil.send(player, "messages.recovered");
        plugin.getLogger().info("Restored " + player.getName() + " from a snapshot left by an unclean shutdown.");
    }

    private File snapshotFile(UUID uuid) {
        return new File(snapshotFolder, uuid + ".yml");
    }

    private void deleteSnapshotFile(UUID uuid) {
        File file = snapshotFile(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName());
        }
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    /**
     * Called from onDisable. Everything is stopped at once: tasks are cancelled, every player gets
     * their normal state back and all changed blocks are restored immediately.
     */
    public void shutdown() {
        for (BukkitTask task : timers.values()) {
            task.cancel();
        }
        for (BukkitTask task : endingTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        timers.clear();
        endingTasks.clear();
        respawnTasks.clear();

        for (Match match : new ArrayList<>(matches.values())) {
            for (UUID uuid : match.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    restorePlayer(player);
                }
            }
        }
        playerMatches.clear();
        matches.clear();
        pausedRounds.clear();

        resetter.restoreAllNow();
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    public int getScoreLimit(Arena arena) {
        return arena.getScoreLimit() > 0 ? arena.getScoreLimit() : plugin.getSettings().scoreLimit;
    }

    public int getDuration(Arena arena) {
        return arena.getDurationSeconds() > 0 ? arena.getDurationSeconds() : plugin.getSettings().duration;
    }

    /** The players of the match that are online right now. */
    private List<Player> getOnlinePlayers(Match match) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private String formatTime(int totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void debug(String message) {
        if (plugin.getSettings().debug) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    public Collection<Match> getMatches() {
        return matches.values();
    }
}