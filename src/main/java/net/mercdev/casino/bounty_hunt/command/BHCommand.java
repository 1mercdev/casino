package net.mercdev.casino.bounty_hunt.command;

import net.mercdev.casino.bounty_hunt.BountyHunt;
import net.mercdev.casino.core.CasinoPlugin;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BHCommand implements CommandExecutor, TabCompleter {
    private final BountyHunt bHunt;
    private final CasinoPlugin plugin;

   public BHCommand(BountyHunt bHunt, CasinoPlugin plugin) {
        this.bHunt = bHunt;
        this.plugin = plugin;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String args[]){
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        //logic

    return true;
   }

   @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String args[]){
        return List.of();
   }
}
