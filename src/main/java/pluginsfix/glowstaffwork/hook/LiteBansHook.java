package pluginsfix.glowstaffwork.hook;

import litebans.api.Database;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LiteBansHook {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("LiteBans");
    }

    public CompletableFuture<PunishmentStats> getPunishments(UUID uuid, String playerName) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(PunishmentStats.EMPTY);
        }

        return CompletableFuture.supplyAsync(() -> {
            String staffUuid = uuid.toString();
            String staffName = playerName != null ? playerName : "";

            long bans = queryCount("{bans}", staffUuid, staffName);
            long mutes = queryCount("{mutes}", staffUuid, staffName);
            long kicks = queryCount("{kicks}", staffUuid, staffName);
            long warns = queryCount("{warnings}", staffUuid, staffName);
            long total = bans + mutes + kicks + warns;

            return new PunishmentStats(bans, mutes, kicks, warns, total);
        });
    }

    private long queryCount(String tablePlaceholder, String uuid, String name) {
        String query = "SELECT COUNT(*) FROM " + tablePlaceholder + " WHERE banned_by_uuid = ? OR banned_by_name = ?;";
        try (PreparedStatement statement = Database.get().prepareStatement(query)) {
            statement.setString(1, uuid);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        } catch (SQLException | IllegalStateException ignored) {
            return 0L;
        }
        return 0L;
    }

    public record PunishmentStats(
            long bans,
            long mutes,
            long kicks,
            long warns,
            long total
    ) {
        public static final PunishmentStats EMPTY = new PunishmentStats(0L, 0L, 0L, 0L, 0L);
    }
}
