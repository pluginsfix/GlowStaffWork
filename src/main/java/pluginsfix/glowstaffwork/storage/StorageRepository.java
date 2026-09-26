package pluginsfix.glowstaffwork.storage;

import pluginsfix.glowstaffwork.domain.StaffProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageRepository {
    CompletableFuture<Void> init();

    CompletableFuture<Optional<StaffProfile>> loadProfile(UUID uuid);

    CompletableFuture<Optional<StaffProfile>> loadProfileByName(String playerName);

    CompletableFuture<Void> saveProfile(StaffProfile profile);

    CompletableFuture<Void> recordSession(UUID uuid, String playerName, long startEpochMillis, long endEpochMillis, long durationSeconds);

    void saveProfileSync(StaffProfile profile);

    void close();
}
