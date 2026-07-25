package com.buysell;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BuySellIndicatorSettingsPanelTest
{
    private BuySellIndicatorConfig config;
    private ConfigManager configManager;

    @Before
    public void setUp()
    {
        config = mock(BuySellIndicatorConfig.class);
        configManager = mock(ConfigManager.class);
        when(config.showOnInventory()).thenReturn(true);
        when(config.showOnBank()).thenReturn(false);
        when(config.minConfidence()).thenReturn(40);
        when(config.minItemPrice()).thenReturn(50);
        when(config.maxItemPrice()).thenReturn(1000);
        when(config.fontSize()).thenReturn(BuySellIndicatorConfig.FontSize.SMALL);
        when(config.cacheMinutes()).thenReturn(5);
        when(config.analysisBundle()).thenReturn(BuySellIndicatorConfig.AnalysisBundle.FLIPPING_WEEK_FLIP);
        when(config.enableBankSignalFilter()).thenReturn(true);
        when(config.blacklistedItems()).thenReturn("Coins");
    }

    @Test
    public void save_persistsValues() throws Exception
    {
        AtomicBoolean ok = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () ->
            {
            });
            ok.set(panel.save());
        });
        assertTrue(ok.get());
        verify(configManager).setConfiguration(eq("buysell"), eq("showOnBank"), eq("false"));
        verify(configManager).setConfiguration(eq("buysell"), eq("blacklistedItems"), eq("Coins"));
    }

    @Test
    public void tryGoBack_clean_invokesCallback() throws Exception
    {
        AtomicBoolean backCalled = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () -> backCalled.set(true));
            assertTrue(panel.tryGoBack());
        });
        assertTrue(backCalled.get());
    }

    @Test
    public void getDraft_reflectsControlEdits() throws Exception
    {
        AtomicReference<SettingsDraft> draft = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () ->
            {
            });
            assertNotNull(panel.getShowOnInventoryCheckbox());
            panel.getShowOnInventoryCheckbox().setSelected(false);
            draft.set(panel.getDraft());
        });
        assertFalse(draft.get().isShowOnInventory());
    }

    @Test
    public void save_invalidDraft_returnsFalse() throws Exception
    {
        AtomicBoolean ok = new AtomicBoolean(true);
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () ->
            {
            });
            JSpinner minConf = panel.getMinConfidenceSpinner();
            SpinnerNumberModel model = (SpinnerNumberModel) minConf.getModel();
            model.setMaximum(200);
            minConf.setValue(99);
            ok.set(panel.save());
        });
        assertFalse(ok.get());
    }

    @Test
    public void reloadFromConfig_resetsDirtyState() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () ->
            {
            });
            panel.getDraft().setMinConfidence(11);
            panel.reloadFromConfig();
            assertFalse(panel.getDraft().isDirty(config));
        });
    }

    @Test
    public void navigationControls_haveStableNames() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () ->
            {
            });
            assertNotNull(panel.getBackButton().getIcon());
            assertEqualsName(panel.getBackButton().getName(), "backButton");
            assertEqualsName(panel.getShowOnInventoryCheckbox().getName(), "showOnInventory");
            assertEqualsName(panel.getMinConfidenceSpinner().getName(), "minConfidence");
        });
    }

    private static void assertEqualsName(String actual, String expected)
    {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
