package net.akat.service;

import net.akat.BalanceHttpClient;
import net.akat.NameplateItem;
import net.akat.NameplatesPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NameplateActions {
    private final BalanceHttpClient balance;
    private final NameplateOwnershipService ownership;
    private final Map<UUID, Long> lastPreviewTime = new HashMap<>();
    private final Map<UUID, Long> lastPreviewCurrentTime = new HashMap<>();
    private static final long COOLDOWN_MS = 5000;
    private static final long PREVIEW_CURRENT_COOLDOWN_MS = 10000;

    public NameplateActions(BalanceHttpClient balance, NameplateOwnershipService ownership) {
        this.balance = balance;
        this.ownership = ownership;
    }

    public void preview(Player player, NameplateItem item) {
        if (checkCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Подождите " + formatCooldown(player) + " секунд перед следующим предпросмотром!");
            return;
        }

        player.closeInventory();
        Bukkit.getGlobalRegionScheduler().run(NameplatesPlugin.getInstance(), t -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "nameplates force-preview " + player.getName() + " " + item.getId());
        });
        player.sendMessage(ChatColor.GREEN + "Предпросмотр ника включен!");
        player.sendMessage(ChatColor.YELLOW + "(!) Для просмотра переключитесь в режим от третьего лица (F5)");
        updateCooldown(player);
    }

    public void previewCurrent(Player player) {
        if (checkPreviewCurrentCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Подождите " + formatPreviewCurrentCooldown(player) + " секунд перед следующим предпросмотром текущего ника!");
            return;
        }


        player.closeInventory();
        Bukkit.getGlobalRegionScheduler().run(NameplatesPlugin.getInstance(), t -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "nameplates force-preview " + player.getName());
        });
        player.sendMessage(ChatColor.GREEN + "Предпросмотр текущего ника включен!");
        player.sendMessage(ChatColor.YELLOW + "(!) Для просмотра переключитесь в режим от третьего лица (F5)");
        updateCooldown(player);
    }

    public void equip(Player player, NameplateItem item) {
        player.closeInventory();
        Bukkit.getGlobalRegionScheduler().run(NameplatesPlugin.getInstance(), t -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "nameplates force-equip " + player.getName() + " " + item.getId());
        });
        player.sendMessage(ChatColor.GREEN + "Ник " + ChatColor.YELLOW + item.getName() + ChatColor.GREEN + " надет!");
    }

    public void unequip(Player player) {
        player.closeInventory();
        Bukkit.getGlobalRegionScheduler().run(NameplatesPlugin.getInstance(), t -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "nameplates force-unequip " + player.getName());
        });
        player.sendMessage(ChatColor.GREEN + "Текущий кастомный ник снят!");
    }

    public boolean buy(Player player, NameplateItem item) {
        if (item.hasPermission() && ownership.owns(player, item)) {
            player.sendMessage(ChatColor.RED + "Вы уже приобрели этот ник!");
            return false;
        }

        boolean success = balance.withdraw(player.getName(), item.getPrice());
        if (!success) {
            player.sendMessage(ChatColor.RED + "У тебя недостаточно нефткоинов!");
            return false;
        }

        ownership.grant(player, item);
        player.sendMessage(ChatColor.GREEN + "Ты купил " + ChatColor.YELLOW + item.getName() +
                ChatColor.GREEN + " за " + ChatColor.AQUA + item.getPrice() + ChatColor.GREEN + " нефткоинов!");
        return true;
    }



    private boolean checkCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (lastPreviewTime.containsKey(playerId)) {
            long lastTime = lastPreviewTime.get(playerId);
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastTime) < COOLDOWN_MS;
        }
        return false;
    }

    private void updateCooldown(Player player) {
        long currentTime = System.currentTimeMillis();
        lastPreviewTime.put(player.getUniqueId(), currentTime);
        lastPreviewCurrentTime.put(player.getUniqueId(), currentTime);
    }

    private String formatCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (lastPreviewTime.containsKey(playerId)) {
            long lastTime = lastPreviewTime.get(playerId);
            long currentTime = System.currentTimeMillis();
            long remaining = COOLDOWN_MS - (currentTime - lastTime);
            return String.valueOf((int) Math.ceil(remaining / 1000.0));
        }
        return "0";
    }

    private boolean checkPreviewCurrentCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (lastPreviewCurrentTime.containsKey(playerId)) {
            long lastTime = lastPreviewCurrentTime.get(playerId);
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastTime) < PREVIEW_CURRENT_COOLDOWN_MS;
        }
        return false;
    }

    private String formatPreviewCurrentCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (lastPreviewCurrentTime.containsKey(playerId)) {
            long lastTime = lastPreviewCurrentTime.get(playerId);
            long currentTime = System.currentTimeMillis();
            long remaining = PREVIEW_CURRENT_COOLDOWN_MS - (currentTime - lastTime);
            return String.valueOf((int) Math.ceil(remaining / 1000.0));
        }
        return "0";
    }

    public void clearCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        lastPreviewTime.remove(playerId);
        lastPreviewCurrentTime.remove(playerId);
    }
}
