package net.akat.confirm.managers;

import org.bukkit.entity.Player;

public class ConfirmationResult {
    private final Player player;
    private final boolean success;
    private final Runnable onSuccess;
    private final Runnable onFailure;

    public ConfirmationResult(Player player, boolean success) {
        this.player = player;
        this.success = success;
        this.onSuccess = null;
        this.onFailure = null;
    }

    public ConfirmationResult onSuccess(Runnable action) {
        if (success && action != null) {
            action.run();
        }
        return this;
    }

    public ConfirmationResult onFailure(Runnable action) {
        if (!success && action != null) {
            action.run();
        }
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public Player getPlayer() {
        return player;
    }
}
