package net.akat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

public class BalanceHttpClient {
    private static final long DEFAULT_REQUEST_DELAY_MILLIS = 1000L;
    private final String apiUrl;
    private final Logger logger;
    private final long requestDelayMillis;
    private final Object requestLock = new Object();
    private long lastRequestMillis;

    public BalanceHttpClient(String apiUrl) {
        this(apiUrl, null);
    }

    public BalanceHttpClient(String apiUrl, Logger logger) {
        this(apiUrl, logger, DEFAULT_REQUEST_DELAY_MILLIS);
    }

    public BalanceHttpClient(String apiUrl, Logger logger, long requestDelayMillis) {
        this.apiUrl = apiUrl != null && apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.logger = logger;
        this.requestDelayMillis = Math.max(0L, requestDelayMillis);
    }

    public BalanceInfo getBalanceInfo(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        try {
            return sendBalanceRequest(username);
        } catch (IOException e) {
            logFailure("getBalanceInfo", username, 0, e);
            return new BalanceInfo(0, false);
        }
    }

    public boolean withdraw(String username, double amount) {
        int normalized = normalizeAmount(amount);
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        try {
            return sendCoinsRequest(username, -normalized);
        } catch (IOException e) {
            logFailure("withdraw", username, normalized, e);
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (player == null) {
            return false;
        }
        return withdraw(player.getName(), amount);
    }

    public boolean deposit(String username, double amount) {
        int normalized = normalizeAmount(amount);
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        try {
            return sendCoinsRequest(username, normalized);
        } catch (IOException e) {
            logFailure("deposit", username, normalized, e);
            return false;
        }
    }

    public boolean deposit(Player player, double amount) {
        if (player == null) {
            return false;
        }
        return deposit(player.getName(), amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        if (uuid == null) {
            return false;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline == null) {
            return false;
        }
        String name = offline.getName();
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return deposit(name, amount);
    }

    private int normalizeAmount(double amount) {
        long rounded = Math.round(amount);
        if (rounded <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Amount too large");
        }
        return (int) rounded;
    }

    private boolean sendCoinsRequest(String username, int amount) throws IOException {
        throttleRequests();
        URL url = new URL(apiUrl + "/give-coins");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        String json = "{\"username\":\"" + username + "\",\"coins\":" + amount + "}";

        try (OutputStream os = con.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        if (code != 200) {
            return false;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
            JsonObject obj = JsonParser.parseString(resp.toString()).getAsJsonObject();
            return obj.has("status") && obj.get("status").getAsString().equalsIgnoreCase("success");
        }
    }

    private BalanceInfo sendBalanceRequest(String username) throws IOException {
        throttleRequests();
        URL url = new URL(apiUrl + "/balance/" + URLEncoder.encode(username, StandardCharsets.UTF_8));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json");

        int code = con.getResponseCode();
        if (code != 200) {
            return new BalanceInfo(0, false);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
            JsonObject obj = JsonParser.parseString(resp.toString()).getAsJsonObject();

            if (obj.has("status") && obj.get("status").getAsString().equalsIgnoreCase("success")) {
                int balance = obj.has("balance") ? obj.get("balance").getAsInt() : 0;
                boolean playerExists = obj.has("player_exists") && obj.get("player_exists").getAsBoolean();
                return new BalanceInfo(balance, playerExists);
            }
        }
        return new BalanceInfo(0, false);
    }

    private void logFailure(String action, String username, int amount, Exception e) {
        if (logger != null) {
            logger.warning("Failed to " + action + " " + amount + " coins for " + username + ": " + e.getMessage());
        }
    }
    private void throttleRequests() {
        if (requestDelayMillis <= 0L) {
            return;
        }
        synchronized (requestLock) {
            long now = System.currentTimeMillis();
            long earliest = lastRequestMillis + requestDelayMillis;
            if (earliest > now) {
                if (Bukkit.isPrimaryThread()) {

                    lastRequestMillis = now;
                    return;
                }
                long sleep = earliest - now;
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                now = System.currentTimeMillis();
            }
            lastRequestMillis = now;
        }
    }

    public static class BalanceInfo {
        private final int balance;
        private final boolean playerExists;

        public BalanceInfo(int balance, boolean playerExists) {
            this.balance = balance;
            this.playerExists = playerExists;
        }

        public int getBalance() {
            return balance;
        }

        public boolean isPlayerExists() {
            return playerExists;
        }

        @Override
        public String toString() {
            return "BalanceInfo{balance=" + balance + ", playerExists=" + playerExists + '}';
        }
    }
}

