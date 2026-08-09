package net.mercdev.casino.core.economy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.mercdev.casino.core.audit.AuditLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every player's chip balance. Balances live in memory for instant reads/writes
 * during gameplay; every change also schedules an async write-through to SQLite via
 * AuditLogger, so gameplay never blocks on disk I/O. A crash between writes loses at
 * most the very last in-memory delta — the transaction/bet tables give a full audit
 * trail regardless.
 * <p>
 * Deposit/withdraw convert 1:1 between chips and the physical currency item (echo
 * shards) in the player's inventory. This never touches the house bankroll — it's
 * purely reformatting a player's own already-owned value, not house money.
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private final AuditLogger audit;
    private final Material currencyItem;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    public EconomyManager(JavaPlugin plugin, AuditLogger audit, Material currencyItem) {
        this.plugin = plugin;
        this.audit = audit;
        this.currencyItem = currencyItem;
    }

    /** Loads a player's balance from the database into the in-memory cache. Call on join. */
    public void load(UUID uuid) {
        balances.put(uuid, audit.loadBalance(uuid));
    }

    /** Schedules an async flush of a player's current balance to the database. */
    private void saveAsync(UUID uuid) {
        Long balance = balances.get(uuid);
        if (balance == null) return;
        long snapshot = balance;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> audit.saveBalance(uuid, snapshot));
    }

    /** Flushes then drops a player's cached balance. Call on quit. */
    public void unload(UUID uuid) {
        saveAsync(uuid);
        balances.remove(uuid);
    }

    /** Synchronously flushes every cached balance. Only for use during shutdown. */
    public void saveAllSync() {
        balances.forEach(audit::saveBalance);
    }

    public long getBalance(Player player) {
        return balances.getOrDefault(player.getUniqueId(), 0L);
    }

    public boolean hasBalance(Player player, long amount) {
        return getBalance(player) >= amount;
    }

    /** Adds chips to a player's balance (a game payout, or a deposit). */
    public void addChips(Player player, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
        balances.merge(player.getUniqueId(), amount, Long::sum);
        saveAsync(player.getUniqueId());
    }

    /** Removes chips from a player's balance (a bet). Returns false if they don't have enough. */
    public boolean removeChips(Player player, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
        UUID uuid = player.getUniqueId();
        long current = balances.getOrDefault(uuid, 0L);
        if (current < amount) return false;
        balances.put(uuid, current - amount);
        saveAsync(uuid);
        return true;
    }

    /**
     * Converts physical currency items from the player's inventory into chips, 1:1.
     * Returns false if the player doesn't have enough of the item.
     */
    public boolean deposit(Player player, int amount) {
        if (amount <= 0) return false;
        ItemStack toRemove = new ItemStack(currencyItem, amount);
        if (!player.getInventory().containsAtLeast(toRemove, amount)) {
            return false;
        }
        player.getInventory().removeItem(toRemove);
        addChips(player, amount);
        audit.logTransaction(player.getUniqueId(), "deposit", amount);
        return true;
    }

    /**
     * Converts chips back into physical currency items, 1:1, given to the player directly.
     * Returns false if the player doesn't have enough chips. Any inventory overflow is
     * dropped at the player's feet rather than destroyed, since they already paid for it.
     */
    public boolean withdraw(Player player, int amount) {
        if (amount <= 0) return false;
        if (!hasBalance(player, amount)) return false;

        removeChips(player, amount);
        ItemStack toGive = new ItemStack(currencyItem, amount);
        player.getInventory().addItem(toGive).values()
                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        audit.logTransaction(player.getUniqueId(), "withdraw", amount);
        return true;
    }
}
