package com.buysell;

import com.buysell.event.SignalUpdated;
import com.buysell.event.SignalsCleared;
import com.buysell.model.Signal;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.SwingUtil;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.List;
import java.util.Map;

/**
 * Sidebar panel showing Buy / Sell / Hold lists of observed items, with an
 * in-panel settings editor accessed via a gear button.
 */
@Slf4j
public class BuySellIndicatorPanel extends PluginPanel
{
    private static final int BUY_TAB_INDEX = 0;
    private static final int SELL_TAB_INDEX = 1;
    private static final int HOLD_TAB_INDEX = 2;

    private final PriceAnalysisService analysisService;
    private final ItemManager itemManager;
    private final BuySellIndicatorConfig config;
    private final ConfigManager configManager;
    private final GraphOpeningService graphOpeningService;

    private final JPanel cardHost = new JPanel(new BorderLayout());
    private final JPanel dashboard = new JPanel(new BorderLayout());
    private BuySellIndicatorSettingsPanel settingsPanel;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel buyList = newListPanel();
    private final JPanel sellList = newListPanel();
    private final JPanel holdList = newListPanel();
    private final JLabel emptyBuy = emptyLabel("No BUY signals yet");
    private final JLabel emptySell = emptyLabel("No SELL signals yet");
    private final JLabel emptyHold = emptyLabel("No HOLD signals yet");
    private JButton settingsButton;

    private boolean showingSettings;

    @Inject
    public BuySellIndicatorPanel(
        PriceAnalysisService analysisService,
        ItemManager itemManager,
        BuySellIndicatorConfig config,
        ConfigManager configManager,
        GraphOpeningService graphOpeningService)
    {
        super(false);
        this.analysisService = analysisService;
        this.itemManager = itemManager;
        this.config = config;
        this.configManager = configManager;
        this.graphOpeningService = graphOpeningService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildDashboard();
        settingsPanel = new BuySellIndicatorSettingsPanel(config, configManager, this::showDashboard);

        cardHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardHost.add(dashboard, BorderLayout.CENTER);
        add(cardHost, BorderLayout.CENTER);

        refreshLists();
    }

    private void buildDashboard()
    {
        dashboard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dashboard.setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel title = new JLabel("Buy / Sell Signals");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        settingsButton = new JButton(new CogIcon());
        SwingUtil.removeButtonDecorations(settingsButton);
        settingsButton.setToolTipText("Open settings");
        settingsButton.setPreferredSize(new Dimension(28, 28));
        settingsButton.setForeground(ColorScheme.TEXT_COLOR);
        settingsButton.setFocusPainted(false);
        settingsButton.getAccessibleContext().setAccessibleName("Settings");
        settingsButton.addActionListener(e -> showSettings());

        header.add(title, BorderLayout.CENTER);
        header.add(settingsButton, BorderLayout.EAST);

        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.addTab("Buy", wrapList(buyList));
        tabs.addTab("Sell", wrapList(sellList));
        tabs.addTab("Hold", wrapList(holdList));
        tabs.addChangeListener(e -> updateTabLabelColors());
        updateTabLabelColors();

        dashboard.add(header, BorderLayout.NORTH);
        dashboard.add(tabs, BorderLayout.CENTER);
    }

    void showSettings()
    {
        settingsPanel.reloadFromConfig();
        cardHost.removeAll();
        cardHost.add(settingsPanel, BorderLayout.CENTER);
        showingSettings = true;
        cardHost.revalidate();
        cardHost.repaint();
    }

    void showDashboard()
    {
        cardHost.removeAll();
        cardHost.add(dashboard, BorderLayout.CENTER);
        showingSettings = false;
        refreshLists();
        cardHost.revalidate();
        cardHost.repaint();
    }

    boolean isShowingSettings()
    {
        return showingSettings;
    }

    BuySellIndicatorSettingsPanel getSettingsPanel()
    {
        return settingsPanel;
    }

    JButton getSettingsButton()
    {
        return settingsButton;
    }

    String getTabLabelText(int index)
    {
        return tabs.getTitleAt(index);
    }

    Color getTabLabelColor(int index)
    {
        return tabs.getForegroundAt(index);
    }

    @Subscribe
    public void onSignalUpdated(SignalUpdated event)
    {
        SwingUtilities.invokeLater(this::refreshLists);
    }

    @Subscribe
    public void onSignalsCleared(SignalsCleared event)
    {
        SwingUtilities.invokeLater(this::refreshLists);
    }

    void refreshLists()
    {
        Map<Signal, List<SignalListModel.ItemEntry>> partitioned = SignalListModel.partition(
            analysisService.getCacheSnapshot(),
            itemId -> itemManager.getItemComposition(itemId).getName());

        populate(buyList, emptyBuy, partitioned.get(Signal.BUY));
        populate(sellList, emptySell, partitioned.get(Signal.SELL));
        populate(holdList, emptyHold, partitioned.get(Signal.HOLD));

        updateTabTitle(BUY_TAB_INDEX, "Buy (" + partitioned.get(Signal.BUY).size() + ")");
        updateTabTitle(SELL_TAB_INDEX, "Sell (" + partitioned.get(Signal.SELL).size() + ")");
        updateTabTitle(HOLD_TAB_INDEX, "Hold (" + partitioned.get(Signal.HOLD).size() + ")");
    }

    private void populate(JPanel list, JLabel empty, List<SignalListModel.ItemEntry> entries)
    {
        list.removeAll();
        if (entries == null || entries.isEmpty())
        {
            list.add(empty);
        }
        else
        {
            for (SignalListModel.ItemEntry entry : entries)
            {
                list.add(createRow(entry));
                list.add(Box.createVerticalStrut(4));
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel createRow(SignalListModel.ItemEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 4, 4, 4));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
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

        JLabel name = new JLabel(entry.getName());
        name.setForeground(Color.WHITE);

        JLabel conf = new JLabel(String.format("%.0f%%", entry.getConfidence()));
        conf.setForeground(signalColor(entry.getSignal()));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        row.add(conf, BorderLayout.EAST);

        row.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                graphOpeningService.openItemGraph(entry.getItemId());
            }
        });
        return row;
    }

    private static Color signalColor(Signal signal)
    {
        switch (signal)
        {
            case BUY:
                return new Color(0, 220, 80);
            case SELL:
                return new Color(255, 128, 128);
            default:
                return ColorScheme.TEXT_COLOR;
        }
    }

    private void updateTabTitle(int index, String title)
    {
        tabs.setTitleAt(index, title);
        updateTabLabelColors();
    }

    private void updateTabLabelColors()
    {
        int selectedIndex = tabs.getSelectedIndex();
        if (selectedIndex >= 0)
        {
            tabs.setForegroundAt(selectedIndex, Color.BLACK);
        }
    }

    private static JPanel newListPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(4, 0, 4, 0));
        return panel;
    }

    private static JScrollPane wrapList(JPanel list)
    {
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(ColorScheme.DARK_GRAY_COLOR);
        north.add(list, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(north);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JLabel emptyLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.TEXT_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(8, 4, 8, 4));
        return label;
    }

    /**
     * Draws a gear directly so its appearance does not depend on a font glyph
     * being available in the RuneLite client.
     */
    private static final class CogIcon implements Icon
    {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.isEnabled() ? ColorScheme.TEXT_COLOR : Color.GRAY);
                int centerX = x + SIZE / 2;
                int centerY = y + SIZE / 2;

                for (int angle = 0; angle < 360; angle += 45)
                {
                    g.fill(new Arc2D.Double(centerX - 3, centerY - 8, 6, 6, angle, 30, Arc2D.PIE));
                }
                g.fillOval(centerX - 6, centerY - 6, 12, 12);
                g.setColor(ColorScheme.DARK_GRAY_COLOR);
                g.fillOval(centerX - 2, centerY - 2, 4, 4);
            }
            finally
            {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }
}
