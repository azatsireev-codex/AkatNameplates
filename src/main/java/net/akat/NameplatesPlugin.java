package net.akat;

import net.akat.api.spigui.SpiGUI;
import net.akat.command.ConfirmationCommandHandler;
import net.akat.command.NameplateCommand;
import net.akat.joinquit.JoinQuitMessageService;
import net.akat.listener.JoinQuitMessageListener;
import net.akat.manager.NameplateManager;
import net.akat.menu.JoinQuitMessageMenu;
import net.akat.menu.NameplateMenu;
import net.akat.menu.UniqueOrderAdminMenu;
import net.akat.service.NameplateActions;
import net.akat.service.NameplateOwnershipService;
import net.akat.unique.UniqueOrderService;
import net.akat.api.AkatPointaucAPI;
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
    private UniqueOrderService uniqueOrderService;
    private UniqueOrderAdminMenu uniqueOrderAdminMenu;
    private JoinQuitMessageService joinQuitMessageService;
    private JoinQuitMessageMenu joinQuitMessageMenu;

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
        AkatPointaucAPI pointaucAPI = getServer().getServicesManager().load(AkatPointaucAPI.class);
        this.uniqueOrderService = new UniqueOrderService(this, balanceClient);
        this.joinQuitMessageService = new JoinQuitMessageService(this, balanceClient, pointaucAPI);
        this.joinQuitMessageMenu = new JoinQuitMessageMenu(spiGUI, joinQuitMessageService);
        this.actions = new NameplateActions(balanceClient, ownership, uniqueOrderService);
        this.uniqueOrderAdminMenu = new UniqueOrderAdminMenu(spiGUI, uniqueOrderService);

        var nameplateItems = manager.getNameplates();
        var packsMenuTitle = manager.getPacksMenuTitle();
        var packsMenuRows = manager.getPacksMenuRows();
        var nameplatesMenuTitle = manager.getNameplatesMenuTitle();
        var nameplatesMenuRows = manager.getNameplatesMenuRows();

        this.purchaseMenu = new NameplateMenu(
                spiGUI,
                nameplateItems,
                packsMenuTitle,
                packsMenuRows,
                nameplatesMenuTitle,
                nameplatesMenuRows,
                manager.getPacksMenuInfoSlot(),
                manager.getPacksMenuPreviewSlot(),
                manager.getPacksMenuUnequipSlot(),
                manager.getPacksMenuPreviousSlot(),
                manager.getPacksMenuNextSlot(),
                manager.getNameplatesMenuBackSlot(),
                manager.getNameplatesMenuUnequipSlot(),
                manager.getNameplatesMenuPreviousSlot(),
                manager.getNameplatesMenuNextSlot(),
                manager.getDonateUrl(),
                manager.getPacksMenuPackSlots(),
                manager.getNameplatesMenuItemSlots(),
                actions,
                manager.getPackModels(),
                manager.getButtonModels(),
                manager.getPacksAfterGeneral(),
                uniqueOrderService
        );

        getCommand("confirm").setExecutor(new ConfirmationCommandHandler());
        getCommand("cancel").setExecutor(new ConfirmationCommandHandler());

        NameplateCommand command = new NameplateCommand(
                manager, purchaseMenu, spiGUI, actions, uniqueOrderService, uniqueOrderAdminMenu, joinQuitMessageService, joinQuitMessageMenu
        );
        Objects.requireNonNull(getCommand("akatnameplates")).setExecutor(command);
        Objects.requireNonNull(getCommand("akatnameplates")).setTabCompleter(command);
        Objects.requireNonNull(getCommand("akatmessages")).setExecutor(command);
        Objects.requireNonNull(getCommand("akatmessagesreload")).setExecutor(command);

        getServer().getPluginManager().registerEvents(new JoinQuitMessageListener(joinQuitMessageService), this);
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
