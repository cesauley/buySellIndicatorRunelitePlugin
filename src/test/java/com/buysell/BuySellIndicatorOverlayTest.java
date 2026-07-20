package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BuySellIndicatorOverlayTest
{
    private PriceAnalysisService analysisService;
    private BuySellIndicatorConfig config;
    private ItemManager itemManager;
    private BuySellIndicatorOverlay overlay;
    private Graphics2D graphics;
    private WidgetItem widgetItem;
    private ItemComposition composition;

    @Before
    public void setUp()
    {
        analysisService = mock(PriceAnalysisService.class);
        config = mock(BuySellIndicatorConfig.class);
        itemManager = mock(ItemManager.class);
        when(config.fontSize()).thenReturn(BuySellIndicatorConfig.FontSize.SMALL);
        when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        composition = mock(ItemComposition.class);
        when(composition.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);

        overlay = new BuySellIndicatorOverlay(analysisService, config, itemManager);

        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        graphics = img.createGraphics();
        widgetItem = mock(WidgetItem.class);
        when(widgetItem.getCanvasBounds()).thenReturn(new Rectangle(10, 10, 36, 36));
    }

    @Test
    public void render_nonTradeable_skips()
    {
        when(composition.isTradeable()).thenReturn(false);
        overlay.renderItemOverlay(graphics, 1, widgetItem);
        verify(analysisService, never()).getSignal(anyInt());
    }

    @Test
    public void render_loading_showsEllipsisPath()
    {
        when(analysisService.getSignal(1)).thenReturn(null);
        overlay.renderItemOverlay(graphics, 1, widgetItem);
        verify(analysisService).getSignal(1);
    }

    @Test
    public void render_filtered_skipsDrawing()
    {
        when(analysisService.getSignal(1))
            .thenReturn(new SignalResult(Signal.FILTERED, 0, 1));
        overlay.renderItemOverlay(graphics, 1, widgetItem);
    }

    @Test
    public void render_buySellHold_draws()
    {
        when(analysisService.getSignal(1)).thenReturn(new SignalResult(Signal.BUY, 80, 1));
        overlay.renderItemOverlay(graphics, 1, widgetItem);

        when(analysisService.getSignal(2)).thenReturn(new SignalResult(Signal.SELL, 70, 1));
        overlay.renderItemOverlay(graphics, 2, widgetItem);

        when(analysisService.getSignal(3)).thenReturn(new SignalResult(Signal.HOLD, 20, 1));
        overlay.renderItemOverlay(graphics, 3, widgetItem);
    }

    @Test
    public void render_nullBounds_returnsEarly()
    {
        when(widgetItem.getCanvasBounds()).thenReturn(null);
        when(analysisService.getSignal(1)).thenReturn(new SignalResult(Signal.BUY, 80, 1));
        overlay.renderItemOverlay(graphics, 1, widgetItem);
    }

    @Test
    public void render_mediumAndLargeFonts()
    {
        when(config.fontSize()).thenReturn(BuySellIndicatorConfig.FontSize.MEDIUM);
        when(analysisService.getSignal(1)).thenReturn(new SignalResult(Signal.BUY, 55, 1));
        overlay.renderItemOverlay(graphics, 1, widgetItem);

        when(config.fontSize()).thenReturn(BuySellIndicatorConfig.FontSize.LARGE);
        overlay.renderItemOverlay(graphics, 1, widgetItem);
    }
}
