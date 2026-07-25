package com.buysell;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
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
import java.awt.FlowLayout;
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
    private JButton backButton;
    private JButton saveButton;

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
        setBorder(new EmptyBorder(10, 10, 10, 10));

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
        backButton = PanelUi.iconButton(PanelUi.backIcon(), "Back to signals", "Back to signals");
        backButton.setName("backButton");
        backButton.addActionListener(e -> tryGoBack());
        return PanelUi.header("Settings", backButton, null);
    }

    private JScrollPane buildFormScroll()
    {
        PanelUi.WidthTrackingPanel form = PanelUi.formColumn();

        showOnInventory = namedCheckBox("showOnInventory");
        showOnBank = namedCheckBox("showOnBank");
        enableBankSignalFilter = namedCheckBox("enableBankSignalFilter");

        minConfidence = new JSpinner(new SpinnerNumberModel(40, 0, 90, 1));
        minConfidence.setName("minConfidence");
        minItemPrice = new JSpinner(new SpinnerNumberModel(50, 0, Integer.MAX_VALUE, 1));
        minItemPrice.setName("minItemPrice");
        maxItemPrice = new JSpinner(new SpinnerNumberModel(1_000_000_000, 0, Integer.MAX_VALUE, 1));
        maxItemPrice.setName("maxItemPrice");
        cacheMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        cacheMinutes.setName("cacheMinutes");
        fontSize = new JComboBox<>(BuySellIndicatorConfig.FontSize.values());
        fontSize.setName("fontSize");
        analysisBundle = new JComboBox<>(BuySellIndicatorConfig.AnalysisBundle.values());
        analysisBundle.setName("analysisBundle");
        blacklistedItems = new JTextArea(3, 16);
        blacklistedItems.setName("blacklistedItems");

        form.add(PanelUi.sectionTitle("Display"));
        form.add(PanelUi.checkboxRow("Show on Inventory", showOnInventory));
        form.add(PanelUi.strut(4));
        form.add(PanelUi.checkboxRow("Show on Bank", showOnBank));
        form.add(PanelUi.strut(4));
        form.add(PanelUi.labeledField("Font Size", fontSize));
        form.add(PanelUi.strut(10));

        form.add(PanelUi.sectionTitle("Analysis"));
        form.add(PanelUi.labeledField("Minimum Confidence (%)", minConfidence));
        form.add(PanelUi.strut(6));
        form.add(PanelUi.labeledField("Min Item Price (coins)", minItemPrice));
        form.add(PanelUi.strut(6));
        form.add(PanelUi.labeledField("Max Item Price (coins)", maxItemPrice));
        form.add(PanelUi.strut(6));
        form.add(PanelUi.labeledField("Cache Duration (minutes)", cacheMinutes));
        form.add(PanelUi.strut(6));
        form.add(PanelUi.labeledField("Analysis bundle", analysisBundle));
        form.add(PanelUi.strut(10));

        form.add(PanelUi.sectionTitle("Filtering"));
        form.add(PanelUi.checkboxRow("Bank signal filter button", enableBankSignalFilter));
        form.add(PanelUi.strut(6));
        form.add(PanelUi.labeledField("Blacklisted Items", new JScrollPane(blacklistedItems)));
        form.add(Box.createVerticalGlue());

        return PanelUi.formScroll(form);
    }

    private JPanel buildFooter()
    {
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        statusLabel = new JLabel(" ");
        statusLabel.setName("statusLabel");
        statusLabel.setForeground(PanelUi.STATUS_COLOR);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());

        saveButton = new JButton("Save");
        saveButton.setName("saveButton");
        saveButton.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.add(saveButton);

        footer.add(statusLabel, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        return footer;
    }

    private static JCheckBox namedCheckBox(String name)
    {
        JCheckBox box = new JCheckBox();
        box.setName(name);
        return box;
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

    JButton getBackButton()
    {
        return backButton;
    }

    JCheckBox getShowOnInventoryCheckbox()
    {
        return showOnInventory;
    }

    JSpinner getMinConfidenceSpinner()
    {
        return minConfidence;
    }
}
