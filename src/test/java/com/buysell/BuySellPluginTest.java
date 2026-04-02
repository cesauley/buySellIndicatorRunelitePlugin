package com.buysell;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Test launcher that starts the RuneLite client in developer mode with the
 * Buy/Sell Indicator plugin pre-loaded.
 *
 * Run via Gradle:
 *   ./gradlew run
 *
 * Or directly in IntelliJ by right-clicking this class and choosing
 * "Run BuySellPluginTest.main()".
 *
 * The client will launch, prompt for login, and once you open your inventory
 * or bank you will see the buy/sell signals rendered on each item tile.
 */
public class BuySellPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(BuySellIndicatorPlugin.class);
        RuneLite.main(args);
    }
}
