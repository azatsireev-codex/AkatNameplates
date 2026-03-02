package net.akat.unique;

import java.time.LocalDateTime;
import java.util.UUID;

public class UniqueOrder {
    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final int price;
    private final String status;
    private final LocalDateTime createdAt;

    public UniqueOrder(long id, UUID playerUuid, String playerName, int price, String status, LocalDateTime createdAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public int getPrice() { return price; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
