package com.buysell;

import com.buysell.model.Signal;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Time Tracking-style list row: item icon, two-line text, fixed confidence affordance.
 */
@Slf4j
final class SignalItemPanel extends JPanel
{
    private static final Color HOVER_COLOR = ColorScheme.DARKER_GRAY_HOVER_COLOR;

    private final JPanel textContainer;
    private final Runnable onOpen;

    SignalItemPanel(SignalListModel.ItemEntry entry, ItemManager itemManager, Runnable onOpen)
    {
        this.onOpen = onOpen;

        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(7, 7, 7, 7));
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setMinimumSize(new Dimension(32, 32));
        try
        {
            AsyncBufferedImage image = itemManager.getImage(entry.getItemId());
            if (image != null)
            {
                image.addTo(iconLabel);
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load item image for {}: {}", entry.getItemId(), e.getMessage());
        }
        add(iconLabel, BorderLayout.WEST);

        textContainer = new JPanel(new GridLayout(2, 1));
        textContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textContainer.setBorder(new EmptyBorder(5, 7, 5, 7));

        JLabel titleLabel = new JLabel(entry.getName());
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel statusLabel = new JLabel(signalSummary(entry.getSignal()));
        statusLabel.setForeground(signalColor(entry.getSignal()));
        statusLabel.setFont(FontManager.getRunescapeSmallFont());

        textContainer.add(titleLabel);
        textContainer.add(statusLabel);
        add(textContainer, BorderLayout.CENTER);

        JLabel confidenceLabel = new JLabel(String.format("%.0f%%", entry.getConfidence()));
        confidenceLabel.setForeground(signalColor(entry.getSignal()));
        confidenceLabel.setFont(FontManager.getRunescapeBoldFont());
        confidenceLabel.setHorizontalAlignment(JLabel.RIGHT);
        confidenceLabel.setPreferredSize(new Dimension(42, 32));
        add(confidenceLabel, BorderLayout.EAST);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                setHighlighted(false);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                setHighlighted(true);
                if (SignalItemPanel.this.onOpen != null)
                {
                    SignalItemPanel.this.onOpen.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setHighlighted(true);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setHighlighted(false);
            }
        });
    }

    private void setHighlighted(boolean highlighted)
    {
        Color bg = highlighted ? HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR;
        setBackground(bg);
        textContainer.setBackground(bg);
        setCursor(highlighted ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    private static String signalSummary(Signal signal)
    {
        switch (signal)
        {
            case BUY:
                return "Buy signal";
            case SELL:
                return "Sell signal";
            case HOLD:
                return "Hold signal";
            default:
                return "Filtered";
        }
    }

    private static Color signalColor(Signal signal)
    {
        switch (signal)
        {
            case BUY:
                return PanelUi.BUY_COLOR;
            case SELL:
                return PanelUi.SELL_COLOR;
            default:
                return ColorScheme.TEXT_COLOR;
        }
    }
}
