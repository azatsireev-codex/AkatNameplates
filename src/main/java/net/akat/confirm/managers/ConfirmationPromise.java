package net.akat.confirm.managers;

import java.util.function.Consumer;

public class ConfirmationPromise {
    private boolean resolved = false;
    private boolean success = false;
    private Consumer<Boolean> callback;

    public void then(Consumer<Boolean> callback) {
        if (resolved) {
            callback.accept(success);
        } else {
            this.callback = callback;
        }
    }

    public void resolve(boolean success) {
        this.resolved = true;
        this.success = success;
        if (callback != null) {
            callback.accept(success);
        }
    }

    public boolean isResolved() {
        return resolved;
    }
}
