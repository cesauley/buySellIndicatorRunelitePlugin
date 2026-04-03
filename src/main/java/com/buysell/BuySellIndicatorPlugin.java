package com.buysell;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

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
    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BuySellIndicatorOverlay overlay;

    @Inject
    private PriceAnalysisService analysisService;

    @Override
    protected void startUp()
    {
        eventBus.register(this);
        overlayManager.add(overlay);
        log.info("Buy/Sell Indicator plugin started");
    }

    @Override
    protected void shutDown()
    {
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        analysisService.clearCache();
        log.info("Buy/Sell Indicator plugin stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("buysell".equals(event.getGroup()) && "analysisBundle".equals(event.getKey()))
        {
            analysisService.clearCache();
        }
    }

    @Provides
    BuySellIndicatorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BuySellIndicatorConfig.class);
    }
}
