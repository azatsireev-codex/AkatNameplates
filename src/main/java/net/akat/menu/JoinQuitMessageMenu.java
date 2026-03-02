package net.akat.menu;

import net.akat.api.spigui.SpiGUI;
import net.akat.api.spigui.buttons.SGButton;
import net.akat.api.spigui.menu.SGMenu;
import net.akat.joinquit.JoinQuitMessageOption;
import net.akat.joinquit.JoinQuitMessageService;
import net.akat.util.ClickLimiter;
import net.akat.util.ColorUtil;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class JoinQuitMessageMenu {
    private final SpiGUI spiGUI;
    private final JoinQuitMessageService service;

    public JoinQuitMessageMenu(SpiGUI spiGUI, JoinQuitMessageService service) {
        this.spiGUI = spiGUI;
        this.service = service;
    }

    public void open(Player player) {
        SGMenu menu = spiGUI.create(ChatColor.translateAlternateColorCodes('&', service.getMenuTitle()), service.getMenuRows());

        List<JoinQuitMessageOption> options = service.getOptions();
        List<Integer> slots = service.getMenuSlots();

        for (int i = 0; i < options.size() && i < slots.size(); i++) {
            JoinQuitMessageOption option = options.get(i);
            menu.setButton(slots.get(i), createOptionButton(player, option));
        }

        player.openInventory(menu.getInventory());
    }

    private SGButton createOptionButton(Player player, JoinQuitMessageOption option) {
        ItemStack stack = new ItemStack(option.getIcon());
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&e" + option.getId()));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.colorize("&7Вход: " + option.getJoinMessage().replace("{player}", player.getName())));
            lore.add(ColorUtil.colorize("&7Выход: " + option.getQuitMessage().replace("{player}", player.getName())));
            lore.add(" ");
            lore.add(ColorUtil.colorize("&7Цена: &b" + option.getPointsPrice() + " кубиславов &7+ &a" + option.getNeftPrice() + " нефткоинов"));
            lore.add(ColorUtil.colorize("&eЛКМ - купить/активировать"));

            meta.setLore(lore);
            stack.setItemMeta(meta);
        }

        return new SGButton(stack).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            boolean ok = service.purchaseOrActivate(player, option);
            if (ok) {
                player.sendMessage("§aСообщение входа/выхода активировано: §e" + option.getId());
            }
            open(player);
        }));
    }
}
