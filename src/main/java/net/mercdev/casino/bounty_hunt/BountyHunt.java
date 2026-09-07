package net.mercdev.casino.bounty_hunt;

import java.util.logging.Level;

import net.mercdev.casino.bounty_hunt.command.BHCommand;
import net.mercdev.casino.bounty_hunt.db.BHDatabase;
import net.mercdev.casino.bounty_hunt.listener.BHGuiListener;
import net.mercdev.casino.bounty_hunt.listener.BHListener;
import net.mercdev.casino.bounty_hunt.manager.BountyManager;
import net.mercdev.casino.core.CasinoPlugin;

public class BountyHunt {

    private CasinoPlugin plugin;

    private BHDatabase database;
    private BHCommand command;
    private BountyManager manager;

    public BountyHunt(CasinoPlugin plugin){
        this.plugin = plugin;
    }

    public void init(){
        database = new BHDatabase(plugin);
        database.init();

        manager = new BountyManager(plugin, database, plugin.getEconomyManager(), plugin.getAuditLogger());

        plugin.getServer().getPluginManager().registerEvents(new BHListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new BHGuiListener(this), plugin);

        command = new BHCommand(this);
        plugin.getCommand("bhunt").setExecutor(command);
        plugin.getCommand("bhunt").setTabCompleter(command);

        plugin.getLogger().log(Level.INFO, "Initialized Bounty-Hunt module.");
    }

    public void disable() {
        plugin.getLogger().log(Level.INFO, "Disabling Bounty-hunt module.");
        if (database != null)
            database.close();
    }

    public CasinoPlugin getCasinoPlugin(){
        return plugin;
    }

    public BountyManager getBountyManager(){
        return manager;
    }

    public BHDatabase getDatabase(){
        return database;
    }
}
