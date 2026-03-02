package net.akat.confirm.actions;

import net.akat.confirm.ConfirmationAction;
import net.akat.unique.UniqueOrderService;
import org.bukkit.entity.Player;

public class UniqueOrderDeleteRefundAction implements ConfirmationAction {
    private final UniqueOrderService service;
    private final long orderId;

    public UniqueOrderDeleteRefundAction(UniqueOrderService service, long orderId) {
        this.service = service;
        this.orderId = orderId;
    }

    @Override
    public boolean execute(Player player) {
        return service.deleteOrderWithRefund(orderId, player.getName());
    }
}
