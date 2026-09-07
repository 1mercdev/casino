package net.mercdev.casino.bounty_hunt.command;

import net.mercdev.casino.bounty_hunt.BountyHunt;
import net.mercdev.casino.bounty_hunt.gui.BHMenuHolder;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BHCommand implements CommandExecutor, TabCompleter {

    private final BountyHunt bHunt;

   public BHCommand(BountyHunt bHunt) {
        this.bHunt = bHunt;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String args[]){
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return false;
        }
        if (!sender.hasPermission("casino.use")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            sender.sendMessage("§4You cannot use this if there's less than two people online.");
            return true;
        }

        bHunt.getBountyManager().checkBountyExpiry(bHunt.getBountyManager().getBounty(player.getUniqueId()));
        player.openInventory(new BHMenuHolder(bHunt).getInventory());
        return true;
   }

   @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String args[]){
        return List.of();
   }
}
