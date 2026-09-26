package pluginsfix.glowstaffwork;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import pluginsfix.glowstaffwork.command.StaffWorkCommand;
import pluginsfix.glowstaffwork.config.PluginConfig;
import pluginsfix.glowstaffwork.hook.PlaceholderApiHook;
import pluginsfix.glowstaffwork.listener.StaffWorkListener;
import pluginsfix.glowstaffwork.platform.PlatformScheduler;
import pluginsfix.glowstaffwork.service.StaffWorkService;
import pluginsfix.glowstaffwork.storage.SqliteStorageRepository;
import pluginsfix.glowstaffwork.storage.StorageRepository;
import pluginsfix.glowstaffwork.text.Messages;

import java.io.File;

public final class GlowStaffWork extends JavaPlugin {
    private PluginConfig config;
    private Messages messages;
    private PlatformScheduler scheduler;
    private StorageRepository repository;
    private StaffWorkService service;
    private PlaceholderApiHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = PluginConfig.fromYaml(getConfig());
        this.messages = new Messages(this);
        this.scheduler = new PlatformScheduler(this);

        File databaseFile = new File(getDataFolder(), this.config.databaseFile());
        this.repository = new SqliteStorageRepository(
                databaseFile,
                this.config.databasePoolSize(),
                this.config.databaseConnectionTimeoutMs(),
                getLogger()
        );
        this.repository.init().join();

        this.service = new StaffWorkService(
                this.repository,
                this.config,
                this.messages,
                this.scheduler,
                getLogger()
        );
        this.service.startAutoSave();

        getServer().getPluginManager().registerEvents(
                new StaffWorkListener(this.service, this.config),
                this
        );

        PluginCommand command = getCommand("glowstaffwork");
        if (command != null) {
            StaffWorkCommand executor = new StaffWorkCommand(this.service, this.messages, this::reloadPlugin);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderHook = new PlaceholderApiHook(this, this.service, this.messages);
            this.placeholderHook.register();
        }
    }

    @Override
    public void onDisable() {
        if (this.placeholderHook != null) {
            this.placeholderHook.unregister();
        }

        HandlerList.unregisterAll(this);

        if (this.scheduler != null) {
            this.scheduler.cancelTasks();
        }

        if (this.service != null) {
            this.service.flushAllSync();
        }

        if (this.repository != null) {
            this.repository.close();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        this.config = PluginConfig.fromYaml(getConfig());
        this.messages.reload();
    }
}
