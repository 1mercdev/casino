package net.mercdev.casino.core.economy;

import org.bukkit.plugin.java.JavaPlugin;

import net.mercdev.casino.core.audit.AuditLogger;

/**
 * Tracks the house's own chip bankroll, entirely separate from player balances.
 * <p>
 * Flow for a player-vs-house game: when a bet is placed, the wager moves into the
 * bankroll immediately ({@link #reserveBet}). When the round resolves, the payout
 * (if any) moves back out ({@link #resolveBet}). A bet should only be accepted if
 * {@link #canAcceptBet} confirms the bankroll could survive the game's worst-case
 * payout for that wager — that's what stops a lucky streak from minting chips (and
 * therefore withdrawable items) the server never actually backed.
 * <p>
 * Coinflip never touches this class: chips move directly between two players'
 * balances via EconomyManager, with zero house involvement by design.
 */
public class HouseBankroll {

    private final JavaPlugin plugin;
    private final AuditLogger audit;
    private long bankroll;

    public HouseBankroll(JavaPlugin plugin, AuditLogger audit, long startingBankroll) {
        this.plugin = plugin;
        this.audit = audit;
        this.bankroll = startingBankroll;
    }

    public synchronized long getBankroll() {
        return bankroll;
    }

    /**
     * True if, after taking this wager, the bankroll could still cover the largest
     * payout this game could possibly award for it. Call before accepting any bet.
     */
    public synchronized boolean canAcceptBet(long wager, long maxPossiblePayout) {
        return (bankroll + wager) >= maxPossiblePayout;
    }

    /** Moves a wager from the player into the bankroll. Call only after canAcceptBet() is true. */
    public synchronized void reserveBet(long wager) {
        bankroll += wager;
        persist();
    }

    /** Pays out winnings from the bankroll back to the player. Pass 0 if the player lost. */
    public synchronized void resolveBet(long payout) {
        bankroll -= payout;
        persist();
    }

    /** Admin top-up: injects chips into the bankroll (backed by shards the admin deposited). */
    public synchronized void deposit(long amount) {
        bankroll += amount;
        persist();
    }

    private void persist() {
        long snapshot = bankroll;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> audit.saveBankroll(snapshot));
    }
}
