package net.mercdev.casino.core.games.hilo;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Push-your-luck Hi-Lo: guess whether the next card is higher or lower than the current
 * one, ranks 1 (Ace) through 13 (King), drawn independently and uniformly each time — an
 * infinite deck, not a finite shoe. There's nothing to card-count; every guess has exactly
 * the odds computed below, regardless of what's been drawn before. A tie (the next card
 * matches the current rank) counts as a loss for the guess.
 * <p>
 * Payout per correct guess is dynamic: multiplier = (1 / true win probability), scaled by
 * the configured house edge. A "safe" guess (Higher on a 2) pays little; a "risky" one
 * (Higher on a Queen) pays a lot. Verified analytically that this construction is exactly
 * fair (EV = 0) pre-scaling for every rank, including the edge cases (rank 1 and 13) — the
 * odds/payout shown in the GUI are the literal numbers the payout is computed from.
 * <p>
 * Unlike Slots, a streak's multiplier compounds with no fixed ceiling, so this game never
 * tries to reserve a worst-case payout up front. Instead HiLoSession checks HouseBankroll
 * capacity before every single guess (not just at the start of a round) and refuses to
 * extend the streak if the bankroll couldn't cover the next step — the player can still
 * cash out at their current, already-covered total. One known gap: while a round is open,
 * its potential payout is invisible to other players' bankroll checks (it's not "reserved"
 * as a lump sum the way Slots/Roulette reserve theirs) — with ~15 casual players this is a
 * low-impact edge case, but if the bankroll ever runs uncomfortably thin, that's why.
 */
public class HiLoGame implements CasinoGame {

    public enum Guess { HIGHER, LOWER }

    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 13;
    private static final int RANK_COUNT = MAX_RANK - MIN_RANK + 1;

    private final double houseEdge;

    public HiLoGame(CasinoPlugin plugin) {
        this.houseEdge = plugin.getHouseEdge();
    }

    public int drawCard() {
        return SecureRng.nextInt(MIN_RANK, MAX_RANK);
    }

    public boolean canGuess(int currentRank, Guess guess) {
        return guess == Guess.HIGHER ? currentRank < MAX_RANK : currentRank > MIN_RANK;
    }

    public double winProbability(int currentRank, Guess guess) {
        int favorable = guess == Guess.HIGHER ? (MAX_RANK - currentRank) : (currentRank - MIN_RANK);
        return favorable / (double) RANK_COUNT;
    }

    /** Fair-odds multiplier for this single guess, scaled by the configured house edge. */
    public double stepMultiplier(int currentRank, Guess guess) {
        return (1.0 / winProbability(currentRank, guess)) * (1 - houseEdge);
    }

    /** Ties (newRank == currentRank) count as a loss. */
    public boolean isWin(int currentRank, int newRank, Guess guess) {
        if (newRank == currentRank) return false;
        return guess == Guess.HIGHER ? newRank > currentRank : newRank < currentRank;
    }

    public static String rankName(int rank) {
        return switch (rank) {
            case 1 -> "Ace";
            case 11 -> "Jack";
            case 12 -> "Queen";
            case 13 -> "King";
            default -> String.valueOf(rank);
        };
    }

    public static Material rankIcon(int rank) {
        if (rank <= 4) return Material.COAL;
        if (rank <= 8) return Material.IRON_INGOT;
        if (rank <= 11) return Material.GOLD_INGOT;
        return Material.DIAMOND;
    }

    @Override
    public String getId() {
        return "hilo";
    }

    @Override
    public String getDisplayName() {
        return getMenuIcon().getItemMeta().getDisplayName();
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bHiLo");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new HiLoSession(plugin, player, this);
    }
}