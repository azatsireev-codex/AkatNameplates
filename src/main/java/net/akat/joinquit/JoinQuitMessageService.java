package net.akat.joinquit;

import net.akat.BalanceHttpClient;
import net.akat.api.AkatPointaucAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class JoinQuitMessageService {
    private final JavaPlugin plugin;
    private final BalanceHttpClient balanceClient;
    private final AkatPointaucAPI pointaucAPI;
    private final LuckPerms luckPerms;
    private final List<JoinQuitMessageOption> options = new ArrayList<>();

    private FileConfiguration config;
    private String dbType;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    public JoinQuitMessageService(JavaPlugin plugin, BalanceHttpClient balanceClient, AkatPointaucAPI pointaucAPI) {
        this.plugin = plugin;
        this.balanceClient = balanceClient;
        this.pointaucAPI = pointaucAPI;
        this.luckPerms = LuckPermsProvider.get();
        reload();
    }

    public void reload() {
        loadConfig();
        initTable();
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "joinquit-messages.yml");
        if (!file.exists()) {
            plugin.saveResource("joinquit-messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        options.clear();

        ConfigurationSection optionsSection = config.getConfigurationSection("messages");
        if (optionsSection != null) {
            for (String id : optionsSection.getKeys(false)) {
                ConfigurationSection section = optionsSection.getConfigurationSection(id);
                if (section == null) continue;
                String permission = section.getString("permission", "joinquit.message." + id);
                int neftPrice = section.getInt("neft-price", 0);
                int pointsPrice = section.getInt("points-price", 0);
                String joinMessage = section.getString("join", "&a+ &f{player}");
                String quitMessage = section.getString("quit", "&c- &f{player}");
                Material icon = Material.matchMaterial(section.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;
                options.add(new JoinQuitMessageOption(id, permission, neftPrice, pointsPrice, joinMessage, quitMessage, icon));
            }
        }

        dbType = config.getString("database.type", "mariadb").toLowerCase(Locale.ROOT);
        if ("sqlite".equals(dbType)) {
            String sqliteFileName = config.getString("database.sqlite.file", "joinquit-messages.db");
            File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            dbUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
            dbUser = "";
            dbPassword = "";
        } else {
            String host = config.getString("database.host", "127.0.0.1");
            int port = config.getInt("database.port", 3306);
            String dbName = config.getString("database.name", "nameplates");
            dbUser = config.getString("database.user", "root");
            dbPassword = config.getString("database.password", "");
            dbUrl = "jdbc:mariadb://" + host + ":" + port + "/" + dbName + "?useUnicode=true&characterEncoding=utf8";
            dbType = "mariadb";
        }
    }

    private Connection getConnection() throws SQLException {
        if ("sqlite".equals(dbType)) return DriverManager.getConnection(dbUrl);
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_joinquit_messages ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "message_id VARCHAR(64) NOT NULL"
                + ")";
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось инициализировать таблицу player_joinquit_messages", e);
        }
    }

    public List<JoinQuitMessageOption> getOptions() { return new ArrayList<>(options); }

    public String getMenuTitle() { return config.getString("menu.title", "&8Сообщения входа/выхода"); }
    public int getMenuRows() { return Math.max(1, Math.min(6, config.getInt("menu.rows", 6))); }
    public List<Integer> getMenuSlots() {
        List<Integer> slots = config.getIntegerList("menu.slots");
        if (slots == null || slots.isEmpty()) return Arrays.asList(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34);
        return slots;
    }

    public List<String> getOptionLoreTemplate() {
        List<String> lore = config.getStringList("menu.option-lore");
        if (lore == null || lore.isEmpty()) {
            return Arrays.asList(
                    "&7Вход: {join}",
                    "&7Выход: {quit}",
                    " ",
                    "{price}",
                    "{click-action}"
            );
        }
        return lore;
    }

    public String getPurchasedClickActionText() {
        return config.getString("menu.purchased-click-action", "&eЛКМ - Активировать");
    }

    public String getUnpurchasedClickActionText() {
        return config.getString("menu.unpurchased-click-action", "&eЛКМ - Купить/активировать");
    }

    public String getNeftPriceFormat() {
        return config.getString("menu.price-formats.neft", "&a{amount} нефткоинов");
    }

    public String getPointsPriceFormat() {
        return config.getString("menu.price-formats.points", "&b{amount} кубиславов");
    }

    public String getBothPriceFormat() {
        return config.getString("menu.price-formats.both", "&7Цена: {points} &7или {neft}");
    }

    public JoinQuitMessageOption getOption(String id) {
        for (JoinQuitMessageOption option : options) {
            if (option.getId().equalsIgnoreCase(id)) return option;
        }
        return null;
    }

    public JoinQuitMessageOption getActiveOption(Player player) {
        String activeId = getActiveMessageId(player.getUniqueId());
        if (activeId == null) return null;
        JoinQuitMessageOption option = getOption(activeId);
        if (option == null) return null;
        if (!player.hasPermission(option.getPermission())) return null;
        return option;
    }

    public String getActiveMessageId(UUID uuid) {
        String sql = "SELECT message_id FROM player_joinquit_messages WHERE player_uuid=?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("message_id");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось получить активное join/quit сообщение", e);
        }
        return null;
    }

    public void setActiveMessage(UUID uuid, String messageId) {
        String sql = "INSERT INTO player_joinquit_messages (player_uuid, message_id) VALUES (?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET message_id=excluded.message_id";
        if (!"sqlite".equals(dbType)) {
            sql = "INSERT INTO player_joinquit_messages (player_uuid, message_id) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE message_id=VALUES(message_id)";
        }
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, messageId);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить активное join/quit сообщение", e);
        }
    }

    public boolean purchaseOrActivate(Player player, JoinQuitMessageOption option) {
        if (player.hasPermission(option.getPermission())) {
            setActiveMessage(player.getUniqueId(), option.getId());
            return true;
        }

        if (!option.hasPointsPayment() && !option.hasNeftPayment()) {
            player.sendMessage("§cДля этого набора не настроена цена.");
            return false;
        }

        if (option.hasPointsPayment() && option.hasNeftPayment()) {
            if (tryPurchaseWithPoints(player, option)) {
                return true;
            }
            return tryPurchaseWithNeft(player, option);
        }

        if (option.hasPointsPayment()) {
            return tryPurchaseWithPoints(player, option);
        }

        return tryPurchaseWithNeft(player, option);
    }

    private boolean tryPurchaseWithPoints(Player player, JoinQuitMessageOption option) {
        if (pointaucAPI == null) {
            player.sendMessage("§cPointAuc API недоступно.");
            return false;
        }

        boolean removed = pointaucAPI.removePoints(player.getUniqueId(), option.getPointsPrice());
        if (!removed) {
            player.sendMessage("§cНедостаточно кубиславов!");
            return false;
        }

        grantPermission(player, option.getPermission());
        setActiveMessage(player.getUniqueId(), option.getId());
        return true;
    }

    private boolean tryPurchaseWithNeft(Player player, JoinQuitMessageOption option) {
        boolean ok = balanceClient.withdraw(player.getName(), option.getNeftPrice());
        if (!ok) {
            player.sendMessage("§cНедостаточно нефткоинов!");
            return false;
        }

        grantPermission(player, option.getPermission());
        setActiveMessage(player.getUniqueId(), option.getId());
        return true;
    }

    private void grantPermission(Player player, String permission) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        user.data().add(Node.builder(permission).build());
        luckPerms.getUserManager().saveUser(user);
    }
}
