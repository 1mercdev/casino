package net.mercdev.casino.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.mercdev.casino.core.audit.AuditLogger;
import net.mercdev.casino.core.betting.BetLimitManager;
import net.mercdev.casino.core.command.CasinoCommand;
import net.mercdev.casino.core.economy.EconomyManager;
import net.mercdev.casino.core.economy.HouseBankroll;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameRegistry;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.game.SessionManager;
import net.mercdev.casino.core.games.slots.SlotsGame;
import net.mercdev.casino.core.games.blackjack.BlackjackGame;
import net.mercdev.casino.core.games.coinflip.CoinflipGame;
import net.mercdev.casino.core.games.hilo.HiLoGame;
import net.mercdev.casino.core.games.mines.MinesGame;
import net.mercdev.casino.core.games.roulette.RouletteGame;
import net.mercdev.casino.core.games.shop.ShopGame;
import net.mercdev.casino.core.listener.CasinoGuiListener;
import net.mercdev.casino.core.listener.PlayerJoinQuitListener;

public class CasinoPlugin extends JavaPlugin {

    private AuditLogger auditLogger;
    private EconomyManager economyManager;
    private HouseBankroll houseBankroll;
    private BetLimitManager betLimitManager;
    private GameRegistry gameRegistry;
    private SessionManager sessionManager;
    private Material currencyItem;
    private double houseEdge;

    private boolean isPluginEnabled(String id, ConfigurationSection gameSection){
        return gameSection.getConfigurationSection(id).getBoolean("enabled", true);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        currencyItem = Material.matchMaterial(getConfig().getString("currency-item", "ECHO_SHARD"));
        if (currencyItem == null) {
            getLogger().warning("Invalid currency-item in config.yml, falling back to ECHO_SHARD.");
            currencyItem = Material.ECHO_SHARD;
        }
        houseEdge = getConfig().getDouble("house-edge", 0.02);

        auditLogger = new AuditLogger(this, getConfig().getString("database.file", "casino.db"));
        auditLogger.init();

        long configuredStart = getConfig().getLong("house-starting-bankroll", 0);
        long storedBankroll = auditLogger.loadBankroll();
        houseBankroll = new HouseBankroll(this, auditLogger, storedBankroll >= 0 ? storedBankroll : configuredStart);

        economyManager = new EconomyManager(this, auditLogger, currencyItem);
        
        ConfigurationSection gameSection = getConfig().getConfigurationSection("games");
        betLimitManager = new BetLimitManager(gameSection,
                getConfig().getLong("bet-cooldown-ms", 750));

        gameRegistry = new GameRegistry();
        sessionManager = new SessionManager();

        gameRegistry.tryRegister(new SlotsGame(this), isPluginEnabled("slots", gameSection));
        gameRegistry.tryRegister(new CoinflipGame(), isPluginEnabled("coinflip", gameSection));
        gameRegistry.tryRegister(new ShopGame(this), isPluginEnabled("shop", gameSection));
        gameRegistry.tryRegister(new RouletteGame(), isPluginEnabled("roulette", gameSection));
        gameRegistry.tryRegister(new HiLoGame(this), isPluginEnabled("hilo", gameSection));
        gameRegistry.tryRegister(new BlackjackGame(), isPluginEnabled("blackjack", gameSection));
        gameRegistry.tryRegister(new MinesGame(this), isPluginEnabled("mines", gameSection));

        getServer().getPluginManager().registerEvents(new CasinoGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

        CasinoCommand casinoCommand = new CasinoCommand(this);
        getCommand("casino").setExecutor(casinoCommand);
        getCommand("casino").setTabCompleter(casinoCommand);

        // Covers the case of a /reload: players already online won't get a join event.
        for (Player player : getServer().getOnlinePlayers()) {
            economyManager.load(player.getUniqueId());
        }

        getLogger().info("Casino framework enabled. " + gameRegistry.all().size() + " game(s) registered.");
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.saveAllSync();
        }
        if (auditLogger != null) {
            auditLogger.close();
        }
    }

    /** Opens a game for a player, enforcing one active session at a time. */
    public void openGame(Player player, CasinoGame game) {
        if (sessionManager.get(player) != null) {
            player.sendMessage("§cFinish or close your current game first.");
            return;
        }
        GameSession session = game.createSession(this, player);
        sessionManager.put(player, session);
        session.open();
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HouseBankroll getHouseBankroll() {
        return houseBankroll;
    }

    public BetLimitManager getBetLimitManager() {
        return betLimitManager;
    }

    public GameRegistry getGameRegistry() {
        return gameRegistry;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public Material getCurrencyItem() {
        return currencyItem;
    }

    public double getHouseEdge() {
        return houseEdge;
    }
}
