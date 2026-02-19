package net.akat;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class NameplateItem {
    private final String id;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final double price;
    private final String permission;
    private final Map<String, String> context;
    private final Integer customModelData;
    private final String customModel;
    private final boolean hiddenInShop;
    private final String pack;
    private final String date; // Новое поле для даты

    public NameplateItem(String id, Material material, String name, List<String> lore,
                         double price, String permission, Map<String, String> context,
                         Integer customModelData, String customModel, boolean hiddenInShop,
                         String pack, String date) { // Добавляем date в конструктор
        this.id = id;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.price = price;
        this.permission = permission;
        this.context = context;
        this.customModelData = customModelData;
        this.customModel = customModel;
        this.hiddenInShop = hiddenInShop;
        this.pack = pack;
        this.date = date; // Инициализируем поле
    }

    // Геттеры
    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public double getPrice() { return price; }
    public String getPermission() { return permission; }
    public Map<String, String> getContext() { return context; }
    public Integer getCustomModelData() { return customModelData; }
    public String getCustomModel() { return customModel; }
    public boolean isHiddenInShop() { return hiddenInShop; }
    public String getPack() { return pack; }
    public String getDate() { return date; } // Новый геттер

    public boolean hasContext() { return context != null && !context.isEmpty(); }
    public boolean hasCustomModelData() { return customModelData != null; }
    public boolean hasCustomModel() { return customModel != null && !customModel.isEmpty(); }
    public boolean hasPermission() { return permission != null && !permission.trim().isEmpty(); }
    public boolean hasPack() { return pack != null && !pack.trim().isEmpty(); }
    public boolean hasDate() { return date != null && !date.trim().isEmpty(); } // Проверка наличия даты

    public boolean shouldAddSymbol() {
        if (!hasDate()) return false;

        try {
            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate configDate = LocalDate.parse(date, formatter);

            return !configDate.isAfter(currentDate) &&
                    ChronoUnit.DAYS.between(configDate, currentDate) <= 4;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Ошибка при парсинге даты для ники " + id + ": " + e.getMessage());
            return false;
        }
    }

    public String getDisplayName() {
        if (shouldAddSymbol()) {
            return name + "&f\uE063";
        }
        return name;
    }

    public int getPriceAsInt() { return (int) Math.round(price); }

    public String getFormattedPrice() {
        if (price == (int) price) {
            return String.format("%d", (int) price);
        }
        return String.format("%.2f", price);
    }
}
