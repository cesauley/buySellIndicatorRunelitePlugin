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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

/**
 * Main plugin entry point.
 *
 * Registers the WidgetItemOverlay so RuneLite calls renderItemOverlay() for
 * every visible item in the inventory and bank interfaces, and a sidebar panel
 * listing Buy / Sell / Hold signals for observed items.
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

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private BuySellIndicatorPanel panel;

    @Inject
    private GraphOpeningService graphOpeningService;

    private NavigationButton navButton;

    @Override
    protected void startUp()
    {
        eventBus.register(this);
        eventBus.register(bankFilterManager);
        eventBus.register(panel);
        overlayManager.add(overlay);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("Buy/Sell Indicator")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        log.info("Buy/Sell Indicator plugin started");
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        eventBus.unregister(panel);
        eventBus.unregister(bankFilterManager);
        bankFilterManager.reset();
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        analysisService.clearCache();
        graphOpeningService.shutdown();
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

        if ("analysisBundle".equals(event.getKey())
            || "minItemPrice".equals(event.getKey())
            || "maxItemPrice".equals(event.getKey())
            || "blacklistedItems".equals(event.getKey())
            || "minConfidence".equals(event.getKey()))
        {
            log.debug("Analysis-affecting config changed; clearing price cache key={}", event.getKey());
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
        graphOpeningService.openItemGraph(canonicalId);
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

    @Provides
    BuySellIndicatorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BuySellIndicatorConfig.class);
    }
}
