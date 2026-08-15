package net.mercdev.casino.core.games.roulette;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;

/**
 * Standard European roulette: single 0, 37 pockets. Unlike Slots/HiLo, this game's house
 * edge is deliberately NOT derived from config.yml's "house-edge" value — it's a
 * structural consequence of the single 0 pocket (1/37 \u2248 2.70%) combined with standard,
 * recognizable casino payout ratios (35:1 straight up, 2:1 dozens, 1:1 even-money).
 * Scaling these to an arbitrary configured edge would mean paying off-standard odds on a
 * game players already know the real numbers for — verified analytically that all three
 * bet shapes land on exactly the same ~2.7027% edge, so there's no tuning needed here.
 * <p>
 * Only straight-up numbers and the standard even-money/dozen outside bets are supported —
 * no split/street/corner/six-line/column bets, and only one bet per spin (clicking a bet
 * target places that bet and spins immediately, there's no multi-bet "betting slip").
 * That's a real simplification of a full roulette table, made to fit cleanly in a
 * single-click GUI without a multi-selection concept the rest of the framework doesn't have.
 */
public class RouletteGame implements CasinoGame {

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** 35:1 payout = 36x total returned (profit + original stake). */
    private static final int STRAIGHT_TOTAL_MULTIPLIER = 36;

    public enum OutsideBet {
        RED(2), BLACK(2), ODD(2), EVEN(2), LOW(2), HIGH(2),
        DOZEN_1(3), DOZEN_2(3), DOZEN_3(3);

        private final int totalMultiplier;

        OutsideBet(int totalMultiplier) {
            this.totalMultiplier = totalMultiplier;
        }

        public int totalMultiplier() {
            return totalMultiplier;
        }
    }

    /** Spins the wheel: a uniform result in [0, 36]. */
    public int spin() {
        return SecureRng.nextInt(37);
    }

    public boolean isRed(int number) {
        return RED_NUMBERS.contains(number);
    }

    public boolean isBlack(int number) {
        return number != 0 && !RED_NUMBERS.contains(number);
    }

    /** Standard rule: 0 loses every outside bet. */
    public boolean outsideBetWins(OutsideBet bet, int result) {
        if (result == 0) return false;
        return switch (bet) {
            case RED -> isRed(result);
            case BLACK -> isBlack(result);
            case ODD -> result % 2 == 1;
            case EVEN -> result % 2 == 0;
            case LOW -> result >= 1 && result <= 18;
            case HIGH -> result >= 19 && result <= 36;
            case DOZEN_1 -> result >= 1 && result <= 12;
            case DOZEN_2 -> result >= 13 && result <= 24;
            case DOZEN_3 -> result >= 25 && result <= 36;
        };
    }

    public long straightPayout(long bet) {
        return bet * STRAIGHT_TOTAL_MULTIPLIER;
    }

    public long outsidePayout(OutsideBet betType, long bet) {
        return bet * betType.totalMultiplier();
    }

    @Override
    public String getId() {
        return "roulette";
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.CLOCK);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cRoulette");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new RouletteSession(plugin, player, this);
    }
}