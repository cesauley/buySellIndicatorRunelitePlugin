package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Draws a BUY / SELL / HOLD label plus confidence percentage in the
 * bottom-left corner of every item tile in the inventory and bank.
 *
 * Rendering is non-blocking: if the analysis result is not yet cached the
 * overlay shows a small "…" indicator while the background fetch completes.
 */
public class BuySellIndicatorOverlay extends WidgetItemOverlay
{
    private static final Color COLOR_BUY  = new Color(0, 220, 80);
    private static final Color COLOR_SELL = new Color(220, 50, 50);
    private static final Color COLOR_HOLD = new Color(160, 160, 160);
    private static final Color COLOR_CONF = Color.WHITE;
    private static final Color COLOR_SHADOW = new Color(0, 0, 0, 180);

    private final PriceAnalysisService analysisService;
    private final BuySellIndicatorConfig config;

    @Inject
    public BuySellIndicatorOverlay(
        PriceAnalysisService analysisService,
        BuySellIndicatorConfig config)
    {
        this.analysisService = analysisService;
        this.config = config;

        showOnInventory();
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        // Respect config toggles by checking the widget's parent interface
        // The WidgetItemOverlay base class already guards these via showOnInventory/showOnBank,
        // but we re-check the config booleans in case the user toggled them at runtime.
        // (RuneLite will call renderItemOverlay for every visible item in registered interfaces.)
        // We cannot determine inventory vs bank from this method alone without checking widget IDs,
        // so we rely on the showOnInventory()/showOnBank() calls in the constructor together with
        // the plugin wiring that registers/unregisters the overlay based on config.

        SignalResult result = analysisService.getSignal(itemId);

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null)
        {
            return;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (result == null)
        {
            // Still loading — show a subtle ellipsis
            drawLoadingDot(graphics, bounds);
            return;
        }

        int pts = config.fontSize().getPts();
        Font signalFont = new Font(Font.SANS_SERIF, Font.BOLD, pts);
        Font confFont   = new Font(Font.SANS_SERIF, Font.PLAIN, pts - 1);

        String signalText = result.getSignal().name();
        String confText   = String.format("%.0f%%", result.getConfidence());

        Color signalColor;
        switch (result.getSignal())
        {
            case BUY:
                signalColor = COLOR_BUY;
                break;
            case SELL:
                signalColor = COLOR_SELL;
                break;
            default:
                signalColor = COLOR_HOLD;
                break;
        }

        // Position: bottom-left of the item tile, small padding
        graphics.setFont(signalFont);
        FontMetrics fm = graphics.getFontMetrics();
        int lineH = fm.getHeight();

        int x = bounds.x + 1;
        int ySignal = bounds.y + bounds.height - lineH / 2;
        int yConf   = ySignal - lineH + 2;

        drawShadowedString(graphics, confText,   confFont,   x, yConf,   COLOR_CONF);
        drawShadowedString(graphics, signalText, signalFont, x, ySignal, signalColor);
    }

    private void drawShadowedString(
        Graphics2D g,
        String text,
        Font font,
        int x,
        int y,
        Color color)
    {
        g.setFont(font);
        g.setColor(COLOR_SHADOW);
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawLoadingDot(Graphics2D g, Rectangle bounds)
    {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        g.setColor(COLOR_SHADOW);
        g.drawString("\u2026", bounds.x + 2, bounds.y + bounds.height - 1);
        g.setColor(COLOR_HOLD);
        g.drawString("\u2026", bounds.x + 1, bounds.y + bounds.height - 2);
    }
}
