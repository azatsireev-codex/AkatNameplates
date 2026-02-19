package net.akat.command;

import net.akat.confirm.managers.ConfirmationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ConfirmationCommandHandler implements CommandExecutor {
    private final ConfirmationManager manager = ConfirmationManager.getInstance();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cИспользование: /" + command.getName() + " <id>");
            return false;
        }

        String id = args[0];

        if (command.getName().equalsIgnoreCase("confirm")) {
            boolean success = manager.confirm(player, id);
            if (success) {
            } else {
                Component msg = miniMessage.deserialize("<red>❌ Подтверждение не найдено или время истекло</red>");
                player.sendMessage(msg);
            }
        } else if (command.getName().equalsIgnoreCase("cancel")) {
            boolean success = manager.cancel(player, id);
            if (success) {
                Component msg = miniMessage.deserialize("<yellow>✗ Покупка отменена</yellow>");
                player.sendMessage(msg);
            } else {
                Component msg = miniMessage.deserialize("<red>❌ Отменяемое подтверждение не найдено</red>");
                player.sendMessage(msg);
            }
        }

        return true;
    }
}
