package net.mercdev.casino.core.games.coinflip;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.gui.GuiItems;
import net.mercdev.casino.core.gui.GameFx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's view into the shared Coinflip queue: controls to set a bet and create (or
 * cancel) their own open challenge, plus a grid of other players' open challenges to
 * accept. The queue itself lives on {@link CoinflipGame}, not here — this class only
 * renders it and forwards clicks, so a challenge someone creates keeps waiting even
 * after they close this GUI.
 * <p>
 * The challenge grid is a snapshot taken when this GUI was opened or last touched by
 * this player; it won't live-update if someone else accepts a challenge you're looking
 * at (you'd just get "no longer available" if you clicked it) or creates a new one.
 * Fine for a small server — worth revisiting if this ever needs to feel more real-time.
 */
public class CoinflipSession extends GameSession {

    private static final int SLOT_BET_MINUS_5 = 1;
    private static final int SLOT_BET_MINUS_1 = 2;
    private static final int SLOT_BET_DISPLAY = 3;
    private static final int SLOT_BET_PLUS_1 = 4;
    private static final int SLOT_BET_PLUS_5 = 5;
    private static final int SLOT_ACTION = 7;
    private static final int SLOT_BALANCE = 8;

    private static final int FIRST_CHALLENGE_SLOT = 9;
    private static final int LAST_CHALLENGE_SLOT = 26;

    private final CoinflipGame coinflipGame;
    private final Map<Integer, UUID> slotToChallenger = new HashMap<>();
    private long currentBet;

    protected CoinflipSession(CasinoPlugin plugin, Player player, CoinflipGame game) {
        super(plugin, player, game);
        this.coinflipGame = game;
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "Duels");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.ORANGE_TERRACOTTA));
        }
        renderCreateControls();
        renderChallenges();
        player.openInventory(inventory);
    }

    private void renderCreateControls() {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());

        inventory.setItem(SLOT_BET_MINUS_5, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
        inventory.setItem(SLOT_BET_MINUS_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
        inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                "§7Min " + min + " / Max " + max));
        inventory.setItem(SLOT_BET_PLUS_1, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
        inventory.setItem(SLOT_BET_PLUS_5, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));

        if (coinflipGame.hasOpenChallenge(player.getUniqueId())) {
            inventory.setItem(SLOT_ACTION, GuiItems.named(Material.BARRIER, "§cCancel your duel",
                    "§7You have an open duel waiting for", "§7an opponent. Click to refund it."));
        } else {
            inventory.setItem(SLOT_ACTION, GuiItems.named(Material.GOLD_NUGGET, "§a§lCreate duel",
                    "§7Challenge anyone for " + currentBet + " chips!", "§7You win, you keep everything."));
        }
        renderBalance();
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private void renderChallenges() {
        slotToChallenger.clear();
        for (int slot = FIRST_CHALLENGE_SLOT; slot <= LAST_CHALLENGE_SLOT; slot++) {
            inventory.setItem(slot, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        int slot = FIRST_CHALLENGE_SLOT;
        for (CoinflipGame.OpenChallenge challenge : coinflipGame.getOpenChallenges()) {
            if (challenge.challengerId().equals(player.getUniqueId())) continue; // you can't accept your own
            if (slot > LAST_CHALLENGE_SLOT) break; // more open flips than fit on screen — simplest cutoff for now

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(challenge.challengerId()));
                meta.setDisplayName("§e" + challenge.challengerName());
                meta.setLore(List.of("§7Bet: §f" + challenge.amount() + " chips", "§aClick to accept"));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
            slotToChallenger.put(slot, challenge.challengerId());
            slot++;
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_BET_MINUS_5 -> adjustBet(-5);
            case SLOT_BET_MINUS_1 -> adjustBet(-1);
            case SLOT_BET_PLUS_1 -> adjustBet(1);
            case SLOT_BET_PLUS_5 -> adjustBet(5);
            case SLOT_ACTION -> handleAction();
            default -> handleChallengeClick(slot);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // An open challenge lives on CoinflipGame, independent of this GUI/session — it's
        // meant to keep waiting after the creator closes this menu. Nothing to refund here;
        // refunds only happen via explicit cancel or on disconnect (CoinflipGame#onPlayerQuit).
        settled = true;
    }

    private void adjustBet(long delta) {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());
        currentBet = Math.max(min, Math.min(max, currentBet + delta));
        renderCreateControls();
    }

    private void handleAction() {
        if (coinflipGame.hasOpenChallenge(player.getUniqueId())) {
            coinflipGame.cancelChallenge(plugin, player);
            GameFx.click(player);
            player.sendMessage("§7Your duel was cancelled and refunded.");
        } else {
            Optional<String> error = coinflipGame.createChallenge(plugin, player, currentBet);
            if (error.isPresent()) {
                player.sendMessage("§c" + error.get());
            } else {
                GameFx.chip(player);
                player.sendMessage("§aDuel created for " + currentBet + " chips — waiting for someone to accept.");
            }
        }
        renderCreateControls();
        renderChallenges();
    }

    private void handleChallengeClick(int slot) {
        UUID challengerId = slotToChallenger.get(slot);
        if (challengerId == null) return;
        Optional<String> error = coinflipGame.acceptChallenge(plugin, player, challengerId);
        if (error.isPresent()) {
            player.sendMessage("§c" + error.get());
        }
        // Win/loss sound plays from the messages CoinflipGame already sent both players;
        // GameFx here just confirms the acceptance itself went through.
        renderCreateControls();
        renderChallenges();
    }
}
