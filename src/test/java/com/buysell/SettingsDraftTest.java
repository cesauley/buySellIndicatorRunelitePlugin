package com.buysell;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SettingsDraftTest
{
    private BuySellIndicatorConfig config;

    @Before
    public void setUp()
    {
        config = mock(BuySellIndicatorConfig.class);
        when(config.showOnInventory()).thenReturn(true);
        when(config.showOnBank()).thenReturn(true);
        when(config.minConfidence()).thenReturn(40);
        when(config.minItemPrice()).thenReturn(50);
        when(config.maxItemPrice()).thenReturn(1_000_000_000);
        when(config.fontSize()).thenReturn(BuySellIndicatorConfig.FontSize.SMALL);
        when(config.cacheMinutes()).thenReturn(5);
        when(config.analysisBundle()).thenReturn(BuySellIndicatorConfig.AnalysisBundle.FLIPPING_WEEK_FLIP);
        when(config.enableBankSignalFilter()).thenReturn(true);
        when(config.blacklistedItems()).thenReturn("");
    }

    @Test
    public void fromConfig_copiesAllFields()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        assertTrue(d.isShowOnInventory());
        assertTrue(d.isShowOnBank());
        assertEquals(40, d.getMinConfidence());
        assertEquals(50, d.getMinItemPrice());
        assertEquals(1_000_000_000, d.getMaxItemPrice());
        assertEquals(BuySellIndicatorConfig.FontSize.SMALL, d.getFontSize());
        assertEquals(5, d.getCacheMinutes());
        assertEquals(BuySellIndicatorConfig.AnalysisBundle.FLIPPING_WEEK_FLIP, d.getAnalysisBundle());
        assertTrue(d.isEnableBankSignalFilter());
        assertEquals("", d.getBlacklistedItems());
    }

    @Test
    public void fromConfig_nullBlacklist_becomesEmpty()
    {
        when(config.blacklistedItems()).thenReturn(null);
        assertEquals("", SettingsDraft.fromConfig(config).getBlacklistedItems());
    }

    @Test
    public void validate_happyPath_returnsNull()
    {
        assertNull(SettingsDraft.fromConfig(config).validate());
    }

    @Test
    public void validate_minConfidenceOutOfRange()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setMinConfidence(-1);
        assertNotNull(d.validate());
        d.setMinConfidence(91);
        assertNotNull(d.validate());
    }

    @Test
    public void validate_negativePrices()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setMinItemPrice(-5);
        assertNotNull(d.validate());
        d.setMinItemPrice(0);
        d.setMaxItemPrice(-1);
        assertNotNull(d.validate());
    }

    @Test
    public void validate_minExceedsMax()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setMinItemPrice(100);
        d.setMaxItemPrice(50);
        assertNotNull(d.validate());
    }

    @Test
    public void validate_cacheMinutesOutOfRange()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setCacheMinutes(0);
        assertNotNull(d.validate());
        d.setCacheMinutes(61);
        assertNotNull(d.validate());
    }

    @Test
    public void validate_nullEnums()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setFontSize(null);
        assertNotNull(d.validate());
        d.setFontSize(BuySellIndicatorConfig.FontSize.MEDIUM);
        d.setAnalysisBundle(null);
        assertNotNull(d.validate());
    }

    @Test
    public void validate_nullBlacklist_normalized()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setBlacklistedItems(null);
        assertNull(d.validate());
        assertEquals("", d.getBlacklistedItems());
    }

    @Test
    public void isDirty_falseWhenUnchanged()
    {
        assertFalse(SettingsDraft.fromConfig(config).isDirty(config));
    }

    @Test
    public void isDirty_trueWhenChanged()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setMinConfidence(55);
        assertTrue(d.isDirty(config));
    }

    @Test
    public void isDirty_handlesNullBlacklistOnConfig()
    {
        when(config.blacklistedItems()).thenReturn(null);
        SettingsDraft d = SettingsDraft.fromConfig(config);
        assertFalse(d.isDirty(config));
        d.setBlacklistedItems("Coins");
        assertTrue(d.isDirty(config));
    }

    @Test
    public void toConfigValues_containsAllKeys()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        Map<String, String> values = d.toConfigValues();
        assertEquals("true", values.get("showOnInventory"));
        assertEquals("true", values.get("showOnBank"));
        assertEquals("40", values.get("minConfidence"));
        assertEquals("50", values.get("minItemPrice"));
        assertEquals("1000000000", values.get("maxItemPrice"));
        assertEquals("SMALL", values.get("fontSize"));
        assertEquals("5", values.get("cacheMinutes"));
        assertEquals("FLIPPING_WEEK_FLIP", values.get("analysisBundle"));
        assertEquals("true", values.get("enableBankSignalFilter"));
        assertEquals("", values.get("blacklistedItems"));
    }

    @Test
    public void toConfigValues_nullBlacklist_emptyString()
    {
        SettingsDraft d = SettingsDraft.fromConfig(config);
        d.setBlacklistedItems(null);
        assertEquals("", d.toConfigValues().get("blacklistedItems"));
    }

    @Test(expected = NullPointerException.class)
    public void fromConfig_null_throws()
    {
        SettingsDraft.fromConfig(null);
    }
}
