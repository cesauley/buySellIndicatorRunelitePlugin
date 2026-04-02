package com.buysell;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
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
        + "using EMA crossover, RSI-14, and VWAP trend analysis of OSRS Wiki GE prices.",
    tags = {"buy", "sell", "ge", "grand exchange", "price", "indicator", "trading", "flipping",
        "bank", "inventory"}
)
public class BuySellIndicatorPlugin extends Plugin
{
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BuySellIndicatorOverlay overlay;

    @Inject
    private PriceAnalysisService analysisService;

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        log.info("Buy/Sell Indicator plugin started");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        analysisService.clearCache();
        log.info("Buy/Sell Indicator plugin stopped");
    }

    @Provides
    BuySellIndicatorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BuySellIndicatorConfig.class);
    }
}
