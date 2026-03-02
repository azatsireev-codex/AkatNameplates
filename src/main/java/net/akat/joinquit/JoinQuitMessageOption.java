package net.akat.joinquit;

import org.bukkit.Material;

public class JoinQuitMessageOption {
    private final String id;
    private final String permission;
    private final int neftPrice;
    private final int pointsPrice;
    private final String joinMessage;
    private final String quitMessage;
    private final Material icon;
    private final boolean hiddenFromShop;

    public JoinQuitMessageOption(String id, String permission, int neftPrice, int pointsPrice,
                                 String joinMessage, String quitMessage, Material icon, boolean hiddenFromShop) {
        this.id = id;
        this.permission = permission;
        this.neftPrice = neftPrice;
        this.pointsPrice = pointsPrice;
        this.joinMessage = joinMessage;
        this.quitMessage = quitMessage;
        this.icon = icon;
        this.hiddenFromShop = hiddenFromShop;
    }

    public String getId() { return id; }
    public String getPermission() { return permission; }
    public int getNeftPrice() { return neftPrice; }
    public int getPointsPrice() { return pointsPrice; }
    public String getJoinMessage() { return joinMessage; }
    public String getQuitMessage() { return quitMessage; }
    public Material getIcon() { return icon; }
    public boolean isHiddenFromShop() { return hiddenFromShop; }

    public boolean hasPointsPayment() {
        return pointsPrice > 0;
    }

    public boolean hasNeftPayment() {
        return neftPrice > 0;
    }
}
