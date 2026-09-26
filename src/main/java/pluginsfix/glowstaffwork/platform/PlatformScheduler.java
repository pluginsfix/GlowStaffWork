package pluginsfix.glowstaffwork.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public final class PlatformScheduler {
    private final Plugin plugin;
    private final boolean isFolia;
    private BukkitTask timerTask;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = checkFolia();
    }

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() {
        return this.isFolia;
    }

    public void runGlobal(Runnable task) {
        if (this.isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this.plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }

    public void runEntity(Entity entity, Runnable task) {
        if (this.isFolia) {
            entity.getScheduler().run(this.plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }

    public void runAsync(Runnable task) {
        if (this.isFolia) {
            Bukkit.getAsyncScheduler().runNow(this.plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
        }
    }

    public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (this.isFolia) {
            long delayMillis = delayTicks * 50L;
            long periodMillis = periodTicks * 50L;
            Bukkit.getAsyncScheduler().runAtFixedRate(
                    this.plugin,
                    scheduledTask -> task.run(),
                    delayMillis,
                    periodMillis,
                    TimeUnit.MILLISECONDS
            );
        } else {
            this.timerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, task, delayTicks, periodTicks);
        }
    }

    public void executeConsoleCommand(String command) {
        runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    public void cancelTasks() {
        if (this.timerTask != null && !this.timerTask.isCancelled()) {
            this.timerTask.cancel();
        }
        if (this.isFolia) {
            Bukkit.getAsyncScheduler().cancelTasks(this.plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(this.plugin);
        }
    }
}
