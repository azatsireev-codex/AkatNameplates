package net.akat.command;

import net.akat.api.spigui.SpiGUI;
import net.akat.manager.NameplateManager;
import net.akat.menu.NameplateMenu;
import net.akat.menu.UniqueOrderAdminMenu;
import net.akat.service.NameplateActions;
import net.akat.unique.UniqueOrderService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NameplateCommand implements CommandExecutor, TabCompleter {
    private final NameplateManager manager;
    private NameplateMenu purchaseMenu;
    private final SpiGUI spiGUI;
    private final NameplateActions actions;
    private final UniqueOrderService uniqueOrderService;
    private final UniqueOrderAdminMenu uniqueOrderAdminMenu;

    public NameplateCommand(NameplateManager manager, NameplateMenu purchaseMenu,
                            SpiGUI spiGUI,
                            NameplateActions actions,
                            UniqueOrderService uniqueOrderService,
                            UniqueOrderAdminMenu uniqueOrderAdminMenu) {
        this.manager = manager;
        this.purchaseMenu = purchaseMenu;
        this.spiGUI = spiGUI;
        this.actions = actions;
        this.uniqueOrderService = uniqueOrderService;
        this.uniqueOrderAdminMenu = uniqueOrderAdminMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверяем, какая команда была использована
        boolean isShortCommand = label.equalsIgnoreCase("anp") ||
                label.equalsIgnoreCase("np") ||
                label.equalsIgnoreCase("plates") ||
                label.equalsIgnoreCase("tags");

        if (args.length == 0) {
            if (sender instanceof Player) {
                // По умолчанию открываем магазин покупки
                purchaseMenu.open((Player) sender);
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Использование: /" + label + " <shop|my|uniqueorders> [игрок]");
                sender.sendMessage(ChatColor.RED + "Или: /" + label + " reload");
                return false;
            }
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "shop":
                return handleShopCommand(sender, args, label);

            case "reload":
                return handleReloadCommand(sender, label);

            case "uniqueorders":
                return handleUniqueOrdersCommand(sender);

            default:
                // Показываем разный хелп в зависимости от команды
                String cmd = isShortCommand ? label : "akatnameplates";
                if (sender.hasPermission("akatnameplates.admin")) {
                    sender.sendMessage(ChatColor.RED + "Использование: /" + cmd + " <shop|my|uniqueorders> [игрок]");
                    sender.sendMessage(ChatColor.RED + "Или: /" + cmd + " reload");
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /" + cmd + " [shop|my]");
                }
                return false;
        }
    }

    private boolean handleShopCommand(CommandSender sender, String[] args, String label) {
        // Проверяем базовые права
        if (!sender.hasPermission("akatnameplates.use")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
            return false;
        }

        Player targetPlayer = null;

        if (args.length >= 2) {
            // Если указан ник игрока, проверяем права админа
            if (!sender.hasPermission("akatnameplates.admin")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав для открытия магазина другим игрокам!");
                return false;
            }

            String playerName = args[1];
            targetPlayer = Bukkit.getPlayer(playerName);

            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден или оффлайн!");
                return false;
            }
        } else {
            // Если ник не указан, открываем для отправителя
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Консоль должна указать игрока!");
                sender.sendMessage(ChatColor.RED + "Пример: /" + label + " shop <игрок>");
                return false;
            }
            targetPlayer = (Player) sender;
        }

        // Открываем магазин для целевого игрока
        purchaseMenu.open(targetPlayer);

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender, String label) {
        if (!sender.hasPermission("akatnameplates.reload")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на эту команду!");
            return false;
        }

        if (manager.reloadConfig()) {
            uniqueOrderService.reload();

            purchaseMenu = new NameplateMenu(
                    spiGUI,
                    manager.getNameplates(),
                    manager.getPacksMenuTitle(),
                    manager.getPacksMenuRows(),
                    manager.getNameplatesMenuTitle(),
                    manager.getNameplatesMenuRows(),
                    manager.getPacksMenuInfoSlot(),
                    manager.getPacksMenuPreviewSlot(),
                    manager.getPacksMenuUnequipSlot(),
                    manager.getPacksMenuPreviousSlot(),
                    manager.getPacksMenuNextSlot(),
                    manager.getNameplatesMenuBackSlot(),
                    manager.getNameplatesMenuUnequipSlot(),
                    manager.getNameplatesMenuPreviousSlot(),
                    manager.getNameplatesMenuNextSlot(),
                    manager.getDonateUrl(),
                    manager.getPacksMenuPackSlots(),
                    manager.getNameplatesMenuItemSlots(),
                    actions,
                    manager.getPackModels(),
                    manager.getButtonModels(),
                    manager.getPacksAfterGeneral(),
                    uniqueOrderService
            );

            sender.sendMessage(ChatColor.GREEN + "Конфигурация ников перезагружена!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка при перезагрузке конфигурации!");
        }
        return true;
    }


    private boolean handleUniqueOrdersCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Команда доступна только игрокам.");
            return true;
        }

        if (!sender.hasPermission("akatnameplates.uniqueorders") && !sender.hasPermission("akatnameplates.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на эту команду!");
            return true;
        }

        uniqueOrderAdminMenu.open(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allCommands = Arrays.asList("shop", "my", "reload", "uniqueorders");

            for (String cmd : allCommands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    if (shouldShowCommand(sender, cmd)) {
                        completions.add(cmd);
                    }
                }
            }

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("shop") || subCommand.equals("my")) {
                // Для команд shop и my предлагаем ники игроков, если у отправителя есть права админа
                if (sender.hasPermission("akatnameplates.admin")) {
                    String partialName = args[1].toLowerCase();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        String playerName = player.getName();
                        if (playerName.toLowerCase().startsWith(partialName)) {
                            completions.add(playerName);
                        }
                    }
                }
            }
        }

        return completions;
    }

    private boolean shouldShowCommand(CommandSender sender, String command) {
        switch (command.toLowerCase()) {
            case "shop":
            case "my":
                // Для shop и my проверяем базовый пермишен
                return sender.hasPermission("akatnameplates.use");

            case "reload":
                // Для reload проверяем специальный пермишен
                return sender.hasPermission("akatnameplates.reload");

            case "uniqueorders":
                return sender.hasPermission("akatnameplates.uniqueorders") || sender.hasPermission("akatnameplates.admin");

            default:
                return false;
        }
    }
}
