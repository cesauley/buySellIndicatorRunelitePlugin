package com.buysell;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Map;
import java.util.Objects;

/**
 * Staged settings editor hosted inside the plugin sidebar panel.
 */
public class BuySellIndicatorSettingsPanel extends JPanel
{
    private static final String CONFIG_GROUP = "buysell";

    private final BuySellIndicatorConfig config;
    private final ConfigManager configManager;
    private final Runnable onBack;

    private SettingsDraft draft;

    private JCheckBox showOnInventory;
    private JCheckBox showOnBank;
    private JSpinner minConfidence;
    private JSpinner minItemPrice;
    private JSpinner maxItemPrice;
    private JComboBox<BuySellIndicatorConfig.FontSize> fontSize;
    private JSpinner cacheMinutes;
    private JComboBox<BuySellIndicatorConfig.AnalysisBundle> analysisBundle;
    private JCheckBox enableBankSignalFilter;
    private JTextArea blacklistedItems;
    private JLabel statusLabel;

    public BuySellIndicatorSettingsPanel(
        BuySellIndicatorConfig config,
        ConfigManager configManager,
        Runnable onBack)
    {
        this.config = Objects.requireNonNull(config, "config");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.onBack = Objects.requireNonNull(onBack, "onBack");

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(6, 6, 6, 6));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildFormScroll(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        reloadFromConfig();
    }

    public void reloadFromConfig()
    {
        draft = SettingsDraft.fromConfig(config);
        applyDraftToControls();
        setStatus("");
    }

    public SettingsDraft getDraft()
    {
        syncDraftFromControls();
        return draft;
    }

    boolean tryGoBack()
    {
        syncDraftFromControls();
        if (!draft.isDirty(config))
        {
            onBack.run();
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Discard unsaved changes?",
            "Unsaved changes",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION)
        {
            reloadFromConfig();
            onBack.run();
            return true;
        }
        return false;
    }

    boolean save()
    {
        syncDraftFromControls();
        String error = draft.validate();
        if (error != null)
        {
            setStatus(error);
            return false;
        }
        for (Map.Entry<String, String> entry : draft.toConfigValues().entrySet())
        {
            configManager.setConfiguration(CONFIG_GROUP, entry.getKey(), entry.getValue());
        }
        setStatus("Saved.");
        return true;
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));

        JButton back = new JButton("\u2190");
        SwingUtil.removeButtonDecorations(back);
        back.setToolTipText("Back");
        back.setPreferredSize(new Dimension(28, 28));
        back.addActionListener(e -> tryGoBack());

        JLabel title = new JLabel("Settings");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        header.add(back, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        return header;
    }

    private JScrollPane buildFormScroll()
    {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(ColorScheme.DARK_GRAY_COLOR);

        showOnInventory = new JCheckBox("Show on Inventory");
        showOnBank = new JCheckBox("Show on Bank");
        enableBankSignalFilter = new JCheckBox("Bank signal filter button");
        styleCheck(showOnInventory);
        styleCheck(showOnBank);
        styleCheck(enableBankSignalFilter);

        minConfidence = new JSpinner(new SpinnerNumberModel(40, 0, 90, 1));
        minItemPrice = new JSpinner(new SpinnerNumberModel(50, 0, Integer.MAX_VALUE, 1));
        maxItemPrice = new JSpinner(new SpinnerNumberModel(1_000_000_000, 0, Integer.MAX_VALUE, 1));
        cacheMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        fontSize = new JComboBox<>(BuySellIndicatorConfig.FontSize.values());
        analysisBundle = new JComboBox<>(BuySellIndicatorConfig.AnalysisBundle.values());
        blacklistedItems = new JTextArea(3, 16);
        blacklistedItems.setLineWrap(true);
        blacklistedItems.setWrapStyleWord(true);
        blacklistedItems.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        blacklistedItems.setForeground(Color.WHITE);
        blacklistedItems.setCaretColor(Color.WHITE);

        form.add(labeled("Show on Inventory", showOnInventory));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Show on Bank", showOnBank));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Minimum Confidence (%)", minConfidence));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Min Item Price (coins)", minItemPrice));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Max Item Price (coins)", maxItemPrice));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Font Size", fontSize));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Cache Duration (minutes)", cacheMinutes));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Analysis bundle", analysisBundle));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Bank signal filter button", enableBankSignalFilter));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Blacklisted Items", new JScrollPane(blacklistedItems)));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildFooter()
    {
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(220, 180, 80));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        JButton save = new JButton("Save");
        save.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.add(save);

        footer.add(statusLabel, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        return footer;
    }

    private void applyDraftToControls()
    {
        showOnInventory.setSelected(draft.isShowOnInventory());
        showOnBank.setSelected(draft.isShowOnBank());
        minConfidence.setValue(draft.getMinConfidence());
        minItemPrice.setValue(draft.getMinItemPrice());
        maxItemPrice.setValue(draft.getMaxItemPrice());
        fontSize.setSelectedItem(draft.getFontSize());
        cacheMinutes.setValue(draft.getCacheMinutes());
        analysisBundle.setSelectedItem(draft.getAnalysisBundle());
        enableBankSignalFilter.setSelected(draft.isEnableBankSignalFilter());
        blacklistedItems.setText(draft.getBlacklistedItems() == null ? "" : draft.getBlacklistedItems());
    }

    private void syncDraftFromControls()
    {
        draft.setShowOnInventory(showOnInventory.isSelected());
        draft.setShowOnBank(showOnBank.isSelected());
        draft.setMinConfidence((Integer) minConfidence.getValue());
        draft.setMinItemPrice((Integer) minItemPrice.getValue());
        draft.setMaxItemPrice((Integer) maxItemPrice.getValue());
        draft.setFontSize((BuySellIndicatorConfig.FontSize) fontSize.getSelectedItem());
        draft.setCacheMinutes((Integer) cacheMinutes.getValue());
        draft.setAnalysisBundle((BuySellIndicatorConfig.AnalysisBundle) analysisBundle.getSelectedItem());
        draft.setEnableBankSignalFilter(enableBankSignalFilter.isSelected());
        draft.setBlacklistedItems(blacklistedItems.getText());
    }

    private void setStatus(String text)
    {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
    }

    private static void styleCheck(JCheckBox box)
    {
        box.setBackground(ColorScheme.DARK_GRAY_COLOR);
        box.setForeground(Color.WHITE);
        box.setFocusPainted(false);
    }

    private static JPanel labeled(String title, Component field)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel label = new JLabel(title);
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, field instanceof JScrollPane ? 90 : 52));
        return panel;
    }
}
