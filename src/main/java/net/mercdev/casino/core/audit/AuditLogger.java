package net.mercdev.casino.core.audit;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite-backed persistence and audit trail: player balances, house bankroll, every
 * deposit/withdrawal, and every resolved bet.
 * <p>
 * All methods here are synchronous JDBC calls. {@link #init()} must run on the main
 * thread during startup (before any event can fire); everything else is normally
 * called from a background task scheduled by the caller (see EconomyManager /
 * HouseBankroll), except for the final flush on shutdown, which is deliberately
 * synchronous since async tasks aren't guaranteed to run once the plugin is disabling.
 */
public class AuditLogger {

    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private Connection connection;

    public AuditLogger(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        File dbFile = new File(plugin.getDataFolder(), fileName);
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public synchronized void init() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS players (
                            uuid TEXT PRIMARY KEY,
                            chip_balance INTEGER NOT NULL DEFAULT 0
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS house (
                            id INTEGER PRIMARY KEY CHECK (id = 1),
                            bankroll INTEGER NOT NULL DEFAULT 0
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT NOT NULL,
                            type TEXT NOT NULL,
                            amount INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS bets (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT NOT NULL,
                            game TEXT NOT NULL,
                            wager INTEGER NOT NULL,
                            payout INTEGER NOT NULL,
                            result TEXT NOT NULL,
                            timestamp INTEGER NOT NULL
                        )""");
            }
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize casino database", e);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to close casino database cleanly", e);
        }
    }

    public synchronized long loadBalance(UUID uuid) {
        String sql = "SELECT chip_balance FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("chip_balance");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load balance for " + uuid, e);
        }
        return 0L;
    }

    public synchronized void saveBalance(UUID uuid, long balance) {
        String sql = """
                INSERT INTO players (uuid, chip_balance) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET chip_balance = excluded.chip_balance
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save balance for " + uuid, e);
        }
    }

    /** Returns the persisted bankroll, or -1 if no row exists yet (first-ever run). */
    public synchronized long loadBankroll() {
        String sql = "SELECT bankroll FROM house WHERE id = 1";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong("bankroll");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load house bankroll", e);
        }
        return -1L;
    }

    public synchronized void saveBankroll(long bankroll) {
        String sql = """
                INSERT INTO house (id, bankroll) VALUES (1, ?)
                ON CONFLICT(id) DO UPDATE SET bankroll = excluded.bankroll
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bankroll);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save house bankroll", e);
        }
    }

    public synchronized void logTransaction(UUID uuid, String type, long amount) {
        String sql = "INSERT INTO transactions (uuid, type, amount, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.setLong(3, amount);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log transaction for " + uuid, e);
        }
    }

    public synchronized void logBet(UUID uuid, String game, long wager, long payout, String result) {
        String sql = "INSERT INTO bets (uuid, game, wager, payout, result, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, game);
            ps.setLong(3, wager);
            ps.setLong(4, payout);
            ps.setString(5, result);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log bet for " + uuid, e);
        }
    }
}
