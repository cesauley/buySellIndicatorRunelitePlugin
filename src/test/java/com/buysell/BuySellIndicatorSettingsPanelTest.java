package com.buysell;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
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
            AtomicBoolean backCalled = new AtomicBoolean();
            BuySellIndicatorSettingsPanel panel = new BuySellIndicatorSettingsPanel(
                config, configManager, () -> backCalled.set(true));
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
            JCheckBox inventory = findFirst(panel, JCheckBox.class);
            assertNotNull(inventory);
            inventory.setSelected(false);
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
            List<JSpinner> spinners = findAll(panel, JSpinner.class);
            JSpinner minConf = spinners.get(0);
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

    private static <T extends Component> T findFirst(Container root, Class<T> type)
    {
        for (T c : findAll(root, type))
        {
            return c;
        }
        return null;
    }

    private static <T extends Component> List<T> findAll(Container root, Class<T> type)
    {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    private static <T extends Component> void collect(Container root, Class<T> type, List<T> out)
    {
        for (Component c : root.getComponents())
        {
            if (type.isInstance(c))
            {
                out.add(type.cast(c));
            }
            if (c instanceof Container)
            {
                collect((Container) c, type, out);
            }
        }
    }
}
