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
        keyName = "minItemPrice",
        name = "Min Item Price (coins)",
        description = "Skip overlay for items whose current price is below this value. Set to 0 to disable.",
        position = 3
    )
    default int minItemPrice()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "maxItemPrice",
        name = "Max Item Price (coins)",
        description = "Skip overlay for items whose current price is above this value. Set to 0 to disable.",
        position = 4
    )
    default int maxItemPrice()
    {
        return 1_000_000_000;
    }

    @ConfigItem(
        keyName = "fontSize",
        name = "Font Size",
        description = "Size of the signal text drawn on items",
        position = 5
    )
    default FontSize fontSize()
    {
        return FontSize.SMALL;
    }

    @ConfigItem(
        keyName = "cacheMinutes",
        name = "Cache Duration (minutes)",
        description = "How many minutes to cache price analysis before refreshing. Lower = more API requests.",
        position = 6
    )
    @Range(min = 1, max = 60)
    default int cacheMinutes()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "analysisBundle",
        name = "Analysis bundle",
        description = "Combines playstyle (flipping vs long-term merchanting), price model, and Wiki API bar size."
            + " Flipping bundles use short horizons for round-trips; merchanting bundles use daily bars for buy-and-hold context."
            + " BUY/SELL meaning depends on the bundle — see plugin README.",
        position = 7
    )
    default AnalysisBundle analysisBundle()
    {
        return AnalysisBundle.FLIPPING_WEEK_FLIP;
    }

    enum Playstyle
    {
        FLIPPING,
        MERCHANTING
    }

    enum AnalysisModel
    {
        /** Range position + VWAP deviation + contrarian velocity */
        FLIP,
        /** EMA crossover + RSI-14 + short vs long VWAP trend (momentum-style) */
        CLASSIC_TA,
        /** Rolling z-score vs SMA on mids */
        ZSCORE
    }

    enum AnalysisBundle
    {
        FLIPPING_DAY_FLIP(
            Playstyle.FLIPPING,
            AnalysisModel.FLIP,
            "Flipping — day (5m), mean reversion",
            "5m",
            288,
            0),
        FLIPPING_WEEK_FLIP(
            Playstyle.FLIPPING,
            AnalysisModel.FLIP,
            "Flipping — week (1h), mean reversion",
            "1h",
            168,
            0),
        FLIPPING_DAY_CLASSIC_TA(
            Playstyle.FLIPPING,
            AnalysisModel.CLASSIC_TA,
            "Flipping — day (5m), classic TA",
            "5m",
            288,
            0),
        MERCHANTING_MONTHS_FLIP(
            Playstyle.MERCHANTING,
            AnalysisModel.FLIP,
            "Merchanting — months (daily), mean reversion",
            "24h",
            180,
            0),
        MERCHANTING_YEAR_FLIP(
            Playstyle.MERCHANTING,
            AnalysisModel.FLIP,
            "Merchanting — year (daily), mean reversion",
            "24h",
            365,
            0),
        MERCHANTING_ZSCORE(
            Playstyle.MERCHANTING,
            AnalysisModel.ZSCORE,
            "Merchanting — z-score (daily, ~3 mo)",
            "24h",
            95,
            20),
        MERCHANTING_SWING_CLASSIC_TA(
            Playstyle.MERCHANTING,
            AnalysisModel.CLASSIC_TA,
            "Merchanting — swing (daily), classic TA",
            "24h",
            180,
            0);

        private final Playstyle playstyle;
        private final AnalysisModel model;
        private final String label;
        private final String apiTimestep;
        private final int maxCandles;
        /** SMA window for ZSCORE model; ignored otherwise */
        private final int zScoreSmaPeriod;

        AnalysisBundle(Playstyle playstyle, AnalysisModel model, String label,
            String apiTimestep, int maxCandles, int zScoreSmaPeriod)
        {
            this.playstyle = playstyle;
            this.model = model;
            this.label = label;
            this.apiTimestep = apiTimestep;
            this.maxCandles = maxCandles;
            this.zScoreSmaPeriod = zScoreSmaPeriod;
        }

        @Override
        public String toString()
        {
            return label;
        }

        public Playstyle getPlaystyle()
        {
            return playstyle;
        }

        public AnalysisModel getModel()
        {
            return model;
        }

        public String getApiTimestep()
        {
            return apiTimestep;
        }

        public int getMaxCandles()
        {
            return maxCandles;
        }

        /** Used only when {@link #getModel()} is {@link AnalysisModel#ZSCORE} */
        public int getZScoreSmaPeriod()
        {
            return zScoreSmaPeriod;
        }
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

    /**
     * Runtime bank filter mode (cycled by the in-bank button). Declared on the config interface for
     * discoverability; the active mode is not persisted across sessions.
     */
    enum FilterMode
    {
        OFF("\u2013"),
        BUY_ONLY("B"),
        SELL_ONLY("S"),
        BUY_AND_SELL("BS");

        private final String buttonLabel;

        FilterMode(String buttonLabel)
        {
            this.buttonLabel = buttonLabel;
        }

        public String getButtonLabel()
        {
            return buttonLabel;
        }

        public FilterMode next()
        {
            FilterMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    @ConfigItem(
        keyName = "enableBankSignalFilter",
        name = "Bank signal filter button",
        description = "Show a button on the bank interface to filter items by BUY/SELL signal (sorted by confidence).",
        position = 8
    )
    default boolean enableBankSignalFilter()
    {
        return true;
    }

    @ConfigItem(
        keyName = "blacklistedItems",
        name = "Blacklisted Items",
        description = "Comma-separated list of item names to hide signals for (e.g. Coins, Rune essence).",
        position = 9
    )
    default String blacklistedItems()
    {
        return "";
    }
}
