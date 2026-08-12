package net.mercdev.casino.core.games.coinflip;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-vs-player coinflip: two players stake equal amounts, a fair 50/50 SecureRandom
 * flip decides the winner, who takes both stakes. Zero house involvement by design — no
 * rake, no edge, HouseBankroll is never touched by this game.
 * <p>
 * Open challenges are shared state, not per-player session state, so they live here on
 * the singleton CoinflipGame rather than in a GameSession. A challenger's stake is
 * escrowed (deducted) the instant they create a challenge, and refunded if they cancel
 * or disconnect before anyone accepts — see {@link #onPlayerQuit}. That refund-on-quit is
 * what lets {@link #acceptChallenge} assume the challenger is always online: an open
 * challenge simply cannot outlive its creator's session, so there's never a need (and
 * never a safe way) to credit an offline player's balance here.
 */
public class CoinflipGame implements CasinoGame {

    public record OpenChallenge(UUID challengerId, String challengerName, long amount, long createdAt) {}

    private final Map<UUID, OpenChallenge> openChallenges = new LinkedHashMap<>();

    public Collection<OpenChallenge> getOpenChallenges() {
        return openChallenges.values();
    }

    public boolean hasOpenChallenge(UUID uuid) {
        return openChallenges.containsKey(uuid);
    }

    /** Creates and escrows a new open challenge. Returns a player-facing error, or empty on success. */
    public Optional<String> createChallenge(CasinoPlugin plugin, Player player, long amount) {
        if (hasOpenChallenge(player.getUniqueId())) {
            return Optional.of("You already have an open duel — cancel it first.");
        }
        String denyReason = plugin.getBetLimitManager().checkBet(player, getId(), amount);
        if (denyReason != null) {
            return Optional.of(denyReason);
        }
        if (!plugin.getEconomyManager().hasBalance(player, amount)) {
            return Optional.of("You don't have " + amount + " chips.");
        }
        plugin.getEconomyManager().removeChips(player, amount);
        openChallenges.put(player.getUniqueId(),
                new OpenChallenge(player.getUniqueId(), player.getName(), amount, System.currentTimeMillis()));
        plugin.getBetLimitManager().recordBet(player);
        return Optional.empty();
    }

    /** Cancels and refunds the player's own open challenge, if they have one. */
    public boolean cancelChallenge(CasinoPlugin plugin, Player player) {
        OpenChallenge challenge = openChallenges.remove(player.getUniqueId());
        if (challenge == null) return false;
        plugin.getEconomyManager().addChips(player, challenge.amount());
        plugin.getAuditLogger().logBet(player.getUniqueId(), getId(), challenge.amount(), challenge.amount(), "CANCELLED");
        return true;
    }

    /** Accepts an open challenge and resolves the flip immediately. Returns a player-facing
     *  error on failure (both players are already messaged internally on success). */
    public Optional<String> acceptChallenge(CasinoPlugin plugin, Player acceptor, UUID challengerId) {
        if (acceptor.getUniqueId().equals(challengerId)) {
            return Optional.of("You can't accept your own flip.");
        }
        OpenChallenge challenge = openChallenges.get(challengerId);
        if (challenge == null) {
            return Optional.of("That flip is no longer available.");
        }
        Player challenger = Bukkit.getPlayer(challengerId);
        if (challenger == null) {
            // Shouldn't happen — onPlayerQuit refunds and removes a challenge the instant
            // its owner disconnects — but don't trust that invariant blindly.
            openChallenges.remove(challengerId);
            return Optional.of("That flip is no longer available.");
        }
        if (!plugin.getEconomyManager().hasBalance(acceptor, challenge.amount())) {
            return Optional.of("You don't have " + challenge.amount() + " chips.");
        }

        openChallenges.remove(challengerId);
        plugin.getEconomyManager().removeChips(acceptor, challenge.amount());
        plugin.getBetLimitManager().recordBet(acceptor);

        boolean challengerWins = SecureRng.nextBoolean();
        Player winner = challengerWins ? challenger : acceptor;
        long pot = challenge.amount() * 2;
        plugin.getEconomyManager().addChips(winner, pot);

        plugin.getAuditLogger().logBet(challenger.getUniqueId(), getId(), challenge.amount(),
                challengerWins ? pot : 0, challengerWins ? "WIN" : "LOSE");
        plugin.getAuditLogger().logBet(acceptor.getUniqueId(), getId(), challenge.amount(),
                challengerWins ? 0 : pot, challengerWins ? "LOSE" : "WIN");

        challenger.sendMessage(challengerWins
                ? "§a§lYou won the coinflip! §f+" + pot + " chips (vs " + acceptor.getName() + ")"
                : "§c" + acceptor.getName() + " won the coinflip. §f-" + challenge.amount() + " chips.");
        acceptor.sendMessage(challengerWins
                ? "§c" + challenger.getName() + " won the coinflip. §f-" + challenge.amount() + " chips."
                : "§a§lYou won the coinflip! §f+" + pot + " chips (vs " + challenger.getName() + ")");

        return Optional.empty();
    }

    @Override
    public void onPlayerQuit(CasinoPlugin plugin, Player player) {
        OpenChallenge challenge = openChallenges.remove(player.getUniqueId());
        if (challenge != null) {
            plugin.getEconomyManager().addChips(player, challenge.amount());
            plugin.getAuditLogger().logBet(player.getUniqueId(), getId(), challenge.amount(),
                    challenge.amount(), "REFUND_QUIT");
        }
    }

    @Override
    public String getId() {
        return "coinflip";
    }

    @Override
    public String getDisplayName() {
        return "Duels";
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eDuels");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new CoinflipSession(plugin, player, this);
    }
}