package net.mercdev.casino.core.games.blackjack;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard blackjack: infinite shoe (each draw is an independent, uniform pick of one of
 * 13 ranks — not a depleting 52-card deck), dealer stands on all 17s, no split, no
 * insurance, double-down allowed only as the first action. Blackjack (Ace + 10-value card
 * as the first two cards) pays 3:2, a regular win pays 1:1, a push returns the bet.
 * <p>
 * Like Roulette, these payouts are fixed/standard rather than scaled to the config
 * house-edge value — blackjack's edge comes structurally from the player busting (and
 * losing immediately) before the dealer's hand is even compared, not from a tunable
 * multiplier, so there's no clean single number to scale the way Slots' payout table has.
 * With these rules that edge is the ordinary small blackjack edge (roughly 0.5-1% under
 * reasonable play) — that's the standard, well-known order of magnitude for this ruleset,
 * not a number derived here the way the other games' edges were.
 */
public class BlackjackGame implements CasinoGame {

    public enum Rank {
        ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING;

        public String label() {
            return switch (this) {
                case ACE -> "A";
                case TWO -> "2";
                case THREE -> "3";
                case FOUR -> "4";
                case FIVE -> "5";
                case SIX -> "6";
                case SEVEN -> "7";
                case EIGHT -> "8";
                case NINE -> "9";
                case TEN -> "10";
                case JACK -> "J";
                case QUEEN -> "Q";
                case KING -> "K";
            };
        }

        /** Blackjack value; Ace defaults to 11 here, hand value logic downgrades as needed. */
        public int baseValue() {
            return switch (this) {
                case ACE -> 11;
                case TWO -> 2;
                case THREE -> 3;
                case FOUR -> 4;
                case FIVE -> 5;
                case SIX -> 6;
                case SEVEN -> 7;
                case EIGHT -> 8;
                case NINE -> 9;
                case TEN, JACK, QUEEN, KING -> 10;
            };
        }
    }

    public static Rank draw() {
        return Rank.values()[SecureRng.nextInt(13)];
    }

    /** Ace-flexible hand value: as many Aces as needed count as 1 instead of 11 to avoid busting. */
    public static int handValue(List<Rank> hand) {
        int total = 0;
        int aceCount = 0;
        for (Rank r : hand) {
            total += r.baseValue();
            if (r == Rank.ACE) aceCount++;
        }
        while (total > 21 && aceCount > 0) {
            total -= 10;
            aceCount--;
        }
        return total;
    }

    public static boolean isBust(List<Rank> hand) {
        return handValue(hand) > 21;
    }

    public static boolean isBlackjack(List<Rank> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    /** Dealer hits until 17 or more, no choices involved. */
    public static List<Rank> playDealerHand(List<Rank> dealerHand) {
        List<Rank> hand = new ArrayList<>(dealerHand);
        while (handValue(hand) < 17) {
            hand.add(draw());
        }
        return hand;
    }

    @Override
    public String getId() {
        return "blackjack";
    }

    @Override
    public String getDisplayName() {
        return getMenuIcon().getItemMeta().getDisplayName();
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2Blackjack");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new BlackjackSession(plugin, player, this);
    }
}