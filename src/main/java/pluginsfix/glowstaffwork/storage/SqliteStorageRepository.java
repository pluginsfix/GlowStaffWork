package pluginsfix.glowstaffwork.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pluginsfix.glowstaffwork.domain.StaffProfile;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteStorageRepository implements StorageRepository {
    private final Logger logger;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public SqliteStorageRepository(File databaseFile, int poolSize, int timeoutMs, Logger logger) {
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(Math.max(2, poolSize));

        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("GlowStaffWork-SQLite-Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(timeoutMs);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = this.dataSource.getConnection();
                 Statement statement = connection.createStatement()) {

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS staff_work_profiles (
                            uuid VARCHAR(36) PRIMARY KEY,
                            player_name VARCHAR(32) NOT NULL,
                            total_time_seconds BIGINT NOT NULL DEFAULT 0,
                            today_time_seconds BIGINT NOT NULL DEFAULT 0,
                            last_day_epoch BIGINT NOT NULL DEFAULT 0,
                            total_sessions INT NOT NULL DEFAULT 0,
                            last_seen_epoch BIGINT NOT NULL DEFAULT 0
                        );
                        """);

                statement.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_staff_profiles_name ON staff_work_profiles(player_name COLLATE NOCASE);
                        """);

                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS staff_work_sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid VARCHAR(36) NOT NULL,
                            player_name VARCHAR(32) NOT NULL,
                            start_time_epoch BIGINT NOT NULL,
                            end_time_epoch BIGINT NOT NULL,
                            duration_seconds BIGINT NOT NULL
                        );
                        """);

                statement.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_staff_sessions_uuid ON staff_work_sessions(uuid);
                        """);

            } catch (SQLException e) {
                this.logger.log(Level.SEVERE, "Failed to initialize SQLite database tables", e);
            }
        }, this.executor);
    }

    @Override
    public CompletableFuture<Optional<StaffProfile>> loadProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_name, total_time_seconds, today_time_seconds, last_day_epoch, total_sessions, last_seen_epoch " +
                    "FROM staff_work_profiles WHERE uuid = ?;";

            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(new StaffProfile(
                                uuid,
                                resultSet.getString("player_name"),
                                resultSet.getLong("total_time_seconds"),
                                resultSet.getLong("today_time_seconds"),
                                resultSet.getLong("last_day_epoch"),
                                resultSet.getInt("total_sessions"),
                                resultSet.getLong("last_seen_epoch")
                        ));
                    }
                }
            } catch (SQLException e) {
                this.logger.log(Level.SEVERE, "Failed to load staff profile for uuid=" + uuid, e);
            }
            return Optional.empty();
        }, this.executor);
    }

    @Override
    public CompletableFuture<Optional<StaffProfile>> loadProfileByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, player_name, total_time_seconds, today_time_seconds, last_day_epoch, total_sessions, last_seen_epoch " +
                    "FROM staff_work_profiles WHERE player_name = ? COLLATE NOCASE LIMIT 1;";

            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, playerName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        return Optional.of(new StaffProfile(
                                uuid,
                                resultSet.getString("player_name"),
                                resultSet.getLong("total_time_seconds"),
                                resultSet.getLong("today_time_seconds"),
                                resultSet.getLong("last_day_epoch"),
                                resultSet.getInt("total_sessions"),
                                resultSet.getLong("last_seen_epoch")
                        ));
                    }
                }
            } catch (SQLException | IllegalArgumentException e) {
                this.logger.log(Level.SEVERE, "Failed to load staff profile for name=" + playerName, e);
            }
            return Optional.empty();
        }, this.executor);
    }

    @Override
    public CompletableFuture<Void> saveProfile(StaffProfile profile) {
        return CompletableFuture.runAsync(() -> saveProfileInternal(profile), this.executor);
    }

    @Override
    public void saveProfileSync(StaffProfile profile) {
        saveProfileInternal(profile);
    }

    private void saveProfileInternal(StaffProfile profile) {
        String sql = """
                INSERT INTO staff_work_profiles (uuid, player_name, total_time_seconds, today_time_seconds, last_day_epoch, total_sessions, last_seen_epoch)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    total_time_seconds = excluded.total_time_seconds,
                    today_time_seconds = excluded.today_time_seconds,
                    last_day_epoch = excluded.last_day_epoch,
                    total_sessions = excluded.total_sessions,
                    last_seen_epoch = excluded.last_seen_epoch;
                """;

        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, profile.uuid().toString());
            statement.setString(2, profile.playerName());
            statement.setLong(3, profile.totalTimeSeconds());
            statement.setLong(4, profile.todayTimeSeconds());
            statement.setLong(5, profile.lastDayEpoch());
            statement.setInt(6, profile.totalSessions());
            statement.setLong(7, profile.lastSeenEpoch());

            statement.executeUpdate();
        } catch (SQLException e) {
            this.logger.log(Level.SEVERE, "Failed to save staff profile for uuid=" + profile.uuid(), e);
        }
    }

    @Override
    public CompletableFuture<Void> recordSession(UUID uuid, String playerName, long startEpochMillis, long endEpochMillis, long durationSeconds) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO staff_work_sessions (uuid, player_name, start_time_epoch, end_time_epoch, duration_seconds) VALUES (?, ?, ?, ?, ?);";
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                statement.setString(2, playerName);
                statement.setLong(3, startEpochMillis);
                statement.setLong(4, endEpochMillis);
                statement.setLong(5, durationSeconds);

                statement.executeUpdate();
            } catch (SQLException e) {
                this.logger.log(Level.SEVERE, "Failed to record staff session for uuid=" + uuid, e);
            }
        }, this.executor);
    }

    @Override
    public void close() {
        this.executor.shutdown();
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }
}
