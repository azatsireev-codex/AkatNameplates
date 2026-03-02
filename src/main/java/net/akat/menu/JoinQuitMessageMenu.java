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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
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

            boolean purchased = player.hasPermission(option.getPermission());

            List<String> lore = new ArrayList<>();
            String clickAction = purchased
                    ? service.getPurchasedClickActionText()
                    : service.getUnpurchasedClickActionText();
            String priceText = buildPriceText(option);

            for (String line : service.getOptionLoreTemplate()) {
                String formatted = line
                        .replace("{player}", player.getName())
                        .replace("{join}", option.getJoinMessage().replace("{player}", player.getName()))
                        .replace("{quit}", option.getQuitMessage().replace("{player}", player.getName()))
                        .replace("{price}", priceText)
                        .replace("{click-action}", clickAction);
                lore.add(ColorUtil.colorize(formatted));
            }

            if (purchased) {
                meta.addEnchant(Enchantment.EFFICIENCY, 1, true);
                meta.addItemFlags(
                        ItemFlag.HIDE_ENCHANTS,
                        ItemFlag.HIDE_ATTRIBUTES,
                        ItemFlag.HIDE_UNBREAKABLE,
                        ItemFlag.HIDE_DESTROYS,
                        ItemFlag.HIDE_PLACED_ON,
                        ItemFlag.HIDE_DYE,
                        ItemFlag.HIDE_ARMOR_TRIM,
                        ItemFlag.HIDE_ADDITIONAL_TOOLTIP
                );
            }

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
    private String buildPriceText(JoinQuitMessageOption option) {
        if (option.hasPointsPayment() && option.hasNeftPayment()) {
            String points = service.getPointsPriceFormat().replace("{amount}", String.valueOf(option.getPointsPrice()));
            String neft = service.getNeftPriceFormat().replace("{amount}", String.valueOf(option.getNeftPrice()));
            return service.getBothPriceFormat()
                    .replace("{points}", ColorUtil.colorize(points))
                    .replace("{neft}", ColorUtil.colorize(neft));
        }

        if (option.hasPointsPayment()) {
            return service.getPointsPriceFormat().replace("{amount}", String.valueOf(option.getPointsPrice()));
        }

        if (option.hasNeftPayment()) {
            return service.getNeftPriceFormat().replace("{amount}", String.valueOf(option.getNeftPrice()));
        }

        return "&cНе настроена цена";
    }

}
