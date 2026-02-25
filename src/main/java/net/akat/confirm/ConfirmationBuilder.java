package net.akat.confirm;

import net.akat.NameplateItem;
import net.akat.confirm.actions.PurchaseAction;
import net.akat.confirm.actions.UniqueOrderPurchaseAction;
import net.akat.confirm.managers.ConfirmationManager;
import net.akat.confirm.managers.ConfirmationPromise;
import net.akat.service.NameplateActions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public class ConfirmationBuilder {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String CONFIRM_TEMPLATE = """
        <yellow>Покупка:</yellow> <click:run_command:'/confirm <id>'><green><hover:show_text:'<green>Подтвердить действие'>[✓ ПОДТВЕРДИТЬ]</hover></green></click><gray> | </gray><click:run_command:'/cancel <id>'><red><hover:show_text:'<red>Отменить действие'>[✗ ОТМЕНИТЬ]</hover></red></click><gray> | </gray><click:open_url:'https://neft.games/donate'><blue><hover:show_text:'<blue>Нажмите чтобы пополнить баланс'>[💳 Пополнить баланс]</hover></blue></click>
        <gray>Автоотмена через <timeout> сек</gray>
        """;

    public static ConfirmationPromise send(Player player, ConfirmationAction action, int timeout) {
        ConfirmationPromise promise = new ConfirmationPromise();

        String id = ConfirmationManager.getInstance()
                .registerWithPromise(player, action, promise, timeout);

        String templateWithId = CONFIRM_TEMPLATE
                .replace("<id>", id)
                .replace("<timeout>", String.valueOf(timeout));

        Component message = MINI.deserialize(templateWithId);

        player.sendMessage(message);
        return promise;
    }

    public static ConfirmationPromise sendPurchase(Player player, NameplateItem item, NameplateActions actions) {
        return send(player, new PurchaseAction(item, actions), 30);
    }

    public static ConfirmationPromise sendUniqueOrderPurchase(Player player, NameplateActions actions, int timeoutSeconds) {
        player.sendMessage(MINI.deserialize("<red><bold>ВНИМАНИЕ:</bold></red> <yellow>Перед покупкой привяжите актуальный Telegram аккаунт к серверу, иначе администрация не сможет с вами связаться по заказу.</yellow>"));
        return send(player, new UniqueOrderPurchaseAction(actions), timeoutSeconds);
    }
}

