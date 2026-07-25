package com.buysell;

import com.buysell.event.SignalUpdated;
import com.buysell.event.SignalsCleared;
import com.buysell.model.Signal;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;

/**
 * Sidebar panel showing Buy / Sell / Hold lists of observed items, with an
 * in-panel settings editor accessed via a gear button.
 */
@Slf4j
public class BuySellIndicatorPanel extends PluginPanel
{
    private static final String CARD_DASHBOARD = "dashboard";
    private static final String CARD_SETTINGS = "settings";

    private static final int BUY_TAB_INDEX = 0;
    private static final int SELL_TAB_INDEX = 1;
    private static final int HOLD_TAB_INDEX = 2;

    private final PriceAnalysisService analysisService;
    private final ItemManager itemManager;
    private final GraphOpeningService graphOpeningService;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
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
        this.graphOpeningService = graphOpeningService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildDashboard();
        settingsPanel = new BuySellIndicatorSettingsPanel(config, configManager, this::showDashboard);

        cardHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardHost.add(dashboard, CARD_DASHBOARD);
        cardHost.add(settingsPanel, CARD_SETTINGS);
        add(cardHost, BorderLayout.CENTER);

        showDashboard();
    }

    private void buildDashboard()
    {
        dashboard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dashboard.setBorder(new EmptyBorder(10, 10, 10, 10));

        settingsButton = PanelUi.iconButton(PanelUi.cogIcon(), "Open settings", "Settings");
        settingsButton.setName("settingsButton");
        settingsButton.addActionListener(e -> showSettings());

        dashboard.add(PanelUi.header("Buy / Sell Signals", null, settingsButton), BorderLayout.NORTH);

        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabs.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tabs.setFont(FontManager.getRunescapeSmallFont());
        tabs.addTab("Buy", wrapList(buyList));
        tabs.addTab("Sell", wrapList(sellList));
        tabs.addTab("Hold", wrapList(holdList));
        tabs.addChangeListener(e -> updateTabLabelColors());
        updateTabLabelColors();

        dashboard.add(tabs, BorderLayout.CENTER);
    }

    void showSettings()
    {
        settingsPanel.reloadFromConfig();
        cards.show(cardHost, CARD_SETTINGS);
        showingSettings = true;
        cardHost.revalidate();
        cardHost.repaint();
    }

    void showDashboard()
    {
        cards.show(cardHost, CARD_DASHBOARD);
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
            for (int i = 0; i < entries.size(); i++)
            {
                SignalListModel.ItemEntry entry = entries.get(i);
                list.add(new SignalItemPanel(
                    entry,
                    itemManager,
                    () -> graphOpeningService.openItemGraph(entry.getItemId())));
                if (i < entries.size() - 1)
                {
                    list.add(Box.createVerticalStrut(8));
                }
            }
        }
        list.revalidate();
        list.repaint();
    }

    private void updateTabTitle(int index, String title)
    {
        tabs.setTitleAt(index, title);
        updateTabLabelColors();
    }

    private void updateTabLabelColors()
    {
        for (int i = 0; i < tabs.getTabCount(); i++)
        {
            tabs.setForegroundAt(i, ColorScheme.LIGHT_GRAY_COLOR);
        }
        int selectedIndex = tabs.getSelectedIndex();
        if (selectedIndex >= 0)
        {
            // Selected tab text stays high-contrast for readability (not signal-colored).
            tabs.setForegroundAt(selectedIndex, Color.BLACK);
        }
    }

    private static JPanel newListPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    private static JScrollPane wrapList(JPanel list)
    {
        JScrollPane scroll = PanelUi.verticalScroll(list);
        scroll.setBorder(new EmptyBorder(8, 0, 0, 0));
        return scroll;
    }

    private static JLabel emptyLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(12, 4, 12, 4));
        return label;
    }
}
