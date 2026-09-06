package net.mercdev.casino.bounty_hunt.db;

import net.mercdev.casino.bounty_hunt.types.Bounty;
import net.mercdev.casino.bounty_hunt.types.BountyStatus;
import net.mercdev.casino.core.CasinoPlugin;

import java.util.Optional;
import java.util.UUID;
import java.sql.Statement;
import java.util.logging.Level;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BHDatabase {

    private final CasinoPlugin plugin;
    private Connection connection;

    public BHDatabase(CasinoPlugin plugin){
        this.plugin = plugin;
        this.connection = plugin.getAuditLogger().getNewConnection();
    }

    public synchronized void init(){
        if (connection == null) {
            plugin.getLogger().log(Level.SEVERE, "Database connection was not initlialized properly. BountyHunt is not going to start.");
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE IF NOT EXISTS bounties (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    reward INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE IF NOT EXISTS bounty_rerolls (
                    uuid TEXT PRIMARY KEY,
                    rolled_at INTEGER NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_bounty
                ON bounties(owner_uuid)
                WHERE status = 'ACTIVE';

                CREATE INDEX IF NOT EXISTS idx_bounty_target
                ON bounties(target_uuid);
            """);
        }
        catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database properly (BHAudit)", e);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()){
                connection.close();
            }
        } catch (SQLException e){
            plugin.getLogger().log(Level.WARNING, "Failed to shutdown BountyHunt database normally.", e);
        }
    }

    private Bounty toBounty(ResultSet rs) throws SQLException {
    return new Bounty(
        rs.getLong("id"),
        UUID.fromString(rs.getString("owner_uuid")),
        UUID.fromString(rs.getString("target_uuid")),
        rs.getLong("reward"),
        BountyStatus.valueOf(rs.getString("status")),
        rs.getLong("created_at"),
        rs.getLong("expires_at")
    );
}

    public synchronized Bounty createBounty(UUID ownerUuid, UUID targetUuid, int reward){
        long now = System.currentTimeMillis();
        long expiry = now + plugin.getConfig().getConfigurationSection("bounty-hunt").getInt("bounty-duration-hours", 10) * 60L * 60L * 1000L;
        String sql = "INSERT INTO bounties (owner_uuid, target_uuid, reward, status, created_at, expires_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, targetUuid.toString());
            ps.setInt(3, reward);
            ps.setLong(4, now);
            ps.setLong(5, expiry);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Bounty(rs.getLong("id"), targetUuid, targetUuid, reward, BountyStatus.ACTIVE, now, expiry);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create bounty for " + ownerUuid, e);
        }
        return null;
    }

    public synchronized void logReroll(UUID uuid){
        String sql = "INSERT INTO bounty_rerolls (uuid, rolled_at) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not log bounty reroll for player: " + uuid, e);
        }
    }

    /* WILL return 0L even if an error occurs, so caution must be made. */
    public synchronized long getLastRerollTime(UUID uuid) {
        String sql = "SELECT rolled_at FROM bounty_rerolls WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getLong("rolled_at");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not query latest bounty rerolls for player:" + uuid, e);
        }
        return 0L;
    }
    
    /* Won't do anything if bounty isn't marked as ACTIVE */
    public synchronized void updateStatus(long id, BountyStatus status){
        String sql = "UPDATE bounties SET status = ? WHERE id = ? AND status = ACTIVE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update status for bounty id: " + id, e);
        }
    }

    public synchronized Optional<Bounty> findActive(UUID uuid) {
        String sql = """
            SELECT id, owner_uuid, target_uuid, reward,
                status, created_at, expires_at
            FROM bounties
            WHERE owner_uuid = ?
            AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """;
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()){
                    return Optional.empty();
                }
                return Optional.of(toBounty(rs));
            }
        } catch (SQLException e ) {
            plugin.getLogger().log(Level.WARNING, "db: Could not execute query for user " + uuid + "'s bounties.", e);
        }
        return null;
    }
}
