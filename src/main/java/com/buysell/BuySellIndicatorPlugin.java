package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main plugin entry point.
 *
 * Registers the WidgetItemOverlay so RuneLite calls renderItemOverlay() for
 * every visible item in the inventory and bank interfaces.
 * The PriceAnalysisService is a Singleton managed by Guice and shared with
 * the overlay via injection.
 */
@Slf4j
@PluginDescriptor(
    name = "Buy/Sell Indicator",
    description = "Shows BUY/SELL/HOLD signals with confidence % on inventory and bank items "
        + "using configurable analysis bundles for flipping and long-term merchanting "
        + "(mean reversion, classic TA, z-score) on OSRS Wiki GE prices.",
    tags = {"buy", "sell", "ge", "grand exchange", "price", "indicator", "trading", "flipping",
        "merchanting", "bank", "inventory"}
)
public class BuySellIndicatorPlugin extends Plugin
{
    private static final String MENU_OPTION_VIEW_GRAPH = "View Graph";
    private static final String PRICE_GRAPH_BASE_URL = "https://prices.osrs.cloud/item/";

    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BuySellIndicatorOverlay overlay;

    @Inject
    private PriceAnalysisService analysisService;

    @Inject
    private BankFilterManager bankFilterManager;

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private BuySellIndicatorConfig config;

    private final ExecutorService browserExecutor = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "buysell-view-graph");
        t.setDaemon(true);
        return t;
    });

    @Override
    protected void startUp()
    {
        eventBus.register(this);
        eventBus.register(bankFilterManager);
        overlayManager.add(overlay);
        log.info("Buy/Sell Indicator plugin started");
    }

    @Override
    protected void shutDown()
    {
        eventBus.unregister(bankFilterManager);
        bankFilterManager.reset();
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        analysisService.clearCache();
        browserExecutor.shutdown();
        log.info("Buy/Sell Indicator plugin stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"buysell".equals(event.getGroup()))
        {
            return;
        }

        log.debug("Config changed group={} key={} oldValue={} newValue={}",
            event.getGroup(), event.getKey(), event.getOldValue(), event.getNewValue());

        if ("analysisBundle".equals(event.getKey()))
        {
            log.debug("Analysis bundle changed; clearing price cache");
            analysisService.clearCache();
        }

        if ("minItemPrice".equals(event.getKey()) || "maxItemPrice".equals(event.getKey()))
        {
            log.debug("Price threshold changed; clearing price cache");
            analysisService.clearCache();
        }
    }

    /**
     * Adds "View Graph" once per item context menu (hooked off the vanilla Examine line).
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!"Examine".equals(event.getOption()))
        {
            return;
        }

        int itemId = event.getItemId();
        if (itemId <= 0)
        {
            return;
        }

        int packed = event.getActionParam1();
        if (packed == -1)
        {
            return;
        }

        Widget w = client.getWidget(packed);
        if (w == null)
        {
            return;
        }

        boolean onInventory = widgetHasAncestor(w, InterfaceID.Inventory.ITEMS);
        boolean onBank = widgetHasAncestor(w, InterfaceID.Bankmain.ITEMS);

        if (onInventory && !config.showOnInventory())
        {
            return;
        }
        if (onBank && !config.showOnBank())
        {
            return;
        }
        if (!onInventory && !onBank)
        {
            return;
        }

        int canonicalId = itemManager.canonicalize(itemId);
        ItemComposition def = itemManager.getItemComposition(canonicalId);
        if (!def.isTradeable())
        {
            return;
        }

        SignalResult cached = analysisService.getCachedSignal(canonicalId);
        if (cached == null || cached.getSignal() == Signal.FILTERED)
        {
            return;
        }

        // MenuEntryAdded can fire more than once per open (e.g. multiple Examine rows / action types).
        if (menuAlreadyHasViewGraphForItem(canonicalId))
        {
            return;
        }

        client.getMenu().createMenuEntry(-1)
            .setOption(MENU_OPTION_VIEW_GRAPH)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .setIdentifier(canonicalId)
            .setItemId(canonicalId);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!MENU_OPTION_VIEW_GRAPH.equals(event.getMenuOption()))
        {
            return;
        }
        if (event.getMenuAction() != MenuAction.RUNELITE)
        {
            return;
        }

        int canonicalId = event.getId();
        if (canonicalId <= 0)
        {
            canonicalId = event.getItemId();
        }
        if (canonicalId <= 0)
        {
            log.warn("View Graph: no item id on menu entry");
            return;
        }

        event.consume();

        String url = PRICE_GRAPH_BASE_URL + canonicalId;
        browserExecutor.execute(() -> openUrlInBrowser(url));
    }

    private boolean menuAlreadyHasViewGraphForItem(int canonicalId)
    {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        if (entries == null)
        {
            return false;
        }
        for (MenuEntry entry : entries)
        {
            if (entry == null)
            {
                continue;
            }
            if (MENU_OPTION_VIEW_GRAPH.equals(entry.getOption())
                && entry.getIdentifier() == canonicalId
                && entry.getType() == MenuAction.RUNELITE)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean widgetHasAncestor(Widget widget, int ancestorPackedId)
    {
        for (Widget cur = widget; cur != null; cur = cur.getParent())
        {
            if (cur.getId() == ancestorPackedId)
            {
                return true;
            }
        }
        return false;
    }

    private void openUrlInBrowser(String url)
    {
        try
        {
            if (!Desktop.isDesktopSupported())
            {
                log.warn("View Graph: Desktop API not supported; cannot open {}", url);
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE))
            {
                log.warn("View Graph: BROWSE action not supported; cannot open {}", url);
                return;
            }
            desktop.browse(new URI(url));
            log.debug("View Graph opened {}", url);
        }
        catch (Exception e)
        {
            log.warn("View Graph: failed to open {}: {}", url, e.getMessage());
        }
    }

    @Provides
    BuySellIndicatorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BuySellIndicatorConfig.class);
    }
}
