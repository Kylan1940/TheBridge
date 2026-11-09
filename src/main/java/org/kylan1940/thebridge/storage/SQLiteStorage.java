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

/**
 * Saves statistics in a local SQLite file.
 *
 * Same style as ServerTimeline: one connection per operation, plain SQL strings and
 * try-with-resources so connections are always closed. Every SQL value goes through a
 * PreparedStatement, so player names can never break (or inject into) a query.
 *
 * The SQLite driver is provided by the Minecraft server, so it is not shaded into the jar.
 */
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
        String statsTable = "CREATE TABLE IF NOT EXISTS player_stats (\n"
                + "    uuid TEXT PRIMARY KEY,\n"
                + "    name TEXT NOT NULL,\n"
                + "    wins INTEGER NOT NULL DEFAULT 0,\n"
                + "    losses INTEGER NOT NULL DEFAULT 0,\n"
                + "    goals INTEGER NOT NULL DEFAULT 0,\n"
                + "    kills INTEGER NOT NULL DEFAULT 0,\n"
                + "    deaths INTEGER NOT NULL DEFAULT 0,\n"
                + "    matches_played INTEGER NOT NULL DEFAULT 0,\n"
                + "    win_streak INTEGER NOT NULL DEFAULT 0,\n"
                + "    best_win_streak INTEGER NOT NULL DEFAULT 0,\n"
                + "    last_played INTEGER NOT NULL DEFAULT 0\n"
                + ");\n";

        String historyTable = "CREATE TABLE IF NOT EXISTS match_history (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    arena TEXT NOT NULL,\n"
                + "    winner TEXT NOT NULL,\n"
                + "    red_score INTEGER NOT NULL,\n"
                + "    blue_score INTEGER NOT NULL,\n"
                + "    duration_seconds INTEGER NOT NULL,\n"
                + "    ended_at INTEGER NOT NULL\n"
                + ");\n";

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
        // Two steps instead of "INSERT ... ON CONFLICT DO UPDATE": that syntax needs SQLite 3.24, and the
        // driver that comes with older Minecraft servers (1.13 and so on) is older than that.
        // Step 1: UPDATE adds the numbers of this match to the stored ones. On the right side of the "="
        // a bare column name is the OLD stored value.
        String updateSql = "UPDATE player_stats SET\n"
                + "    name = ?,\n"
                + "    wins = wins + ?,\n"
                + "    losses = losses + ?,\n"
                + "    goals = goals + ?,\n"
                + "    kills = kills + ?,\n"
                + "    deaths = deaths + ?,\n"
                + "    matches_played = matches_played + 1,\n"
                + "    win_streak = CASE\n"
                + "        WHEN ? = 1 THEN win_streak + 1\n"
                + "        WHEN ? = 1 THEN 0\n"
                + "        ELSE win_streak\n"
                + "    END,\n"
                + "    best_win_streak = MAX(best_win_streak, CASE\n"
                + "        WHEN ? = 1 THEN win_streak + 1\n"
                + "        ELSE win_streak\n"
                + "    END),\n"
                + "    last_played = ?\n"
                + "WHERE uuid = ?;\n";

        // Step 2: when no row was updated this is a brand new player, so a row is inserted.
        String insertSql = "INSERT INTO player_stats (\n"
                + "    uuid, name, wins, losses, goals, kills, deaths,\n"
                + "    matches_played, win_streak, best_win_streak, last_played\n"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?);\n";

        String historySql = "INSERT INTO match_history (arena, winner, red_score, blue_score, duration_seconds, ended_at)\n"
                + "VALUES (?, ?, ?, ?, ?, ?);\n";

        try (Connection connection = getConnection()) {
            // One transaction: either the whole match is saved, or nothing is.
            connection.setAutoCommit(false);

            try (PreparedStatement update = connection.prepareStatement(updateSql);
                 PreparedStatement insert = connection.prepareStatement(insertSql);
                 PreparedStatement history = connection.prepareStatement(historySql)) {

                for (PlayerResult result : results) {
                    int win = result.won() ? 1 : 0;
                    int loss = result.lost() ? 1 : 0;

                    update.setString(1, result.name());
                    update.setInt(2, win);
                    update.setInt(3, loss);
                    update.setInt(4, result.goals());
                    update.setInt(5, result.kills());
                    update.setInt(6, result.deaths());
                    update.setInt(7, win);
                    update.setInt(8, loss);
                    update.setInt(9, win);
                    update.setLong(10, record.endedAt());
                    update.setString(11, result.uuid().toString());

                    if (update.executeUpdate() == 0) {
                        insert.setString(1, result.uuid().toString());
                        insert.setString(2, result.name());
                        insert.setInt(3, win);
                        insert.setInt(4, loss);
                        insert.setInt(5, result.goals());
                        insert.setInt(6, result.kills());
                        insert.setInt(7, result.deaths());
                        // For a brand new player the streak starts at 1 after a win, else 0.
                        insert.setInt(8, win);
                        insert.setInt(9, win);
                        insert.setLong(10, record.endedAt());
                        insert.executeUpdate();
                    }
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
        return "SELECT uuid, name, wins, losses, goals, kills, deaths,\n"
                + "       matches_played, win_streak, best_win_streak\n"
                + "FROM player_stats\n";
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
