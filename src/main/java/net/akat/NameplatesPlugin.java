package net.akat;

import net.akat.api.spigui.SpiGUI;
import net.akat.command.ConfirmationCommandHandler;
import net.akat.command.NameplateCommand;
import net.akat.manager.NameplateManager;
import net.akat.menu.NameplateMenu;
import net.akat.service.NameplateActions;
import net.akat.service.NameplateOwnershipService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class NameplatesPlugin extends JavaPlugin {
    private static NameplatesPlugin instance;
    private NameplateManager manager;
    private NameplateMenu purchaseMenu;
    private BalanceHttpClient balanceClient;
    private SpiGUI spiGUI;
    private LuckPerms luckPerms;

    private NameplateActions actions;
    private NameplateOwnershipService ownership;

    @Override
    public void onEnable() {
        instance = this;

        // Инициализируем SpiGUI
        this.spiGUI = new SpiGUI(this);
        this.luckPerms = LuckPermsProvider.get();

        // Создаем HTTP клиент
        this.balanceClient = new BalanceHttpClient("http://localhost:32555", getLogger());

        // Менеджер конфигурации
        this.manager = new NameplateManager(this);
        this.ownership = new NameplateOwnershipService(luckPerms);
        this.actions = new NameplateActions(balanceClient, ownership);

        var nameplateItems = manager.getNameplates();
        var menuTitle = manager.getMenuTitle();
        var menuRows = manager.getMenuRows();

        this.purchaseMenu = new NameplateMenu(
                spiGUI,
                nameplateItems,
                menuTitle,
                menuRows,
                actions,
                manager.getPackModels()
        );

        getCommand("confirm").setExecutor(new ConfirmationCommandHandler());
        getCommand("cancel").setExecutor(new ConfirmationCommandHandler());

        NameplateCommand command = new NameplateCommand(
                manager, purchaseMenu, spiGUI, actions
        );
        Objects.requireNonNull(getCommand("akatnameplates")).setExecutor(command);
        Objects.requireNonNull(getCommand("akatnameplates")).setTabCompleter(command);
    }

    public static NameplatesPlugin getInstance() {
        return instance;
    }

    public NameplateManager getManager() {
        return manager;
    }

    public NameplateMenu getPurchaseMenu() {
        return purchaseMenu;
    }

    public LuckPerms getLuckPerms() {return luckPerms;}

    public BalanceHttpClient getBalanceClient() {
        return balanceClient;
    }

    public SpiGUI getSpiGUI() {
        return spiGUI;
    }
}
