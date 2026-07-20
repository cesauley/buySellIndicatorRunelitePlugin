package com.buysell;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises config defaults and enum helpers via an anonymous implementation.
 */
public class BuySellIndicatorConfigTest
{
    private final BuySellIndicatorConfig defaults = new BuySellIndicatorConfig()
    {
    };

    @Test
    public void defaults_matchInterfaceDefaults()
    {
        assertTrue(defaults.showOnInventory());
        assertTrue(defaults.showOnBank());
        assertEquals(40, defaults.minConfidence());
        assertEquals(50, defaults.minItemPrice());
        assertEquals(1_000_000_000, defaults.maxItemPrice());
        assertEquals(BuySellIndicatorConfig.FontSize.SMALL, defaults.fontSize());
        assertEquals(5, defaults.cacheMinutes());
        assertEquals(BuySellIndicatorConfig.AnalysisBundle.FLIPPING_WEEK_FLIP, defaults.analysisBundle());
        assertTrue(defaults.enableBankSignalFilter());
        assertEquals("", defaults.blacklistedItems());
    }

    @Test
    public void fontSize_points()
    {
        assertEquals(9, BuySellIndicatorConfig.FontSize.SMALL.getPts());
        assertEquals(11, BuySellIndicatorConfig.FontSize.MEDIUM.getPts());
        assertEquals(13, BuySellIndicatorConfig.FontSize.LARGE.getPts());
    }

    @Test
    public void analysisBundle_metadata()
    {
        BuySellIndicatorConfig.AnalysisBundle b = BuySellIndicatorConfig.AnalysisBundle.MERCHANTING_ZSCORE;
        assertEquals(BuySellIndicatorConfig.Playstyle.MERCHANTING, b.getPlaystyle());
        assertEquals(BuySellIndicatorConfig.AnalysisModel.ZSCORE, b.getModel());
        assertEquals("24h", b.getApiTimestep());
        assertEquals(95, b.getMaxCandles());
        assertEquals(20, b.getZScoreSmaPeriod());
        assertFalse(b.toString().isEmpty());
    }

    @Test
    public void analysisBundle_flipAndClassic()
    {
        BuySellIndicatorConfig.AnalysisBundle flip = BuySellIndicatorConfig.AnalysisBundle.FLIPPING_DAY_FLIP;
        assertEquals(BuySellIndicatorConfig.AnalysisModel.FLIP, flip.getModel());
        assertEquals(0, flip.getZScoreSmaPeriod());

        BuySellIndicatorConfig.AnalysisBundle classic = BuySellIndicatorConfig.AnalysisBundle.FLIPPING_DAY_CLASSIC_TA;
        assertEquals(BuySellIndicatorConfig.AnalysisModel.CLASSIC_TA, classic.getModel());
        assertEquals(BuySellIndicatorConfig.Playstyle.FLIPPING, classic.getPlaystyle());
    }

    @Test
    public void filterMode_cycleAndLabels()
    {
        assertEquals("B", BuySellIndicatorConfig.FilterMode.BUY_ONLY.getButtonLabel());
        assertEquals(BuySellIndicatorConfig.FilterMode.SELL_ONLY,
            BuySellIndicatorConfig.FilterMode.BUY_ONLY.next());
    }
}
