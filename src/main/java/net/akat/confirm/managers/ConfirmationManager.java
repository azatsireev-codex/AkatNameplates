package net.akat.confirm.managers;

import net.akat.NameplatesPlugin;
import net.akat.confirm.ConfirmationAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationManager {
    private static ConfirmationManager instance;
    private final Map<UUID, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public static class PendingConfirmation {
        private final String id;
        private final ConfirmationAction action;
        private final ConfirmationPromise promise;
        private final long expiresAt;

        public PendingConfirmation(String id, ConfirmationAction action, ConfirmationPromise promise, int timeout) {
            this.id = id;
            this.action = action;
            this.promise = promise;
            this.expiresAt = System.currentTimeMillis() + (timeout * 1000L);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        // Геттеры
        public String getId() { return id; }
        public ConfirmationAction getAction() { return action; }
        public ConfirmationPromise getPromise() { return promise; }
    }

    private ConfirmationManager() {}

    public static ConfirmationManager getInstance() {
        if (instance == null) {
            instance = new ConfirmationManager();
        }
        return instance;
    }

    private void clearExistingConfirmation(UUID playerId) {
        PendingConfirmation existing = pending.get(playerId);
        if (existing != null) {
            existing.getPromise().resolve(false);
            pending.remove(playerId);
        }
    }


    public String registerWithPromise(Player player, ConfirmationAction action,
                                      ConfirmationPromise promise, int timeout) {
        String id = UUID.randomUUID().toString();
        UUID playerId = player.getUniqueId();
        clearExistingConfirmation(playerId);

        PendingConfirmation pc = new PendingConfirmation(id, action, promise, timeout);
        pending.put(playerId, pc);

        Bukkit.getGlobalRegionScheduler().runDelayed(
                NameplatesPlugin.getInstance(),
                task -> {
                    PendingConfirmation current = pending.get(playerId);
                    if (current != null && current.getId().equals(id)) {
                        Player onlinePlayer = Bukkit.getPlayer(playerId);
                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            onlinePlayer.sendMessage("§cВремя подтверждения истекло!");
                        }

                        promise.resolve(false);
                        pending.remove(playerId);
                    }
                },
                timeout * 20L
        );

        return id;
    }

    // Обработка подтверждения
    public boolean confirm(Player player, String id) {
        PendingConfirmation pc = pending.get(player.getUniqueId());
        if (pc != null && pc.getId().equals(id) && !pc.isExpired()) {
            boolean success = pc.getAction().execute(player);
            pc.getPromise().resolve(success);
            pending.remove(player.getUniqueId());
            return true;
        }
        return false;
    }

    // Обработка отмены
    public boolean cancel(Player player, String id) {
        PendingConfirmation pc = pending.get(player.getUniqueId());
        if (pc != null && pc.getId().equals(id)) {
            // Разрешаем Promise как false
            pc.getPromise().resolve(false);
            pending.remove(player.getUniqueId());
            return true;
        }
        return false;
    }
}
