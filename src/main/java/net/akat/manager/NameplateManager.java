package net.akat.manager;

import net.akat.NameplateItem;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class NameplateManager {
    private final JavaPlugin plugin;
    private File configFile;
    private File packConfigFile;
    private FileConfiguration config;
    private FileConfiguration packConfig;
    private final Map<String, NameplateItem> nameplates = new HashMap<>();
    private final Map<String, PackItem> packs = new HashMap<>(); // Новое поле для пакетов
    private final Map<String, String> packModels = new HashMap<>();
    private final Map<String, String> buttonModels = new HashMap<>();
    private String packsMenuTitle;
    private int packsMenuRows;
    private String nameplatesMenuTitle;
    private int nameplatesMenuRows;

    // Класс для хранения информации о пакете
    public static class PackItem {
        private final String name;
        private final Material material;
        private final List<String> lore;
        private final int slot;
        private final List<NameplateItem> items;

        public PackItem(String name, Material material, List<String> lore, int slot) {
            this.name = name;
            this.material = material;
            this.lore = lore;
            this.slot = slot;
            this.items = new ArrayList<>();
        }

        // Геттеры
        public String getName() { return name; }
        public Material getMaterial() { return material; }
        public List<String> getLore() { return lore; }
        public int getSlot() { return slot; }
        public List<NameplateItem> getItems() { return items; }

        public void addItem(NameplateItem item) {
            items.add(item);
        }

        public int getTotalItems() {
            return items.size();
        }

        public int getPurchasedItemsCount(Player player, LuckPerms luckPerms) {
            int count = 0;
            for (NameplateItem item : items) {
                if (hasPurchasedItem(player, item, luckPerms)) {
                    count++;
                }
            }
            return count;
        }

        private boolean hasPurchasedItem(Player player, NameplateItem item, LuckPerms luckPerms) {
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
    }

    public NameplateManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "nameplates.yml");

        if (!configFile.exists()) {
            plugin.saveResource("nameplates.yml", false);
            plugin.getLogger().info("Создан новый файл конфигурации nameplates.yml");
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        packConfigFile = new File(plugin.getDataFolder(), "pack.yml");
        initializePackConfig(collectPackNamesFromNameplatesConfig());

        reloadNameplates();
    }

    public void reloadNameplates() {
        nameplates.clear();
        packs.clear(); // Очищаем пакеты
        Set<String> usedPackNames = new LinkedHashSet<>();

        // Загружаем настройки меню
        ConfigurationSection menuSection = config.getConfigurationSection("menu");
        if (menuSection != null) {
            String legacyTitle = menuSection.getString("title", "&6&lВыбор ника");
            int legacyRows = menuSection.getInt("rows", 3);
            legacyRows = Math.max(1, Math.min(6, legacyRows));

            ConfigurationSection packsMenuSection = menuSection.getConfigurationSection("packs-menu");
            packsMenuTitle = packsMenuSection != null
                    ? packsMenuSection.getString("title", legacyTitle)
                    : legacyTitle;
            packsMenuRows = packsMenuSection != null
                    ? packsMenuSection.getInt("rows", legacyRows)
                    : legacyRows;
            packsMenuRows = Math.max(1, Math.min(6, packsMenuRows));

            ConfigurationSection nameplatesMenuSection = menuSection.getConfigurationSection("nameplates-menu");
            nameplatesMenuTitle = nameplatesMenuSection != null
                    ? nameplatesMenuSection.getString("title", "§8Пакет: {pack} §7(§f{currentPage}/{totalPages}§7)")
                    : "§8Пакет: {pack} §7(§f{currentPage}/{totalPages}§7)";
            nameplatesMenuRows = nameplatesMenuSection != null
                    ? nameplatesMenuSection.getInt("rows", legacyRows)
                    : legacyRows;
            nameplatesMenuRows = Math.max(1, Math.min(6, nameplatesMenuRows));

            // Загружаем пакеты из конфига
            ConfigurationSection packsSection = menuSection.getConfigurationSection("packs");
            if (packsSection != null) {
                for (String packName : packsSection.getKeys(false)) {
                    ConfigurationSection packSection = packsSection.getConfigurationSection(packName);
                    if (packSection != null) {
                        String name = packSection.getString("name", packName);
                        Material material = Material.matchMaterial(packSection.getString("material", "CHEST"));
                        if (material == null) material = Material.CHEST;
                        List<String> lore = packSection.getStringList("lore");
                        int slot = packSection.getInt("slot", 0);

                        packs.put(packName, new PackItem(name, material, lore, slot));
                    }
                }
            }
        } else {
            packsMenuTitle = "&6&lВыбор ника";
            packsMenuRows = 3;
            nameplatesMenuTitle = "§8Пакет: {pack} §7(§f{currentPage}/{totalPages}§7)";
            nameplatesMenuRows = 3;
        }

        // Загружаем таблички
        ConfigurationSection nameplatesSection = config.getConfigurationSection("nameplates");
        if (nameplatesSection != null) {
            for (String key : nameplatesSection.getKeys(false)) {
                try {
                    NameplateItem item = loadNameplateItem(key, nameplatesSection.getConfigurationSection(key));
                    if (item != null) {
                        nameplates.put(key, item);

                        // Добавляем ники в соответствующие пакеты
                        if (item.hasPack()) {
                            usedPackNames.add(item.getPack());
                            PackItem pack = packs.get(item.getPack());
                            if (pack != null) {
                                pack.addItem(item);
                            }
                        } else {
                            usedPackNames.add("Общие");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Ошибка при загрузке ника " + key + ": " + e.getMessage());
                }
            }
        }

        ensurePackConfigContains(usedPackNames);
        loadPackModels(usedPackNames);
        loadButtonModels();

        plugin.getLogger().info("Загружено " + nameplates.size() + " ников и " + packs.size() + " пакетов");
    }


    private Set<String> collectPackNamesFromNameplatesConfig() {
        Set<String> packNames = new LinkedHashSet<>();
        ConfigurationSection nameplatesSection = config.getConfigurationSection("nameplates");
        if (nameplatesSection == null) {
            return packNames;
        }

        for (String key : nameplatesSection.getKeys(false)) {
            ConfigurationSection itemSection = nameplatesSection.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            String packName = itemSection.getString("pack", "").trim();
            if (!packName.isEmpty()) {
                packNames.add(packName);
            } else {
                packNames.add("Общие");
            }
        }

        return packNames;
    }

    private void initializePackConfig(Set<String> packNames) {
        if (!packConfigFile.exists()) {
            YamlConfiguration newPackConfig = new YamlConfiguration();
            ConfigurationSection packsSection = newPackConfig.createSection("packs");
            for (String packName : packNames) {
                packsSection.createSection(packName);
            }

            try {
                newPackConfig.save(packConfigFile);
                plugin.getLogger().info("Создан новый файл конфигурации pack.yml");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось создать pack.yml", e);
            }
        }

        packConfig = YamlConfiguration.loadConfiguration(packConfigFile);
    }

    private void ensurePackConfigContains(Set<String> packNames) {
        if (packConfig == null) {
            packConfig = YamlConfiguration.loadConfiguration(packConfigFile);
        }

        ConfigurationSection packsSection = packConfig.getConfigurationSection("packs");
        if (packsSection == null) {
            packsSection = packConfig.createSection("packs");
        }

        boolean changed = false;
        for (String packName : packNames) {
            if (!packsSection.contains(packName)) {
                packsSection.createSection(packName);
                changed = true;
            }
        }

        if (changed) {
            try {
                packConfig.save(packConfigFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить pack.yml", e);
            }
        }
    }

    private void loadPackModels(Set<String> packNames) {
        packModels.clear();
        if (packConfig == null) {
            packConfig = YamlConfiguration.loadConfiguration(packConfigFile);
        }

        for (String packName : packNames) {
            ConfigurationSection packSection = packConfig.getConfigurationSection("packs." + packName);
            if (packSection == null || !packSection.contains("model")) {
                continue;
            }

            String model = packSection.getString("model", "").trim();
            if (!model.isEmpty()) {
                packModels.put(packName, model);
            }
        }
    }

    private void loadButtonModels() {
        buttonModels.clear();
        if (packConfig == null) {
            packConfig = YamlConfiguration.loadConfiguration(packConfigFile);
        }

        ConfigurationSection buttonsSection = packConfig.getConfigurationSection("buttons");
        if (buttonsSection == null) {
            return;
        }

        for (String key : buttonsSection.getKeys(false)) {
            String model = buttonsSection.getString(key, "").trim();
            if (!model.isEmpty()) {
                buttonModels.put(key, model);
            }
        }
    }

    private NameplateItem loadNameplateItem(String id, ConfigurationSection section) {
        try {
            String materialStr = section.getString("material", "NAME_TAG");
            Material material = Material.getMaterial(materialStr);
            if (material == null) {
                plugin.getLogger().warning("Неизвестный материал: " + materialStr + " для ника " + id);
                material = Material.NAME_TAG;
            }

            String name = section.getString("name", "Табличка");
            List<String> lore = section.getStringList("lore");
            double price = section.getDouble("price", 100.0);
            String permission = section.getString("permission", null);
            String pack = section.getString("pack", null);
            String date = section.getString("date", null);

            boolean hiddenInShop = section.getBoolean("hidden-in-shop", false);

            Map<String, String> context = null;
            ConfigurationSection contextSection = section.getConfigurationSection("context");
            if (contextSection != null) {
                context = new HashMap<>();
                for (String contextKey : contextSection.getKeys(false)) {
                    context.put(contextKey, contextSection.getString(contextKey));
                }
            }

            Integer customModelData = null;
            if (section.contains("custom-model-data")) {
                customModelData = section.getInt("custom-model-data");
            }

            String customModel = section.getString("custom-model", null);

            return new NameplateItem(id, material, name, lore, price, permission,
                    context, customModelData, customModel, hiddenInShop, pack, date);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка загрузки ников " + id, e);
            return null;
        }
    }

    // Метод для перезагрузки конфига
    public boolean reloadConfig() {
        try {
            config = YamlConfiguration.loadConfiguration(configFile);
            initializePackConfig(collectPackNamesFromNameplatesConfig());
            reloadNameplates();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при перезагрузке конфига", e);
            return false;
        }
    }

    // Сохранение конфига
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить конфиг", e);
        }
    }

    // Геттеры
    public List<NameplateItem> getNameplates() {
        return new ArrayList<>(nameplates.values());
    }

    public List<NameplateItem> getNameplatesByPack(String packName) {
        return nameplates.values().stream()
                .filter(item -> packName.equals(item.getPack()))
                .collect(Collectors.toList());
    }

    public List<PackItem> getPacks() {
        return new ArrayList<>(packs.values());
    }

    public PackItem getPack(String packName) {
        return packs.get(packName);
    }

    public NameplateItem getNameplate(String id) {
        return nameplates.get(id);
    }

    public String getPacksMenuTitle() {
        return packsMenuTitle;
    }

    public int getPacksMenuRows() {
        return packsMenuRows;
    }

    public String getNameplatesMenuTitle() {
        return nameplatesMenuTitle;
    }

    public int getNameplatesMenuRows() {
        return nameplatesMenuRows;
    }

    public Map<String, String> getPackModels() {
        return new HashMap<>(packModels);
    }

    public Map<String, String> getButtonModels() {
        return new HashMap<>(buttonModels);
    }
}
