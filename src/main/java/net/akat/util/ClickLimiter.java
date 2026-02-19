package net.akat.util;

import net.akat.api.spigui.buttons.SGButtonListener;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClickLimiter {

    private static final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_DELAY_MS = 250;

    public static SGButtonListener wrapWithLimit(SGButtonListener originalListener) {
        return event -> {
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            long last = lastClickTime.getOrDefault(uuid, 0L);

            if (now - last < CLICK_DELAY_MS) {
                event.setCancelled(true); // всё равно отменяем чтобы не было анимации
                return;
            }

            lastClickTime.put(uuid, now);
            originalListener.onClick(event);
        };
    }

    public static boolean canClick(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastClickTime.getOrDefault(uuid, 0L);

        if (now - last < CLICK_DELAY_MS) {
            return false;
        }

        lastClickTime.put(uuid, now);
        return true;
    }
}
