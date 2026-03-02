package net.akat.joinquit;

import org.bukkit.Material;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class JoinQuitMessageOption {
    private final String id;
    private final String permission;
    private final int neftPrice;
    private final int pointsPrice;
    private final String joinMessage;
    private final String quitMessage;
    private final Material icon;
    private final boolean hiddenFromShop;
    private final String date;

    public JoinQuitMessageOption(String id, String permission, int neftPrice, int pointsPrice,
                                 String joinMessage, String quitMessage, Material icon, boolean hiddenFromShop, String date) {
        this.id = id;
        this.permission = permission;
        this.neftPrice = neftPrice;
        this.pointsPrice = pointsPrice;
        this.joinMessage = joinMessage;
        this.quitMessage = quitMessage;
        this.icon = icon;
        this.hiddenFromShop = hiddenFromShop;
        this.date = date;
    }

    public String getId() { return id; }
    public String getPermission() { return permission; }
    public int getNeftPrice() { return neftPrice; }
    public int getPointsPrice() { return pointsPrice; }
    public String getJoinMessage() { return joinMessage; }
    public String getQuitMessage() { return quitMessage; }
    public Material getIcon() { return icon; }
    public boolean isHiddenFromShop() { return hiddenFromShop; }
    public String getDate() { return date; }

    public boolean hasPointsPayment() {
        return pointsPrice > 0;
    }

    public boolean hasNeftPayment() {
        return neftPrice > 0;
    }

    public boolean hasDate() {
        return date != null && !date.trim().isEmpty();
    }

    public boolean shouldAddSymbol() {
        if (!hasDate()) return false;

        try {
            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate configDate = LocalDate.parse(date, formatter);

            return !configDate.isAfter(currentDate) &&
                    ChronoUnit.DAYS.between(configDate, currentDate) <= 4;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String getDisplayName() {
        if (shouldAddSymbol()) {
            return id + "&f\uE063";
        }
        return id;
    }
}
