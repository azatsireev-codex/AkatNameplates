package net.akat.listener;

import net.akat.joinquit.JoinQuitMessageOption;
import net.akat.joinquit.JoinQuitMessageService;
import net.akat.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitMessageListener implements Listener {
    private final JoinQuitMessageService service;

    public JoinQuitMessageListener(JoinQuitMessageService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        JoinQuitMessageOption option = service.getActiveOption(event.getPlayer());
        if (option == null) return;
        event.setJoinMessage(ColorUtil.colorize(option.getJoinMessage().replace("{player}", event.getPlayer().getName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        JoinQuitMessageOption option = service.getActiveOption(event.getPlayer());
        if (option == null) return;
        event.setQuitMessage(ColorUtil.colorize(option.getQuitMessage().replace("{player}", event.getPlayer().getName())));
    }
}
