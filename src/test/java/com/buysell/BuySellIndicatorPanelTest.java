package com.buysell;

import com.buysell.event.SignalUpdated;
import com.buysell.event.SignalsCleared;
import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BuySellIndicatorPanelTest
{
    private PriceAnalysisService analysisService;
    private ItemManager itemManager;
    private BuySellIndicatorConfig config;
    private ConfigManager configManager;
    private GraphOpeningService graphOpeningService;
    private BuySellIndicatorPanel panel;

    @Before
    public void setUp() throws Exception
    {
        analysisService = mock(PriceAnalysisService.class);
        itemManager = mock(ItemManager.class);
        config = mock(BuySellIndicatorConfig.class);
        configManager = mock(ConfigManager.class);
        graphOpeningService = mock(GraphOpeningService.class);

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
        when(analysisService.getCacheSnapshot()).thenReturn(new HashMap<>());

        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getName()).thenReturn("Abyssal whip");
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
        when(itemManager.getImage(anyInt())).thenReturn(null);

        SwingUtilities.invokeAndWait(() ->
            panel = new BuySellIndicatorPanel(
                analysisService, itemManager, config, configManager, graphOpeningService));
    }

    @Test
    public void showSettings_andBack_togglesView() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            assertFalse(panel.isShowingSettings());
            panel.showSettings();
            assertTrue(panel.isShowingSettings());
            panel.showDashboard();
            assertFalse(panel.isShowingSettings());
        });
    }

    @Test
    public void refreshLists_withSignals_updatesTabs() throws Exception
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(4151, new SignalResult(Signal.BUY, 90, 1));
        snap.put(4152, new SignalResult(Signal.SELL, 80, 1));
        snap.put(4153, new SignalResult(Signal.HOLD, 10, 1));
        snap.put(4154, new SignalResult(Signal.FILTERED, 0, 1));
        when(analysisService.getCacheSnapshot()).thenReturn(snap);

        AsyncBufferedImage image = mock(AsyncBufferedImage.class);
        when(itemManager.getImage(anyInt())).thenReturn(image);

        SwingUtilities.invokeAndWait(panel::refreshLists);
        verify(analysisService, org.mockito.Mockito.atLeastOnce()).getCacheSnapshot();
    }

    @Test
    public void onSignalUpdated_schedulesRefresh() throws Exception
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(1, new SignalResult(Signal.BUY, 50, 1));
        when(analysisService.getCacheSnapshot()).thenReturn(snap);
        SwingUtilities.invokeAndWait(() ->
            panel.onSignalUpdated(new SignalUpdated(1, new SignalResult(Signal.BUY, 50, 1))));
        // allow EDT invokeLater from subscriber if any nested
        SwingUtilities.invokeAndWait(() ->
        {
        });
        verify(analysisService, org.mockito.Mockito.atLeastOnce()).getCacheSnapshot();
    }

    @Test
    public void onSignalsCleared_schedulesRefresh() throws Exception
    {
        when(analysisService.getCacheSnapshot()).thenReturn(new HashMap<>());
        SwingUtilities.invokeAndWait(() -> panel.onSignalsCleared(new SignalsCleared()));
        SwingUtilities.invokeAndWait(() ->
        {
        });
        verify(analysisService, org.mockito.Mockito.atLeastOnce()).getCacheSnapshot();
    }

    @Test
    public void settingsPanel_save_writesConfig() throws Exception
    {
        AtomicBoolean saved = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() ->
        {
            panel.showSettings();
            saved.set(panel.getSettingsPanel().save());
        });
        assertTrue(saved.get());
        verify(configManager).setConfiguration(eq("buysell"), eq("minConfidence"), anyString());
    }
}
