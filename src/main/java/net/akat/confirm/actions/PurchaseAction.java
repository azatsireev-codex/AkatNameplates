package net.akat.confirm.actions;

import net.akat.NameplateItem;
import net.akat.confirm.ConfirmationAction;
import net.akat.service.NameplateActions;
import org.bukkit.entity.Player;

public class PurchaseAction implements ConfirmationAction {
    private final NameplateItem item;
    private final NameplateActions actions;

    public PurchaseAction(NameplateItem item, NameplateActions actions) {
        this.item = item;
        this.actions = actions;
    }

    @Override
    public boolean execute(Player player) {
        return actions.buy(player, item);
    }
}
