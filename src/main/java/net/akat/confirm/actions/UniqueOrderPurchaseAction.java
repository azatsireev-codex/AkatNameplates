package net.akat.confirm.actions;

import net.akat.confirm.ConfirmationAction;
import net.akat.service.NameplateActions;
import org.bukkit.entity.Player;

public class UniqueOrderPurchaseAction implements ConfirmationAction {
    private final NameplateActions actions;

    public UniqueOrderPurchaseAction(NameplateActions actions) {
        this.actions = actions;
    }

    @Override
    public boolean execute(Player player) {
        return actions.buyUniqueOrder(player);
    }
}
