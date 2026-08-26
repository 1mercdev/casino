package net.mercdev.casino.core.gui;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Shared sound/particle/title feedback for game outcomes, so every game's "you won" or
 * "you lost" moment feels the same rather than each session hand-rolling its own.
 * <p>
 * Every Sound/Particle constant here was checked against the Paper 1.21/1.21.1 javadocs
 * before use — several sound and particle names change between Minecraft versions (e.g.
 * VILLAGER_HAPPY became HAPPY_VILLAGER), and this project already hit one real bug from
 * an unverified Bukkit API assumption (see the sqlite-jdbc shading note in the README).
 * Worth re-checking if the target Minecraft version ever moves past 1.21.x.
 */
public final class GameFx {

    private GameFx() {}

    /** A modest win — a pair on Slots, a small correct guess, etc. */
    public static void win(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 10, 0.4, 0.4, 0.4);
    }

    /** The big one — a Slots triple, a full Mines clear, a large HiLo cash-out. */
    public static void jackpot(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.05);
        player.sendTitle("§6§lJACKPOT", "", 5, 40, 10);
    }

    /** A plain, non-bust loss (no luck this spin, dealer wins, wrong guess). */
    public static void lose(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    /** Hitting a mine, busting in Blackjack — a harder, more punishing loss. */
    public static void bust(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1f);
        player.spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 18, 0.4, 0.4, 0.4, 0.02);
    }

    /** Generic menu navigation (bet adjust, page change) — deliberately quiet. */
    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1f);
    }

    /** A lever/spin-style action trigger (Slots spin, Roulette bet placed). */
    public static void lever(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 1f);
    }

    /** Chips changing hands — deposit, withdraw, purchase, bet placed. */
    public static void chip(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.4f);
    }

    /** A single tile/card reveal that isn't itself a win or loss (Mines safe tile, HiLo card). */
    public static void reveal(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }
}
