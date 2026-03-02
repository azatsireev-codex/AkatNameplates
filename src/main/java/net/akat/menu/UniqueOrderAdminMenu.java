package net.akat.menu;

import net.akat.api.spigui.SpiGUI;
import net.akat.api.spigui.buttons.SGButton;
import net.akat.api.spigui.menu.SGMenu;
import net.akat.confirm.ConfirmationBuilder;
import net.akat.unique.UniqueOrder;
import net.akat.unique.UniqueOrderService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class UniqueOrderAdminMenu {
    private final SpiGUI spiGUI;
    private final UniqueOrderService uniqueOrderService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public UniqueOrderAdminMenu(SpiGUI spiGUI, UniqueOrderService uniqueOrderService) {
        this.spiGUI = spiGUI;
        this.uniqueOrderService = uniqueOrderService;
    }

    public void open(Player player, int page) {
        List<UniqueOrder> orders = uniqueOrderService.getPendingOrders();
        SGMenu menu = spiGUI.create("§8Уникальные ники: заказы", 6);

        int[] orderSlots = new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int perPage = orderSlots.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) orders.size() / perPage));

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        final int currentPage = page;
        int start = currentPage * perPage;
        int end = Math.min(start + perPage, orders.size());
        int idx = 0;
        for (int i = start; i < end; i++) {
            UniqueOrder order = orders.get(i);
            menu.setButton(orderSlots[idx++], createOrderButton(player, order, currentPage));
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§bИнформация");
            infoMeta.setLore(Arrays.asList(
                    "§7Открытых заказов: §e" + orders.size(),
                    "§7Страница: §e" + (currentPage + 1) + "§7/§a" + totalPages,
                    "§7ЛКМ: завершить заказ",
                    "§7ПКМ: удалить заказ и вернуть средства"
            ));
            info.setItemMeta(infoMeta);
        }
        menu.setButton(49, new SGButton(info));

        if (currentPage > 0) {
            menu.setButton(48, navButton("§a◀ Предыдущая", p -> open(player, currentPage - 1)));
        }
        if (currentPage < totalPages - 1) {
            menu.setButton(50, navButton("§aСледующая ▶", p -> open(player, currentPage + 1)));
        }

        player.openInventory(menu.getInventory());
    }

    private SGButton navButton(String name, Consumer<Player> action) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            arrow.setItemMeta(meta);
        }
        return new SGButton(arrow).withListener(e -> {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                action.accept(p);
            }
        });
    }

    private SGButton createOrderButton(Player admin, UniqueOrder order, int page) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + order.getPlayerName() + " §7(#" + order.getId() + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§7Цена: §a" + order.getPrice() + " §f\uE058");
            lore.add("§7Создан: §f" + order.getCreatedAt().format(formatter));
            lore.add(" ");
            lore.add("§aЛКМ - завершить заказ");
            lore.add("§cПКМ - удалить заказ и вернуть средства");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }

        return new SGButton(stack).withListener(e -> {
            e.setCancelled(true);

            if (e.isRightClick()) {
                ConfirmationBuilder.sendUniqueOrderDeleteWithRefund(admin, uniqueOrderService, order.getId())
                        .then(success -> {
                            if (success) {
                                admin.sendMessage(ChatColor.GREEN + "Заказ #" + order.getId() + " удалён, средства возвращены игроку.");
                                open(admin, page);
                            } else {
                                admin.sendMessage(ChatColor.RED + "Удаление заказа отменено или завершилось ошибкой.");
                            }
                        });
                return;
            }

            ConfirmationBuilder.sendUniqueOrderComplete(admin, uniqueOrderService, order.getId())
                    .then(success -> {
                        if (success) {
                            admin.sendMessage(ChatColor.GREEN + "Заказ #" + order.getId() + " завершён.");
                            open(admin, page);
                        } else {
                            admin.sendMessage(ChatColor.RED + "Завершение заказа отменено или завершилось ошибкой.");
                        }
                    });
        });
    }
}
