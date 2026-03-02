package net.akat.unique;

import net.akat.BalanceHttpClient;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.logging.Level;

public class UniqueOrderService {
    private final JavaPlugin plugin;
    private final BalanceHttpClient balanceClient;
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

    public UniqueOrderService(JavaPlugin plugin, BalanceHttpClient balanceClient) {
        this.plugin = plugin;
        this.balanceClient = balanceClient;
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
        } else if (dbType.equals("postgresql") || dbType.equals("postgres")) {
            String host = config.getString("database.host", "127.0.0.1");
            int port = config.getInt("database.port", 5432);
            String database = config.getString("database.name", "nameplates");
            dbUser = config.getString("database.user", "postgres");
            dbPassword = config.getString("database.password", "");
            dbUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            dbType = "postgresql";
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
                    + "order_number INTEGER UNIQUE,"
                    + "player_uuid TEXT NOT NULL,"
                    + "player_name TEXT NOT NULL,"
                    + "price INTEGER NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'PENDING',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "completed_at DATETIME NULL"
                    + ")";
        } else if ("postgresql".equals(dbType)) {
            sql = "CREATE TABLE IF NOT EXISTS unique_nameplate_orders ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "order_number BIGINT UNIQUE,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(16) NOT NULL,"
                    + "price INT NOT NULL,"
                    + "status VARCHAR(32) NOT NULL DEFAULT 'PENDING',"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "completed_at TIMESTAMP NULL"
                    + ")";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS unique_nameplate_orders ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "order_number BIGINT UNIQUE,"
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
            ensureOrderNumberColumn(c, st);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось инициализировать таблицу unique_nameplate_orders", e);
        }
    }

    private void ensureOrderNumberColumn(Connection c, Statement st) {
        try {
            DatabaseMetaData meta = c.getMetaData();
            String tableName = "unique_nameplate_orders";
            String normalizedTable = "postgresql".equals(dbType) ? tableName.toLowerCase() : tableName;

            try (ResultSet rs = meta.getColumns(null, null, normalizedTable, "order_number")) {
                if (rs.next()) {
                    return;
                }
            }

            String alterSql;
            if ("sqlite".equals(dbType)) {
                alterSql = "ALTER TABLE unique_nameplate_orders ADD COLUMN order_number INTEGER";
            } else {
                alterSql = "ALTER TABLE unique_nameplate_orders ADD COLUMN order_number BIGINT";
            }

            st.execute(alterSql);
            st.execute("UPDATE unique_nameplate_orders SET order_number = id WHERE order_number IS NULL");
            try {
                st.execute("CREATE UNIQUE INDEX uq_unique_nameplate_orders_order_number ON unique_nameplate_orders(order_number)");
            } catch (SQLException ignored) {
                // Индекс может уже существовать, игнорируем
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось проверить/создать колонку order_number", e);
        }
    }

    public long createOrder(Player player) {
        String sql = "INSERT INTO unique_nameplate_orders (order_number, player_uuid, player_name, price, status) VALUES (?, ?, ?, ?, 'PENDING')";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long orderNumber = generateUniqueOrderNumber(c);
            ps.setLong(1, orderNumber);
            ps.setString(2, player.getUniqueId().toString());
            ps.setString(3, player.getName());
            ps.setInt(4, price);
            ps.executeUpdate();
            sendEndpoint(purchaseEndpoint, orderNumber, player.getUniqueId(), player.getName());
            return orderNumber;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать заказ уникального ника", e);
        }
        return -1;
    }

    private long generateUniqueOrderNumber(Connection c) throws SQLException {
        String checkSql = "SELECT 1 FROM unique_nameplate_orders WHERE order_number = ?";
        for (int i = 0; i < 20; i++) {
            long candidate = ThreadLocalRandom.current().nextLong(100000L, 1_000_000_000L);
            try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                ps.setLong(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            }
        }
        throw new SQLException("Не удалось сгенерировать уникальный номер заказа");
    }

    public List<UniqueOrder> getPendingOrders() {
        String sql = "SELECT order_number, player_uuid, player_name, price, status, created_at FROM unique_nameplate_orders WHERE status='PENDING' ORDER BY id DESC";
        List<UniqueOrder> result = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new UniqueOrder(
                        rs.getLong("order_number"),
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
        String fetch = "SELECT player_uuid, player_name FROM unique_nameplate_orders WHERE order_number=? AND status='PENDING'";
        String update = "UPDATE unique_nameplate_orders SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE order_number=? AND status='PENDING'";

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


    public boolean deleteOrderWithRefund(long orderId, String adminName) {
        String fetch = "SELECT player_uuid, player_name, price FROM unique_nameplate_orders WHERE order_number=? AND status='PENDING'";
        String delete = "DELETE FROM unique_nameplate_orders WHERE order_number=? AND status='PENDING'";

        try (Connection c = getConnection()) {
            String playerName = null;
            int orderPrice = 0;

            try (PreparedStatement fps = c.prepareStatement(fetch)) {
                fps.setLong(1, orderId);
                try (ResultSet rs = fps.executeQuery()) {
                    if (rs.next()) {
                        playerName = rs.getString("player_name");
                        orderPrice = rs.getInt("price");
                    } else {
                        return false;
                    }
                }
            }

            try (PreparedStatement dps = c.prepareStatement(delete)) {
                dps.setLong(1, orderId);
                int changed = dps.executeUpdate();
                if (changed <= 0) {
                    return false;
                }
            }

            boolean refunded = balanceClient.deposit(playerName, orderPrice);
            if (!refunded) {
                plugin.getLogger().warning("Не удалось вернуть средства игроку " + playerName + " за удалённый заказ #" + orderId);
            }

            plugin.getLogger().info("Администратор " + adminName + " удалил заказ #" + orderId + " с возвратом средств игроку " + playerName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось удалить заказ с возвратом", e);
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
