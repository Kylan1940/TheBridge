package org.kylan1940.thebridge.storage;

import org.kylan1940.thebridge.stats.MatchRecord;
import org.kylan1940.thebridge.stats.PlayerResult;
import org.kylan1940.thebridge.stats.PlayerStats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public class SQLiteStorage implements Storage {

    private final String databaseUrl;

    public SQLiteStorage(File databaseFile) {
        this.databaseUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        // If two operations ever overlap, wait up to 5 seconds instead of failing at once.
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    @Override
    public void initialize() throws StorageException {
        String statsTable = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    wins INTEGER NOT NULL DEFAULT 0,
                    losses INTEGER NOT NULL DEFAULT 0,
                    goals INTEGER NOT NULL DEFAULT 0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    matches_played INTEGER NOT NULL DEFAULT 0,
                    win_streak INTEGER NOT NULL DEFAULT 0,
                    best_win_streak INTEGER NOT NULL DEFAULT 0,
                    last_played INTEGER NOT NULL DEFAULT 0
                );
                """;

        String historyTable = """
                CREATE TABLE IF NOT EXISTS match_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    arena TEXT NOT NULL,
                    winner TEXT NOT NULL,
                    red_score INTEGER NOT NULL,
                    blue_score INTEGER NOT NULL,
                    duration_seconds INTEGER NOT NULL,
                    ended_at INTEGER NOT NULL
                );
                """;

        String nameIndex = "CREATE INDEX IF NOT EXISTS idx_player_stats_name ON player_stats (name COLLATE NOCASE);";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(statsTable);
            statement.executeUpdate(historyTable);
            statement.executeUpdate(nameIndex);
        } catch (SQLException exception) {
            throw new StorageException("Could not create the SQLite tables.", exception);
        }
    }

    @Override
    public void saveMatch(MatchRecord record, List<PlayerResult> results) throws StorageException {
        // ON CONFLICT ... DO UPDATE adds the numbers of this match to the stored ones.
        // On the right side of the "=" a bare column name is the OLD stored value,
        // "excluded.x" is the value we tried to insert (this match).
        String upsertSql = """
                INSERT INTO player_stats (
                    uuid, name, wins, losses, goals, kills, deaths,
                    matches_played, win_streak, best_win_streak, last_played
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    wins = wins + excluded.wins,
                    losses = losses + excluded.losses,
                    goals = goals + excluded.goals,
                    kills = kills + excluded.kills,
                    deaths = deaths + excluded.deaths,
                    matches_played = matches_played + 1,
                    win_streak = CASE
                        WHEN excluded.wins = 1 THEN win_streak + 1
                        WHEN excluded.losses = 1 THEN 0
                        ELSE win_streak
                    END,
                    best_win_streak = MAX(best_win_streak, CASE
                        WHEN excluded.wins = 1 THEN win_streak + 1
                        ELSE win_streak
                    END),
                    last_played = excluded.last_played;
                """;

        String historySql = """
                INSERT INTO match_history (arena, winner, red_score, blue_score, duration_seconds, ended_at)
                VALUES (?, ?, ?, ?, ?, ?);
                """;

        try (Connection connection = getConnection()) {
            // One transaction: either the whole match is saved, or nothing is.
            connection.setAutoCommit(false);

            try (PreparedStatement upsert = connection.prepareStatement(upsertSql);
                 PreparedStatement history = connection.prepareStatement(historySql)) {

                for (PlayerResult result : results) {
                    int win = result.won() ? 1 : 0;
                    int loss = result.lost() ? 1 : 0;

                    upsert.setString(1, result.uuid().toString());
                    upsert.setString(2, result.name());
                    upsert.setInt(3, win);
                    upsert.setInt(4, loss);
                    upsert.setInt(5, result.goals());
                    upsert.setInt(6, result.kills());
                    upsert.setInt(7, result.deaths());
                    // For a brand new player the streak starts at 1 after a win, else 0.
                    upsert.setInt(8, win);
                    upsert.setInt(9, win);
                    upsert.setLong(10, record.endedAt());
                    upsert.executeUpdate();
                }

                history.setString(1, record.arena());
                history.setString(2, record.winner());
                history.setInt(3, record.redScore());
                history.setInt(4, record.blueScore());
                history.setInt(5, record.durationSeconds());
                history.setLong(6, record.endedAt());
                history.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not save the match results.", exception);
        }
    }

    @Override
    public PlayerStats loadStats(UUID uuid) throws StorageException {
        String sql = baseSelect() + " WHERE uuid = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            return readOne(statement);
        } catch (SQLException exception) {
            throw new StorageException("Could not load stats.", exception);
        }
    }

    @Override
    public PlayerStats loadStatsByName(String name) throws StorageException {
        // If a name was used by two accounts over time, the most recently active one wins.
        String sql = baseSelect() + " WHERE name = ? COLLATE NOCASE ORDER BY last_played DESC LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            return readOne(statement);
        } catch (SQLException exception) {
            throw new StorageException("Could not load stats.", exception);
        }
    }

    private String baseSelect() {
        return """
                SELECT uuid, name, wins, losses, goals, kills, deaths,
                       matches_played, win_streak, best_win_streak
                FROM player_stats
                """;
    }

    private PlayerStats readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return new PlayerStats(
                    UUID.fromString(resultSet.getString("uuid")),
                    resultSet.getString("name"),
                    resultSet.getInt("wins"),
                    resultSet.getInt("losses"),
                    resultSet.getInt("goals"),
                    resultSet.getInt("kills"),
                    resultSet.getInt("deaths"),
                    resultSet.getInt("matches_played"),
                    resultSet.getInt("win_streak"),
                    resultSet.getInt("best_win_streak")
            );
        }
    }

    @Override
    public void close() {
        // Nothing to do: every operation opens and closes its own connection.
    }
}
