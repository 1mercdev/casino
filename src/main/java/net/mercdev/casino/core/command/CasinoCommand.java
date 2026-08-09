package net.mercdev.casino.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.gui.CasinoMenuHolder;

import java.util.List;

public class CasinoCommand implements CommandExecutor, TabCompleter {

    private final CasinoPlugin plugin;

    public CasinoCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        if (args.length == 0) {
            player.openInventory(new CasinoMenuHolder(plugin.getGameRegistry()).getInventory());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "balance" ->
                    player.sendMessage("§6Chip balance: §f" + plugin.getEconomyManager().getBalance(player));

            case "deposit" -> {
                int amount = parseAmount(args, player);
                if (amount <= 0) return true;
                if (plugin.getEconomyManager().deposit(player, amount)) {
                    player.sendMessage("§aDeposited " + amount + " " + plugin.getCurrencyItem()
                            + " for " + amount + " chips.");
                } else {
                    player.sendMessage("§cYou don't have " + amount + " " + plugin.getCurrencyItem() + ".");
                }
            }

            case "withdraw" -> {
                int amount = parseAmount(args, player);
                if (amount <= 0) return true;
                if (plugin.getEconomyManager().withdraw(player, amount)) {
                    player.sendMessage("§aWithdrew " + amount + " chips as " + plugin.getCurrencyItem() + ".");
                } else {
                    player.sendMessage("§cYou don't have " + amount + " chips to withdraw.");
                }
            }

            case "admin" -> handleAdmin(player, args);

            default -> player.sendMessage("§cUsage: /casino [balance|deposit <amount>|withdraw <amount>]");
        }
        return true;
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("casino.admin")) {
            player.sendMessage("§cYou don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§6House bankroll: §f" + plugin.getHouseBankroll().getBankroll());
            return;
        }
        if (args[1].equalsIgnoreCase("bankroll") && args.length >= 3) {
            try {
                long amount = Long.parseLong(args[2]);
                plugin.getHouseBankroll().deposit(amount);
                player.sendMessage("§aHouse bankroll topped up by " + amount
                        + ". New total: " + plugin.getHouseBankroll().getBankroll());
            } catch (NumberFormatException e) {
                player.sendMessage("§cUsage: /casino admin bankroll <amount>");
            }
        }
    }

    private int parseAmount(String[] args, Player player) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /casino " + args[0] + " <amount>");
            return -1;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                player.sendMessage("§cAmount must be positive.");
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            player.sendMessage("§cThat's not a number.");
            return -1;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return List.of("balance", "deposit", "withdraw", "admin");
        }
        return List.of();
    }
}
