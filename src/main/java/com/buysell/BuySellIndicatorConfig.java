package com.buysell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("buysell")
public interface BuySellIndicatorConfig extends Config
{
    @ConfigItem(
        keyName = "showOnInventory",
        name = "Show on Inventory",
        description = "Display buy/sell signals on items in your inventory",
        position = 0
    )
    default boolean showOnInventory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showOnBank",
        name = "Show on Bank",
        description = "Display buy/sell signals on items in your bank",
        position = 1
    )
    default boolean showOnBank()
    {
        return true;
    }

    @Range(min = 0, max = 90)
    @ConfigItem(
        keyName = "minConfidence",
        name = "Minimum Confidence (%)",
        description = "Only show signals at or above this confidence level. Below this threshold shows HOLD.",
        position = 2
    )
    default int minConfidence()
    {
        return 40;
    }

    @ConfigItem(
        keyName = "fontSize",
        name = "Font Size",
        description = "Size of the signal text drawn on items",
        position = 3
    )
    default FontSize fontSize()
    {
        return FontSize.SMALL;
    }

    @ConfigItem(
        keyName = "cacheMinutes",
        name = "Cache Duration (minutes)",
        description = "How many minutes to cache price analysis before refreshing. Lower = more API requests.",
        position = 4
    )
    @Range(min = 1, max = 60)
    default int cacheMinutes()
    {
        return 5;
    }

    enum FontSize
    {
        SMALL(9),
        MEDIUM(11),
        LARGE(13);

        private final int pts;

        FontSize(int pts)
        {
            this.pts = pts;
        }

        public int getPts()
        {
            return pts;
        }
    }
}
