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
import java.util.List;
import java.util.Map;

/**
 * Sidebar panel showing Buy / Sell / Hold lists of observed items, with an
 * in-panel settings editor accessed via a gear button.
 */
@Slf4j
public class BuySellIndicatorPanel extends PluginPanel
{
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

        JButton gear = new JButton("\u2699");
        SwingUtil.removeButtonDecorations(gear);
        gear.setToolTipText("Settings");
        gear.setPreferredSize(new Dimension(28, 28));
        gear.setForeground(Color.LIGHT_GRAY);
        gear.addActionListener(e -> showSettings());

        header.add(title, BorderLayout.CENTER);
        header.add(gear, BorderLayout.EAST);

        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.addTab("Buy", wrapList(buyList));
        tabs.addTab("Sell", wrapList(sellList));
        tabs.addTab("Hold", wrapList(holdList));

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

        tabs.setTitleAt(0, "Buy (" + partitioned.get(Signal.BUY).size() + ")");
        tabs.setTitleAt(1, "Sell (" + partitioned.get(Signal.SELL).size() + ")");
        tabs.setTitleAt(2, "Hold (" + partitioned.get(Signal.HOLD).size() + ")");
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
                return new Color(220, 50, 50);
            default:
                return new Color(160, 160, 160);
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
        label.setForeground(Color.GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(8, 4, 8, 4));
        return label;
    }
}
