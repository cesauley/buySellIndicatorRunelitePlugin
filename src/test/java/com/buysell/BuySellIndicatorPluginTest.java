package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BuySellIndicatorPluginTest
{
    private BuySellIndicatorPlugin plugin;
    private EventBus eventBus;
    private OverlayManager overlayManager;
    private BuySellIndicatorOverlay overlay;
    private PriceAnalysisService analysisService;
    private BankFilterManager bankFilterManager;
    private Client client;
    private ItemManager itemManager;
    private BuySellIndicatorConfig config;
    private ClientToolbar clientToolbar;
    private BuySellIndicatorPanel panel;
    private GraphOpeningService graphOpeningService;

    @Before
    public void setUp() throws Exception
    {
        plugin = new BuySellIndicatorPlugin();
        eventBus = mock(EventBus.class);
        overlayManager = mock(OverlayManager.class);
        overlay = mock(BuySellIndicatorOverlay.class);
        analysisService = mock(PriceAnalysisService.class);
        bankFilterManager = mock(BankFilterManager.class);
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        config = mock(BuySellIndicatorConfig.class);
        clientToolbar = mock(ClientToolbar.class);
        panel = mock(BuySellIndicatorPanel.class);
        graphOpeningService = mock(GraphOpeningService.class);

        set(plugin, "eventBus", eventBus);
        set(plugin, "overlayManager", overlayManager);
        set(plugin, "overlay", overlay);
        set(plugin, "analysisService", analysisService);
        set(plugin, "bankFilterManager", bankFilterManager);
        set(plugin, "client", client);
        set(plugin, "itemManager", itemManager);
        set(plugin, "config", config);
        set(plugin, "clientToolbar", clientToolbar);
        set(plugin, "panel", panel);
        set(plugin, "graphOpeningService", graphOpeningService);
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field f = BuySellIndicatorPlugin.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void startUp_and_shutDown_registerLifecycle()
    {
        plugin.startUp();
        verify(eventBus).register(plugin);
        verify(eventBus).register(bankFilterManager);
        verify(eventBus).register(panel);
        verify(overlayManager).add(overlay);
        ArgumentCaptor<NavigationButton> cap = ArgumentCaptor.forClass(NavigationButton.class);
        verify(clientToolbar).addNavigation(cap.capture());
        assertNotNull(cap.getValue());

        plugin.shutDown();
        verify(clientToolbar).removeNavigation(any(NavigationButton.class));
        verify(eventBus).unregister(panel);
        verify(eventBus).unregister(bankFilterManager);
        verify(bankFilterManager).reset();
        verify(eventBus).unregister(plugin);
        verify(overlayManager).remove(overlay);
        verify(analysisService).clearCache();
        verify(graphOpeningService).shutdown();
    }

    @Test
    public void onConfigChanged_clearsCacheForAnalysisKeys()
    {
        ConfigChanged ev = mock(ConfigChanged.class);
        when(ev.getGroup()).thenReturn("buysell");
        when(ev.getKey()).thenReturn("minConfidence");
        plugin.onConfigChanged(ev);
        verify(analysisService).clearCache();
    }

    @Test
    public void onConfigChanged_ignoresOtherGroupsAndKeys()
    {
        ConfigChanged ev = mock(ConfigChanged.class);
        when(ev.getGroup()).thenReturn("other");
        when(ev.getKey()).thenReturn("minConfidence");
        plugin.onConfigChanged(ev);
        verify(analysisService, never()).clearCache();

        when(ev.getGroup()).thenReturn("buysell");
        when(ev.getKey()).thenReturn("fontSize");
        plugin.onConfigChanged(ev);
        verify(analysisService, never()).clearCache();
    }

    @Test
    public void onMenuEntryAdded_createsViewGraphWhenEligible()
    {
        when(config.showOnInventory()).thenReturn(true);
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getOption()).thenReturn("Examine");
        when(event.getItemId()).thenReturn(4151);
        when(event.getActionParam1()).thenReturn(100);
        when(event.getTarget()).thenReturn("Whip");

        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(InterfaceID.Inventory.ITEMS);
        when(widget.getParent()).thenReturn(null);
        when(client.getWidget(100)).thenReturn(widget);

        when(itemManager.canonicalize(4151)).thenReturn(4151);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(4151)).thenReturn(def);
        when(analysisService.getCachedSignal(4151))
            .thenReturn(new SignalResult(Signal.BUY, 80, 1));

        Menu menu = mock(Menu.class);
        when(client.getMenu()).thenReturn(menu);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[0]);
        MenuEntry created = mock(MenuEntry.class);
        when(menu.createMenuEntry(-1)).thenReturn(created);
        when(created.setOption(any())).thenReturn(created);
        when(created.setTarget(any())).thenReturn(created);
        when(created.setType(any())).thenReturn(created);
        when(created.setIdentifier(anyInt())).thenReturn(created);
        when(created.setItemId(anyInt())).thenReturn(created);

        plugin.onMenuEntryAdded(event);
        verify(menu).createMenuEntry(-1);
        verify(created).setOption("View Graph");
    }

    @Test
    public void onMenuEntryAdded_skipsWhenNotExamine()
    {
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getOption()).thenReturn("Use");
        plugin.onMenuEntryAdded(event);
        verify(client, never()).getWidget(anyInt());
    }

    @Test
    public void onMenuOptionClicked_opensGraph()
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getMenuOption()).thenReturn("View Graph");
        when(event.getMenuAction()).thenReturn(MenuAction.RUNELITE);
        when(event.getId()).thenReturn(4151);

        plugin.onMenuOptionClicked(event);
        verify(event).consume();
        verify(graphOpeningService).openItemGraph(4151);
    }

    @Test
    public void onMenuOptionClicked_fallsBackToItemId()
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getMenuOption()).thenReturn("View Graph");
        when(event.getMenuAction()).thenReturn(MenuAction.RUNELITE);
        when(event.getId()).thenReturn(0);
        when(event.getItemId()).thenReturn(99);

        plugin.onMenuOptionClicked(event);
        verify(graphOpeningService).openItemGraph(99);
    }

    @Test
    public void provideConfig_delegates()
    {
        ConfigManager cm = mock(ConfigManager.class);
        BuySellIndicatorConfig cfg = mock(BuySellIndicatorConfig.class);
        when(cm.getConfig(BuySellIndicatorConfig.class)).thenReturn(cfg);
        assertEquals(cfg, plugin.provideConfig(cm));
    }

    @Test
    public void onMenuEntryAdded_skipsNonTradeableAndFiltered()
    {
        when(config.showOnInventory()).thenReturn(true);
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getOption()).thenReturn("Examine");
        when(event.getItemId()).thenReturn(1);
        when(event.getActionParam1()).thenReturn(100);

        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(InterfaceID.Inventory.ITEMS);
        when(widget.getParent()).thenReturn(null);
        when(client.getWidget(100)).thenReturn(widget);
        when(itemManager.canonicalize(1)).thenReturn(1);

        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(false);
        when(itemManager.getItemComposition(1)).thenReturn(def);
        plugin.onMenuEntryAdded(event);
        verify(analysisService, never()).getCachedSignal(anyInt());

        when(def.isTradeable()).thenReturn(true);
        when(analysisService.getCachedSignal(1)).thenReturn(new SignalResult(Signal.FILTERED, 0, 1));
        Menu menu = mock(Menu.class);
        when(client.getMenu()).thenReturn(menu);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[0]);
        plugin.onMenuEntryAdded(event);
        verify(menu, never()).createMenuEntry(anyInt());
    }

    @Test
    public void onMenuEntryAdded_skipsWhenAlreadyPresent()
    {
        when(config.showOnInventory()).thenReturn(true);
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getOption()).thenReturn("Examine");
        when(event.getItemId()).thenReturn(5);
        when(event.getActionParam1()).thenReturn(100);
        when(event.getTarget()).thenReturn("x");

        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(InterfaceID.Inventory.ITEMS);
        when(widget.getParent()).thenReturn(null);
        when(client.getWidget(100)).thenReturn(widget);
        when(itemManager.canonicalize(5)).thenReturn(5);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(5)).thenReturn(def);
        when(analysisService.getCachedSignal(5)).thenReturn(new SignalResult(Signal.BUY, 80, 1));

        Menu menu = mock(Menu.class);
        when(client.getMenu()).thenReturn(menu);
        MenuEntry existing = mock(MenuEntry.class);
        when(existing.getOption()).thenReturn("View Graph");
        when(existing.getIdentifier()).thenReturn(5);
        when(existing.getType()).thenReturn(MenuAction.RUNELITE);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{null, existing});

        plugin.onMenuEntryAdded(event);
        verify(menu, never()).createMenuEntry(anyInt());
    }

    @Test
    public void onMenuOptionClicked_invalidId_doesNotOpen()
    {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getMenuOption()).thenReturn("View Graph");
        when(event.getMenuAction()).thenReturn(MenuAction.RUNELITE);
        when(event.getId()).thenReturn(0);
        when(event.getItemId()).thenReturn(0);
        plugin.onMenuOptionClicked(event);
        verify(graphOpeningService, never()).openItemGraph(anyInt());
    }

    @Test
    public void onMenuEntryAdded_respectsInventoryAndBankToggles()
    {
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getOption()).thenReturn("Examine");
        when(event.getItemId()).thenReturn(1);
        when(event.getActionParam1()).thenReturn(100);
        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(InterfaceID.Inventory.ITEMS);
        when(widget.getParent()).thenReturn(null);
        when(client.getWidget(100)).thenReturn(widget);

        when(config.showOnInventory()).thenReturn(false);
        plugin.onMenuEntryAdded(event);
        verify(itemManager, never()).canonicalize(anyInt());

        when(widget.getId()).thenReturn(InterfaceID.Bankmain.ITEMS);
        when(config.showOnBank()).thenReturn(false);
        plugin.onMenuEntryAdded(event);
        verify(itemManager, never()).canonicalize(anyInt());
    }
}
