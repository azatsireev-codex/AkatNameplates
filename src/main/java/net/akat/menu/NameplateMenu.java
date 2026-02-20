package net.akat.menu;

import net.akat.BalanceHttpClient;
import net.akat.NameplateItem;
import net.akat.NameplatesPlugin;
import net.akat.api.spigui.SpiGUI;
import net.akat.api.spigui.buttons.SGButton;
import net.akat.api.spigui.buttons.SGButtonListener;
import net.akat.api.spigui.menu.SGMenu;
import net.akat.confirm.ConfirmationBuilder;
import net.akat.confirm.managers.ConfirmationPromise;
import net.akat.service.NameplateActions;
import net.akat.util.ClickLimiter;
import net.akat.util.ColorUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class NameplateMenu {
    private final SpiGUI spiGUI;
    private final List<NameplateItem> allItems;
    private final LuckPerms luckPerms;
    private final String menuTitle;
    private final int menuRows;
    private final NameplateActions actions;
    private final Map<String, String> packModels;

    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int BACK_BUTTON_SLOT = 49;
    private static final int EQUIP_ALL_BUTTON_SLOT = 48;
    private static final int UNEQUIP_SLOT_MAIN = 48;
    private static final int CURRENT_NICK_SLOT = 50;
    private static final int DONATE_INFO_SLOT = 49;
    private static final String DONATE_URL = "https://neft.games/donate";

    private static final int[] PACK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerCurrentPack = new HashMap<>();

    private final Map<String, List<NameplateItem>> packItemsCache = new HashMap<>();
    private final Map<String, PackInfo> packInfoCache = new HashMap<>();

    private static class PackInfo {
        final String name;
        final Material material;
        final List<String> lore;
        final String model;

        PackInfo(String name, Material material, List<String> lore, String model) {
            this.name = name;
            this.material = material;
            this.lore = lore;
            this.model = model;
        }
    }

    public NameplateMenu(SpiGUI spiGUI, List<NameplateItem> items,
                         String menuTitle, int menuRows, NameplateActions actions,
                         Map<String, String> packModels) {
        this.spiGUI = spiGUI;
        this.allItems = items;
        this.luckPerms = LuckPermsProvider.get();
        this.menuTitle = menuTitle;
        this.menuRows = menuRows;
        this.actions = actions;
        this.packModels = new HashMap<>(packModels);

        initPackCache();
    }

    private void initPackCache() {
        packItemsCache.clear();
        packInfoCache.clear();

        // Группируем ники по пакетам
        for (NameplateItem item : allItems) {
            String packName = item.getPack();
            if (packName == null || packName.isEmpty()) {
                packName = "Общие";
            }

            packItemsCache.computeIfAbsent(packName, k -> new ArrayList<>()).add(item);

            // Если это первый ники в пакете, создаем базовую информацию о пакете
            if (!packInfoCache.containsKey(packName)) {
                Material material = Material.CHEST;
                List<String> lore = new ArrayList<>();
                String model = packModels.get(packName);

                packInfoCache.put(packName, new PackInfo(packName, material, lore, model));
            }
        }
    }

    public void open(Player player) {
        openMainMenu(player);
    }

    private void openMainMenu(Player player) {
        // Очищаем текущий пакет игрока
        playerCurrentPack.remove(player.getUniqueId());
        playerPages.remove(player.getUniqueId());

        // Получаем список уникальных пакетов
        List<String> packs = new ArrayList<>(packItemsCache.keySet());

        // Создаем главное меню
        SGMenu menu = spiGUI.create(
                ChatColor.translateAlternateColorCodes('&', menuTitle),
                menuRows
        );

        // Добавляем пакеты в меню
        int packIndex = 0;
        for (String packName : packs) {
            if (packIndex >= PACK_SLOTS.length) break;

            PackInfo packInfo = packInfoCache.get(packName);
            List<NameplateItem> packItems = packItemsCache.get(packName);

            // Создаем кнопку пакета
            menu.setButton(PACK_SLOTS[packIndex], createPackButton(packInfo, packItems, player));
            packIndex++;
        }

        if (hasAnyPurchasedItemGlobally(player)) {
            menu.setButton(UNEQUIP_SLOT_MAIN, createUnequipButton(player));
        }

        menu.setButton(CURRENT_NICK_SLOT, createPreviewCurrentButton(player));
        menu.setButton(DONATE_INFO_SLOT, createDonateInfoButton(player));

        player.openInventory(menu.getInventory());
    }

    private SGButton createPackButton(PackInfo packInfo, List<NameplateItem> packItems, Player player) {
        ItemStack stack = new ItemStack(packInfo.material);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            int newItemsCount = countNewItems(packItems);
            String displayName = ChatColor.YELLOW + packInfo.name;
            if (newItemsCount > 0) {
                displayName = displayName + ChatColor.GRAY + " " + ChatColor.GOLD + newItemsCount
                        + ChatColor.WHITE + "\uE063";
            }
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>(packInfo.lore);
            lore.add(" ");

            // Подсчитываем статистику
            int totalItems = packItems.size();
            int purchasedItems = 0;

            for (NameplateItem item : packItems) {
                if (item.isHiddenInShop()) {
                    totalItems--;
                    continue;
                }

                if (item.hasPermission() && hasPurchasedItem(player, item)) {
                    purchasedItems++;
                }
            }

            lore.add("§7Всего ников: §a" + totalItems);
            lore.add("§7Куплено: §e" + purchasedItems + "§7/§a" + totalItems);
            lore.add(" ");
            // Процент заполнения
            double percentage = totalItems > 0 ?
                    (double) purchasedItems / totalItems * 100 : 0;
            lore.add("§7Заполнение: §a" + String.format("%.1f", percentage) + "%");
            lore.add(" ");
            lore.add("§eНажмите для просмотра");

            meta.setLore(lore);
            if (packInfo.model != null && !packInfo.model.isEmpty()) {
                try {
                    String[] parts = packInfo.model.split(":", 2);
                    if (parts.length == 2) {
                        NamespacedKey modelKey = new NamespacedKey(parts[0], parts[1]);
                        try {
                            meta.setItemModel(modelKey);
                        } catch (NoSuchMethodError e) {
                            Bukkit.getLogger().warning("Кастомные модели паков не поддерживаются в этой версии Minecraft");
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Ошибка при установке модели для пака " + packInfo.name + ": " + e.getMessage());
                }
            }
            stack.setItemMeta(meta);
        }

        return new SGButton(stack).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            openPackMenu(player, packInfo.name, 0);
        }));
    }

    private int countNewItems(List<NameplateItem> packItems) {
        int count = 0;
        for (NameplateItem item : packItems) {
            if (!item.isHiddenInShop() && item.shouldAddSymbol()) {
                count++;
            }
        }
        return count;
    }

    private SGButton createDonateInfoButton(Player player) {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Информация о донат-валюте");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "1 нефткоин " + ChatColor.WHITE + "\uE058" + ChatColor.GRAY + " = 1 рубль.",
                    ChatColor.GRAY + "Пополнить баланс можно на сайте:",
                    ChatColor.YELLOW + DONATE_URL,
                    "",
                    ChatColor.GRAY + "Кастомные ники видны над головой",
                    ChatColor.GRAY + "почти на всех режимах",
                    ChatColor.GRAY + "и выдаются навсегда после покупки.",
                    "",
                    ChatColor.GREEN + "Нажмите, чтобы открыть сайт."
            ));
            book.setItemMeta(meta);
        }

        return new SGButton(book).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);

            TextComponent link = new TextComponent(ChatColor.GREEN + "Открыть страницу доната");
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DONATE_URL));

            player.spigot().sendMessage(link);
        }));
    }

    private SGButton createPreviewCurrentButton(Player player) {
        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = eye.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Показать текущий ник");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Нажмите, чтобы увидеть");
            lore.add(ChatColor.GRAY + "ваш текущий надетый ник");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Включит предпросмотр ника");

            meta.setLore(lore);
            eye.setItemMeta(meta);
        }

        return new SGButton(eye).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            actions.previewCurrent(player);
        }));
    }

    private void openPackMenu(Player player, String packName, int page) {
        playerCurrentPack.put(player.getUniqueId(), packName);
        List<NameplateItem> packItems = packItemsCache.getOrDefault(packName, new ArrayList<>());
        List<NameplateItem> visibleItems = getVisibleItemsForPack(packItems, player);

        if (visibleItems.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "В этом пакете нет доступных ников!");
            openMainMenu(player);
            return;
        }

        visibleItems.sort((a, b) -> {
            boolean aPurchased = itemIsPurchased(player, a);
            boolean bPurchased = itemIsPurchased(player, b);

            if (aPurchased && !bPurchased) return -1;
            if (!aPurchased && bPurchased) return 1;
            return Double.compare(a.getPrice(), b.getPrice());
        });

        int itemsPerPage = ITEM_SLOTS.length;
        int totalPages = (int) Math.ceil((double) visibleItems.size() / itemsPerPage);

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        if (totalPages == 0) page = 0;

        playerPages.put(player.getUniqueId(), page);
        String title = "§8Пакет: " + packName + " §7(§f" + (page + 1) + "/" + totalPages + "§7)";
        SGMenu menu = spiGUI.create(title, menuRows);

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, visibleItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            NameplateItem item = visibleItems.get(i);
            int slotIndex = i - startIndex;
            if (slotIndex < ITEM_SLOTS.length) {
                menu.setButton(ITEM_SLOTS[slotIndex], createItemButton(item, player));
            }
        }

        menu.setButton(BACK_BUTTON_SLOT, createBackButton(player));
        if (hasAnyPurchasedItem(player, packItems)) {
            menu.setButton(EQUIP_ALL_BUTTON_SLOT, createUnequipButton(player));
        }

        if (totalPages > 1) {
            if (page > 0) {
                menu.setButton(PREVIOUS_PAGE_SLOT, createPreviousPageButton(player, page - 1, packName));
            }
            if (page < totalPages - 1) {
                menu.setButton(NEXT_PAGE_SLOT, createNextPageButton(player, page + 1, packName));
            }
        }

        player.openInventory(menu.getInventory());
    }

    private SGButton createBackButton(Player player) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "◀ Назад к пакетам");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Нажмите, чтобы вернуться",
                    ChatColor.GRAY + "к выбору пакетов"
            ));
            arrow.setItemMeta(meta);
        }

        return new SGButton(arrow).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            openMainMenu(player);
        }));
    }

    private SGButton createPreviousPageButton(Player player, int newPage, String packName) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "◀ Предыдущая страница");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Нажмите, чтобы перейти",
                    ChatColor.GRAY + "на предыдущую страницу"
            ));
            arrow.setItemMeta(meta);
        }

        return new SGButton(arrow).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            openPackMenu(player, packName, newPage);
        }));
    }

    private SGButton createNextPageButton(Player player, int newPage, String packName) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Следующая страница ▶");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Нажмите, чтобы перейти",
                    ChatColor.GRAY + "на следующую страницу"
            ));
            arrow.setItemMeta(meta);
        }

        return new SGButton(arrow).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            openPackMenu(player, packName, newPage);
        }));
    }

    private SGButton createUnequipButton(Player player) {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Снять текущий ник");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Нажмите, чтобы снять",
                    ChatColor.GRAY + "текущий кастомный ник",
                    "",
                    ChatColor.YELLOW + "Удалит эффекты текущего ника"
            ));
            barrier.setItemMeta(meta);
        }

        return new SGButton(barrier).withListener(ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);
            actions.unequip(player);
        }));
    }

    private List<NameplateItem> getVisibleItemsForPack(List<NameplateItem> packItems, Player player) {
        List<NameplateItem> visible = new ArrayList<>();

        for (NameplateItem item : packItems) {
            if (item.isHiddenInShop()) {
                if (item.hasPermission() && hasPurchasedItem(player, item)) {
                    visible.add(item);
                }
                continue;
            }

            visible.add(item);
        }

        return visible;
    }

    private boolean itemIsPurchased(Player player, NameplateItem item) {
        return hasPurchasedItem(player, item);
    }

    private boolean hasAnyPurchasedItem(Player player, List<NameplateItem> items) {
        for (NameplateItem item : items) {
            if (!item.isHiddenInShop() && itemIsPurchased(player, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPurchasedItem(Player player, NameplateItem item) {
        if (!item.hasPermission()) {
            return false;
        }

        if (item.hasContext()) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getCachedData().getPermissionData()
                        .checkPermission(item.getPermission()).asBoolean();
            }
            return false;
        } else {
            return player.hasPermission(item.getPermission());
        }
    }

    private SGButton createItemButton(NameplateItem item, Player player) {
        ItemStack stack = new ItemStack(item.getMaterial());
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + ColorUtil.colorize(item.getDisplayName()));

            List<String> lore = item.getLore().stream()
                    .map(ColorUtil::colorize)
                    .collect(Collectors.toList());

            lore.add("§7Цена: §a" + item.getPrice() + " §f\uE058 §7(нефткоинов)");
            lore.add(" ");

            // Проверяем, куплен ли уже этот ник
            boolean purchased = item.hasPermission() && hasPurchasedItem(player, item);

            if (purchased) {
                lore.add("§f\uE056");
                lore.add("§eЛКМ - Надеть");

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
            } else {
                lore.add("§eЛКМ - Купить");
            }
            lore.add("§eПКМ - Предпросмотр");

            meta.setLore(lore);

            if (item.hasCustomModelData() || item.hasCustomModel()) {
                try {
                    if (item.hasCustomModel()) {
                        String modelString = item.getCustomModel();
                        String[] parts = modelString.split(":");
                        if (parts.length == 2) {
                            String namespace = parts[0];
                            String key = parts[1];
                            NamespacedKey modelKey = new NamespacedKey(namespace, key);

                            try {
                                meta.setItemModel(modelKey);
                            } catch (NoSuchMethodError e) {
                                Bukkit.getLogger().warning("Кастомные модели не поддерживаются в этой версии Minecraft");
                            }
                        }
                    } else if (item.hasCustomModelData()) {
                        meta.setCustomModelData(item.getCustomModelData());
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Ошибка при установке кастомной модели: " + e.getMessage());
                }
            }

            stack.setItemMeta(meta);
        }

        SGButtonListener listener = ClickLimiter.wrapWithLimit(e -> {
            e.setCancelled(true);

            // Проверяем, куплен ли предмет
            boolean purchased = item.hasPermission() && hasPurchasedItem(player, item);

            if (e.isLeftClick()) {
                if (purchased) {
                    // ЛКМ на купленный ник - надеть
                    actions.equip(player, item);
                } else {
                    // ЛКМ на некупленный ник - покупка
                    handlePurchase(item, player);
                }
            }
            // ПКМ - предпросмотр (всегда доступен)
            else if (e.isRightClick()) {
                actions.preview(player, item);
            }
        });

        return new SGButton(stack).withListener(listener);
    }

    private boolean hasAnyPurchasedItemGlobally(Player player) {
        for (List<NameplateItem> packItems : packItemsCache.values()) {
            if (hasAnyPurchasedItem(player, packItems)) {
                return true;
            }
        }
        return false;
    }

    private void handlePurchase(NameplateItem item, Player player) {
        player.closeInventory();
        ConfirmationPromise promise = ConfirmationBuilder.sendPurchase(player, item, actions);
        promise.then(success -> {
            if (success) {
                Bukkit.getGlobalRegionScheduler().runDelayed(
                        NameplatesPlugin.getInstance(),
                        task -> {
                            String currentPack = playerCurrentPack.get(player.getUniqueId());
                            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

                            if (currentPack != null) {
                                openPackMenu(player, currentPack, currentPage);
                            }
                        },
                        3L
                );
            }
        });
    }
}
