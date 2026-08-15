package net.mercdev.casino.core.games.mines;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Push-your-luck Mines: a 5x5 grid, a fixed number of hidden mines placed once at the
 * start of a round (not redrawn per click, unlike HiLo's independent card draws — this is
 * sampling without replacement from a static board). Each safe tile revealed raises the
 * multiplier; a mine ends the round with nothing.
 * <p>
 * Multiplier per step = 1 / P(next reveal is safe), scaled by the configured house edge —
 * same "fair odds by construction" approach as HiLo, verified analytically to be exactly
 * fair (EV = 0) pre-scaling at every mine count and reveal count. Like HiLo, there's no
 * fixed payout ceiling, so HouseBankroll capacity is checked before every reveal, not just
 * at the start of a round.
 */
public class MinesGame implements CasinoGame {

    public static final int GRID_SIZE = 25; // 5x5
    public static final int MIN_MINES = 1;
    public static final int MAX_MINES = 24; // must leave at least 1 safe tile
    public static final int DEFAULT_MINES = 3;

    private final double houseEdge;

    public MinesGame(CasinoPlugin plugin) {
        this.houseEdge = plugin.getHouseEdge();
    }

    /** Places mineCount mines uniformly at random among the 25 tile positions (0-24),
     *  each of the C(25, mineCount) possible layouts equally likely. */
    public Set<Integer> generateMines(int mineCount) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE; i++) positions.add(i);
        Set<Integer> mines = new HashSet<>();
        for (int i = 0; i < mineCount; i++) {
            int idx = SecureRng.nextInt(positions.size());
            mines.add(positions.remove(idx));
        }
        return mines;
    }

    /** Fair-odds multiplier for revealing one more safe tile, given how many have already
     *  been safely revealed this round, scaled by the configured house edge. */
    public double stepMultiplier(int mineCount, int safeRevealedSoFar) {
        int remainingTiles = GRID_SIZE - safeRevealedSoFar;
        int remainingSafe = remainingTiles - mineCount;
        double probability = remainingSafe / (double) remainingTiles;
        return (1.0 / probability) * (1 - houseEdge);
    }

    @Override
    public String getId() {
        return "mines";
    }

    @Override
    public String getDisplayName() {
        return getMenuIcon().getItemMeta().getDisplayName();
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.TNT);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cMines");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new MinesSession(plugin, player, this);
    }
}