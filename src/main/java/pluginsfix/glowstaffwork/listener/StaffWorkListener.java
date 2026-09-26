package pluginsfix.glowstaffwork.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pluginsfix.glowstaffwork.config.PluginConfig;
import pluginsfix.glowstaffwork.service.StaffWorkService;

import java.util.UUID;

public final class StaffWorkListener implements Listener {
    private final StaffWorkService service;
    private final PluginConfig config;

    public StaffWorkListener(StaffWorkService service, PluginConfig config) {
        this.service = service;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (this.service.isOnDuty(uuid)) {
            if (this.config.autoOffOnQuit()) {
                this.service.stopDuty(player, true);
            }
        } else {
            this.service.cleanupCache(uuid);
        }
    }
}
