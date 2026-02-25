package net.akat.unique;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class UniqueOrderService {
    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private String packName;
    private int price;
    private int buttonSlot;
    private String buttonName;
    private List<String> buttonLore;
    private List<String> warningMessages;

    private String dbType;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    private String purchaseEndpoint;
    private String completeEndpoint;

    public UniqueOrderService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        initTable();
    }

    public void reload() {
        loadConfig();
        initTable();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "unique-orders.yml");
        if (!configFile.exists()) {
            plugin.saveResource("unique-orders.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("unique-orders.enabled", true);
        packName = config.getString("unique-orders.pack-name", "Уникальные");
        price = config.getInt("unique-orders.price", 3000);
        buttonSlot = config.getInt("unique-orders.button-slot", 49);
        buttonName = config.getString("unique-orders.button-name", "§6Заказать уникальный ник");
        buttonLore = config.getStringList("unique-orders.button-lore");
        if (buttonLore == null || buttonLore.isEmpty()) {
            buttonLore = List.of("§7Цена: §e3000 §f\uE058", "§eНажмите для оформления заказа");
        }
        warningMessages = config.getStringList("unique-orders.warning-messages");
        if (warningMessages == null || warningMessages.isEmpty()) {
            warningMessages = List.of("§cПеред покупкой привяжите актуальный Telegram аккаунт.");
        }

        dbType = config.getString("database.type", "mariadb").trim().toLowerCase();
        if (dbType.equals("sqlite")) {
            String sqliteFileName = config.getString("database.sqlite.file", "unique-orders.db");
            File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            dbUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
            dbUser = "";
            dbPassword = "";
        } else {
            String host = config.getString("database.host", "127.0.0.1");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.name", "nameplates");
            dbUser = config.getString("database.user", "root");
            dbPassword = config.getString("database.password", "");
            dbUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8";
            dbType = "mariadb";
        }

        purchaseEndpoint = config.getString("endpoints.purchase", "").trim();
        completeEndpoint = config.getString("endpoints.complete", "").trim();
    }

    private Connection getConnection() throws SQLException {
        if ("sqlite".equals(dbType)) {
            return DriverManager.getConnection(dbUrl);
        }
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private void initTable() {
        String sql;
        if ("sqlite".equals(dbType)) {
            sql = "CREATE TABLE IF NOT EXISTS unique_nameplate_orders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_uuid TEXT NOT NULL,"
                    + "player_name TEXT NOT NULL,"
                    + "price INTEGER NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'PENDING',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "completed_at DATETIME NULL"
                    + ")";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS unique_nameplate_orders ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(16) NOT NULL,"
                    + "price INT NOT NULL,"
                    + "status VARCHAR(32) NOT NULL DEFAULT 'PENDING',"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "completed_at TIMESTAMP NULL"
                    + ")";
        }

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось инициализировать таблицу unique_nameplate_orders", e);
        }
    }

    public long createOrder(Player player) {
        String sql = "INSERT INTO unique_nameplate_orders (player_uuid, player_name, price, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.setInt(3, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    sendEndpoint(purchaseEndpoint, id, player.getUniqueId(), player.getName());
                    return id;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать заказ уникального ника", e);
        }
        return -1;
    }

    public List<UniqueOrder> getPendingOrders() {
        String sql = "SELECT id, player_uuid, player_name, price, status, created_at FROM unique_nameplate_orders WHERE status='PENDING' ORDER BY id DESC";
        List<UniqueOrder> result = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new UniqueOrder(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getInt("price"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить список заказов", e);
            return Collections.emptyList();
        }
        return result;
    }

    public boolean completeOrder(long orderId, String adminName) {
        String fetch = "SELECT player_uuid, player_name FROM unique_nameplate_orders WHERE id=? AND status='PENDING'";
        String update = "UPDATE unique_nameplate_orders SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'";

        try (Connection c = getConnection()) {
            String playerUuid = null;
            String playerName = null;

            try (PreparedStatement fps = c.prepareStatement(fetch)) {
                fps.setLong(1, orderId);
                try (ResultSet rs = fps.executeQuery()) {
                    if (rs.next()) {
                        playerUuid = rs.getString("player_uuid");
                        playerName = rs.getString("player_name");
                    } else {
                        return false;
                    }
                }
            }

            try (PreparedStatement ups = c.prepareStatement(update)) {
                ups.setLong(1, orderId);
                int changed = ups.executeUpdate();
                if (changed > 0) {
                    sendEndpoint(completeEndpoint, orderId, UUID.fromString(playerUuid), playerName, adminName);
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось завершить заказ", e);
        }

        return false;
    }

    private void sendEndpoint(String endpoint, long orderId, UUID playerUuid, String playerName) {
        sendEndpoint(endpoint, orderId, playerUuid, playerName, null);
    }

    private void sendEndpoint(String endpoint, long orderId, UUID playerUuid, String playerName, String adminName) {
        if (endpoint == null || endpoint.isEmpty()) {
            return;
        }

        try {
            URL url = URI.create(endpoint).toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String json = "{"
                    + "\"orderId\":" + orderId + ","
                    + "\"playerUuid\":\"" + playerUuid + "\","
                    + "\"playerName\":\"" + playerName + "\","
                    + "\"price\":" + price
                    + (adminName == null ? "" : ",\"adminName\":\"" + adminName + "\"")
                    + "}";

            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = con.getResponseCode();
            if (code >= 300) {
                plugin.getLogger().warning("Endpoint " + endpoint + " вернул код " + code);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось отправить endpoint: " + endpoint, e);
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getPackName() { return packName; }
    public int getPrice() { return price; }
    public int getButtonSlot() { return buttonSlot; }
    public String getButtonName() { return buttonName; }
    public List<String> getButtonLore() { return new ArrayList<>(buttonLore); }
    public List<String> getWarningMessages() { return new ArrayList<>(warningMessages); }
}
