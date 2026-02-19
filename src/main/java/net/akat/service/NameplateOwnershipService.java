package net.akat.service;

import net.akat.NameplateItem;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.Map;

public class NameplateOwnershipService {
    private final LuckPerms luckPerms;

    public NameplateOwnershipService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public boolean owns(Player player, NameplateItem item) {
        if (player == null || item == null || !item.hasPermission()) return false;

        if (item.hasContext()) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;

            return user.getCachedData().getPermissionData()
                    .checkPermission(item.getPermission()).asBoolean();
        }

        return player.hasPermission(item.getPermission());
    }

    public void grant(Player player, NameplateItem item) {
        if (player == null || item == null || !item.hasPermission()) return;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        Node node;
        if (item.hasContext()) {
            var contextBuilder = net.luckperms.api.context.ImmutableContextSet.builder();
            for (Map.Entry<String, String> e : item.getContext().entrySet()) {
                contextBuilder.add(e.getKey().toLowerCase(), String.valueOf(e.getValue()).toLowerCase());
            }
            var ctx = contextBuilder.build();
            node = Node.builder(item.getPermission()).context(ctx).build();
        } else {
            node = Node.builder(item.getPermission()).build();
        }

        user.data().add(node);
        luckPerms.getUserManager().saveUser(user);
    }
}
