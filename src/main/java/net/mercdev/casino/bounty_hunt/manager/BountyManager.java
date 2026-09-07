package net.mercdev.casino.bounty_hunt.manager;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import net.mercdev.casino.bounty_hunt.db.BHDatabase;
import net.mercdev.casino.bounty_hunt.types.Bounty;
import net.mercdev.casino.bounty_hunt.types.BountyStatus;
import net.mercdev.casino.core.audit.AuditLogger;
import net.mercdev.casino.core.economy.EconomyManager;

public class BountyManager {

    private final JavaPlugin plugin;
    private final BHDatabase database;
    private final EconomyManager manager;
    private final AuditLogger auditLogger;

    private boolean skipRegenCheck = false;

    private final Map<UUID,Bounty> bounties = new ConcurrentHashMap<>();

    public BountyManager(JavaPlugin plugin, BHDatabase database, EconomyManager manager, AuditLogger audit) {
        this.plugin = plugin;
        this.database = database;
        this.manager = manager;
        this.auditLogger = audit;
    }
    
    /* returns true if expired */
    public boolean checkBountyExpiry(Bounty bounty) {
        if (System.currentTimeMillis() > bounty.getExpiryTimeMillis()){
            bounty.setBountyStatus(BountyStatus.EXPIRED);
            database.updateStatus(bounty.getId(), BountyStatus.EXPIRED);
            return true;
        }
        return false;
    }

    public @Nullable Bounty getBounty(UUID uuid) {
        return bounties.get(uuid);
    }

    public boolean reroll(Player player) {
        long cooldown_ms = plugin.getConfig().getConfigurationSection("bounty-hunt").getInt("reroll-cooldown-minutes", 30) * 60L * 1000L;
        long last_reroll = database.getLastRerollTime(player.getUniqueId());
        long now = System.currentTimeMillis();
        long elapsed = now - last_reroll;

        if (last_reroll > 0 && elapsed < cooldown_ms){
            long remainingMs = cooldown_ms - elapsed;
            long minutes = (remainingMs / (60 * 1000)) % 60;
            long seconds = (remainingMs / 1000) % 60;
            player.sendMessage("§cYou cannot reroll your bounty yet. Try again in " + minutes + "m " + seconds + "s.");
            return false;
        }

        Bounty bounty = bounties.remove(player.getUniqueId());
        database.logReroll(player.getUniqueId());
        database.updateStatus(bounty.getId(), BountyStatus.REROLLED);
        
        regen(player);

        return true;
    }

    /* Provide the bounty or null it */
    private void payoutBounty(Player player, @Nullable Bounty _bounty) {
        Bounty bounty;
        if (_bounty != null)
            bounty = _bounty;
        else
            bounty = bounties.get(player.getUniqueId());

        if (!checkBountyExpiry(bounty))
            return;

        manager.addChips(player, bounty.getReward());
        auditLogger.logTransaction(player.getUniqueId(), "bounty-claim", bounty.getReward());

        manager.removeChips(Bukkit.getPlayer(bounty.getVictimUuid()), (bounty.getReward() / 100) * plugin.getConfig().getConfigurationSection("bounty-hunt").getInt("victim-payout-percentage", 70));
        auditLogger.logTransaction(bounty.getVictimUuid(), "bounty-payout", bounty.getReward());

        database.updateStatus(bounty.getId(), BountyStatus.CLAIMED);
    }

    /* Utilizes the private skipRegenCheck to break a loop upon player 2 join. */
    private void regen(Player player){
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.remove(player);

        if (players.size() == 1 && !skipRegenCheck){
            skipRegenCheck = true;
            loadPlayer(players.getFirst());
        }
        skipRegenCheck = false;

        if (players.size() >= 1) {
            Player randomPlayer = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            
            int reward = (((int)manager.getBalance(randomPlayer)) / 100) * (ThreadLocalRandom.current().nextInt(plugin.getConfig().getConfigurationSection("bounty-hunt").getInt("bounty-price-percentage", 25))) + 1;
            Bounty bounty = database.createBounty(player.getUniqueId(), randomPlayer.getUniqueId(), reward);

            bounties.put(player.getUniqueId(), bounty);
            return;
        }
        // if the server is empty, make sure a regen clears the cache.
        bounties.remove(player.getUniqueId());
    }

    public void tryBountyClaim(Player target, Player victim) {
        Bounty bounty = bounties.get(target.getUniqueId());
        if (bounty.getTargetUuid() == target.getUniqueId() && bounty.getVictimUuid() == victim.getUniqueId() && !checkBountyExpiry(bounty)) {
            payoutBounty(target, bounty);
        }
    }

    public void loadPlayer(Player player) {
        Optional<Bounty> bounty = database.findActive(player.getUniqueId());
        if (bounty == null){
            plugin.getLogger().warning("Database error while querying active bounty for user " + player.name());
            return;
        }
        if (bounty.isEmpty())
            regen(player);

        if (checkBountyExpiry(bounty.get()))
            regen(player);

        bounties.put(player.getUniqueId(), bounty.get());
    }

    public void unloadPlayer(Player player) {
        bounties.remove(player.getUniqueId());
    }
}
